package com.datingapp.chat.icebreaker.service.impl;

import com.datingapp.chat.common.exception.ErrorCode;
import com.datingapp.chat.common.exception.ForbiddenException;
import com.datingapp.chat.common.exception.ResourceNotFoundException;
import com.datingapp.chat.config.AiAssistantProperties;
import com.datingapp.chat.config.IceBreakerProperties;
import com.datingapp.chat.conversation.entity.Conversation;
import com.datingapp.chat.conversation.repository.ConversationParticipantRepository;
import com.datingapp.chat.conversation.repository.ConversationRepository;
import com.datingapp.chat.icebreaker.dto.IceBreakerCircle;
import com.datingapp.chat.icebreaker.dto.IceBreakerResponse;
import com.datingapp.chat.icebreaker.dto.IceBreakerSuggestion;
import com.datingapp.chat.icebreaker.service.ConversationCircleRulesEngine;
import com.datingapp.chat.icebreaker.service.ConversationSignalExtractor;
import com.datingapp.chat.icebreaker.service.IceBreakerService;
import com.datingapp.chat.icebreaker.service.LiveConversationAssistant;
import com.datingapp.chat.message.entity.Message;
import com.datingapp.chat.message.repository.MessageRepository;
import com.datingapp.chat.moderation.service.LanguageModerationService;
import com.datingapp.chat.security.User;
import com.datingapp.chat.security.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

@Service
public class InterestBasedIceBreakerService implements IceBreakerService {

    private static final Set<String> TONES = Set.of("ALL", "CURIOUS", "WARM", "PLAYFUL", "THOUGHTFUL");
    private static final Set<String> LANGUAGES = Set.of("AUTO", "ENGLISH", "HINGLISH");
    private static final Set<String> MODES = Set.of("SUGGEST", "WRITE_FOR_ME", "AUTOPILOT", "UNABLE_TO_TALK");

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ConversationCircleRulesEngine circleRules;
    private final ConversationSignalExtractor signalExtractor;
    private final LanguageModerationService moderationService;
    private final IceBreakerProperties properties;
    private final AiAssistantProperties aiProperties;
    private final LiveConversationAssistant liveAssistant;

