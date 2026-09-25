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
import com.datingapp.chat.replycoach.model.ConversationContext;
import com.datingapp.chat.replycoach.model.ConversationEnvironment;
import com.datingapp.chat.replycoach.model.LatestMessageAnalysis;
import com.datingapp.chat.replycoach.model.MessageIntelligence;
import com.datingapp.chat.replycoach.model.PartnerCommunicationProfile;
import com.datingapp.chat.replycoach.model.UserWritingProfile;
import com.datingapp.chat.replycoach.provider.AIReplyProvider;
import com.datingapp.chat.replycoach.repository.AiReplyFeedbackRepository;
import com.datingapp.chat.replycoach.service.AiReplyFeedbackRecorder;
import com.datingapp.chat.replycoach.service.ConversationIntelligenceService;
import com.datingapp.chat.replycoach.service.ConversationMemoryExtractor;
import com.datingapp.chat.replycoach.service.LanguageIntelligenceService;
import com.datingapp.chat.replycoach.service.LatestMessageAnalyzer;
import com.datingapp.chat.replycoach.service.ReplyCandidateGenerator;
import com.datingapp.chat.replycoach.service.ReplyCoachQualityFilter;
import com.datingapp.chat.replycoach.service.ReplyCoachRateLimiter;
import com.datingapp.chat.replycoach.service.ReplyCoachService;
import com.datingapp.chat.replycoach.service.ReplyIntentPlanner;
import com.datingapp.chat.replycoach.service.ReplyRanker;
import com.datingapp.chat.replycoach.service.UserStyleEngine;
import com.datingapp.chat.replycoach.service.SuggestionGenerationSession;
import com.bondcircle.entity.User;
import com.bondcircle.repository.UserRepository;
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
    private static final int MAX_FETCH_MESSAGES = 40;
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
    private final ConversationMemoryExtractor memoryExtractor;
    private final ReplyIntentPlanner replyIntentPlanner;
    private final ReplyCandidateGenerator candidateGenerator;
    private final ReplyRanker replyRanker;
    private final LatestMessageAnalyzer latestMessageAnalyzer;
    private final SuggestionGenerationSession generationSession;

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
            ReplyCoachRateLimiter rateLimiter,
            ConversationMemoryExtractor memoryExtractor,
            ReplyIntentPlanner replyIntentPlanner,
            ReplyCandidateGenerator candidateGenerator,
            ReplyRanker replyRanker,
            LatestMessageAnalyzer latestMessageAnalyzer,
            SuggestionGenerationSession generationSession) {
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
        this.memoryExtractor = memoryExtractor != null ? memoryExtractor : new ConversationMemoryExtractor();
        this.replyIntentPlanner = replyIntentPlanner != null ? replyIntentPlanner : new ReplyIntentPlanner();
        this.candidateGenerator = candidateGenerator != null ? candidateGenerator : new ReplyCandidateGenerator();
        this.replyRanker = replyRanker != null ? replyRanker : new ReplyRanker();
        this.latestMessageAnalyzer = latestMessageAnalyzer != null ? latestMessageAnalyzer : new LatestMessageAnalyzer();
        this.generationSession = generationSession != null ? generationSession : new SuggestionGenerationSession();
    }

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
        this(conversationRepository, participantRepository, messageRepository, userRepository, blockRepository,
                feedbackRepository, aiReplyProvider, feedbackRecorder,
                conversationIntelligenceService, userStyleEngine, qualityFilter, rateLimiter,
                new ConversationMemoryExtractor(), new ReplyIntentPlanner(),
                new ReplyCandidateGenerator(), new ReplyRanker(),
                new LatestMessageAnalyzer(), new SuggestionGenerationSession());
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
                new ReplyCoachQualityFilter(), new ReplyCoachRateLimiter(),
                new ConversationMemoryExtractor(), new ReplyIntentPlanner(),
                new ReplyCandidateGenerator(), new ReplyRanker(),
                new LatestMessageAnalyzer(), new SuggestionGenerationSession());
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
                new ReplyCoachQualityFilter(), new ReplyCoachRateLimiter(),
                new ConversationMemoryExtractor(), new ReplyIntentPlanner(),
                new ReplyCandidateGenerator(), new ReplyRanker(),
                new LatestMessageAnalyzer(), new SuggestionGenerationSession());
    }

    @Override
    @Transactional(readOnly = true)
    public ReplySuggestionResponse getReplySuggestions(String conversationId, Long userId, int limit) {
        return generateInternal(conversationId, userId, Collections.emptyList(), Collections.emptyList(), limit, false);
    }

    @Override
    @Transactional(readOnly = true)
    public ReplySuggestionResponse regenerateReplySuggestions(
            String conversationId,
            Long userId,
            List<String> rejectedSuggestionIds,
            List<String> rejectedTexts,
            int limit) {
        return generateInternal(conversationId, userId, rejectedSuggestionIds, rejectedTexts, limit, true);
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
        generationSession.recordFeedback(
                userId, request.getConversationId(), request.getSuggestionText(), request.getAction());
        log.debug("Recorded AI Reply Coach feedback: user={}, action={}, suggestionId={}",
                userId, request.getAction(), request.getSuggestionId());
    }

    private ReplySuggestionResponse generateInternal(
            String conversationId,
            Long userId,
            List<String> rejectedIds,
            List<String> rejectedTextsParam,
            int requestedLimit,
            boolean regeneration) {

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

        // 3. Fetch a bounded history. Only the newest messages go to the LLM;
        // older messages feed memory and style extraction.
        List<Message> rawMessages = messageRepository.findRecentMessages(conversation.getId(), MAX_FETCH_MESSAGES);
        if (rawMessages == null) rawMessages = List.of();
        List<Message> chronologicalMessages = new ArrayList<>(rawMessages.stream()
                .filter(m -> !m.isDeleted())
                .toList());
        Collections.reverse(chronologicalMessages); // Chronological order (oldest to newest)

        // 4. Normalize the fetched history, then select recent context.
        List<ConversationContext.ContextMessage> historyMessages = new ArrayList<>();
        for (Message msg : chronologicalMessages) {
            String content = msg.getContent() != null ? msg.getContent().trim() : "";
            if (content.isEmpty()) continue;
            boolean isCurrentUser = msg.getSenderId().equals(userId);
            historyMessages.add(new ConversationContext.ContextMessage(
                    msg.getId(),
                    msg.getPublicId(),
                    msg.getSenderId(),
                    content,
                    isCurrentUser,
                    msg.getCreatedAt()
            ));
        }

        int recentStart = Math.max(0, historyMessages.size() - MAX_CONTEXT_MESSAGES);
        List<ConversationContext.ContextMessage> contextMessages = new ArrayList<>(
                historyMessages.subList(recentStart, historyMessages.size()));

        // Extract memory only from messages outside the recent context to avoid
        // repeating the same line in both memory and dialogue sections.
        List<ConversationContext.ContextMessage> olderMessages = recentStart > 0
                ? historyMessages.subList(0, recentStart)
                : List.of();
        List<String> longTermMemories = memoryExtractor.extractMemories(olderMessages, 10);

        String userInterests = currentUser != null ? currentUser.getInterests() : "";
        String partnerInterests = otherUser != null ? otherUser.getInterests() : "";

        ConversationContext context = new ConversationContext(
                conversationId,
                userId,
                otherUserId,
                userInterests,
                partnerInterests,
                contextMessages,
                longTermMemories
        );

        // 5. Run Conversation Intelligence Environment Analysis & User Style Engine
        ConversationEnvironment env = conversationIntelligenceService.analyzeEnvironment(context);
        UserWritingProfile styleProfile = userStyleEngine.analyzeStyle(userId, historyMessages, env.language());
        PartnerCommunicationProfile partnerStyle = userStyleEngine.analyzePartnerStyle(historyMessages, env.language());
        List<MessageIntelligence> messageSignals = conversationIntelligenceService.analyzeMessages(context);
        LatestMessageAnalysis latest = latestMessageAnalyzer.analyze(context, messageSignals, env);
        String latestMessageId = context.getLastMessage() != null
                ? (context.getLastMessage().publicId() != null
                ? context.getLastMessage().publicId() : String.valueOf(context.getLastMessage().id()))
                : null;
        SuggestionGenerationSession.Snapshot session = generationSession.begin(
                userId, conversationId, latestMessageId, regeneration);

        // 6. Plan Reply Intents
        ReplyIntentPlanner.ReplyIntentPlan intentPlan = replyIntentPlanner.planIntents(
                env, styleProfile, latest, session.previousStrategies(), session.generationNumber());

        // 7. Compile rejected texts list
        List<String> combinedRejectedTexts = new ArrayList<>();
        if (rejectedTextsParam != null) {
            combinedRejectedTexts.addAll(rejectedTextsParam);
            generationSession.recordRejected(userId, conversationId, rejectedTextsParam);
        }
        for (String previous : session.previousSuggestionTexts()) {
            if (previous != null && !previous.isBlank() && !combinedRejectedTexts.contains(previous)) {
                combinedRejectedTexts.add(previous);
            }
        }
        for (String rejected : session.rejectedSuggestions()) {
            if (rejected != null && !rejected.isBlank() && !combinedRejectedTexts.contains(rejected)) {
                combinedRejectedTexts.add(rejected);
            }
        }
        if (rejectedIds != null && !rejectedIds.isEmpty()) {
            try {
                List<String> textsFromIds = feedbackRepository.findSuggestionTextsByIds(rejectedIds);
                if (textsFromIds != null) {
                    generationSession.recordRejected(userId, conversationId, textsFromIds);
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
        // 8. Call AI Reply Provider
        Optional<AIReplyProvider.GenerationResult> aiResult = Optional.empty();

        // Try primary method with ConversationEnvironment and planned intents
        try {
            aiResult = aiReplyProvider.generateSuggestions(
                    context,
                    env,
                    styleProfile,
                    intentPlan.intents(),
                    combinedRejectedTexts,
                    limit,
                    latest,
                    partnerStyle,
                    session
            );
            if (aiResult == null) aiResult = Optional.empty();
        } catch (Exception ex) {
            log.debug("Advanced generation call threw: {}", ex.getMessage());
        }

        // 9. Merge provider output with deterministic candidates. This both
        // fills partial/malformed provider results and lets every path pass
        // through the same safety, truthfulness, diversity, and ranking gates.
        List<ReplySuggestionItem> candidatePool = new ArrayList<>();
        aiResult.ifPresent(result -> candidatePool.addAll(result.suggestions()));
        candidatePool.addAll(candidateGenerator.generateCandidates(
                context, env, styleProfile, combinedRejectedTexts, limit,
                latest, intentPlan.getStrategies(), session.generationNumber()));

        List<ReplySuggestionItem> safeCandidates = qualityFilter.filter(
                candidatePool, combinedRejectedTexts, context, Math.min(12, candidatePool.size()));
        List<ReplySuggestionItem> finalSuggestions = replyRanker.rankAndFilter(
                safeCandidates,
                env,
                styleProfile,
                partnerStyle,
                latest,
                intentPlan.getStrategies(),
                combinedRejectedTexts,
                limit
        );

        // Conversation state is deterministic and authorization-bound; never
        // let model-generated metadata override the backend's analysis.
        ConversationStateDto finalState = buildStateDto(env, context);

        // Auto-record SHOWN feedback
        recordShownFeedbackAsync(userId, conversationId, finalSuggestions);
        generationSession.recordGeneration(userId, conversationId, latestMessageId, finalSuggestions);

        String generationId = "gen_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return new ReplySuggestionResponse(
                generationId,
                conversationId,
                finalSuggestions,
                finalState
        );
    }

    private ConversationStateDto buildStateDto(ConversationEnvironment env, ConversationContext context) {
        return new ConversationStateDto(
                env.primaryTopic(),
                switch (env.temperature()) {
                    case PLAYFUL -> "Playful";
                    case EMOTIONAL -> "Empathetic";
                    case FLIRTY -> "Playful & Flirty";
                    case SERIOUS -> "Thoughtful";
                    case COLD -> "Re-energizing";
                    case WARM -> "Warm & Conversational";
                },
                env.isDry() ? "DRY" : (context.isEmpty() ? "NEW" : "BALANCED"),
                env.language(),
                env.isDry(),
                env.stage().name(),
                env.hasUnansweredQuestion(),
                env.unansweredQuestionText(),
                env.momentum().name(),
                env.direction().name(),
                env.responseExpectation().name(),
                env.depth().name()
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
}
