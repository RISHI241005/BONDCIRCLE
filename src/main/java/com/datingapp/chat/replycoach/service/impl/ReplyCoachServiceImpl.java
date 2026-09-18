package com.datingapp.chat.replycoach.service.impl;

import com.datingapp.chat.block.repository.BlockRepository;
import com.datingapp.chat.common.exception.BadRequestException;
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
import com.datingapp.chat.replycoach.model.ConversationAnalysis;
import com.datingapp.chat.replycoach.model.ConversationContext;
import com.datingapp.chat.replycoach.model.ReplyStrategy;
import com.datingapp.chat.replycoach.model.UserWritingProfile;
import com.datingapp.chat.replycoach.provider.AIReplyProvider;
import com.datingapp.chat.replycoach.repository.AiReplyFeedbackRepository;
import com.datingapp.chat.replycoach.service.AiReplyFeedbackRecorder;
import com.datingapp.chat.replycoach.service.ConversationIntelligenceService;
import com.datingapp.chat.replycoach.service.ReplyCoachQualityFilter;
import com.datingapp.chat.replycoach.service.ReplyCoachRateLimiter;
import com.datingapp.chat.replycoach.service.ReplyCoachService;
import com.datingapp.chat.replycoach.service.UserStyleEngine;
import com.datingapp.chat.security.User;
import com.datingapp.chat.security.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReplyCoachServiceImpl implements ReplyCoachService {

    private static final Logger log = LoggerFactory.getLogger(ReplyCoachServiceImpl.class);
    private static final int MAX_CONTEXT_MESSAGES = 20;

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final BlockRepository blockRepository;
    private final AiReplyFeedbackRepository feedbackRepository;
    private final AIReplyProvider aiReplyProvider;
    private final AiReplyFeedbackRecorder feedbackRecorder;
    private final ConversationIntelligenceService conversationIntelligenceService;
    private final UserStyleEngine userStyleEngine;
    private final ReplyCoachQualityFilter qualityFilter;
    private final ReplyCoachRateLimiter rateLimiter;

    @Autowired
    public ReplyCoachServiceImpl(
            ConversationRepository conversationRepository,
            ConversationParticipantRepository participantRepository,
            MessageRepository messageRepository,
            UserRepository userRepository,
            BlockRepository blockRepository,
            AiReplyFeedbackRepository feedbackRepository,
            AIReplyProvider aiReplyProvider,
            AiReplyFeedbackRecorder feedbackRecorder,
            ConversationIntelligenceService conversationIntelligenceService,
            UserStyleEngine userStyleEngine,
            ReplyCoachQualityFilter qualityFilter,
            ReplyCoachRateLimiter rateLimiter) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.blockRepository = blockRepository;
        this.feedbackRepository = feedbackRepository;
        this.aiReplyProvider = aiReplyProvider;
        this.feedbackRecorder = feedbackRecorder != null ? feedbackRecorder : new AiReplyFeedbackRecorder(feedbackRepository);
        this.conversationIntelligenceService = conversationIntelligenceService != null ? conversationIntelligenceService : new ConversationIntelligenceService();
        this.userStyleEngine = userStyleEngine != null ? userStyleEngine : new UserStyleEngine(feedbackRepository);
        this.qualityFilter = qualityFilter != null ? qualityFilter : new ReplyCoachQualityFilter();
        this.rateLimiter = rateLimiter != null ? rateLimiter : new ReplyCoachRateLimiter();
    }

    public ReplyCoachServiceImpl(
            ConversationRepository conversationRepository,
            ConversationParticipantRepository participantRepository,
            MessageRepository messageRepository,
            UserRepository userRepository,
            BlockRepository blockRepository,
            AiReplyFeedbackRepository feedbackRepository,
            AIReplyProvider aiReplyProvider,
            AiReplyFeedbackRecorder feedbackRecorder) {
        this(conversationRepository, participantRepository, messageRepository, userRepository, blockRepository,
                feedbackRepository, aiReplyProvider, feedbackRecorder,
                new ConversationIntelligenceService(), new UserStyleEngine(feedbackRepository),
                new ReplyCoachQualityFilter(), new ReplyCoachRateLimiter());
    }

    public ReplyCoachServiceImpl(
            ConversationRepository conversationRepository,
            ConversationParticipantRepository participantRepository,
            MessageRepository messageRepository,
            UserRepository userRepository,
            BlockRepository blockRepository,
            AiReplyFeedbackRepository feedbackRepository,
            AIReplyProvider aiReplyProvider) {
        this(conversationRepository, participantRepository, messageRepository, userRepository, blockRepository,
                feedbackRepository, aiReplyProvider, new AiReplyFeedbackRecorder(feedbackRepository),
                new ConversationIntelligenceService(), new UserStyleEngine(feedbackRepository),
                new ReplyCoachQualityFilter(), new ReplyCoachRateLimiter());
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
    public void recordFeedback(Long userId, ReplyFeedbackRequest request) {
        if (request == null || request.getConversationId() == null || request.getSuggestionId() == null) {
            return;
        }

        // Verify conversation access
        if (!participantRepository.existsByConversation_PublicIdAndUserId(request.getConversationId(), userId)) {
            throw new ForbiddenException("You are not a participant in this conversation", ErrorCode.CHAT_ACCESS_DENIED);
        }

        feedbackRecorder.recordSingle(
                userId,
                request.getConversationId(),
                request.getSuggestionId(),
                request.getSuggestionText(),
                request.getAction()
        );
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

        // 0. Rate limit check (20 requests per minute)
        if (!rateLimiter.tryAcquire(userId)) {
            log.warn("Rate limit exceeded for user {}", userId);
            throw new BadRequestException("You are requesting AI suggestions too frequently. Please wait a moment.", ErrorCode.RATE_LIMIT_EXCEEDED);
        }

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
        Collections.reverse(chronologicalMessages); // Chronological order (oldest to newest)

        // 4. Build ConversationContext
        List<ConversationContext.ContextMessage> contextMessages = new ArrayList<>();
        List<String> formattedDialogue = new ArrayList<>();
        for (Message msg : chronologicalMessages) {
            String content = msg.getContent() != null ? msg.getContent().trim() : "";
            if (content.isEmpty()) continue;
            boolean isCurrentUser = msg.getSenderId().equals(userId);
            contextMessages.add(new ConversationContext.ContextMessage(
                    msg.getId(),
                    msg.getPublicId(),
                    msg.getSenderId(),
                    content,
                    isCurrentUser,
                    msg.getCreatedAt()
            ));
            if (isCurrentUser) {
                formattedDialogue.add("CURRENT_USER (YOU): " + content);
            } else {
                formattedDialogue.add("OTHER_USER: " + content);
            }
        }

        String userInterests = currentUser != null ? currentUser.getInterests() : "";
        String partnerInterests = otherUser != null ? otherUser.getInterests() : "";

        ConversationContext context = new ConversationContext(
                conversationId,
                userId,
                otherUserId,
                userInterests,
                partnerInterests,
                contextMessages
        );

        // 5. Run Conversation Intelligence & User Writing Style Engine
        ConversationAnalysis analysis = conversationIntelligenceService.analyze(context);
        UserWritingProfile styleProfile = userStyleEngine.analyzeStyle(userId, contextMessages, analysis.language());

        // 6. Compile rejected texts list
        List<String> combinedRejectedTexts = new ArrayList<>();
        if (rejectedTextsParam != null) {
            combinedRejectedTexts.addAll(rejectedTextsParam);
        }
        if (rejectedIds != null && !rejectedIds.isEmpty()) {
            try {
                List<String> textsFromIds = feedbackRepository.findSuggestionTextsByIds(rejectedIds);
                if (textsFromIds != null) {
                    for (String t : textsFromIds) {
                        if (t != null && !t.isBlank() && !combinedRejectedTexts.contains(t)) {
                            combinedRejectedTexts.add(t);
                        }
                    }
                }
            } catch (Exception ex) {
                log.debug("Could not resolve rejected suggestion texts by IDs: {}", ex.getMessage());
            }
        }

        // 7. Call AI Reply Provider with rich context
        Optional<AIReplyProvider.GenerationResult> aiResult = aiReplyProvider.generateSuggestions(
                context,
                analysis,
                styleProfile,
                combinedRejectedTexts,
                limit
        );

        // Backward-compatibility: if the advanced call returns empty, check legacy dialogue method (for tests mocking legacy method)
        if (aiResult.isEmpty()) {
            aiResult = aiReplyProvider.generateSuggestions(
                    formattedDialogue,
                    analysis.language(),
                    analysis.isDry(),
                    combinedRejectedTexts,
                    styleProfile.promptDirectives(),
                    userInterests,
                    partnerInterests,
                    limit
            );
        }

        if (aiResult.isPresent() && !aiResult.get().suggestions().isEmpty()) {
            List<ReplySuggestionItem> validSuggestions = qualityFilter.filter(
                    aiResult.get().suggestions(), combinedRejectedTexts, limit);

            if (!validSuggestions.isEmpty()) {
                // Auto-record SHOWN feedback
                recordShownFeedbackAsync(userId, conversationId, validSuggestions);

                String generationId = "gen_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                return new ReplySuggestionResponse(
                        generationId,
                        conversationId,
                        validSuggestions,
                        aiResult.get().state()
                );
            }
        }

        // 8. Fallback generation (deterministic, intelligent, non-repetitive)
        List<ReplySuggestionItem> fallbackCandidates = generateFallbackReplies(
                context, analysis, styleProfile, combinedRejectedTexts, limit);

        List<ReplySuggestionItem> fallbackSuggestions = qualityFilter.filter(
                fallbackCandidates, combinedRejectedTexts, limit);

        if (fallbackSuggestions.isEmpty()) {
            fallbackSuggestions = fallbackCandidates.subList(0, Math.min(limit, fallbackCandidates.size()));
        }

        ConversationStateDto fallbackState = new ConversationStateDto(
                analysis.primaryTopic(),
                analysis.tone(),
                analysis.isDry() ? "DRY" : (context.isEmpty() ? "NEW" : "BALANCED"),
                analysis.language(),
                analysis.isDry(),
                analysis.stage().name(),
                analysis.hasUnansweredQuestion(),
                analysis.lastQuestionText()
        );

        recordShownFeedbackAsync(userId, conversationId, fallbackSuggestions);

        String generationId = "gen_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return new ReplySuggestionResponse(
                generationId,
                conversationId,
                fallbackSuggestions,
                fallbackState
        );
    }

    private void recordShownFeedbackAsync(Long userId, String conversationId, List<ReplySuggestionItem> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) return;
        List<AiReplyFeedback> records = new ArrayList<>();
        for (ReplySuggestionItem item : suggestions) {
            if (item != null && item.getId() != null && item.getText() != null) {
                records.add(new AiReplyFeedback(
                        userId,
                        conversationId,
                        item.getId(),
                        item.getText(),
                        FeedbackAction.SHOWN
                ));
            }
        }
        feedbackRecorder.recordBatch(records);
    }

    private List<ReplySuggestionItem> generateFallbackReplies(
            ConversationContext context,
            ConversationAnalysis analysis,
            UserWritingProfile styleProfile,
            List<String> rejectedTexts,
            int limit) {

        List<ReplySuggestionItem> candidates = new ArrayList<>();
        boolean isHinglish = "HINGLISH".equalsIgnoreCase(analysis.language());
        String partnerInterests = context.partnerInterests();

        if (context.isEmpty()) {
            // Fresh conversation / Icebreaker fallback
            if (partnerInterests != null && !partnerInterests.isBlank()) {
                String interest = partnerInterests.split("\\|")[0].trim();
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Hey! Saw you're into " + interest + " — what's your favorite thing about it?",
                        "Interests", "Curious",
                        ReplyStrategy.CURIOUS.name(), "Curious"));
            } else {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Hey! How's your week treating you so far? 😊",
                        "Greeting", "Warm",
                        ReplyStrategy.SUPPORTIVE.name(), "Warm"));
            }
            candidates.add(new ReplySuggestionItem(
                    UUID.randomUUID().toString(),
                    "Coffee or tea person? Need to know before we talk further! 👀",
                    "Icebreaker", "Playful",
                    ReplyStrategy.PLAYFUL.name(), "Playful"));
            candidates.add(new ReplySuggestionItem(
                    UUID.randomUUID().toString(),
                    "Random question: what's one place you've always wanted to travel to?",
                    "Travel", "Curious",
                    ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
            candidates.add(new ReplySuggestionItem(
                    UUID.randomUUID().toString(),
                    "Hey there! What's been the highlight of your day today?",
                    "Day", "Friendly",
                    ReplyStrategy.THOUGHTFUL.name(), "Friendly"));
        } else if (analysis.isDry()) {
            // Dry conversation re-openers
            if (isHinglish) {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Arre itna serious 'haan'? 😂 Sab theek na?",
                        "Banter", "Playful",
                        ReplyStrategy.BANTER.name(), "Playful"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Waise aaj din bhar kya kiya? Kuch interesting?",
                        "Curiosity", "Curious",
                        ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Lagta hai kaafi thake huye ho aaj! Kya chal raha hai?",
                        "Warmth", "Warm",
                        ReplyStrategy.EMPATHIZE.name(), "Warm"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Haha details se overwhelm mat karo mujhe! Batao sach me kya hua?",
                        "Humor", "Playful",
                        ReplyStrategy.PLAYFUL.name(), "Witty"));
            } else {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Okay that's a very concise reply 😂 What's actually going on today?",
                        "Humor", "Playful",
                        ReplyStrategy.BANTER.name(), "Playful"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Haha don't overwhelm me with all the details! What are you up to?",
                        "Banter", "Playful",
                        ReplyStrategy.PLAYFUL.name(), "Witty"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Random question to shake things up — what made you smile today?",
                        "Curiosity", "Curious",
                        ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Sounds like a long day! Doing anything fun tonight to unwind?",
                        "Empathy", "Warm",
                        ReplyStrategy.SUPPORTIVE.name(), "Warm"));
            }
        } else if (analysis.hasUnansweredQuestion()) {
            // Direct answers to the question asked by the other user
            String topic = analysis.primaryTopic();
            if ("Sports & Fitness".equals(topic)) {
                if (isHinglish) {
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Haan dekha tha! Kya crazy finish tha match ka!",
                            "Sports", "Excited",
                            ReplyStrategy.ANSWER.name(), "Playful"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Sach bataun toh pura match nahi dekh paya, score kya raha?",
                            "Sports", "Curious",
                            ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Haan! Tum kis team ko support kar rahe the?",
                            "Sports", "Playful",
                            ReplyStrategy.PLAYFUL.name(), "Playful"));
                } else {
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Yes I did! That match was absolute madness!",
                            "Sports", "Excited",
                            ReplyStrategy.ANSWER.name(), "Playful"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "I caught the highlights! Which team were you rooting for?",
                            "Sports", "Curious",
                            ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Yes! Can't believe how that game turned out. Did you enjoy it?",
                            "Sports", "Engaged",
                            ReplyStrategy.THOUGHTFUL.name(), "Warm"));
                }
            } else if ("Studies & Academics".equals(topic)) {
                if (isHinglish) {
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Haan bas chal raha hai! Tumhara kitna prep hua?",
                            "Studies", "Curious",
                            ReplyStrategy.ANSWER.name(), "Warm"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Almost done! Par kaafi intense lag raha hai abhi.",
                            "Studies", "Direct",
                            ReplyStrategy.ANSWER.name(), "Thoughtful"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Haha mat pucho! Study break lene ka mann kar raha hai 😂",
                            "Studies", "Playful",
                            ReplyStrategy.BANTER.name(), "Playful"));
                } else {
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Making good progress on it! How is yours going?",
                            "Studies", "Curious",
                            ReplyStrategy.ANSWER.name(), "Warm"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Almost done with it, though it's been pretty intense today!",
                            "Studies", "Direct",
                            ReplyStrategy.ANSWER.name(), "Thoughtful"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Haha don't ask! Already dreaming about a study break 😂",
                            "Studies", "Playful",
                            ReplyStrategy.BANTER.name(), "Playful"));
                }
            } else if (isHinglish) {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Haha accha sawal hai! Honestly, situation pe depend karta hai.",
                        "Direct", "Playful",
                        ReplyStrategy.ANSWER.name(), "Playful"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Sach bataun toh haan! Tumhara kya opinion hai ispe?",
                        "Curious", "Curious",
                        ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Maine is baare me kabhi socha nahi tha, par ab sochna padega 😂",
                        "Thoughtful", "Humorous",
                        ReplyStrategy.THOUGHTFUL.name(), "Playful"));
            } else {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Haha great question! Honestly, it depends on the day 😂",
                        "Direct", "Playful",
                        ReplyStrategy.ANSWER.name(), "Playful"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "To be completely honest, yes! What's your take on it though?",
                        "Curious", "Curious",
                        ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "I hadn't thought about that before, but now I'm intrigued!",
                        "Thoughtful", "Thoughtful",
                        ReplyStrategy.THOUGHTFUL.name(), "Thoughtful"));
            }
        } else if (analysis.stage() == ConversationAnalysis.Stage.RECONNECTING || analysis.intent() == ConversationAnalysis.Intent.RECONNECTING) {
            // Natural reconnection after conversation gap
            if (isHinglish) {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Hey stranger! Look who's back 😊 Kaise ho?",
                        "Reconnection", "Warm",
                        ReplyStrategy.SUPPORTIVE.name(), "Warm"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Hey! Itne dino baad! Sab theek chal raha hai na?",
                        "Catchup", "Curious",
                        ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Look who finally remembered me! 😂 Kya chal raha hai aaj kal?",
                        "Banter", "Playful",
                        ReplyStrategy.BANTER.name(), "Playful"));
            } else {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Hey stranger! Look who's back 😊 How have you been?",
                        "Reconnection", "Warm",
                        ReplyStrategy.SUPPORTIVE.name(), "Warm"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Hey! Was just thinking about you earlier. How are things with you?",
                        "Catchup", "Thoughtful",
                        ReplyStrategy.THOUGHTFUL.name(), "Warm"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Look who's back from the dead! 😂 What have you been up to?",
                        "Banter", "Playful",
                        ReplyStrategy.BANTER.name(), "Playful"));
            }
        } else if (analysis.intent() == ConversationAnalysis.Intent.EMOTIONAL_SUPPORT) {
            // Compassionate empathetic responses for tough/exhausting days
            if (isHinglish) {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Arre, I'm so sorry! Kaafi rough lag raha hai. Sab theek na?",
                        "Empathy", "Empathetic",
                        ReplyStrategy.EMPATHIZE.name(), "Warm"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Aisa kya hua yaar? If you want to vent, I'm right here to listen.",
                        "Support", "Supportive",
                        ReplyStrategy.SUPPORTIVE.name(), "Thoughtful"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Take a deep breath! Aaj aaram karo thoda, kal better hoga ❤️",
                        "Comfort", "Warm",
                        ReplyStrategy.SUPPORTIVE.name(), "Warm"));
            } else {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "I'm so sorry, that sounds really rough. Are you holding up okay?",
                        "Empathy", "Empathetic",
                        ReplyStrategy.EMPATHIZE.name(), "Warm"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Oh no, what happened? If you need to vent, I'm here to listen.",
                        "Support", "Supportive",
                        ReplyStrategy.SUPPORTIVE.name(), "Thoughtful"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Sending you good vibes! Hopefully you can relax and unwind tonight.",
                        "Comfort", "Warm",
                        ReplyStrategy.SUPPORTIVE.name(), "Warm"));
            }
        } else {
            // Active ongoing dialogue (including topic continuity)
            String topic = analysis.primaryTopic();
            if ("Sports & Fitness".equals(topic)) {
                if (isHinglish) {
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Match dekha tha kya? Kaafi crazy finish tha!",
                            "Sports", "Curious",
                            ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Haha totally agree! Tum kis team ko support karte ho?",
                            "Sports", "Playful",
                            ReplyStrategy.PLAYFUL.name(), "Playful"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Game on! Next match saath me dekhna padega 👀",
                            "Sports", "Warm",
                            ReplyStrategy.BANTER.name(), "Warm"));
                } else {
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Did you watch the match? That finish was absolute madness!",
                            "Sports", "Curious",
                            ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Haha 100%! Which team do you root for the most?",
                            "Sports", "Playful",
                            ReplyStrategy.PLAYFUL.name(), "Playful"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Game on! We definitely have to debate this over coffee 👀",
                            "Sports", "Warm",
                            ReplyStrategy.BANTER.name(), "Warm"));
                }
            } else if ("Studies & Academics".equals(topic)) {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Haha that sounds like quite a journey! What are you studying?",
                        "Studies", "Curious",
                        ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "College can be an absolute whirlwind! How are classes going?",
                        "Studies", "Supportive",
                        ReplyStrategy.SUPPORTIVE.name(), "Warm"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Respect! That definitely takes a lot of dedication 👏",
                        "Studies", "Thoughtful",
                        ReplyStrategy.THOUGHTFUL.name(), "Thoughtful"));
            } else if (isHinglish) {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Arre waah, yeh toh kaafi cool hai! Aur batao?",
                        "Engage", "Warm",
                        ReplyStrategy.SUPPORTIVE.name(), "Warm"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Haha seriously? Mujhe bilkul expected nahi tha yeh!",
                        "Surprise", "Playful",
                        ReplyStrategy.PLAYFUL.name(), "Playful"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Bilkul sahi kaha tumne, I totally agree with you on this!",
                        "Agreement", "Thoughtful",
                        ReplyStrategy.THOUGHTFUL.name(), "Thoughtful"));
            } else {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Haha no way! What happened after that?",
                        "Story", "Curious",
                        ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "That sounds awesome! I completely agree with you on that.",
                        "Supportive", "Warm",
                        ReplyStrategy.SUPPORTIVE.name(), "Warm"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Okay now you definitely have my full attention 👀",
                        "Teasing", "Playful",
                        ReplyStrategy.BANTER.name(), "Playful"));
            }
        }

        return candidates;
    }
}