    public InterestBasedIceBreakerService(
            ConversationRepository conversationRepository,
            ConversationParticipantRepository participantRepository,
            MessageRepository messageRepository,
            UserRepository userRepository,
            ConversationCircleRulesEngine circleRules,
            ConversationSignalExtractor signalExtractor,
            LanguageModerationService moderationService,
            IceBreakerProperties properties,
            AiAssistantProperties aiProperties,
            LiveConversationAssistant liveAssistant) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.circleRules = circleRules;
        this.signalExtractor = signalExtractor;
        this.moderationService = moderationService;
        this.properties = properties;
        this.aiProperties = aiProperties;
        this.liveAssistant = liveAssistant;
    }

    @Override
    public IceBreakerResponse getSuggestions(String conversationId, Long userId) {
        return getSuggestions(
                conversationId,
                userId,
                properties.getDefaultSuggestions(),
                "ALL",
                0,
                "AUTO",
                "SUGGEST");
    }

    @Override
    @Transactional(readOnly = true)
    public IceBreakerResponse getSuggestions(
            String conversationId,
            Long userId,
            int limit,
            String requestedTone,
            int variant) {
        return getSuggestions(conversationId, userId, limit, requestedTone, variant, "AUTO", "SUGGEST");
    }

    @Override
    @Transactional(readOnly = true)
    public IceBreakerResponse getSuggestions(
            String conversationId,
            Long userId,
            int limit,
            String requestedTone,
            int variant,
            String requestedLanguage,
            String requestedMode) {
        Conversation conversation = conversationRepository.findByPublicId(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conversation not found: " + conversationId, ErrorCode.CONVERSATION_NOT_FOUND));

        if (!participantRepository.existsByConversation_PublicIdAndUserId(conversationId, userId)) {
            throw new ForbiddenException("You are not a participant in this conversation", ErrorCode.CHAT_ACCESS_DENIED);
        }

        Long otherUserId = participantRepository.findOtherParticipantUserIds(conversationId, userId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Other participant not found", ErrorCode.PARTICIPANT_NOT_FOUND));

        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found", ErrorCode.RESOURCE_NOT_FOUND));
        User otherUser = userRepository.findById(otherUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Other user not found", ErrorCode.RESOURCE_NOT_FOUND));

        List<Message> recentMessages = messageRepository
                .findRecentMessages(conversation.getId(), Math.max(1, properties.getHistoryLookback()))
                .stream()
                .filter(message -> !message.isDeleted())
                .toList();
        String context = detectContext(recentMessages);
        List<String> sharedInterests = sharedInterests(
                currentUser.getInterestList(),
                otherUser.getInterestList());
        List<String> allKnownInterests = distinctValues(
                currentUser.getInterestList(),
                otherUser.getInterestList());
        ConversationSignalExtractor.ConversationSignals signals = signalExtractor.extract(
                recentMessages,
                userId,
                allKnownInterests);

        int safeLimit = Math.max(1, Math.min(limit, properties.getMaxSuggestions()));
        String tone = normalizeTone(requestedTone);
        String language = normalizeLanguage(requestedLanguage);
        String mode = normalizeMode(requestedMode);
        Optional<LiveConversationAssistant.ReplyBatch> liveBatch = liveAssistant.generate(
                buildLiveRequest(
                        conversationId,
                        userId,
                        context,
                        language,
                        tone,
                        mode,
                        safeLimit,
                        variant,
                        currentUser,
                        otherUser,
                        recentMessages));
        if (liveBatch.isPresent()) {
            List<IceBreakerSuggestion> liveSuggestions = mapLiveSuggestions(
                    liveBatch.get().replies(), tone, safeLimit);
            if (!liveSuggestions.isEmpty()) {
                LiveConversationAssistant.ReplyBatch batch = liveBatch.get();
                return response(
                        conversationId,
                        context,
                        batch.guidance(),
                        sharedInterests,
                        signals.priorTopics(),
                        liveSuggestions,
                        "LIVE_AI",
                        language,
                        mode,
                        true,
                        batch.shouldReply(),
                        batch.urgency(),
                        batch.decisionReason(),
                        batch.replyTiming());
            }
        }

        ReplyDecision decision = evaluateReplyDecision(recentMessages, userId, context);
        List<IceBreakerSuggestion> pool = buildSuggestionPool(
                context,
                currentUser.getInterestList(),
                otherUser.getInterestList(),
                sharedInterests,
                signals,
                mode,
                language);
        Predicate<IceBreakerSuggestion> toneFilter = suggestion ->
                tone.equals("ALL") || suggestion.tone().equals(tone);
        List<IceBreakerSuggestion> eligible = deduplicate(pool).stream()
                .filter(toneFilter)
                .filter(suggestion -> !moderationService.analyze(suggestion.text()).flagged())
                .toList();
        List<IceBreakerSuggestion> selected = selectAcrossCircles(
                eligible,
                safeLimit,
                conversationId,
                variant);
        return response(
                conversationId,
                context,
                decision.reason() + " (Offline mode; connect OpenAI API key for live GPT suggestions)",
                sharedInterests,
                signals.priorTopics(),
                selected,
                "RULES",
                language,
                mode,
                false,
                decision.shouldReply(),
                decision.urgency(),
                decision.reason(),
                decision.timing());
    }

    private LiveConversationAssistant.GenerationRequest buildLiveRequest(
            String conversationId,
            Long userId,
            String context,
            String language,
            String tone,
            String mode,
            int count,
            int variation,
            User currentUser,
            User otherUser,
            List<Message> recentMessages) {
        int historyLimit = Math.max(1, aiProperties.getHistoryMessages());
        List<LiveConversationAssistant.ConversationTurn> history = recentMessages.stream()
                .limit(historyLimit)
                .toList()
                .reversed()
                .stream()
                .map(message -> new LiveConversationAssistant.ConversationTurn(
                        message.getSenderId().equals(userId) ? "ME" : "THEM",
                        truncate(message.getContent(), 600)))
                .toList();
        int requestedCount = mode.equals("AUTOPILOT") ? 1 : count;
        return new LiveConversationAssistant.GenerationRequest(
                userId,
                conversationId,
                context,
                language,
                tone,
                mode,
                requestedCount,
                variation,
                currentUser.getInterestList(),
                otherUser.getInterestList(),
                history);
    }

    private List<IceBreakerSuggestion> mapLiveSuggestions(
            List<LiveConversationAssistant.Reply> replies,
            String requestedTone,
            int limit) {
        Map<String, IceBreakerSuggestion> unique = new LinkedHashMap<>();
        int rank = 0;
        for (LiveConversationAssistant.Reply reply : replies) {
            String text = truncate(reply.text(), 500).trim();
            String tone = normalizeGeneratedTone(reply.tone());
            if (text.isBlank()
                    || (!requestedTone.equals("ALL") && !requestedTone.equals(tone))
                    || moderationService.analyze(text).flagged()) {
                continue;
            }
            String circle = circleRules.all().stream().anyMatch(rule -> rule.code().equals(reply.circle()))
                    ? reply.circle()
                    : "LIGHT_TOUCH";
            ConversationCircleRulesEngine.CircleRule rule = circleRules.get(circle);
            unique.putIfAbsent(text.toLowerCase(Locale.ROOT), new IceBreakerSuggestion(
                    text,
                    truncate(reply.topic(), 80),
                    truncate(reply.reason(), 180),
                    circle,
                    rule.label(),
                    tone,
                    Math.max(70, 100 - rank++),
                    normalizeGeneratedLanguage(reply.language()),
                    true));
            if (unique.size() >= limit) {
                break;
            }
        }
        return List.copyOf(unique.values());
    }

    private IceBreakerResponse response(
            String conversationId,
            String context,
            String guidance,
            List<String> sharedInterests,
            List<String> priorTopics,
            List<IceBreakerSuggestion> suggestions,
            String source,
            String language,
            String mode,
            boolean generatedLive,
            String shouldReply,
            String urgency,
            String decisionReason,
            String replyTiming) {
        Set<String> includedCircles = suggestions.stream()
                .map(IceBreakerSuggestion::circle)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<IceBreakerCircle> availableCircles = circleRules.all().stream()
                .filter(rule -> includedCircles.contains(rule.code()))
                .map(ConversationCircleRulesEngine.CircleRule::toResponse)
                .toList();
        return new IceBreakerResponse(
                conversationId,
                context,
                guidance,
                sharedInterests,
                priorTopics,
                availableCircles,
                suggestions,
                source,
                language,
                mode,
                generatedLive,
                shouldReply,
                urgency,
                decisionReason,
                replyTiming);
    }

    private record ReplyDecision(String shouldReply, String urgency, String reason, String timing) {}

    private ReplyDecision evaluateReplyDecision(List<Message> messages, Long currentUserId, String context) {
        if (messages == null || messages.isEmpty()) {
            return new ReplyDecision(
                    "RECOMMENDED",
                    "LOW",
                    "Start the conversation! A friendly opening question or shared topic breaks the ice nicely.",
                    "Whenever you want to start chatting");
        }

        Message latest = messages.getFirst();
        boolean isMine = latest.getSenderId() != null && latest.getSenderId().equals(currentUserId);
        if (isMine) {
            return new ReplyDecision(
                    "NO_RUSH",
                    "LOW",
                    "You sent the last message. Giving them time and space to reply keeps things comfortable.",
                    "Wait for their reply");
        }

        String content = latest.getContent() != null ? latest.getContent().trim() : "";
        String lower = content.toLowerCase(Locale.ROOT);

        boolean isQuestion = content.contains("?") || lower.matches(".*\\b(what|when|where|why|how|who|kya|kab|kaise|free|plan|batao)\\b.*");
        boolean isClosing = lower.matches(".*\\b(good night|gn|bye|see you|cya|take care|shubh ratri|chalo bye|goodnight)\\b.*");

        if (isClosing) {
            return new ReplyDecision(
                    "OPTIONAL",
                    "LOW",
                    "They sent a friendly closing. No immediate reply is needed, but an emoji or sweet sign-off works great.",
                    "No rush, or tomorrow morning");
        }

        if (isQuestion) {
            return new ReplyDecision(
                    "RECOMMENDED",
                    "HIGH",
                    "They asked a direct question: \"" + truncate(content, 45) + "\". A reply keeps the chat alive and shows interest.",
                    "Within 1–2 hours");
        }

        if ("SHORT_REPLIES".equals(context)) {
            return new ReplyDecision(
                    "OPTIONAL",
                    "MEDIUM",
                    "The conversation is moving with quick short messages. You can answer casually or bring up a fresh topic.",
                    "Whenever you're free");
        }

        if ("QUIET_CONVERSATION".equals(context)) {
            return new ReplyDecision(
                    "RECOMMENDED",
                    "LOW",
                    "Some time has passed since their message. A relaxed callback or light question can restart the conversation.",
                    "When you have a quiet moment");
        }

        return new ReplyDecision(
                "RECOMMENDED",
                "MEDIUM",
                "They sent the latest message. Replying while it's fresh maintains great momentum!",
                "Within a few hours");
    }

    private String detectContext(List<Message> messages) {
        if (messages.isEmpty()) {
            return "NEW_CONVERSATION";
        }
        long shortReplies = messages.stream()
                .limit(4)
                .filter(message -> message.getContent() != null && message.getContent().trim().length() <= 24)
                .count();
        if (messages.size() >= 3 && shortReplies >= 3) {
            return "SHORT_REPLIES";
        }
        Instant lastMessageAt = messages.getFirst().getCreatedAt();
        if (lastMessageAt != null && lastMessageAt.isBefore(
                Instant.now().minus(properties.getQuietAfterHours(), ChronoUnit.HOURS))) {
            return "QUIET_CONVERSATION";
        }
        return "KEEP_IT_GOING";
    }

    private String guidanceFor(String context) {
        return switch (context) {
            case "NEW_CONVERSATION" -> "Start in an outer circle, then move closer as you discover shared context.";
            case "SHORT_REPLIES" -> "Use a playful or specific prompt that is easy to answer, then follow their lead.";
            case "QUIET_CONVERSATION" -> "Restart with a callback or shared interest without focusing on the silence.";
            default -> "Start with the closest available circle: reply now, revisit history, then explore interests.";
        };
    }

    private List<IceBreakerSuggestion> buildSuggestionPool(
            String context,
            List<String> mine,
            List<String> theirs,
            List<String> shared,
            ConversationSignalExtractor.ConversationSignals signals,
            String mode,
            String language) {
        List<IceBreakerSuggestion> result = new ArrayList<>();

        if ("UNABLE_TO_TALK".equals(mode)) {
            addUnableToTalkSuggestions(result, language);
        }

        signals.latestIncomingTopic().ifPresent(topic -> addDirectReplySuggestions(result, topic, context));
        signals.priorTopics().stream().limit(5).forEach(topic -> addCallbackSuggestions(result, topic, context));
        shared.stream().limit(4).forEach(interest -> addSharedInterestSuggestions(result, interest, context));

        List<String> discoveryTopics = theirs.stream()
                .filter(interest -> shared.stream().noneMatch(value -> value.equalsIgnoreCase(interest)))
                .limit(5)
                .toList();
        discoveryTopics.forEach(interest -> addDiscoverySuggestions(result, interest, context));
        if (discoveryTopics.isEmpty() && !mine.isEmpty()) {
            addSelfDisclosureSuggestions(result, mine.getFirst(), context);
        }
        addLightTouchSuggestions(result, context);
        return result;
    }

    private void addUnableToTalkSuggestions(List<IceBreakerSuggestion> result, String language) {
        boolean includeHinglish = "HINGLISH".equalsIgnoreCase(language) || "AUTO".equalsIgnoreCase(language);
        boolean includeEnglish = "ENGLISH".equalsIgnoreCase(language) || "AUTO".equalsIgnoreCase(language);

        if (includeEnglish) {
            result.add(new IceBreakerSuggestion(
                    "Hey! Caught up in something right now, will reply properly in a bit! 🙌",
                    "Busy right now",
                    "Quick, warm acknowledgment so they know you saw their message.",
                    "DIRECT_REPLY",
                    circleRules.get("DIRECT_REPLY").label(),
                    "WARM",
                    98,
                    "ENGLISH",
                    false));
            result.add(new IceBreakerSuggestion(
                    "In the middle of work right now, but will get back to you later tonight 😊",
                    "Work / Meeting",
                    "Sets clear expectations without breaking the connection.",
                    "DIRECT_REPLY",
                    circleRules.get("DIRECT_REPLY").label(),
                    "WARM",
                    95,
                    "ENGLISH",
                    false));
            result.add(new IceBreakerSuggestion(
                    "Can't chat at the moment, but let's definitely catch up once I'm free!",
                    "Can't talk now",
                    "Keeps the energy positive while giving you time.",
                    "LIGHT_TOUCH",
                    circleRules.get("LIGHT_TOUCH").label(),
                    "PLAYFUL",
                    92,
                    "ENGLISH",
                    false));
            result.add(new IceBreakerSuggestion(
                    "A bit tied up today! Saw your text and will reply as soon as I get a break.",
                    "Tied up",
                    "Reassures them you will reply when you have a moment.",
                    "DIRECT_REPLY",
                    circleRules.get("DIRECT_REPLY").label(),
                    "THOUGHTFUL",
                    90,
                    "ENGLISH",
                    false));
        }

        if (includeHinglish) {
            result.add(new IceBreakerSuggestion(
                    "Thoda busy hoon abhi, free hote hi text karta hoon! 🙌",
                    "Busy right now",
                    "Polite and clear update so they know you're tied up.",
                    "DIRECT_REPLY",
                    circleRules.get("DIRECT_REPLY").label(),
                    "WARM",
                    97,
                    "HINGLISH",
                    false));
            result.add(new IceBreakerSuggestion(
                    "Abhi thoda kaam mein fasa hoon, shaam ko aaram se reply karta hoon 😊",
                    "Kaam mein busy",
                    "Sets expectations on when you will be free.",
                    "DIRECT_REPLY",
                    circleRules.get("DIRECT_REPLY").label(),
                    "WARM",
                    94,
                    "HINGLISH",
                    false));
            result.add(new IceBreakerSuggestion(
                    "Saw your message! Abhi chat nahi kar sakta, thodi der mein baat karte hain.",
                    "Quick update",
                    "Acknowledges their text without ghosting.",
                    "LIGHT_TOUCH",
                    circleRules.get("LIGHT_TOUCH").label(),
                    "PLAYFUL",
                    91,
                    "HINGLISH",
                    false));
        }
    }

    private void addDirectReplySuggestions(List<IceBreakerSuggestion> result, String rawTopic, String context) {
        String topic = displayTopic(rawTopic);
        add(result, "DIRECT_REPLY", "CURIOUS", topic,
                "You mentioned " + topic + " — what part of it has been on your mind most?",
                "Uses a concrete signal from their latest message.", context, 4);
        add(result, "DIRECT_REPLY", "WARM", topic,
                "I’d like to hear more about " + topic + ". What happened next?",
                "Acknowledges what they just shared and invites a fuller answer.", context, 3);
        add(result, "DIRECT_REPLY", "PLAYFUL", topic,
                topic + " sounds like there’s a story there 😄 What’s the full version?",
                "Keeps the reply light while staying connected to their message.", context, 2);
        add(result, "DIRECT_REPLY", "THOUGHTFUL", topic,
                "What has " + topic + " taught you recently?",
                "Turns their latest topic into a more meaningful conversation.", context, 1);
    }

    private void addCallbackSuggestions(List<IceBreakerSuggestion> result, String rawTopic, String context) {
        String topic = displayTopic(rawTopic);
        add(result, "CALLBACK", "WARM", topic,
                "You mentioned " + topic + " earlier — how did that turn out?",
                "Remembering an earlier topic shows genuine attention.", context, 4);
        add(result, "CALLBACK", "CURIOUS", topic,
                "I was thinking about what you said about " + topic + ". Any update?",
                "Reopens a topic already established in your chat history.", context, 3);
        add(result, "CALLBACK", "PLAYFUL", topic,
                "Okay, I need the next episode of the " + topic + " story 😄",
                "A playful callback can revive a familiar thread.", context, 1);
    }

    private void addSharedInterestSuggestions(List<IceBreakerSuggestion> result, String interest, String context) {
        add(result, "COMMON_GROUND", "WARM", interest,
                "We both like " + interest + " — what’s your favorite memory connected to it?",
                "A shared interest makes the question personal without adding pressure.", context, 4);
        add(result, "COMMON_GROUND", "CURIOUS", interest,
                "What’s one thing about " + interest + " you wish more people understood?",
                "Invites an opinion about something you both enjoy.", context, 3);
        add(result, "COMMON_GROUND", "PLAYFUL", interest,
                "If we had a free day for " + interest + ", what would the plan be?",
                "Creates an easy, imaginative shared scenario.", context, 2);
    }

    private void addDiscoverySuggestions(List<IceBreakerSuggestion> result, String interest, String context) {
        add(result, "DISCOVERY", "CURIOUS", interest,
                "I saw you’re into " + interest + " — what got you started?",
                "Lets them tell a story about something they already enjoy.", context, 4);
        add(result, "DISCOVERY", "PLAYFUL", interest,
                "Quick choice: a relaxed " + interest + " day or an adventurous one?",
                "An either-or question is easy to answer and easy to follow up.", context, 3);
        add(result, "DISCOVERY", "THOUGHTFUL", interest,
                "What do you enjoy most about " + interest + " that people usually miss?",
                "Moves beyond a surface-level profile question.", context, 2);
    }

    private void addSelfDisclosureSuggestions(List<IceBreakerSuggestion> result, String interest, String context) {
        add(result, "DISCOVERY", "WARM", interest,
                "I’ve been really into " + interest + " lately. What have you been enjoying recently?",
                "Sharing first makes the invitation feel balanced.", context, 2);
        add(result, "DISCOVERY", "CURIOUS", interest,
                "My current favorite is " + interest + ". What interest would you introduce me to?",
                "Uses your interest to open a two-way discovery question.", context, 1);
    }

    private void addLightTouchSuggestions(List<IceBreakerSuggestion> result, String context) {
        add(result, "LIGHT_TOUCH", "WARM", "Today",
                "What’s been the best part of your day so far?",
                "Warm, specific, and easy to answer with more than one word.", context, 3);
        add(result, "LIGHT_TOUCH", "PLAYFUL", "Just for fun",
                "Would you rather plan a cozy evening or do something spontaneous?",
                "A simple choice creates an immediate follow-up.", context, 4);
        add(result, "LIGHT_TOUCH", "CURIOUS", "Favorites",
                "What’s something you could talk about for hours?",
                "Discovers a new interest when profile signals are limited.", context, 2);
        add(result, "LIGHT_TOUCH", "THOUGHTFUL", "This week",
                "What’s something you’re looking forward to this week?",
                "Restarts gently without calling attention to a reply gap.", context, 4);
        add(result, "LIGHT_TOUCH", "PLAYFUL", "Quick choice",
                "Pick one: sunrise plans, late-night conversations, or a lazy afternoon?",
                "Three concrete choices lower the effort needed to reply.", context, 3);
        add(result, "LIGHT_TOUCH", "WARM", "Small wins",
                "What’s a small thing that made you smile recently?",
                "Encourages a positive story without becoming too personal.", context, 2);
        add(result, "LIGHT_TOUCH", "CURIOUS", "Recommendations",
                "What’s one movie, song, or place you’d recommend without hesitation?",
                "Offers several routes into a more specific topic.", context, 2);
        add(result, "LIGHT_TOUCH", "THOUGHTFUL", "Goals",
                "What’s something you’d love to get better at this year?",
                "Invites aspiration while staying open and respectful.", context, 1);
    }

    private void add(
            List<IceBreakerSuggestion> result,
            String circle,
            String tone,
            String topic,
            String text,
            String reason,
            String context,
            int signalBoost) {
        ConversationCircleRulesEngine.CircleRule rule = circleRules.get(circle);
        int contextBoost = switch (context) {
            case "SHORT_REPLIES" -> tone.equals("PLAYFUL") ? 4 : 0;
            case "QUIET_CONVERSATION" -> circle.equals("CALLBACK") || tone.equals("WARM") ? 4 : 0;
            case "KEEP_IT_GOING" -> circle.equals("DIRECT_REPLY") ? 4 : 0;
            default -> circle.equals("COMMON_GROUND") || circle.equals("DISCOVERY") ? 3 : 0;
        };
        result.add(new IceBreakerSuggestion(
                text,
                topic,
                reason,
                circle,
                rule.label(),
                tone,
                Math.min(100, rule.baseScore() + contextBoost + signalBoost)));
    }

    private List<IceBreakerSuggestion> selectAcrossCircles(
            List<IceBreakerSuggestion> pool,
            int limit,
            String conversationId,
            int variant) {
        Map<String, List<IceBreakerSuggestion>> byCircle = new LinkedHashMap<>();
        for (ConversationCircleRulesEngine.CircleRule rule : circleRules.all()) {
            List<IceBreakerSuggestion> values = pool.stream()
                    .filter(suggestion -> suggestion.circle().equals(rule.code()))
                    .sorted(Comparator
                            .comparingInt((IceBreakerSuggestion suggestion) -> suggestion.relevanceScore() / 5).reversed()
                            .thenComparingLong(suggestion -> variantOrder(
                                    conversationId,
                                    suggestion.text(),
                                    variant)))
                    .toList();
            if (!values.isEmpty()) {
                byCircle.put(rule.code(), new ArrayList<>(values));
            }
        }

        List<IceBreakerSuggestion> selected = new ArrayList<>();
        boolean added;
        do {
            added = false;
            for (List<IceBreakerSuggestion> values : byCircle.values()) {
                if (!values.isEmpty() && selected.size() < limit) {
                    selected.add(values.removeFirst());
                    added = true;
                }
            }
        } while (added && selected.size() < limit);
        return List.copyOf(selected);
    }

    private long variantOrder(String conversationId, String text, int variant) {
        return Integer.toUnsignedLong((conversationId + '|' + variant + '|' + text).hashCode());
    }

    private List<IceBreakerSuggestion> deduplicate(List<IceBreakerSuggestion> suggestions) {
        Map<String, IceBreakerSuggestion> unique = new LinkedHashMap<>();
        suggestions.forEach(suggestion -> unique.putIfAbsent(
                suggestion.text().toLowerCase(Locale.ROOT),
                suggestion));
        return List.copyOf(unique.values());
    }

    private List<String> sharedInterests(List<String> mine, List<String> theirs) {
        Map<String, String> mineByKey = new LinkedHashMap<>();
        mine.forEach(value -> mineByKey.put(value.toLowerCase(Locale.ROOT), value));
        return theirs.stream()
                .filter(value -> mineByKey.containsKey(value.toLowerCase(Locale.ROOT)))
                .map(value -> mineByKey.get(value.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private List<String> distinctValues(List<String> first, List<String> second) {
        Map<String, String> values = new LinkedHashMap<>();
        first.forEach(value -> values.putIfAbsent(value.toLowerCase(Locale.ROOT), value));
        second.forEach(value -> values.putIfAbsent(value.toLowerCase(Locale.ROOT), value));
        return List.copyOf(values.values());
    }

    private String normalizeTone(String requestedTone) {
        String tone = requestedTone == null ? "ALL" : requestedTone.trim().toUpperCase(Locale.ROOT);
        return TONES.contains(tone) ? tone : "ALL";
    }

    private String normalizeLanguage(String requestedLanguage) {
        String language = requestedLanguage == null
                ? "AUTO"
                : requestedLanguage.trim().toUpperCase(Locale.ROOT);
        return LANGUAGES.contains(language) ? language : "AUTO";
    }

    private String normalizeMode(String requestedMode) {
        String mode = requestedMode == null ? "SUGGEST" : requestedMode.trim().toUpperCase(Locale.ROOT);
        return MODES.contains(mode) ? mode : "SUGGEST";
    }

    private String normalizeGeneratedTone(String value) {
        String tone = normalizeTone(value);
        return tone.equals("ALL") ? "WARM" : tone;
    }

    private String normalizeGeneratedLanguage(String value) {
        String language = normalizeLanguage(value);
        return language.equals("AUTO") ? "ENGLISH" : language;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String clean = value.trim().replaceAll("\\s+", " ");
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength);
    }

    private String displayTopic(String value) {
        if (value == null || value.isBlank()) {
            return "that";
        }
        String clean = value.trim().replaceAll("\\s+", " ");
        return clean.substring(0, 1).toUpperCase(Locale.ROOT) + clean.substring(1);
    }
}
