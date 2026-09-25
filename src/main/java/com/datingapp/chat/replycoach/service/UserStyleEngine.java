package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.entity.FeedbackAction;
import com.datingapp.chat.replycoach.model.ConversationContext.ContextMessage;
import com.datingapp.chat.replycoach.model.PartnerCommunicationProfile;
import com.datingapp.chat.replycoach.model.ReplyStrategy;
import com.datingapp.chat.replycoach.model.UserWritingProfile;
import com.datingapp.chat.replycoach.model.UserWritingProfile.EmojiUsage;
import com.datingapp.chat.replycoach.model.UserWritingProfile.Formality;
import com.datingapp.chat.replycoach.model.UserWritingProfile.LengthPreference;
import com.datingapp.chat.replycoach.repository.AiReplyFeedbackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class UserStyleEngine {

    private static final Logger log = LoggerFactory.getLogger(UserStyleEngine.class);
    private static final Pattern SLANG_TOKEN_PATTERN = Pattern.compile(
            "\\b(bro|bhai|yaar|scene|chill|mast|sahi|boss|tbh|ngl|fr|idk|smh|btw|imo|haan|accha|acha)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final AiReplyFeedbackRepository feedbackRepository;

    public UserStyleEngine(AiReplyFeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    public UserWritingProfile analyzeStyle(Long userId, List<ContextMessage> messages, String detectedLanguage) {
        List<ContextMessage> userMessages = messages != null ? messages.stream()
                .filter(ContextMessage::isCurrentUser)
                .toList() : List.of();

        StringBuilder directives = new StringBuilder();
        StringBuilder negativeDirectives = new StringBuilder();

        LengthPreference lengthPreference = LengthPreference.MEDIUM;
        EmojiUsage emojiUsage = EmojiUsage.OCCASIONAL;
        Formality formality = Formality.CASUAL;

        Set<String> slangTokens = new HashSet<>();
        List<String> favoriteEmojis = new ArrayList<>();
        Set<ReplyStrategy> preferredStrategies = new HashSet<>();
        Set<ReplyStrategy> rejectedStrategies = new HashSet<>();

        double avgLen = 35.0;
        int medianLen = 30;
        double hinglishRatio = "HINGLISH".equalsIgnoreCase(detectedLanguage) ? 0.4 : 0.0;

        // 1. Inspect recent USED and LIKED suggestions from feedback repository
        try {
            List<String> usedTexts = feedbackRepository.findRecentPositiveTexts(userId, PageRequest.of(0, 20));
            if (usedTexts == null || usedTexts.isEmpty()) {
                // Compatibility with existing repositories/mocks while the
                // broader positive-action model rolls out.
                usedTexts = feedbackRepository.findRecentUsedTexts(userId, PageRequest.of(0, 10));
            }
            if (usedTexts == null) usedTexts = List.of();
            if (!usedTexts.isEmpty()) {
                avgLen = usedTexts.stream().mapToInt(String::length).average().orElse(35);
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

                // Infer preferred strategies based on phrasing
                for (String ut : usedTexts) {
                    String lower = ut.toLowerCase(Locale.ROOT);
                    if (lower.contains("?") && (lower.contains("kya") || lower.contains("how") || lower.contains("what"))) {
                        preferredStrategies.add(ReplyStrategy.ASK_FOLLOWUP);
                        preferredStrategies.add(ReplyStrategy.CURIOUS);
                    }
                    if (lower.contains("😂") || lower.contains("haha") || lower.contains("seriously")) {
                        preferredStrategies.add(ReplyStrategy.PLAYFUL);
                        preferredStrategies.add(ReplyStrategy.BANTER);
                    }
                    if (lower.contains("rough") || lower.contains("sorry") || lower.contains("vent") || lower.contains("relax")) {
                        preferredStrategies.add(ReplyStrategy.EMPATHIZE);
                        preferredStrategies.add(ReplyStrategy.SUPPORTIVE);
                    }
                }
            }

            // Inspect recent REJECTED suggestions to learn negative signals
            List<String> rejectedTexts = feedbackRepository.findRecentRejectedTexts(userId, PageRequest.of(0, 10));
            if (!rejectedTexts.isEmpty()) {
                double rejAvgLen = rejectedTexts.stream().mapToInt(String::length).average().orElse(0);
                if (rejAvgLen > 65 && lengthPreference == LengthPreference.SHORT) {
                    negativeDirectives.append("Avoid long or verbose sentences. ");
                }

                for (String rt : rejectedTexts) {
                    String lower = rt.toLowerCase(Locale.ROOT);
                    if (lower.contains("wonderful") || lower.contains("fascinating") || lower.contains("elaborate") || lower.contains("delightful")) {
                        negativeDirectives.append("Avoid formal or overly enthusiastic assistant tone. ");
                        break;
                    }
                    if (lower.contains("tell me more") || lower.contains("how are you doing today") || lower.contains("what else")) {
                        negativeDirectives.append("Avoid generic robotic follow-up questions. ");
                        rejectedStrategies.add(ReplyStrategy.ASK_FOLLOWUP);
                        break;
                    }
                    if (lower.contains("wow") || lower.contains("absolutely") || lower.contains("amazing")) {
                        rejectedStrategies.add(ReplyStrategy.PLAYFUL);
                    }
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

            List<Integer> sortedLens = userMessages.stream()
                    .map(m -> m.content() != null ? m.content().length() : 0)
                    .sorted()
                    .toList();
            medianLen = sortedLens.get(sortedLens.size() / 2);

            long messagesWithEmoji = userMessages.stream()
                    .filter(m -> m.content() != null && m.content().codePoints().anyMatch(Character::isEmoji))
                    .count();
            double emojiRatio = (double) messagesWithEmoji / userMessages.size();
            if (emojiRatio == 0.0) {
                if (emojiUsage != EmojiUsage.FREQUENT) {
                    emojiUsage = EmojiUsage.NONE;
                }
            } else if (emojiRatio >= 0.35) {
                emojiUsage = EmojiUsage.FREQUENT;
            } else {
                emojiUsage = EmojiUsage.OCCASIONAL;
            }
            if (messagesWithEmoji > 0 && emojiUsage == EmojiUsage.OCCASIONAL) {
                directives.append("Use emojis occasionally like the user does. ");
            }

            // Extract favorite emojis
            for (ContextMessage m : userMessages) {
                if (m.content() == null) continue;
                m.content().codePoints()
                        .filter(Character::isEmoji)
                        .forEach(cp -> {
                            String emojiStr = new String(Character.toChars(cp));
                            if (!favoriteEmojis.contains(emojiStr) && favoriteEmojis.size() < 5) {
                                favoriteEmojis.add(emojiStr);
                            }
                        });
            }

            // Extract slang tokens
            for (ContextMessage m : userMessages) {
                if (m.content() == null) continue;
                String[] words = m.content().split("\\s+");
                for (String w : words) {
                    String clean = w.replaceAll("[^a-zA-Z]", "").toLowerCase(Locale.ROOT);
                    if (SLANG_TOKEN_PATTERN.matcher(clean).matches()) {
                        slangTokens.add(clean);
                    }
                }
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

        if (!slangTokens.isEmpty()) {
            directives.append("Natural slang used: ").append(String.join(", ", slangTokens)).append(". ");
        }

        return new UserWritingProfile(
                lengthPreference,
                emojiUsage,
                detectedLanguage != null ? detectedLanguage : "ENGLISH",
                formality,
                "HINGLISH".equalsIgnoreCase(detectedLanguage) || !slangTokens.isEmpty(),
                true,
                directives.toString().trim(),
                avgLen,
                medianLen,
                slangTokens,
                favoriteEmojis,
                hinglishRatio,
                preferredStrategies,
                rejectedStrategies,
                negativeDirectives.toString().trim()
        );
    }

    /**
     * Learns only observable partner communication traits. Unlike the current
     * user's profile this deliberately does not read the user's feedback.
     */
    public PartnerCommunicationProfile analyzePartnerStyle(
            List<ContextMessage> messages,
            String conversationLanguage) {
        List<ContextMessage> partnerMessages = messages == null ? List.of() : messages.stream()
                .filter(message -> !message.isCurrentUser())
                .filter(message -> message.content() != null && !message.content().isBlank())
                .toList();
        if (partnerMessages.isEmpty()) {
            return PartnerCommunicationProfile.defaults(conversationLanguage);
        }

        double averageLength = partnerMessages.stream().mapToInt(message -> message.content().length())
                .average().orElse(35.0);
        LengthPreference length = averageLength <= 24 ? LengthPreference.SHORT
                : averageLength >= 75 ? LengthPreference.LONG : LengthPreference.MEDIUM;

        long emojiMessages = partnerMessages.stream()
                .filter(message -> message.content().codePoints().anyMatch(Character::isEmoji))
                .count();
        double emojiRatio = (double) emojiMessages / partnerMessages.size();
        EmojiUsage partnerEmojiUsage = emojiRatio == 0.0 ? EmojiUsage.NONE
                : emojiRatio >= 0.35 ? EmojiUsage.FREQUENT : EmojiUsage.OCCASIONAL;

        Set<String> partnerSlang = new HashSet<>();
        int hinglishTokens = 0;
        int totalTokens = 0;
        long humorousMessages = 0;
        long enthusiasticMessages = 0;
        long questions = 0;
        long punctuated = 0;

        for (ContextMessage message : partnerMessages) {
            String content = message.content();
            String lower = content.toLowerCase(Locale.ROOT);
            if (lower.contains("😂") || lower.contains("🤣") || lower.matches(".*\\b(lol|haha|lmao|mazak)\\b.*")) humorousMessages++;
            if (content.contains("!") || lower.matches(".*\\b(omg|wow|brooo+|no way|crazy|mast)\\b.*")) enthusiasticMessages++;
            if (content.contains("?") || lower.matches(".*\\b(what|why|how|when|where|kya|kyun|kaise|kab|kaha)\\b.*")) questions++;
            if (content.endsWith(".") || content.endsWith("!") || content.endsWith("?")) punctuated++;

            for (String token : content.split("\\s+")) {
                String clean = token.replaceAll("[^a-zA-Z]", "").toLowerCase(Locale.ROOT);
                if (clean.isBlank()) continue;
                totalTokens++;
                if (SLANG_TOKEN_PATTERN.matcher(clean).matches()) partnerSlang.add(clean);
                if (Set.of("haan", "acha", "accha", "yaar", "bhai", "kya", "kyun", "kaise", "nahi", "hai", "kal", "aaj", "mujhe", "tum").contains(clean)) {
                    hinglishTokens++;
                }
            }
        }

        String language = totalTokens > 0 && (double) hinglishTokens / totalTokens >= 0.12
                ? "HINGLISH"
                : (conversationLanguage == null ? "ENGLISH" : conversationLanguage);
        Formality partnerFormality = (double) punctuated / partnerMessages.size() >= 0.7
                ? Formality.PUNCTUATED : Formality.CASUAL;
        double humor = (double) humorousMessages / partnerMessages.size();
        double enthusiasm = (double) enthusiasticMessages / partnerMessages.size();
        double questionFrequency = (double) questions / partnerMessages.size();

        String directives = "Mirror partner energy: " + length.name().toLowerCase(Locale.ROOT)
                + " messages, " + partnerEmojiUsage.name().toLowerCase(Locale.ROOT) + " emoji use"
                + (partnerSlang.isEmpty() ? "" : ", slang such as " + String.join(", ", partnerSlang))
                + ". Keep the current user's own voice; do not copy quirks mechanically.";

        return new PartnerCommunicationProfile(
                length,
                partnerEmojiUsage,
                language,
                partnerFormality,
                Set.copyOf(partnerSlang),
                humor,
                enthusiasm,
                questionFrequency,
                directives
        );
    }
}
