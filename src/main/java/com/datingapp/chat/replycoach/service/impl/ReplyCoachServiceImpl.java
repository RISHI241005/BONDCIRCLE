package com.datingapp.chat.replycoach.service.impl;

import com.datingapp.chat.block.repository.BlockRepository;
import com.datingapp.chat.common.exception.ErrorCode;
import com.datingapp.chat.common.exception.ForbiddenException;
import com.datingapp.chat.common.exception.ResourceNotFoundException;
import com.datingapp.chat.conversation.entity.Conversation;
import com.datingapp.chat.conversation.repository.ConversationParticipantRepository;
import com.datingapp.chat.conversation.repository.ConversationRepository;
import com.datingapp.chat.message.entity.Message;
import com.datingapp.chat.message.repository.MessageRepository;
import com.datingapp.chat.replycoach.dto.ConversationStateDto;
import com.datingapp.chat.replycoach.dto.ReplyFeedbackRequest;
import com.datingapp.chat.replycoach.dto.ReplySuggestionItem;
import com.datingapp.chat.replycoach.dto.ReplySuggestionResponse;
import com.datingapp.chat.replycoach.entity.AiReplyFeedback;
import com.datingapp.chat.replycoach.entity.FeedbackAction;
import com.datingapp.chat.replycoach.provider.AIReplyProvider;
import com.datingapp.chat.replycoach.repository.AiReplyFeedbackRepository;
import com.datingapp.chat.replycoach.service.ReplyCoachService;
import com.datingapp.chat.security.User;
import com.datingapp.chat.security.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ReplyCoachServiceImpl implements ReplyCoachService {

    private static final Logger log = LoggerFactory.getLogger(ReplyCoachServiceImpl.class);
    private static final int MAX_CONTEXT_MESSAGES = 20;

    private static final Set<String> DRY_WORDS = Set.of(
            "k", "ok", "okay", "yeah", "yea", "yup", "cool", "hmm", "hm", "nice",
            "fine", "yep", "sure", "lol", "haha", "kk", "np", "alright", "oh", "accha", "haan"
    );

    private static final Pattern HINGLISH_PATTERN = Pattern.compile(
            "\\b(kya|hai|nahi|nahin|yaar|chal|accha|acha|haan|han|bhai|theek|thik|kaise|kaisa|kaha|kahan|kuch|hoga|raha|rahi|rahe|meri|mera|mere|tere|tera|teri|apna|apni|sab|matlab|shuru|suno|sun|aaj|kal|parso|waise|badiya|mast|fasa|bata|batao)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final BlockRepository blockRepository;
    private final AiReplyFeedbackRepository feedbackRepository;
    private final AIReplyProvider aiReplyProvider;

    public ReplyCoachServiceImpl(
            ConversationRepository conversationRepository,
            ConversationParticipantRepository participantRepository,
            MessageRepository messageRepository,
            UserRepository userRepository,
            BlockRepository blockRepository,
            AiReplyFeedbackRepository feedbackRepository,
            AIReplyProvider aiReplyProvider) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.blockRepository = blockRepository;
        this.feedbackRepository = feedbackRepository;
        this.aiReplyProvider = aiReplyProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public ReplySuggestionResponse getReplySuggestions(String conversationId, Long userId, int limit) {
        return generateInternal(conversationId, userId, Collections.emptyList(), Collections.emptyList(), limit);
    }

    @Override
    @Transactional(readOnly = true)
    public ReplySuggestionResponse regenerateReplySuggestions(
            String conversationId,
            Long userId,
            List<String> rejectedSuggestionIds,
            List<String> rejectedTexts,
            int limit) {
        return generateInternal(conversationId, userId, rejectedSuggestionIds, rejectedTexts, limit);
    }

    @Override
    @Transactional
    public void recordFeedback(Long userId, ReplyFeedbackRequest request) {
        if (request == null || request.getConversationId() == null || request.getSuggestionId() == null) {
            return;
        }

        // Verify conversation access
        if (!participantRepository.existsByConversation_PublicIdAndUserId(request.getConversationId(), userId)) {
            throw new ForbiddenException("You are not a participant in this conversation", ErrorCode.CHAT_ACCESS_DENIED);
        }

        AiReplyFeedback feedback = new AiReplyFeedback(
                userId,
                request.getConversationId(),
                request.getSuggestionId(),
                request.getSuggestionText() != null ? request.getSuggestionText() : "",
                request.getAction() != null ? request.getAction() : FeedbackAction.SHOWN
        );
        feedbackRepository.save(feedback);
        log.debug("Recorded AI Reply Coach feedback: user={}, action={}, suggestionId={}",
                userId, request.getAction(), request.getSuggestionId());
    }

    private ReplySuggestionResponse generateInternal(
            String conversationId,
            Long userId,
            List<String> rejectedIds,
            List<String> rejectedTextsParam,
            int requestedLimit) {

        int limit = Math.max(1, Math.min(requestedLimit, 3));

        // 1. Authenticate and verify conversation access
        Conversation conversation = conversationRepository.findByPublicId(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conversation not found: " + conversationId, ErrorCode.CONVERSATION_NOT_FOUND));

        if (!participantRepository.existsByConversation_PublicIdAndUserId(conversationId, userId)) {
            throw new ForbiddenException("You are not a participant in this conversation", ErrorCode.CHAT_ACCESS_DENIED);
        }

        // 2. Block check
        List<Long> otherUserIds = participantRepository.findOtherParticipantUserIds(conversationId, userId);
        Long otherUserId = otherUserIds.isEmpty() ? null : otherUserIds.get(0);

        if (otherUserId != null && blockRepository.isBlockedBetween(userId, otherUserId)) {
            throw new ForbiddenException("Cannot generate suggestions for a blocked conversation", ErrorCode.CHAT_ACCESS_DENIED);
        }

        User currentUser = userRepository.findById(userId).orElse(null);
        User otherUser = otherUserId != null ? userRepository.findById(otherUserId).orElse(null) : null;

        // 3. Retrieve recent messages (up to MAX_CONTEXT_MESSAGES=20)
        List<Message> rawMessages = messageRepository.findRecentMessages(conversation.getId(), MAX_CONTEXT_MESSAGES);
        List<Message> chronologicalMessages = new ArrayList<>(rawMessages.stream()
                .filter(m -> !m.isDeleted())
                .toList());
        Collections.reverse(chronologicalMessages); // Reverse to get chronological order (oldest to newest)

        // 4. Identify sender of every message & build dialogue
        List<String> formattedDialogue = new ArrayList<>();
        List<Message> currentUserMessages = new ArrayList<>();
        Message lastMessage = null;

        for (Message msg : chronologicalMessages) {
            String content = msg.getContent() != null ? msg.getContent().trim() : "";
            if (content.isEmpty()) continue;

            if (msg.getSenderId().equals(userId)) {
                formattedDialogue.add("CURRENT_USER (YOU): " + content);
                currentUserMessages.add(msg);
            } else {
                formattedDialogue.add("OTHER_USER: " + content);
            }
            lastMessage = msg;
        }

        // 5. Language detection (English vs Hinglish)
        String detectedLanguage = detectLanguage(chronologicalMessages);

        // 6. Dry conversation detection
        boolean isDry = isDryConversation(lastMessage, chronologicalMessages, userId);

        // 7. User style analysis & Personalization from feedback
        String userStyleHints = analyzeUserStyle(currentUserMessages, userId);

        // 8. Compile rejected texts list
        List<String> combinedRejectedTexts = new ArrayList<>();
        if (rejectedTextsParam != null) {
            combinedRejectedTexts.addAll(rejectedTextsParam);
        }

        String userInterests = currentUser != null ? currentUser.getInterests() : "";
        String partnerInterests = otherUser != null ? otherUser.getInterests() : "";

        // 9. Call AI Reply Provider
        Optional<AIReplyProvider.GenerationResult> aiResult = aiReplyProvider.generateSuggestions(
                formattedDialogue,
                detectedLanguage,
                isDry,
                combinedRejectedTexts,
                userStyleHints,
                userInterests,
                partnerInterests,
                limit
        );

        if (aiResult.isPresent() && !aiResult.get().suggestions().isEmpty()) {
            List<ReplySuggestionItem> validSuggestions = filterValidSuggestions(
                    aiResult.get().suggestions(), combinedRejectedTexts, limit);

            if (!validSuggestions.isEmpty()) {
                // Auto-record SHOWN feedback
                recordShownFeedbackAsync(userId, conversationId, validSuggestions);

                return new ReplySuggestionResponse(
                        conversationId,
                        validSuggestions,
                        aiResult.get().state()
                );
            }
        }

        // 10. Fallback generation (deterministic, intelligent, non-repetitive)
        List<ReplySuggestionItem> fallbackSuggestions = generateFallbackReplies(
                lastMessage, userId, detectedLanguage, isDry, userInterests, partnerInterests, combinedRejectedTexts, limit);

        ConversationStateDto fallbackState = new ConversationStateDto(
                chronologicalMessages.isEmpty() ? "Introduction" : "Catching up",
                isDry ? "Re-energizing" : "Warm",
                isDry ? "DRY" : (chronologicalMessages.isEmpty() ? "NEW" : "BALANCED"),
                detectedLanguage,
                isDry
        );

        recordShownFeedbackAsync(userId, conversationId, fallbackSuggestions);

        return new ReplySuggestionResponse(
                conversationId,
                fallbackSuggestions,
                fallbackState
        );
    }

    private void recordShownFeedbackAsync(Long userId, String conversationId, List<ReplySuggestionItem> suggestions) {
        try {
            for (ReplySuggestionItem item : suggestions) {
                feedbackRepository.save(new AiReplyFeedback(
                        userId,
                        conversationId,
                        item.getId(),
                        item.getText(),
                        FeedbackAction.SHOWN
                ));
            }
        } catch (Exception ex) {
            log.warn("Failed to record SHOWN feedback for suggestions: {}", ex.getMessage());
        }
    }

    private List<ReplySuggestionItem> filterValidSuggestions(
            List<ReplySuggestionItem> suggestions,
            List<String> rejectedTexts,
            int limit) {

        Set<String> normalizedRejected = new HashSet<>();
        for (String r : rejectedTexts) {
            if (r != null) normalizedRejected.add(r.trim().toLowerCase(Locale.ROOT));
        }

        List<ReplySuggestionItem> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (ReplySuggestionItem item : suggestions) {
            if (item.getText() == null || item.getText().isBlank()) continue;
            String normalized = item.getText().trim().toLowerCase(Locale.ROOT);
            if (!normalizedRejected.contains(normalized) && !seen.contains(normalized)) {
                seen.add(normalized);
                result.add(item);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private String detectLanguage(List<Message> messages) {
        int hinglishCount = 0;
        int totalWords = 0;

        for (Message msg : messages) {
            String text = msg.getContent();
            if (text == null) continue;
            String[] words = text.split("\\s+");
            for (String w : words) {
                totalWords++;
                if (HINGLISH_PATTERN.matcher(w).find()) {
                    hinglishCount++;
                }
            }
        }

        if (totalWords > 0 && (double) hinglishCount / totalWords >= 0.10) {
            return "HINGLISH";
        }
        return "ENGLISH";
    }

    private boolean isDryConversation(Message lastMessage, List<Message> messages, Long currentUserId) {
        if (lastMessage == null) return false;

        // If the other person spoke last and sent a single dry word
        if (!lastMessage.getSenderId().equals(currentUserId)) {
            String content = lastMessage.getContent() != null ? lastMessage.getContent().trim().toLowerCase(Locale.ROOT) : "";
            if (DRY_WORDS.contains(content) || (content.length() <= 8 && !content.contains("?"))) {
                return true;
            }
        }

        // Check if last 3 messages from other user were very short
        int shortOtherUserMessages = 0;
        int checked = 0;
        for (int i = messages.size() - 1; i >= 0 && checked < 5; i--) {
            Message m = messages.get(i);
            if (!m.getSenderId().equals(currentUserId)) {
                checked++;
                String c = m.getContent() != null ? m.getContent().trim() : "";
                if (c.length() <= 12) {
                    shortOtherUserMessages++;
                }
            }
        }

        return shortOtherUserMessages >= 3;
    }

    private String analyzeUserStyle(List<Message> userMessages, Long userId) {
        StringBuilder hints = new StringBuilder();

        // 1. Check feedback history for personalized traits
        try {
            List<String> usedTexts = feedbackRepository.findRecentUsedTexts(userId, PageRequest.of(0, 8));
            if (!usedTexts.isEmpty()) {
                boolean prefersEmojis = usedTexts.stream().anyMatch(t -> t.codePoints().anyMatch(Character::isEmoji));
                int avgLength = (int) usedTexts.stream().mapToInt(String::length).average().orElse(30);
                if (avgLength < 35) {
                    hints.append("User repeatedly chooses short, punchy replies. ");
                } else {
                    hints.append("User appreciates thoughtful, expressive replies. ");
                }
                if (prefersEmojis) {
                    hints.append("Include natural emojis where fitting. ");
                }
            }
        } catch (Exception ex) {
            log.debug("Feedback analysis skipped: {}", ex.getMessage());
        }

        // 2. Inspect sent messages
        if (!userMessages.isEmpty()) {
            boolean usesEmojis = false;
            int totalLength = 0;
            for (Message m : userMessages) {
                String c = m.getContent() != null ? m.getContent() : "";
                totalLength += c.length();
                if (!usesEmojis && c.codePoints().anyMatch(Character::isEmoji)) {
                    usesEmojis = true;
                }
            }
            int avgMsgLen = totalLength / userMessages.size();
            if (avgMsgLen <= 25) {
                hints.append("Keep replies casual, relaxed and brief. ");
            }
            if (usesEmojis) {
                hints.append("Use emojis occasionally like the user does. ");
            }
        }

        return hints.toString().trim();
    }

    private List<ReplySuggestionItem> generateFallbackReplies(
            Message lastMessage,
            Long currentUserId,
            String language,
            boolean isDry,
            String userInterests,
            String partnerInterests,
            List<String> rejectedTexts,
            int limit) {

        List<ReplySuggestionItem> candidates = new ArrayList<>();
        boolean isHinglish = "HINGLISH".equalsIgnoreCase(language);

        if (lastMessage == null) {
            // Fresh conversation / Icebreaker fallback
            if (partnerInterests != null && !partnerInterests.isBlank()) {
                String interest = partnerInterests.split("\\|")[0].trim();
                candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                        "Hey! Saw you're into " + interest + " — what's your favorite thing about it?", "Interests", "Curious"));
            } else {
                candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                        "Hey! How's your week treating you so far? 😊", "Greeting", "Warm"));
            }
            candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                    "Coffee or tea person? Need to know before we talk further! 👀", "Icebreaker", "Playful"));
            candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                    "Random question: what's one place you've always wanted to travel to?", "Travel", "Curious"));
            candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                    "Hey there! What's been the highlight of your day today?", "Day", "Friendly"));
        } else if (isDry) {
            // Dry conversation re-openers
            if (isHinglish) {
                candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                        "Arre itna serious 'haan'? 😂 Sab theek na?", "Banter", "Playful"));
                candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                        "Waise aaj din bhar kya kiya? Kuch interesting?", "Curiosity", "Curious"));
                candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                        "Lagta hai kaafi thake huye ho aaj! Kya chal raha hai?", "Warmth", "Warm"));
                candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                        "Haha details se overwhelm mat karo mujhe! Batao sach me kya hua?", "Humor", "Playful"));
            } else {
                candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                        "Okay that's a very concise reply 😂 What's actually going on today?", "Humor", "Playful"));
                candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                        "Haha don't overwhelm me with all the details! What are you up to?", "Banter", "Playful"));
                candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                        "Random question to shake things up — what made you smile today?", "Curiosity", "Curious"));
                candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                        "Sounds like a long day! Doing anything fun tonight to unwind?", "Empathy", "Warm"));
            }
        } else {
            // Active dialogue replies
            String lastText = lastMessage.getContent() != null ? lastMessage.getContent() : "";
            boolean isQuestion = lastText.contains("?");

            if (isHinglish) {
                if (isQuestion) {
                    candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                            "Haha accha sawal hai! Honestly, situation pe depend karta hai.", "Direct", "Playful"));
                    candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                            "Sach bataun toh haan! Tumhara kya opinion hai ispe?", "Curious", "Curious"));
                    candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                            "Maine is baare me kabhi socha nahi tha, par ab sochna padega 😂", "Thoughtful", "Humorous"));
                } else {
                    candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                            "Arre waah, yeh toh kaafi cool hai! Aur batao?", "Engage", "Warm"));
                    candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                            "Haha seriously? Mujhe bilkul expected nahi tha yeh!", "Surprise", "Playful"));
                    candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                            "Bilkul sahi kaha tumne, I totally agree with you on this!", "Agreement", "Thoughtful"));
                }
            } else {
                if (isQuestion) {
                    candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                            "Haha great question! Honestly, it depends on the day 😂", "Direct", "Playful"));
                    candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                            "To be completely honest, yes! What's your take on it though?", "Curious", "Curious"));
                    candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                            "I hadn't thought about that before, but now I'm intrigued!", "Thoughtful", "Thoughtful"));
                } else {
                    candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                            "Haha no way! What happened after that?", "Story", "Curious"));
                    candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                            "That sounds awesome! I completely agree with you on that.", "Supportive", "Warm"));
                    candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                            "Okay now you definitely have my full attention 👀", "Teasing", "Playful"));
                }
            }
        }

        // Filter against rejected suggestions
        return filterValidSuggestions(candidates, rejectedTexts, limit);
    }
}
