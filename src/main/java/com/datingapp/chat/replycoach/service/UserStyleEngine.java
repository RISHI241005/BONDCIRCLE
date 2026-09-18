package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.entity.FeedbackAction;
import com.datingapp.chat.replycoach.model.ConversationContext.ContextMessage;
import com.datingapp.chat.replycoach.model.UserWritingProfile;
import com.datingapp.chat.replycoach.model.UserWritingProfile.EmojiUsage;
import com.datingapp.chat.replycoach.model.UserWritingProfile.Formality;
import com.datingapp.chat.replycoach.model.UserWritingProfile.LengthPreference;
import com.datingapp.chat.replycoach.repository.AiReplyFeedbackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserStyleEngine {

    private static final Logger log = LoggerFactory.getLogger(UserStyleEngine.class);
    private final AiReplyFeedbackRepository feedbackRepository;

    public UserStyleEngine(AiReplyFeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    public UserWritingProfile analyzeStyle(Long userId, List<ContextMessage> messages, String detectedLanguage) {
        List<ContextMessage> userMessages = messages != null ? messages.stream()
                .filter(ContextMessage::isCurrentUser)
                .toList() : List.of();

        StringBuilder directives = new StringBuilder();

        // 1. Inspect recent USED suggestions from feedback repository
        LengthPreference lengthPreference = LengthPreference.MEDIUM;
        EmojiUsage emojiUsage = EmojiUsage.OCCASIONAL;
        Formality formality = Formality.CASUAL;

        try {
            List<String> usedTexts = feedbackRepository.findRecentUsedTexts(userId, PageRequest.of(0, 8));
            if (!usedTexts.isEmpty()) {
                double avgLen = usedTexts.stream().mapToInt(String::length).average().orElse(35);
                if (avgLen < 30) {
                    lengthPreference = LengthPreference.SHORT;
                    directives.append("User consistently prefers short, punchy replies. ");
                } else if (avgLen > 70) {
                    lengthPreference = LengthPreference.LONG;
                    directives.append("User appreciates expressive, descriptive replies. ");
                }

                boolean usesEmoji = usedTexts.stream().anyMatch(t -> t.codePoints().anyMatch(Character::isEmoji));
                if (usesEmoji) {
                    emojiUsage = EmojiUsage.FREQUENT;
                    directives.append("Include fitting emojis naturally. ");
                }
            }
        } catch (Exception ex) {
            log.debug("Feedback analysis skipped: {}", ex.getMessage());
        }

        // 2. Inspect sent dialogue messages
        if (!userMessages.isEmpty()) {
            double avgMsgLen = userMessages.stream()
                    .mapToInt(m -> m.content() != null ? m.content().length() : 0)
                    .average().orElse(30);

            if (avgMsgLen <= 22) {
                lengthPreference = LengthPreference.SHORT;
                directives.append("Keep replies casual, relaxed and brief. ");
            }

            boolean anyEmoji = userMessages.stream()
                    .anyMatch(m -> m.content() != null && m.content().codePoints().anyMatch(Character::isEmoji));
            if (anyEmoji && emojiUsage == EmojiUsage.OCCASIONAL) {
                directives.append("Use emojis occasionally like the user does. ");
            }

            boolean hasPunctuation = userMessages.stream()
                    .anyMatch(m -> m.content() != null && (m.content().endsWith(".") || m.content().endsWith("!")));
            if (hasPunctuation) {
                formality = Formality.PUNCTUATED;
            }
        }

        if (directives.isEmpty()) {
            directives.append("Keep replies conversational, relaxed, and natural. ");
        }

        return new UserWritingProfile(
                lengthPreference,
                emojiUsage,
                detectedLanguage != null ? detectedLanguage : "ENGLISH",
                formality,
                "HINGLISH".equalsIgnoreCase(detectedLanguage),
                true,
                directives.toString().trim()
        );
    }
}
