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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
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
        return getSuggestions(conversationId, userId, limit, requestedTone, variant, requestedLanguage, requestedMode, null, null, null);
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
            String requestedMode,
            String customApiKey,
            String customProvider,
            String customModel) {
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
                        recentMessages,
                        customApiKey,
                        customProvider,
                        customModel));
        if (liveBatch.isPresent()) {
            List<IceBreakerSuggestion> liveSuggestions = mapLiveSuggestions(
                    liveBatch.get().replies(), tone, language, safeLimit);
            if (!liveSuggestions.isEmpty()) {
                LiveConversationAssistant.ReplyBatch batch = liveBatch.get();
                String source = (customProvider != null && !customProvider.isBlank())
                        ? "LIVE_" + customProvider.toUpperCase(Locale.ROOT)
                        : "LIVE_AI";
                return response(
                        conversationId,
                        context,
                        batch.guidance(),
                        sharedInterests,
                        signals.priorTopics(),
                        liveSuggestions,
                        source,
                        language,
                        mode,
                        true,
                        batch.shouldReply(),
                        batch.urgency(),
                        batch.decisionReason(),
                        batch.replyTiming(),
                        batch.detectedMood(),
                        batch.conversationScenario());
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

        List<IceBreakerSuggestion> deduplicated = deduplicate(pool);
        List<IceBreakerSuggestion> languageFiltered;
        if ("HINGLISH".equalsIgnoreCase(language)) {
            List<IceBreakerSuggestion> hinglishOnly = deduplicated.stream()
                    .filter(s -> "HINGLISH".equalsIgnoreCase(s.language()))
                    .toList();
            languageFiltered = hinglishOnly.isEmpty() ? deduplicated : hinglishOnly;
        } else if ("ENGLISH".equalsIgnoreCase(language)) {
            List<IceBreakerSuggestion> englishOnly = deduplicated.stream()
                    .filter(s -> "ENGLISH".equalsIgnoreCase(s.language()))
                    .toList();
            languageFiltered = englishOnly.isEmpty() ? deduplicated : englishOnly;
        } else {
            if (signals.isHinglish()) {
                List<IceBreakerSuggestion> hinglishOnly = deduplicated.stream()
                        .filter(s -> "HINGLISH".equalsIgnoreCase(s.language()))
                        .toList();
                languageFiltered = hinglishOnly.isEmpty() ? deduplicated : hinglishOnly;
            } else {
                languageFiltered = deduplicated;
            }
        }

        Predicate<IceBreakerSuggestion> toneFilter = suggestion ->
                tone.equals("ALL") || suggestion.tone().equals(tone);
        List<IceBreakerSuggestion> eligible = languageFiltered.stream()
                .filter(toneFilter)
                .filter(suggestion -> !moderationService.analyze(suggestion.text()).flagged())
                .toList();
        if (eligible.isEmpty() && !tone.equals("ALL")) {
            eligible = languageFiltered.stream()
                    .filter(suggestion -> !moderationService.analyze(suggestion.text()).flagged())
                    .toList();
        }
        List<IceBreakerSuggestion> selected = selectAcrossCircles(
                eligible,
                safeLimit,
                conversationId,
                variant);
        return response(
                conversationId,
                context,
                decision.reason() + " (Offline mode; connect Gemini or OpenAI API key for live AI suggestions)",
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
                decision.timing(),
                signals.detectedMood(),
                signals.scenarioSummary());
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
            List<Message> recentMessages,
            String customApiKey,
            String customProvider,
            String customModel) {
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
                history,
                customApiKey,
                customProvider,
                customModel);
    }

    private List<IceBreakerSuggestion> mapLiveSuggestions(
            List<LiveConversationAssistant.Reply> replies,
            String requestedTone,
            String requestedLanguage,
            int limit) {
        List<IceBreakerSuggestion> result = mapLiveSuggestionsInternal(replies, requestedTone, requestedLanguage, limit);
        if (result.isEmpty() && !requestedTone.equals("ALL")) {
            result = mapLiveSuggestionsInternal(replies, "ALL", requestedLanguage, limit);
        }
        if (result.isEmpty() && !"AUTO".equalsIgnoreCase(requestedLanguage)) {
            result = mapLiveSuggestionsInternal(replies, requestedTone, "AUTO", limit);
        }
        return result;
    }

    private List<IceBreakerSuggestion> mapLiveSuggestionsInternal(
            List<LiveConversationAssistant.Reply> replies,
            String requestedTone,
            String requestedLanguage,
            int limit) {
        Map<String, IceBreakerSuggestion> unique = new LinkedHashMap<>();
        int rank = 0;
        for (LiveConversationAssistant.Reply reply : replies) {
            String text = truncate(reply.text(), 500).trim();
            String tone = normalizeGeneratedTone(reply.tone());
            String lang = normalizeGeneratedLanguage(reply.language());
            if (text.isBlank()
                    || (!requestedTone.equals("ALL") && !requestedTone.equals(tone))
                    || ("HINGLISH".equalsIgnoreCase(requestedLanguage) && !"HINGLISH".equalsIgnoreCase(lang))
                    || ("ENGLISH".equalsIgnoreCase(requestedLanguage) && !"ENGLISH".equalsIgnoreCase(lang))
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
                    lang,
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
            String replyTiming,
            String detectedMood,
            String conversationScenario) {
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
                replyTiming,
                detectedMood,
                conversationScenario);
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
            addUnableToTalkSuggestions(result, language, signals);
        }

        // Direct intent recognition (meals, activity, location, availability, well-being, invitations)
        addDirectIntentSuggestions(result, language, signals, context);

        // Word mirroring for short / teaser messages (e.g., "bal", "lol", "hmm")
        addWordMirroringSuggestions(result, language, signals, context);

        // Cross-profile interest synergy
        addInterestSynergySuggestions(result, language, mine, theirs, shared, context);

        boolean includeHinglish = "HINGLISH".equalsIgnoreCase(language) || "AUTO".equalsIgnoreCase(language);
        boolean includeEnglish = "ENGLISH".equalsIgnoreCase(language) || "AUTO".equalsIgnoreCase(language);

        if (includeEnglish) {
            signals.latestIncomingTopic().ifPresent(topic -> addDirectReplySuggestions(result, topic, context, signals));
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
        }

        if (includeHinglish) {
            signals.latestIncomingTopic().ifPresent(topic -> addHinglishDirectReplySuggestions(result, topic, context, signals));
            signals.priorTopics().stream().limit(5).forEach(topic -> addHinglishCallbackSuggestions(result, topic, context));
            shared.stream().limit(4).forEach(interest -> addHinglishSharedInterestSuggestions(result, interest, context));

            List<String> discoveryTopics = theirs.stream()
                    .filter(interest -> shared.stream().noneMatch(value -> value.equalsIgnoreCase(interest)))
                    .limit(5)
                    .toList();
            discoveryTopics.forEach(interest -> addHinglishDiscoverySuggestions(result, interest, context));
            if (discoveryTopics.isEmpty() && !mine.isEmpty()) {
                addHinglishSelfDisclosureSuggestions(result, mine.getFirst(), context);
            }
            addHinglishLightTouchSuggestions(result, context);
        }
        return result;
    }

    private void addUnableToTalkSuggestions(
            List<IceBreakerSuggestion> result,
            String language,
            ConversationSignalExtractor.ConversationSignals signals) {
        boolean includeHinglish = "HINGLISH".equalsIgnoreCase(language) || "AUTO".equalsIgnoreCase(language);
        boolean includeEnglish = "ENGLISH".equalsIgnoreCase(language) || "AUTO".equalsIgnoreCase(language);

        String topic = signals.latestIncomingTopic().map(this::displayTopic).orElse("");
        String mood = signals.detectedMood();
        boolean hasTopic = !topic.isBlank();
        boolean isQuestion = signals.isQuestion();

        if (includeEnglish) {
            if (isQuestion) {
                String questionTarget = hasTopic ? " about " + topic : "";
                result.add(new IceBreakerSuggestion(
                        "Saw your message" + questionTarget + "! A bit tied up right now, will reply with full details as soon as I get a breather 😊",
                        hasTopic ? topic : "Question",
                        "Directly acknowledges their specific question while explaining you are tied up.",
                        "DIRECT_REPLY",
                        circleRules.get("DIRECT_REPLY").label(),
                        "WARM",
                        99,
                        "ENGLISH",
                        false));
            } else if (hasTopic) {
                result.add(new IceBreakerSuggestion(
                        "Caught up with something right now, but saw what you said about " + topic + "! Let's talk about it once I'm free 🙌",
                        topic,
                        "Shows you saw their specific message about " + topic + ".",
                        "DIRECT_REPLY",
                        circleRules.get("DIRECT_REPLY").label(),
                        "WARM",
                        98,
                        "ENGLISH",
                        false));
            }

            if ("Exhausted & Stressed".equals(mood)) {
                result.add(new IceBreakerSuggestion(
                        "Hey, in meetings/working right now, but sounds like you've had quite a demanding day! Get some rest, texting you tonight.",
                        "Demanding day",
                        "Empathizes with their fatigue while setting expectations.",
                        "DIRECT_REPLY",
                        circleRules.get("DIRECT_REPLY").label(),
                        "WARM",
                        96,
                        "ENGLISH",
                        false));
            } else if ("Playful & Teasing".equals(mood)) {
                result.add(new IceBreakerSuggestion(
                        "Haha can't chat right this second! Don't start without me, texting you back shortly 😄",
                        "Banter holding",
                        "Maintains their playful vibe while explaining you are tied up.",
                        "LIGHT_TOUCH",
                        circleRules.get("LIGHT_TOUCH").label(),
                        "PLAYFUL",
                        95,
                        "ENGLISH",
                        false));
            }

            result.add(new IceBreakerSuggestion(
                    "Hey! Caught up in something right now, but will get back to you properly in a bit 🙌",
                    "Busy right now",
                    "Warm holding reply so they don't feel left on read.",
                    "DIRECT_REPLY",
                    circleRules.get("DIRECT_REPLY").label(),
                    "WARM",
                    94,
                    "ENGLISH",
                    false));

            result.add(new IceBreakerSuggestion(
                    "Can't chat at the moment, but let's definitely catch up once I'm free!",
                    "Can't talk now",
                    "Keeps the energy positive while giving you breathing space.",
                    "LIGHT_TOUCH",
                    circleRules.get("LIGHT_TOUCH").label(),
                    "PLAYFUL",
                    90,
                    "ENGLISH",
                    false));
        }

        if (includeHinglish) {
            if (isQuestion) {
                String questionTarget = hasTopic ? " (" + topic + " ke baare mein)" : "";
                result.add(new IceBreakerSuggestion(
                        "Tumhara sawal dekha" + questionTarget + "! Abhi thoda fasa hoon, aaram se poora jawab deta hoon thodi der mein 😊",
                        hasTopic ? topic : "Question",
                        "Sawal acknowledge karke warm holding reply.",
                        "DIRECT_REPLY",
                        circleRules.get("DIRECT_REPLY").label(),
                        "WARM",
                        99,
                        "HINGLISH",
                        false));
            } else if (hasTopic) {
                result.add(new IceBreakerSuggestion(
                        "Thoda busy hoon abhi! " + topic + " wala text dekha, free hote hi aaram se baat karta hoon 🙌",
                        topic,
                        topic + " ko reference karke holding message banaya gaya hai.",
                        "DIRECT_REPLY",
                        circleRules.get("DIRECT_REPLY").label(),
                        "WARM",
                        98,
                        "HINGLISH",
                        false));
            }

            if ("Exhausted & Stressed".equals(mood)) {
                result.add(new IceBreakerSuggestion(
                        "Abhi kaam mein thoda fasa hoon, par tum rest karo abhi! Free hote hi call/text karta hoon 😊",
                        "Kaam / Stress",
                        "Unke mood ko samajh kar empathic holding reply.",
                        "DIRECT_REPLY",
                        circleRules.get("DIRECT_REPLY").label(),
                        "WARM",
                        96,
                        "HINGLISH",
                        false));
            } else if ("Playful & Teasing".equals(mood)) {
                result.add(new IceBreakerSuggestion(
                        "Haha abhi fasa hoon thoda! Mere bina shuru mat ho jana, thodi der mein text karta hoon 😄",
                        "Playful holding",
                        "Masti aur banter continue rakhte hue holding message.",
                        "LIGHT_TOUCH",
                        circleRules.get("LIGHT_TOUCH").label(),
                        "PLAYFUL",
                        95,
                        "HINGLISH",
                        false));
            }

            result.add(new IceBreakerSuggestion(
                    "Thoda sa busy hoon abhi, shaam ko free hote hi text karta hoon! 🙌",
                    "Quick update",
                    "Seedha aur polite message bina ghost kare.",
                    "LIGHT_TOUCH",
                    circleRules.get("LIGHT_TOUCH").label(),
                    "WARM",
                    93,
                    "HINGLISH",
                    false));

            result.add(new IceBreakerSuggestion(
                    "Abhi baat nahi kar paunga, par free hote hi pakka catch up karte hain!",
                    "Can't talk now",
                    "Friendly holding message bina awkwardness ke.",
                    "LIGHT_TOUCH",
                    circleRules.get("LIGHT_TOUCH").label(),
                    "PLAYFUL",
                    90,
                    "HINGLISH",
                    false));
        }
    }

    private void addDirectReplySuggestions(
            List<IceBreakerSuggestion> result,
            String rawTopic,
            String context,
            ConversationSignalExtractor.ConversationSignals signals) {
        String topic = displayTopic(rawTopic);
        String mood = signals.detectedMood();

        if ("Playful & Teasing".equals(mood)) {
            add(result, "DIRECT_REPLY", "PLAYFUL", topic,
                    "Wait, are you seriously telling me about " + topic + " right now? 😄 Tell me the full story!",
                    "Matches their witty, playful mood with matching banter.", context, 5);
            add(result, "DIRECT_REPLY", "WARM", topic,
                    topic + " sounds like there's definitely more here than you're letting on 😉",
                    "Leans into their lighthearted teasing tone.", context, 4);
        } else if ("Exhausted & Stressed".equals(mood)) {
            add(result, "DIRECT_REPLY", "WARM", topic,
                    "Oof, " + topic + " sounds like it was completely draining. Did you at least get a chance to unwind?",
                    "Validates their stress with empathetic care.", context, 5);
            add(result, "DIRECT_REPLY", "THOUGHTFUL", topic,
                    "Take a deep breath! Don't let " + topic + " ruin your whole evening.",
                    "Provides calm, supportive reassurance.", context, 4);
        } else if ("Excited & Enthusiastic".equals(mood)) {
            add(result, "DIRECT_REPLY", "CURIOUS", topic,
                    "Love the excitement around " + topic + "! What was the absolute highlight?",
                    "Matches their high energy with genuine curiosity.", context, 5);
            add(result, "DIRECT_REPLY", "WARM", topic,
                    "That energy about " + topic + " is infectious! Tell me everything 😄",
                    "Keeps the vibrant conversation flowing.", context, 4);
        } else if ("Flirtatious & Warm".equals(mood)) {
            add(result, "DIRECT_REPLY", "PLAYFUL", topic,
                    "Now that's a charming way to talk about " + topic + " 😉",
                    "Deepens the romantic/flirty spark.", context, 5);
            add(result, "DIRECT_REPLY", "WARM", topic,
                    "You bringing up " + topic + " just made my day a whole lot brighter 😊",
                    "Warm, affectionate acknowledgment.", context, 4);
        } else if (signals.isQuestion()) {
            add(result, "DIRECT_REPLY", "CURIOUS", topic,
                    "Regarding " + topic + " — that's such an interesting question! What made you ask?",
                    "Answers their question by exploring the curiosity behind it.", context, 5);
            add(result, "DIRECT_REPLY", "WARM", topic,
                    "I’d love to answer that about " + topic + ". Let me tell you how it went!",
                    "Directly addresses their inquiry with open engagement.", context, 4);
        } else {
            add(result, "DIRECT_REPLY", "CURIOUS", topic,
                    "I was really curious about how " + topic + " turned out for you!",
                    "Engages directly with what they just shared.", context, 4);
            add(result, "DIRECT_REPLY", "WARM", topic,
                    "I'd love to hear more about " + topic + ". What happened next?",
                    "Invites a fuller, richer response.", context, 3);
        }
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
        addTopicSpecificSuggestions(result, interest, "COMMON_GROUND", true, context, false);
    }

    private void addDiscoverySuggestions(List<IceBreakerSuggestion> result, String interest, String context) {
        addTopicSpecificSuggestions(result, interest, "DISCOVERY", false, context, false);
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
        // Playful Banter & Dating Quirks
        add(result, "LIGHT_TOUCH", "PLAYFUL", "Texting Habits",
                "Are you the type of person who replies in 0.2 seconds or takes 3 business days? There is no middle ground 😂",
                "Playful texting habits observation.", context, 4);
        add(result, "LIGHT_TOUCH", "PLAYFUL", "Weekend Vibe",
                "On a scale of 1 to 10, how chaotic is your weekly routine right now?",
                "Lighthearted, relatable lifestyle question.", context, 3);
        add(result, "LIGHT_TOUCH", "PLAYFUL", "Green Flags",
                "What’s your biggest green flag that people usually don’t notice right away? ✨",
                "Positive, flattering inquiry.", context, 4);
        add(result, "LIGHT_TOUCH", "PLAYFUL", "Elevator Test",
                "If we were stuck in an elevator for an hour, would we end up in uncontrollable laughter or deep debates?",
                "Witty situational thought experiment.", context, 4);
        add(result, "LIGHT_TOUCH", "PLAYFUL", "Superpowers",
                "What’s a completely useless superpower that you would secretly love to have?",
                "Fun and imaginative banter.", context, 3);
        add(result, "LIGHT_TOUCH", "PLAYFUL", "Midnight Plans",
                "Spontaneous midnight drive with great music, or cozy terrace conversations under the stars? 🌌",
                "Romantic and atmospheric either-or choice.", context, 4);
        add(result, "LIGHT_TOUCH", "PLAYFUL", "Teleportation",
                "If you could teleport anywhere in the world for just 3 hours tonight, where are we heading?",
                "Adventurous spontaneous fantasy.", context, 4);
        add(result, "LIGHT_TOUCH", "PLAYFUL", "Dinner Dilemma",
                "Cook a homemade dinner together from scratch, or order 4 different comfort takeout dishes?",
                "Fun food dynamic question.", context, 3);

        // Deep & Sincere Connection
        add(result, "LIGHT_TOUCH", "THOUGHTFUL", "Peaceful Sunday",
                "What does your ideal peaceful Sunday morning look like from the moment your eyes open?",
                "Gentle glimpse into their inner world.", context, 4);
        add(result, "LIGHT_TOUCH", "THOUGHTFUL", "Life Lessons",
                "What’s a life lesson you had to learn the hard way that you’re genuinely grateful for today?",
                "Deep, authentic sharing opportunity.", context, 4);
        add(result, "LIGHT_TOUCH", "THOUGHTFUL", "Real Laughter",
                "When was the last time you laughed so hard that your stomach genuinely hurt?",
                "Evokes a joyful personal memory.", context, 3);
        add(result, "LIGHT_TOUCH", "THOUGHTFUL", "Quiet Pride",
                "What’s something about yourself that you’re quietly proud of that you rarely get to brag about?",
                "Honors self-worth and subtle accomplishments.", context, 4);
        add(result, "LIGHT_TOUCH", "THOUGHTFUL", "Comfort",
                "What does true comfort with another person look like to you? Easy banter or comfortable silence?",
                "Reveals emotional intimacy preference.", context, 3);
        add(result, "LIGHT_TOUCH", "THOUGHTFUL", "Perspectives",
                "What’s a belief or perspective you used to hold strongly that completely changed recently?",
                "Shows intellectual openness and depth.", context, 3);
        add(result, "LIGHT_TOUCH", "THOUGHTFUL", "Small Talk Escape",
                "What’s one question you wish people asked you more often instead of generic small talk?",
                "Directly invites them to direct the conversation.", context, 4);
        add(result, "LIGHT_TOUCH", "THOUGHTFUL", "Time Capsule",
                "If you could send a 10-second voice note to your younger self 5 years ago, what would you say?",
                "Reflective and tender question.", context, 3);

        // Daily Life, Habits & Curiosity
        add(result, "LIGHT_TOUCH", "WARM", "Today",
                "What’s been the best part of your day so far?",
                "Warm, specific, and easy to answer.", context, 3);
        add(result, "LIGHT_TOUCH", "WARM", "Comfort Food",
                "What’s your ultimate comfort meal after a long, exhausting day?",
                "Universally relatable comfort food conversation.", context, 3);
        add(result, "LIGHT_TOUCH", "WARM", "Everyday Joy",
                "What’s a small everyday luxury that you refuse to compromise on?",
                "Delightful personal lifestyle preference.", context, 2);
        add(result, "LIGHT_TOUCH", "WARM", "Quiet Afternoon",
                "If you had a completely free afternoon with zero notifications, what’s step one?",
                "Low-pressure relaxation prompt.", context, 3);
        add(result, "LIGHT_TOUCH", "CURIOUS", "Rabbit Holes",
                "What’s something you could easily talk about for 2 hours without preparing at all?",
                "Uncovers their authentic passion.", context, 4);
        add(result, "LIGHT_TOUCH", "CURIOUS", "Recommendations",
                "What’s one movie, album, or destination that you recommend with absolute certainty?",
                "Effortlessly surfaces recommendations.", context, 3);
        add(result, "LIGHT_TOUCH", "CURIOUS", "Energy Clock",
                "Are you an early morning sunrise person or a midnight night-owl thinker?",
                "Explores daily circadian rhythms.", context, 2);
        add(result, "LIGHT_TOUCH", "CURIOUS", "Quirks",
                "What’s a quirky habit or ritual you have that most people don’t know about?",
                "Endearing and vulnerable quirk sharing.", context, 3);

        // Choices & Hypotheses
        add(result, "LIGHT_TOUCH", "PLAYFUL", "Getaways",
                "Quick choice: spontaneous road trip or planned luxury staycation?",
                "Simple choice creating an immediate follow-up.", context, 4);
        add(result, "LIGHT_TOUCH", "PLAYFUL", "Dream Cabin",
                "Living in a cozy wooden cottage in the foggy woods, or a high-rise penthouse overlooking a lit-up city?",
                "Aesthetic living environment debate.", context, 3);
        add(result, "LIGHT_TOUCH", "PLAYFUL", "Chore Eradicator",
                "Never having to do laundry again, or never having to cook dinner again?",
                "Classic witty domestic dilemma.", context, 3);
        add(result, "LIGHT_TOUCH", "PLAYFUL", "Atmospheres",
                "Exploring a vibrant night market with incredible street food, or having a private picnic on a quiet cliff?",
                "Sensory, imaginative choice.", context, 3);
        add(result, "LIGHT_TOUCH", "WARM", "Mood Anthem",
                "What’s a song that immediately puts you in a good mood no matter what kind of day you’re having?",
                "Sparks music sharing and smiles.", context, 3);
        add(result, "LIGHT_TOUCH", "WARM", "Upcoming Joy",
                "What’s something you’re looking forward to this month that’s keeping you excited?",
                "Future-oriented optimistic topic.", context, 3);
        add(result, "LIGHT_TOUCH", "THOUGHTFUL", "Admirable Skills",
                "What’s a skill you’ve always admired in others that you’d love to master yourself?",
                "Invites healthy aspiration.", context, 2);
        add(result, "LIGHT_TOUCH", "CURIOUS", "Life Chapters",
                "If you were writing a book about your life so far, what would the current chapter be titled?",
                "Creative, self-aware storytelling.", context, 3);
    }

    private void addHinglishDirectReplySuggestions(
            List<IceBreakerSuggestion> result,
            String rawTopic,
            String context,
            ConversationSignalExtractor.ConversationSignals signals) {
        String topic = displayTopic(rawTopic);
        String mood = signals.detectedMood();

        if ("Playful & Teasing".equals(mood)) {
            addHinglish(result, "DIRECT_REPLY", "PLAYFUL", topic,
                    "Arre waah, sach mein tum " + topic + " ke baare mein bata rahe ho? 😄 Poori baat batao!",
                    "Witty aur teasing banter ke saath chat aage badhata hai.", context, 5);
            addHinglish(result, "DIRECT_REPLY", "WARM", topic,
                    topic + " sunkar lag raha hai ki koi mast scene chal raha hai 😉",
                    "Playful teasing ke saath chemistry banata hai.", context, 4);
        } else if ("Exhausted & Stressed".equals(mood)) {
            addHinglish(result, "DIRECT_REPLY", "WARM", topic,
                    "Arre baap re, " + topic + " sunkar hi thaka dene wala lag raha hai. Abhi thoda aaram karne ka time mila?",
                    "Unke stress ko samajhkar caring aur warm response deta hai.", context, 5);
            addHinglish(result, "DIRECT_REPLY", "THOUGHTFUL", topic,
                    "Gehri saans lo! " + topic + " ki wajah se apna poora evening kharab mat hone do.",
                    "Calm aur supportive reassurance deta hai.", context, 4);
        } else if ("Excited & Enthusiastic".equals(mood)) {
            addHinglish(result, "DIRECT_REPLY", "CURIOUS", topic,
                    topic + " ko lekar itni badhiya energy dekhkar maza aa gaya! Best part kya tha?",
                    "High energy ko match karte hue curiosity show karta hai.", context, 5);
            addHinglish(result, "DIRECT_REPLY", "WARM", topic,
                    "Yeh " + topic + " wali vibe sach mein contagious hai! Sab kuch batao 😄",
                    "Excitement celebrate karte hue baat aage badhata hai.", context, 4);
        } else if ("Flirtatious & Warm".equals(mood)) {
            addHinglish(result, "DIRECT_REPLY", "PLAYFUL", topic,
                    topic + " ke baare mein baat karne ka yeh andaaz kaafi cute hai 😉",
                    "Romantic spark aur chemistry ko deepen karta hai.", context, 5);
            addHinglish(result, "DIRECT_REPLY", "WARM", topic,
                    "Tumhara " + topic + " wala text dekhkar din ban gaya 😊",
                    "Affectionate aur warm acknowledgment.", context, 4);
        } else if ("Late-night & Cozy".equals(mood)) {
            addHinglish(result, "DIRECT_REPLY", "WARM", topic,
                    "Itni der raat ko " + topic + " ki baatein! Vaise neend nahi aa rahi kya? 🌙",
                    "Late-night cozy feel ke saath natural reply.", context, 5);
            addHinglish(result, "DIRECT_REPLY", "PLAYFUL", topic,
                    "Late night thoughts on " + topic + "! Kal aaram se sunte hain ya abhi bataoge?",
                    "No pressure gentle closing.", context, 4);
        } else if (signals.isQuestion()) {
            addHinglish(result, "DIRECT_REPLY", "CURIOUS", topic,
                    topic + " ke baare mein accha sawal poocha! Vaise tumhare dimag mein yeh kaise aaya?",
                    "Unke sawal ke peeche ki curiosity explore karta hai.", context, 5);
            addHinglish(result, "DIRECT_REPLY", "WARM", topic,
                    "Iska answer toh mere paas mast hai, suno " + topic + " ke baare mein kya hua tha!",
                    "Direct aur engaging response.", context, 4);
        } else {
            addHinglish(result, "DIRECT_REPLY", "CURIOUS", topic,
                    "Main sach mein curious tha ki " + topic + " kaisa raha tumhare liye!",
                    "Unke share kiye topic par genuine interest dikhata hai.", context, 4);
            addHinglish(result, "DIRECT_REPLY", "WARM", topic,
                    topic + " ke baare mein aur sunna chahunga, aage kya hua?",
                    "Richer aur open reply invite karta hai.", context, 3);
        }
    }

    private void addHinglishCallbackSuggestions(List<IceBreakerSuggestion> result, String rawTopic, String context) {
        String topic = displayTopic(rawTopic);
        addHinglish(result, "CALLBACK", "WARM", topic,
                "Tumne pehle " + topic + " ka zikr kiya tha — kaisa raha phir wo?",
                "Pehle ki baat yaad rakhna genuine attention dikhata hai.", context, 4);
        addHinglish(result, "CALLBACK", "CURIOUS", topic,
                "Main wahi soch raha tha jo tumne " + topic + " ke baare mein bola tha. Koi naya update?",
                "Purani chat ke thread ko casually revive karta hai.", context, 3);
        addHinglish(result, "CALLBACK", "PLAYFUL", topic,
                "Chalo, mujhe " + topic + " wali story ka agla episode sunna hai 😄",
                "Playful callback familiar thread ko revive karta hai.", context, 1);
    }

    private void addHinglishSharedInterestSuggestions(List<IceBreakerSuggestion> result, String interest, String context) {
        addTopicSpecificSuggestions(result, interest, "COMMON_GROUND", true, context, true);
    }

    private void addHinglishDiscoverySuggestions(List<IceBreakerSuggestion> result, String interest, String context) {
        addTopicSpecificSuggestions(result, interest, "DISCOVERY", false, context, true);
    }

    private void addHinglishSelfDisclosureSuggestions(List<IceBreakerSuggestion> result, String interest, String context) {
        addHinglish(result, "DISCOVERY", "WARM", interest,
                "Aaj kal mujhe " + interest + " bohot pasand aa raha hai. Tumhe recently kya accha lag raha hai?",
                "Apna batakar unse poochne se convo balanced lagta hai.", context, 2);
        addHinglish(result, "DISCOVERY", "CURIOUS", interest,
                "Mera recent favorite " + interest + " hai! Tum mujhe kaunsi cheez recommend karoge?",
                "Two-way recommendation discovery start karta hai.", context, 1);
    }

    private void addHinglishLightTouchSuggestions(List<IceBreakerSuggestion> result, String context) {
        // Playful Banter & Dating Quirks
        addHinglish(result, "LIGHT_TOUCH", "PLAYFUL", "Reply Time",
                "Aap 2 second mein reply karne wale insaan ho ya 3 business days lagte hain? 😂",
                "Playful texting habits banter.", context, 4);
        addHinglish(result, "LIGHT_TOUCH", "PLAYFUL", "Weekend Plans",
                "Sach sach batao — weekend pe bahar nikalne ka plan banta hai ya 'bed se uthne ka mann nahi' jeet jata hai? 🛌",
                "Super relatable weekend dilemma.", context, 4);
        addHinglish(result, "LIGHT_TOUCH", "PLAYFUL", "Rapid Fire",
                "Agar hum dono ka rapid-fire Q&A ho toh sabse pehle kiska secret bahar aayega? 😉",
                "Fun and teasing interactive prompt.", context, 4);
        addHinglish(result, "LIGHT_TOUCH", "PLAYFUL", "Green Flag",
                "Aapka sabse bada green flag kya hai jo log pehli nazar mein notice nahi karte? ✨",
                "Complimentary and warm icebreaker.", context, 4);
        addHinglish(result, "LIGHT_TOUCH", "PLAYFUL", "Movie Life",
                "Aapki life agar ek movie hoti, toh uska genre rom-com hota ya chaotic comedy? 😄",
                "Witty cinematic self-description.", context, 3);
        addHinglish(result, "LIGHT_TOUCH", "PLAYFUL", "Late Night Drive",
                "Raat ke 12 baje drive pe nikalna with loud music, ya terrace par quiet late-night baatein? 🌙",
                "Atmospheric late-night choice.", context, 4);
        addHinglish(result, "LIGHT_TOUCH", "PLAYFUL", "Teleport",
                "Agar aaj raat 3 ghante ke liye duniya mein kahin bhi teleport ho sakte, toh kahan chalte?",
                "Adventurous spontaneous fantasy.", context, 4);
        addHinglish(result, "LIGHT_TOUCH", "PLAYFUL", "Food Experiment",
                "Ghar par sath mein cooking experiment karna, ya 4 alag alag jagah se favourite food order karna? 🍕",
                "Relatable and delicious scenario.", context, 3);

        // Deep & Sincere Connection
        addHinglish(result, "LIGHT_TOUCH", "THOUGHTFUL", "Perfect Sunday",
                "Aapke liye perfect Sunday ka matlab kya hai — silence, achhi coffee aur book, ya dosto ke sath chill karna?",
                "Gentle exploration of peaceful routines.", context, 4);
        addHinglish(result, "LIGHT_TOUCH", "THOUGHTFUL", "Real Laughter",
                "Aakhri baar itna kab hasse the ki pet mein dard hone laga aur aankhon se aansu nikal aaye? 😂",
                "Evokes a wholesome laughing memory.", context, 3);
        addHinglish(result, "LIGHT_TOUCH", "THOUGHTFUL", "Quiet Pride",
                "Apne baare mein aisi kaunsi baat hai jis par tumhe secretly garv hai par zyada log nahi jaante?",
                "Thoughtful self-worth recognition.", context, 4);
        addHinglish(result, "LIGHT_TOUCH", "THOUGHTFUL", "Peace & Calm",
                "Aapko sabse zyada sukoon kis cheez se milta hai jab poora din stressful raha ho?",
                "Comforting emotional safe space.", context, 4);
        addHinglish(result, "LIGHT_TOUCH", "THOUGHTFUL", "Comfort Zone",
                "Kisi ke sath comfortable hona kya hota hai — bina ruke baatein karna ya chup chaap sath baithna?",
                "Explores emotional connection styles.", context, 3);
        addHinglish(result, "LIGHT_TOUCH", "THOUGHTFUL", "Maturity",
                "Aisi kaunsi baat hai jo pehle tumhare liye bohot matter karti thi par ab bilkul nahi karti?",
                "Growth and perspective shift.", context, 3);
        addHinglish(result, "LIGHT_TOUCH", "THOUGHTFUL", "Real Talk",
                "Formal small talk chhodkar, aisi kaunsi baat hai jisme sach mein ghanto kho sakte ho?",
                "Invites deep, uninhibited conversation.", context, 4);
        addHinglish(result, "LIGHT_TOUCH", "THOUGHTFUL", "Younger Self",
                "Agar 5 saal pehle wale khud se 10 second baat karne ka mauka mile, toh kya advice doge?",
                "Meaningful nostalgic reflection.", context, 3);

        // Daily Life, Habits & Curiosity
        addHinglish(result, "LIGHT_TOUCH", "WARM", "Aaj ka din",
                "Aaj ke din ka sabse best part kya raha?",
                "Warm, specific, and easy to answer.", context, 3);
        addHinglish(result, "LIGHT_TOUCH", "WARM", "Comfort Food",
                "Thaka dene wale din ke baad aapka ultimate comfort food kya hota hai?",
                "Universal comfort food bond.", context, 3);
        addHinglish(result, "LIGHT_TOUCH", "WARM", "Non-Negotiable",
                "Aisi kaunsi aadat ya cheez hai jisme aap compromise bilkul pasand nahi karte?",
                "Personal lifestyle preference.", context, 2);
        addHinglish(result, "LIGHT_TOUCH", "WARM", "No Phone Afternoon",
                "Agar poori dopahar bina kisi phone call ya notification ke mil jaye, toh sabse pehle kya karoge?",
                "Relaxing weekend vision.", context, 3);
        addHinglish(result, "LIGHT_TOUCH", "CURIOUS", "Hours Of Talking",
                "Aisi kaunsi topic ya cheez hai jiske baare mein tum bina ruke ghanto baatein kar sakte ho?",
                "Reveals true inner passions.", context, 4);
        addHinglish(result, "LIGHT_TOUCH", "CURIOUS", "Top Recommendations",
                "Koi aisi movie, gaana ya cafe jo tum bina soche kisi ko bhi recommend kar sakte ho?",
                "Natural and organic recommendation opener.", context, 3);
        addHinglish(result, "LIGHT_TOUCH", "CURIOUS", "Energy Clock",
                "Subah ki shanti pasand hai ya der raat ka sukoon? Kis time energy peak par hoti hai?",
                "Explores day vs night personality.", context, 2);
        addHinglish(result, "LIGHT_TOUCH", "CURIOUS", "Quirky Habits",
                "Aapki koi aisi funny ya quirky aadat jo sirf aapke close friends jaante hain?",
                "Endearing and fun quirk share.", context, 3);

        // Choices & Hypotheses
        addHinglish(result, "LIGHT_TOUCH", "PLAYFUL", "Trip Choice",
                "Quick choice: spontaneous dhabe wali road trip ya planned luxury resort staycation?",
                "Classic travel style debate.", context, 4);
        addHinglish(result, "LIGHT_TOUCH", "PLAYFUL", "Dream Stay",
                "Pahadon mein ek cozy wooden cottage, ya bustling city mein high-rise penthouse view?",
                "Atmospheric living fantasy.", context, 3);
        addHinglish(result, "LIGHT_TOUCH", "PLAYFUL", "Life Hack",
                "Life mein cooking na karni pade ya safai/laundry na karni pade — kis superpower ko chunoge? 😂",
                "Hilarious practical dilemma.", context, 3);
        addHinglish(result, "LIGHT_TOUCH", "PLAYFUL", "Night Market",
                "Night street food market explore karna, ya shaam ko sunset ke waqt quiet terrace par baithna?",
                "Vibrant vs peaceful atmosphere choice.", context, 3);
        addHinglish(result, "LIGHT_TOUCH", "WARM", "Mood Booster Song",
                "Aisa kaunsa gaana hai jo bajte hi aapka mood 100% instant positive ho jata hai?",
                "Music sharing and feel-good energy.", context, 3);
        addHinglish(result, "LIGHT_TOUCH", "WARM", "Upcoming Plans",
                "Is mahine aisi kaunsi cheez aane wali hai jiska aapko sabse zyada intezaar hai?",
                "Future-oriented positive anticipation.", context, 3);
        addHinglish(result, "LIGHT_TOUCH", "THOUGHTFUL", "Admirable Trait",
                "Dusron mein aisi kaunsi quality dekhkar lagta hai ki 'kaash yeh mujhme bhi hoti'?",
                "Vulnerable and inspiring observation.", context, 2);
        addHinglish(result, "LIGHT_TOUCH", "CURIOUS", "Book Title",
                "Agar aapki life par ek book likhi jaye, toh is current chapter ka title kya hoga?",
                "Witty, reflective storytelling prompt.", context, 3);
    }

    /**
     * Generates rich, category-specific icebreaker suggestions for a given interest topic.
     * Supports 12 distinct categories (Coffee, Music, Travel, Movies, Food, Gym, Books,
     * Gaming, Pets, Photography/Art, Outdoors, Tech) with personalized prompts in both
     * English and Hinglish across four tones: WARM, PLAYFUL, CURIOUS, THOUGHTFUL.
     */
    private void addTopicSpecificSuggestions(
            List<IceBreakerSuggestion> result,
            String rawInterest,
            String circle,
            boolean isShared,
            String context,
            boolean hinglish) {
        String interest = rawInterest != null ? rawInterest.trim().toLowerCase(Locale.ROOT) : "";
        String display  = displayTopic(rawInterest);

        // ── Coffee / Chai ────────────────────────────────────────────────
        if (interest.matches(".*\\b(coffee|cafe|chai|tea|latte|espresso|cappuccino|matcha)\\b.*")) {
            if (hinglish) {
                if (isShared) {
                    addHinglish(result, circle, "WARM", display,
                            "Hum dono ko " + display + " pasand hai — filter coffee gang ya desi cutting chai team? ☕",
                            "Shared chai/coffee passion pe playful icebreaker.", context, 5);
                    addHinglish(result, circle, "CURIOUS", display,
                            "Perfect cup of " + display + " ke liye sabse important kya hai tumhare liye — beans, barista ya mood?",
                            "Deep dive into their coffee personality.", context, 4);
                    addHinglish(result, circle, "THOUGHTFUL", display,
                            "Mujhe batao — " + display + " peete waqt kaisa feel hota hai, rush mein lete ho ya sab slow down ho jaata hai?",
                            "Introspective coffee ritual question.", context, 3);
                } else {
                    addHinglish(result, circle, "CURIOUS", display,
                            "Tumhe " + display + " itna pasand hai! Black peete ho ya kuch milake? Favourite cafe batao 😊",
                            "Discovery icebreaker around coffee preference.", context, 4);
                    addHinglish(result, circle, "PLAYFUL", display,
                            "" + display + " lover ho — toh morning bina " + display + " ke mood kaisa hota hai? 😂",
                            "Relatable coffee dependency humour.", context, 3);
                }
            } else {
                if (isShared) {
                    add(result, circle, "WARM", display,
                            "Both coffee people — are you a specialty pour-over fan or a classic strong filter?",
                            "Shared coffee passion icebreaker.", context, 5);
                    add(result, circle, "CURIOUS", display,
                            "What makes the perfect cup of " + display + " for you — the beans, the place, or the company?",
                            "Personal coffee ritual deep dive.", context, 4);
                    add(result, circle, "PLAYFUL", display,
                            "Okay coffee quiz: oat milk, almond milk, or plain milk? There's no wrong answer, only red flags 😄",
                            "Playful choice-based coffee banter.", context, 3);
                } else {
                    add(result, circle, "CURIOUS", display,
                            "You're into " + display + " — black and bold or fancy and frothy? What's your usual order?",
                            "Discovery opener around coffee style.", context, 4);
                    add(result, circle, "WARM", display,
                            "Where's your go-to " + display + " spot? I'm always looking for hidden gems 🗺️",
                            "Café recommendation discovery.", context, 3);
                }
            }

        // ── Music ────────────────────────────────────────────────────────
        } else if (interest.matches(".*\\b(music|song|songs|playlist|concert|band|artist|vinyl|spotify|guitar|piano|drums|singing|rap|jazz|indie|pop|rock|edm|classical|hiphop|hip hop)\\b.*")) {
            if (hinglish) {
                if (isShared) {
                    addHinglish(result, circle, "PLAYFUL", display,
                            "Hum dono " + display + " lovers hain — toh abhi playlist shuffle karein toh konsa track hai jo tum skip kabhi nahi karte? 🎵",
                            "Shared music passion with skip-proof track question.", context, 5);
                    addHinglish(result, circle, "WARM", display,
                            "Aisa kaunsa gaana hai jise sunke tumhare andar kuch shift ho jaata hai — vibe ya memories?",
                            "Emotional connection to music.", context, 4);
                    addHinglish(result, circle, "CURIOUS", display,
                            "Concert mein gaye ho ya mostly headphones wale insaan ho? Favourite live experience batao!",
                            "Live music vs solo listening discovery.", context, 4);
                } else {
                    addHinglish(result, circle, "CURIOUS", display,
                            "Tumhe " + display + " pasand hai — ek aisa gaana batao jo tumhare baare mein sab kuch describe kare!",
                            "Music self-description icebreaker.", context, 5);
                    addHinglish(result, circle, "WARM", display,
                            "Mood ke hisaab se alag alag " + display + " sunna pasand hai ya ek specific playlist pe jilte ho?",
                            "Mood-music preference question.", context, 4);
                }
            } else {
                if (isShared) {
                    add(result, circle, "PLAYFUL", display,
                            "We're both music lovers! What's a song you'd never skip no matter where you are? 🎶",
                            "Shared music passion — skip-proof track icebreaker.", context, 5);
                    add(result, circle, "WARM", display,
                            "Is there an album that completely changed how you hear music for the first time?",
                            "Meaningful album discovery.", context, 4);
                    add(result, circle, "CURIOUS", display,
                            "Concert person or headphone introvert? What's a live show that completely blew your mind?",
                            "Live vs solo music experience.", context, 4);
                } else {
                    add(result, circle, "CURIOUS", display,
                            "You're into " + display + "! What's a song that basically tells your whole life story?",
                            "Music as self-description icebreaker.", context, 5);
                    add(result, circle, "PLAYFUL", display,
                            "If your life had a soundtrack right now, what would the opening track be? 🎧",
                            "Creative cinematic music question.", context, 4);
                }
            }

        // ── Travel ───────────────────────────────────────────────────────
        } else if (interest.matches(".*\\b(travel|travelling|traveling|backpacking|trip|trips|explore|exploring|wanderlust|adventure|adventures|hiking|trekking|road trip|passport|vacation|holiday)\\b.*")) {
            if (hinglish) {
                if (isShared) {
                    addHinglish(result, circle, "PLAYFUL", display,
                            "Dono travel lovers hain! Toh batao — mountains ya beaches? Aur ek dream destination jo abhi bhi wish list pe hai? ✈️",
                            "Classic travel debate plus dream destination.", context, 5);
                    addHinglish(result, circle, "CURIOUS", display,
                            "Sabse unexpected jagah kaunsi thi jahan gaye ho aur sach mein WOW feel hua?",
                            "Surprising travel memory icebreaker.", context, 4);
                    addHinglish(result, circle, "WARM", display,
                            "Aisi koi trip jisme sab galat gaya par baad mein sabse acchi yaad ban gayi? 😄",
                            "Funny travel mishap story prompt.", context, 4);
                } else {
                    addHinglish(result, circle, "CURIOUS", display,
                            "Tumhe " + display + " ka shauk hai — abhi tak ki sabse favourite trip kaunsi thi aur kyun?",
                            "Personal travel highlight discovery.", context, 5);
                    addHinglish(result, circle, "PLAYFUL", display,
                            "Solo trip ya gang ke sath? Aur over-planned itinerary ya complete spontaneous?",
                            "Travel style personality question.", context, 4);
                }
            } else {
                if (isShared) {
                    add(result, circle, "PLAYFUL", display,
                            "Both love " + display + "! Mountains or beaches — and what's still top of the bucket list? ✈️",
                            "Classic travel debate plus bucket list.", context, 5);
                    add(result, circle, "CURIOUS", display,
                            "What's the most underrated " + display + " destination you've been to that genuinely surprised you?",
                            "Hidden gem travel discovery.", context, 4);
                    add(result, circle, "WARM", display,
                            "What " + display + " trip went hilariously wrong but became your best story ever?",
                            "Funny travel mishap story.", context, 4);
                } else {
                    add(result, circle, "CURIOUS", display,
                            "You love " + display + "! What's been your absolute favourite trip and why?",
                            "Personal travel highlight opener.", context, 5);
                    add(result, circle, "PLAYFUL", display,
                            "Solo " + display + " explorer or always with a squad? Hyper-planned or wing-it spontaneous?",
                            "Travel personality style question.", context, 4);
                }
            }

        // ── Movies / Shows / Cinema ──────────────────────────────────────
        } else if (interest.matches(".*\\b(movies|movie|films|film|cinema|web series|series|netflix|ott|bollywood|hollywood|anime|kdrama|tv shows|shows|streaming|webseries)\\b.*")) {
            if (hinglish) {
                if (isShared) {
                    addHinglish(result, circle, "PLAYFUL", display,
                            "Dono cinephiles hain! Ek movie batao jo aapni true personality describe kare — aur lie mat karna 😄",
                            "Movie as self-expression icebreaker.", context, 5);
                    addHinglish(result, circle, "CURIOUS", display,
                            "Sabse zyada underrated " + display + " kaunsa hai jise tum sab ko recommend karte ho bina soche?",
                            "Underrated gem recommendation.", context, 4);
                    addHinglish(result, circle, "WARM", display,
                            "Aisi kaunsi " + display + " hai jo aapne ek se zyada baar dekhi aur har baar naya kuch dikh gaya?",
                            "Deep rewatch emotional connection.", context, 4);
                } else {
                    addHinglish(result, circle, "CURIOUS", display,
                            "Tumhe " + display + " ka shauk hai — abhi kaunsa show/movie chal raha hai? Spoilers mat dena 😂",
                            "No-spoiler current watch question.", context, 5);
                    addHinglish(result, circle, "PLAYFUL", display,
                            "Ek aisi " + display + " batao jisme sab kehte hain 'yeh toh sab dekhte hain' par tumne ab tak nahi dekhi 😂",
                            "Guilty secret unwatched movie prompt.", context, 4);
                }
            } else {
                if (isShared) {
                    add(result, circle, "PLAYFUL", display,
                            "Both movie lovers! What's one film that just describes your personality perfectly — no lying 😄",
                            "Movie as personality mirror icebreaker.", context, 5);
                    add(result, circle, "CURIOUS", display,
                            "What's your most underrated " + display + " recommendation that people always thank you for?",
                            "Hidden gem recommendation.", context, 4);
                    add(result, circle, "THOUGHTFUL", display,
                            "Is there a movie or scene that you rewatched and noticed something completely new?",
                            "Deep rewatch reflection.", context, 3);
                } else {
                    add(result, circle, "CURIOUS", display,
                            "You're into " + display + "! What are you currently watching and is it worth bingeing? No spoilers 🙏",
                            "Current watch discovery question.", context, 5);
                    add(result, circle, "PLAYFUL", display,
                            "Confession time — what's a hugely popular " + display + " you've still not watched and get judged for? 😂",
                            "Guilty secret unwatched content prompt.", context, 4);
                }
            }

        // ── Food / Cooking / Street Food ─────────────────────────────────
        } else if (interest.matches(".*\\b(food|cooking|cook|foodie|foodies|baking|bake|cuisine|recipe|recipes|street food|restaurant|restaurants|biryani|pizza|sushi|cafe|eating|eat)\\b.*")) {
            if (hinglish) {
                if (isShared) {
                    addHinglish(result, circle, "WARM", display,
                            "Dono foodies hain! Aisi kaunsi dish hai jo tum khud ghar par banaate ho aur sach mein proud feel hote ho? 🍳",
                            "Shared foodie passion — signature dish icebreaker.", context, 5);
                    addHinglish(result, circle, "PLAYFUL", display,
                            "Ek honest food confession: junk food guilty pleasure kya hai jo tum kabhi publicly admit nahi karte? 😂",
                            "Relatable guilty food pleasure.", context, 4);
                    addHinglish(result, circle, "CURIOUS", display,
                            "Abhi tak khaya hua sabse amazing meal kaunsa tha — ghar ka, restaurant ka ya kisi trip ka?",
                            "Ultimate meal memory prompt.", context, 4);
                } else {
                    addHinglish(result, circle, "CURIOUS", display,
                            "Tumhe " + display + " ka craze hai — signature dish kya hai jise sab se zyada compliments milte hain?",
                            "Foodie signature dish discovery.", context, 5);
                    addHinglish(result, circle, "PLAYFUL", display,
                            "Ghar ka fresh khana ya bahar ka mast street food — aur kisi ek ka naam lete hi heart happy ho jaata hai?",
                            "Homemade vs street food personality.", context, 3);
                }
            } else {
                if (isShared) {
                    add(result, circle, "WARM", display,
                            "Both foodies! What's one dish you cook at home that you're secretly really proud of? 🍳",
                            "Shared cooking passion — signature dish icebreaker.", context, 5);
                    add(result, circle, "PLAYFUL", display,
                            "Honest food confession: what's a guilty pleasure you'd never admit in public? 😂",
                            "Relatable guilty food pleasure.", context, 4);
                    add(result, circle, "CURIOUS", display,
                            "What's the most memorable meal you've ever had — home cooked, fine dining, or a street stall?",
                            "Memorable meal story prompt.", context, 4);
                } else {
                    add(result, circle, "CURIOUS", display,
                            "You're a " + display + " person! What's your signature dish that always impresses people?",
                            "Foodie signature dish discovery.", context, 5);
                    add(result, circle, "PLAYFUL", display,
                            "Okay food debate: home-cooked comfort vs. best street food ever found. Which wins?",
                            "Home vs street food personality question.", context, 3);
                }
            }

        // ── Gym / Fitness / Sports ───────────────────────────────────────
        } else if (interest.matches(".*\\b(gym|fitness|workout|workouts|exercise|yoga|running|cycling|sports|cricket|football|basketball|badminton|swimming|crossfit|pilates|bodybuilding|training)\\b.*")) {
            if (hinglish) {
                if (isShared) {
                    addHinglish(result, circle, "PLAYFUL", display,
                            "Dono " + display + " lovers hain! Gym mein headphones laga ke zone-in karte ho ya baat karna pasand hai workout ke beech? 😄",
                            "Gym personality — headphones vs social.", context, 5);
                    addHinglish(result, circle, "CURIOUS", display,
                            "Aisi kaunsi " + display + " activity hai jisme genuinely addicted ho gaye ho?",
                            "Fitness addiction discovery.", context, 4);
                    addHinglish(result, circle, "WARM", display,
                            "Rest day pe kya hota hai — proper recovery ya guilt trip? 😂",
                            "Relatable rest day feelings.", context, 3);
                } else {
                    addHinglish(result, circle, "CURIOUS", display,
                            "Tumhe " + display + " ka passion hai — journey kab start hui aur kya spark kiya?",
                            "Fitness journey origin story.", context, 5);
                    addHinglish(result, circle, "PLAYFUL", display,
                            "Morning workout wale ho ya night owl gym person? Aur pre-workout se judge karte ho kya? 😂",
                            "Workout timing and habits banter.", context, 4);
                }
            } else {
                if (isShared) {
                    add(result, circle, "PLAYFUL", display,
                            "Both gym people! Do you go full headphones-in focused mode or love chatting between sets? 😄",
                            "Gym personality discovery.", context, 5);
                    add(result, circle, "CURIOUS", display,
                            "What's one fitness activity you've genuinely become addicted to?",
                            "Fitness addiction discovery.", context, 4);
                    add(result, circle, "WARM", display,
                            "Rest days — proper recovery mode or constant guilt? The eternal fitness struggle 😂",
                            "Relatable rest day question.", context, 3);
                } else {
                    add(result, circle, "CURIOUS", display,
                            "You're into " + display + "! How did that journey start and what was the turning point?",
                            "Fitness origin story icebreaker.", context, 5);
                    add(result, circle, "PLAYFUL", display,
                            "Morning warrior or late-night gym session? And do you judge people who skip leg day? 😂",
                            "Workout timing and habits banter.", context, 4);
                }
            }

        // ── Books / Reading ──────────────────────────────────────────────
        } else if (interest.matches(".*\\b(books|book|reading|read|novel|novels|fiction|non-fiction|literature|author|poetry|kindle|library|bookstore|bookworm)\\b.*")) {
            if (hinglish) {
                if (isShared) {
                    addHinglish(result, circle, "WARM", display,
                            "Dono book lovers! Aisi kaunsi " + display + " hai jisne seriously sochne par majboor kar diya?",
                            "Shared reading passion — life-changing book.", context, 5);
                    addHinglish(result, circle, "PLAYFUL", display,
                            "Honest confession: kitni books 'To Read' list mein hain jo aaj bhi untouched hain? 😅",
                            "Relatable TBR pile guilt.", context, 4);
                    addHinglish(result, circle, "CURIOUS", display,
                            "Bestseller list follow karte ho ya hidden gems khudhi dhundhte ho? Last underrated find kya tha?",
                            "Reading discovery style question.", context, 4);
                } else {
                    addHinglish(result, circle, "CURIOUS", display,
                            "Tumhe " + display + " ka passion hai — abhi kya padh rahe ho aur itna time kahan se milta hai? 😄",
                            "Current read with relatable time question.", context, 5);
                    addHinglish(result, circle, "WARM", display,
                            "Ek aisi " + display + " batao jo dil se recommend karoge bina kisi reason ke?",
                            "Heartfelt book recommendation.", context, 4);
                }
            } else {
                if (isShared) {
                    add(result, circle, "WARM", display,
                            "Fellow book lover! What's one book that genuinely shifted your perspective?",
                            "Shared reading passion — mindset-shifting book.", context, 5);
                    add(result, circle, "PLAYFUL", display,
                            "Confession: how tall is your unread 'To Be Read' pile right now? 😅",
                            "Relatable TBR pile guilt.", context, 4);
                    add(result, circle, "CURIOUS", display,
                            "Bestseller follower or underrated gems hunter? What's your last hidden find?",
                            "Reading discovery style question.", context, 4);
                } else {
                    add(result, circle, "CURIOUS", display,
                            "You read! What are you in the middle of right now and is it living up to the hype?",
                            "Current read discovery question.", context, 5);
                    add(result, circle, "WARM", display,
                            "What's one book you'd hand to someone with zero explanation and just say 'trust me'?",
                            "Heartfelt unconditional book recommendation.", context, 4);
                }
            }

        // ── Gaming ───────────────────────────────────────────────────────
        } else if (interest.matches(".*\\b(gaming|games|game|gamer|playstation|xbox|pc gaming|mobile gaming|pubg|bgmi|valorant|minecraft|fifa|rpg|fps|esports|twitch|stream)\\b.*")) {
            if (hinglish) {
                if (isShared) {
                    addHinglish(result, circle, "PLAYFUL", display,
                            "Dono gamers hain! Casual khiladi ya serious rank grind type? Aur sabse zyada tilt karne wali cheez kya hai? 🎮",
                            "Shared gaming passion — casual vs competitive.", context, 5);
                    addHinglish(result, circle, "CURIOUS", display,
                            "Favourite genre kya hai — action, RPG, strategy ya puzzle? Aur har game mein same style follow karte ho?",
                            "Gaming genre and play-style question.", context, 4);
                    addHinglish(result, circle, "WARM", display,
                            "Aisi kaunsi " + display + " hai jisne tum pe genuinely emotional impact kiya — sad ending ya epic moment?",
                            "Emotionally impactful gaming moment.", context, 3);
                } else {
                    addHinglish(result, circle, "CURIOUS", display,
                            "Tumhe " + display + " ka craze hai — currently kya khel rahe ho? Worth it hai?",
                            "Current game play discovery.", context, 5);
                    addHinglish(result, circle, "PLAYFUL", display,
                            "PC/console ya mobile gamer? Aur seriously puchha toh — kya tum rage-quit karte ho? 😂",
                            "Platform and rage-quit banter.", context, 4);
                }
            } else {
                if (isShared) {
                    add(result, circle, "PLAYFUL", display,
                            "Both gamers! Are you the casual Sunday session type or hardcore rank-grinder? 🎮",
                            "Shared gaming — casual vs competitive.", context, 5);
                    add(result, circle, "CURIOUS", display,
                            "What's your favourite gaming genre and do you actually stick to it or jump around?",
                            "Genre and play-style discovery.", context, 4);
                    add(result, circle, "WARM", display,
                            "What game genuinely hit you emotionally — a plot twist, ending, or moment that you didn't expect?",
                            "Emotionally impactful gaming moment.", context, 3);
                } else {
                    add(result, circle, "CURIOUS", display,
                            "You're a gamer! What are you playing right now and is it actually worth the hype?",
                            "Current game discovery question.", context, 5);
                    add(result, circle, "PLAYFUL", display,
                            "PC, console, or mobile? And let's be real — how often do you rage-quit? 😂",
                            "Platform and rage-quit honesty.", context, 4);
                }
            }

        // ── Pets / Animals ───────────────────────────────────────────────
        } else if (interest.matches(".*\\b(pets|pet|dog|dogs|cat|cats|puppy|puppies|kitten|kittens|animals|birds|hamster|aquarium|fish|rabbit)\\b.*")) {
            if (hinglish) {
                if (isShared) {
                    addHinglish(result, circle, "WARM", display,
                            "Dono " + display + " lovers hain! Tumhara " + display + " hai ghar mein? Ek cute story sunao 🐾",
                            "Shared pet love — cute story prompt.", context, 5);
                    addHinglish(result, circle, "PLAYFUL", display,
                            "Honest bato — insan se pehle " + display + " se pyaar ho sakta hai? 😂",
                            "Relatable pet obsession humour.", context, 4);
                    addHinglish(result, circle, "CURIOUS", display,
                            "" + display + " ki sabse funny ya mischievous harkat kya thi jab genuinely gusse mein nahi has saka? 😄",
                            "Pet mischief story prompt.", context, 4);
                } else {
                    addHinglish(result, circle, "WARM", display,
                            "Tumhe " + display + " bahot pasand hai — ghar mein koi " + display + " hai? Unka naam aur photo bhi bhejo! 🐾",
                            "Pet owner discovery with photo request.", context, 5);
                    addHinglish(result, circle, "PLAYFUL", display,
                            "Dog person, cat person, ya inka jhagda settle nahi hua abhi bhi? 😄",
                            "Classic dog vs cat debate.", context, 4);
                }
            } else {
                if (isShared) {
                    add(result, circle, "WARM", display,
                            "Both " + display + " lovers! Do you have one at home? I need the full cute story 🐾",
                            "Shared pet love — cute story prompt.", context, 5);
                    add(result, circle, "PLAYFUL", display,
                            "Real talk: is it even possible to fall for someone who's not a " + display + " person? 😂",
                            "Relatable pet obsession question.", context, 4);
                    add(result, circle, "CURIOUS", display,
                            "What's the funniest thing your " + display + " has done where you couldn't even be mad?",
                            "Pet mischief story prompt.", context, 4);
                } else {
                    add(result, circle, "WARM", display,
                            "You love " + display + "! Do you have one at home or is it a someday dream?",
                            "Pet owner discovery question.", context, 5);
                    add(result, circle, "PLAYFUL", display,
                            "Dog person, cat person, or are you still on the fence and keeping us in suspense? 😄",
                            "Classic dog vs cat debate.", context, 4);
                }
            }

        // ── Photography / Art ─────────────────────────────────────────────
        } else if (interest.matches(".*\\b(photography|photo|photos|camera|art|drawing|painting|sketch|sketching|design|illustration|creative|artist|portrait|landscape)\\b.*")) {
            if (hinglish) {
                if (isShared) {
                    addHinglish(result, circle, "CURIOUS", display,
                            "Dono creative souls! Tumhari photography/art mein konsa moment ya subject sabse zyada dil ko touch karta hai? 📷",
                            "Shared creative passion — subject preference.", context, 5);
                    addHinglish(result, circle, "WARM", display,
                            "Aisi kaunsi ek creation hai jis par genuine pride feel hota hai — share karoge?",
                            "Personal creative pride piece.", context, 4);
                    addHinglish(result, circle, "PLAYFUL", display,
                            "Filter heavy edits ya completely raw/authentic look? Creative philosophy kya hai tumhari? 😄",
                            "Editing philosophy playful debate.", context, 3);
                } else {
                    addHinglish(result, circle, "CURIOUS", display,
                            "Tumhe " + display + " ka passion hai — kab se shuru kiya aur pehla moment kaunsa tha jab genuinely accha feel hua?",
                            "Creative journey origin story.", context, 5);
                    addHinglish(result, circle, "WARM", display,
                            "Apna favourite captured moment ya drawing share karte ho? Dekhna chahta/chahti hoon 😊",
                            "Creative work sharing invitation.", context, 4);
                }
            } else {
                if (isShared) {
                    add(result, circle, "CURIOUS", display,
                            "Both creative people! What subject or moment do you find yourself drawn to most? 📷",
                            "Shared creative passion — subject focus.", context, 5);
                    add(result, circle, "WARM", display,
                            "What's one piece of your work that you're genuinely proud of? I'd love to see it!",
                            "Personal creative pride piece.", context, 4);
                    add(result, circle, "PLAYFUL", display,
                            "Heavy filters or raw authenticity? What's your creative philosophy? 😄",
                            "Editing style debate.", context, 3);
                } else {
                    add(result, circle, "CURIOUS", display,
                            "You're into " + display + "! How did you start and when did it click that you were genuinely good?",
                            "Creative journey and skill discovery.", context, 5);
                    add(result, circle, "WARM", display,
                            "Would you share something you've made? I'm genuinely curious about your style 😊",
                            "Creative work sharing invitation.", context, 4);
                }
            }

        // ── Outdoors / Nature / Trekking ─────────────────────────────────
        } else if (interest.matches(".*\\b(outdoors|nature|trekking|trek|camping|hiking|mountains|sunset|sunsets|sunrise|beach|forests|national park|wildlife|birding)\\b.*")) {
            if (hinglish) {
                if (isShared) {
                    addHinglish(result, circle, "WARM", display,
                            "Dono " + display + " lovers! Abhi tak ki sabse epic trek ya sunset ka experience kya tha? 🏔️",
                            "Shared outdoors passion — epic memory prompt.", context, 5);
                    addHinglish(result, circle, "CURIOUS", display,
                            "Mountains, forests ya beaches — kaunsi jagah actually sab bhula deti hai completely?",
                            "Nature preference personality question.", context, 4);
                    addHinglish(result, circle, "PLAYFUL", display,
                            "Alag kit ke saath fully prepared wale ho ya 'chalo dekhte hain' wale adventure type? 😄",
                            "Prepared vs spontaneous outdoors style.", context, 3);
                } else {
                    addHinglish(result, circle, "CURIOUS", display,
                            "Tumhe " + display + " pasand hai — abhi tak ki favourite jagah kaunsi thi jahan jaake peace mili?",
                            "Favourite peaceful outdoors spot discovery.", context, 5);
                    addHinglish(result, circle, "WARM", display,
                            "Koi aisi " + display + " destination hai jo wish list mein hai aur bus ek mauka chahiye?",
                            "Dream outdoors destination.", context, 4);
                }
            } else {
                if (isShared) {
                    add(result, circle, "WARM", display,
                            "Outdoors people both! What's been your most stunning trek or sunset experience so far? 🏔️",
                            "Shared outdoors passion — epic memory.", context, 5);
                    add(result, circle, "CURIOUS", display,
                            "Mountains, forests, or beaches — which one actually makes you forget everything?",
                            "Nature personality preference.", context, 4);
                    add(result, circle, "PLAYFUL", display,
                            "Fully kitted adventure gear person or spontaneous 'we'll figure it out' energy? 😄",
                            "Outdoors planning style question.", context, 3);
                } else {
                    add(result, circle, "CURIOUS", display,
                            "You love " + display + "! What's a place that genuinely gave you peace like nowhere else?",
                            "Peaceful outdoors spot discovery.", context, 5);
                    add(result, circle, "WARM", display,
                            "What's still on your outdoors bucket list that you're planning to hit?",
                            "Dream outdoors destination.", context, 4);
                }
            }

        // ── Tech / Coding / Science ──────────────────────────────────────
        } else if (interest.matches(".*\\b(tech|technology|coding|programming|software|code|developer|ai|machine learning|data science|startup|gadgets|science|engineering|robotics|iot)\\b.*")) {
            if (hinglish) {
                if (isShared) {
                    addHinglish(result, circle, "CURIOUS", display,
                            "Dono " + display + " wale hain! Kaunsi emerging technology abhi genuinely excite kar rahi hai? 💻",
                            "Shared tech passion — exciting tech trend.", context, 5);
                    addHinglish(result, circle, "PLAYFUL", display,
                            "Sabse bizarre ya unexpected " + display + " use case kaunsa tha jo dekh ke genuinely amazed ho gaye? 🤯",
                            "Wild tech use-case discovery.", context, 4);
                    addHinglish(result, circle, "WARM", display,
                            "Tech mein kab aaya — passion se ya galti se? Koi interesting backstory hai? 😄",
                            "Tech journey origin story.", context, 4);
                } else {
                    addHinglish(result, circle, "CURIOUS", display,
                            "Tumhara " + display + " mein interest hai — kaunsa project ya area abhi sab se interesting lag raha hai?",
                            "Current tech interest focus discovery.", context, 5);
                    addHinglish(result, circle, "PLAYFUL", display,
                            "Dark mode ya light mode? Yeh toh character test hai 😂",
                            "Classic dev debate icebreaker.", context, 4);
                }
            } else {
                if (isShared) {
                    add(result, circle, "CURIOUS", display,
                            "Both " + display + " people! What's one emerging area that has you genuinely excited right now? 💻",
                            "Shared tech passion — exciting trend.", context, 5);
                    add(result, circle, "PLAYFUL", display,
                            "What's the most unexpected or bizarre tech use case you've seen that blew your mind? 🤯",
                            "Wild tech use-case icebreaker.", context, 4);
                    add(result, circle, "WARM", display,
                            "How did you get into " + display + " — pure passion or a happy accident?",
                            "Tech journey origin story.", context, 4);
                } else {
                    add(result, circle, "CURIOUS", display,
                            "You're into " + display + "! What project or area is keeping you most engaged right now?",
                            "Current tech focus discovery.", context, 5);
                    add(result, circle, "PLAYFUL", display,
                            "Okay important question: dark mode or light mode? This tells me everything I need to know 😂",
                            "Classic dev personality debate.", context, 4);
                }
            }

        // ── Generic fallback ─────────────────────────────────────────────
        } else {
            if (hinglish) {
                if (isShared) {
                    addHinglish(result, circle, "WARM", display,
                            "Hum dono ko " + display + " pasand hai — tumhara sabse favourite experience kaunsa raha isme? ✨",
                            "Shared passion ko favourite experience ke through deepen karta hai.", context, 4);
                    addHinglish(result, circle, "PLAYFUL", display,
                            "" + display + " mein hum dono match karte hain — lagta hai ek aur cheez toh common hai 😉",
                            "Playful chemistry with shared interest.", context, 3);
                    addHinglish(result, circle, "CURIOUS", display,
                            "" + display + " ka sabse interesting aspect kaunsa hai jo aksar log miss kar jaate hain?",
                            "Deeper discovery of shared interest.", context, 3);
                } else {
                    addHinglish(result, circle, "CURIOUS", display,
                            "Tumhe " + display + " ka interest hai — iski shuruat kaise hui aur kya mast laga pehli baar? 😊",
                            "Discovery icebreaker for any interest.", context, 4);
                    addHinglish(result, circle, "WARM", display,
                            "" + display + " ke baare mein mujhe bhi samajhna hai — koi tips ya starting point bata sakte ho?",
                            "Warm curiosity-driven discovery.", context, 3);
                }
            } else {
                if (isShared) {
                    add(result, circle, "WARM", display,
                            "Since we both love " + display + ", what's your absolute favourite experience with it so far? ✨",
                            "Deepens connection around a shared passion.", context, 4);
                    add(result, circle, "PLAYFUL", display,
                            "We both love " + display + " — I think that already puts us ahead of most people 😉",
                            "Playful shared interest chemistry.", context, 3);
                    add(result, circle, "CURIOUS", display,
                            "What's the most underrated thing about " + display + " that most people overlook?",
                            "Deeper shared interest discovery.", context, 3);
                } else {
                    add(result, circle, "CURIOUS", display,
                            "You're into " + display + " — how did you first get into it and what hooked you?",
                            "Discovery icebreaker for any interest.", context, 4);
                    add(result, circle, "WARM", display,
                            "I'd love to learn more about " + display + " from your perspective — any great starting points?",
                            "Warm curiosity-driven interest discovery.", context, 3);
                }
            }
        }
    }

    private void addHinglish(
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
                Math.min(100, rule.baseScore() + contextBoost + signalBoost),
                "HINGLISH",
                false));
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

    private void addDirectIntentSuggestions(
            List<IceBreakerSuggestion> result,
            String language,
            ConversationSignalExtractor.ConversationSignals signals,
            String context) {
        if (signals.latestIncomingText().isEmpty()) {
            return;
        }
        String incoming = signals.latestIncomingText().get().trim();
        if (incoming.isBlank()) {
            return;
        }
        String lower = incoming.toLowerCase(Locale.ROOT);
        boolean includeHinglish = "HINGLISH".equalsIgnoreCase(language) || "AUTO".equalsIgnoreCase(language);
        boolean includeEnglish = "ENGLISH".equalsIgnoreCase(language) || "AUTO".equalsIgnoreCase(language);

        // Food / Meal Intent
        if (lower.matches(".*\\b(khana|lunch|dinner|breakfast|nashta|kuch khaya|kha liya|kha rahe|food|eat|eating|ate|bhook|hungry)\\b.*")) {
            if (includeHinglish) {
                addHinglish(result, "DIRECT_REPLY", "WARM", "Khana & Meals",
                        "Haanji khana ho gaya! Aapne kiya kuch tasty ya abhi baaki hai? 😋",
                        "Seedha meal check-in ka jawab dekar unse poochta hai.", context, 5);
                addHinglish(result, "DIRECT_REPLY", "PLAYFUL", "Khana & Meals",
                        "Bas abhi kha hi raha tha! Vaise aaj menu mein kya special tha aapki taraf?",
                        "Playful food discussion shuru karta hai.", context, 4);
            }
            if (includeEnglish) {
                add(result, "DIRECT_REPLY", "WARM", "Meals & Food",
                        "Yes, just grabbed a bite! Did you have your lunch/dinner yet or still working through it? 🍽️",
                        "Directly answers the meal check-in and turns it back to them.", context, 5);
                add(result, "DIRECT_REPLY", "PLAYFUL", "Meals & Food",
                        "Not yet, actually on the hunt for something good! What did you have today?",
                        "Invites their food recommendations with light banter.", context, 4);
            }
        }

        // Current Activity Intent
        if (lower.matches(".*\\b(kya kar rahe|kya kr rhe|kya chal raha|kya scene|what are you doing|what r u doing|what're you doing|what you doing|sup|what's up|whats up|what up|what are you up to|what you up to)\\b.*")) {
            if (includeHinglish) {
                addHinglish(result, "DIRECT_REPLY", "WARM", "Activity & Day",
                        "Bas thoda kaam wrap kar raha tha, aur socha aapko text karun! Aapka din kaisa chal raha hai? 😊",
                        "Friendly status update jo unke din ke baare mein poochti hai.", context, 5);
                addHinglish(result, "DIRECT_REPLY", "PLAYFUL", "Activity & Day",
                        "Kuch khaas nahi, bas pro level par chill kar raha tha 😄 Aap batao, kya exciting chal raha hai?",
                        "Lighthearted aur fun update jo unki curiosity jagati hai.", context, 4);
            }
            if (includeEnglish) {
                add(result, "DIRECT_REPLY", "WARM", "Current Activity",
                        "Just wrapping up a few things and was thinking about texting you! How's your day treating you? 😊",
                        "Friendly status update that turns the attention back to them.", context, 5);
                add(result, "DIRECT_REPLY", "PLAYFUL", "Current Activity",
                        "Currently mastering the fine art of relaxing 😄 What about you, productive day or chill day?",
                        "Witty response that invites playful banter.", context, 4);
            }
        }

        // Location / Whereabouts Intent
        if (lower.matches(".*\\b(kahan ho|kaha ho|kidhar ho|ghar pe ho|office mein|where are you|where r u|are you home|at home|where you at)\\b.*")) {
            if (includeHinglish) {
                addHinglish(result, "DIRECT_REPLY", "WARM", "Whereabouts",
                        "Bas ghar par hi hoon abhi, unwind kar raha tha! Aap kahan ho abhi? 🏡",
                        "Casual update jo unke whereabouts ke baare mein poochti hai.", context, 5);
                addHinglish(result, "DIRECT_REPLY", "PLAYFUL", "Whereabouts",
                        "Safe and sound ghar pe! Aap kahan ghoom rahe ho aaj kal? 😉",
                        "Playful whereabouts banter.", context, 4);
            }
            if (includeEnglish) {
                add(result, "DIRECT_REPLY", "WARM", "Whereabouts",
                        "Just at home unwinding right now! Where are you hanging out today? 🏡",
                        "Casual, relaxed reply about current whereabouts.", context, 5);
                add(result, "DIRECT_REPLY", "PLAYFUL", "Whereabouts",
                        "Out and about doing a couple errands! What about you, out or cozy at home?",
                        "Lighthearted response asking where they are.", context, 4);
            }
        }

        // Availability / Free Intent
        if (lower.matches(".*\\b(free ho|free hai|free h|time hai|busy ho|call kar sakte|can we talk|are you free|got a minute|free now)\\b.*")) {
            if (includeHinglish) {
                addHinglish(result, "DIRECT_REPLY", "WARM", "Availability",
                        "Haanji abhi free hoon, bataiye kya baat hai! 😊",
                        "Warm aur welcoming response jo ready to chat signal karta hai.", context, 5);
                addHinglish(result, "DIRECT_REPLY", "PLAYFUL", "Availability",
                        "Thoda busy tha but aapke text ke liye time nikaal liya! Bolo kya scene hai? ✨",
                        "Charming response showing interest in talking to them.", context, 4);
            }
            if (includeEnglish) {
                add(result, "DIRECT_REPLY", "WARM", "Availability",
                        "Yes, pretty free right now! What's on your mind? 😊",
                        "Direct confirmation that you are free to chat.", context, 5);
                add(result, "DIRECT_REPLY", "PLAYFUL", "Availability",
                        "Just finished what I was doing, perfect timing! What's the latest update?",
                        "Encouraging, engaging response.", context, 4);
            }
        }

        // Well-being / How are you Intent
        if (lower.matches(".*\\b(kaise ho|kaisi ho|kaisa hai|sab badhiya|sab theek|tabiyat kaisi|how are you|how r u|how are things|how's it going|hows it going|how have you been)\\b.*")) {
            if (includeHinglish) {
                addHinglish(result, "DIRECT_REPLY", "WARM", "Well-being",
                        "Main ekdum badhiya! Aap batao, aap kaise ho aur aaj ka din kaisa raha? ✨",
                        "Polite aur caring reply jo unka haal-chaal poochti hai.", context, 5);
                addHinglish(result, "DIRECT_REPLY", "PLAYFUL", "Well-being",
                        "Main to mast hoon! Aapki kya reports hain aaj ki? 😄",
                        "Upbeat aur friendly check-in.", context, 4);
            }
            if (includeEnglish) {
                add(result, "DIRECT_REPLY", "WARM", "Well-being",
                        "Doing great, thank you! How have you been holding up today? ✨",
                        "Genuine and warm check-in.", context, 5);
                add(result, "DIRECT_REPLY", "PLAYFUL", "Well-being",
                        "Thriving and surviving! How's your week treating you so far? 😄",
                        "Upbeat and easygoing reply.", context, 4);
            }
        }

        // Meetup / Invitation Intent
        if (lower.matches(".*\\b(coffee|chai|milte hain|milna hai|milte h|hangout|hang out|meet up|meetup|plan karein|chalein|chaloge)\\b.*")) {
            if (includeHinglish) {
                addHinglish(result, "DIRECT_REPLY", "WARM", "Meetup & Plans",
                        "Coffee/chai ka plan to zabardast lag raha hai! Kab ka socha hai aapne? ☕",
                        "Enthusiastic agreement inviting specific timing.", context, 5);
                addHinglish(result, "DIRECT_REPLY", "PLAYFUL", "Meetup & Plans",
                        "Done! Aap bas time aur place decide karo, main pohonch jaunga 😉",
                        "Confident and playful acceptance of the invite.", context, 4);
            }
            if (includeEnglish) {
                add(result, "DIRECT_REPLY", "WARM", "Meetup & Plans",
                        "A coffee meetup sounds awesome! When were you thinking? ☕",
                        "Positive and receptive response to invitation.", context, 5);
                add(result, "DIRECT_REPLY", "PLAYFUL", "Meetup & Plans",
                        "Count me in! Are you thinking this weekend or a quick weekday evening?",
                        "Proactive and fun scheduling reply.", context, 4);
            }
        }
    }

    private void addWordMirroringSuggestions(
            List<IceBreakerSuggestion> result,
            String language,
            ConversationSignalExtractor.ConversationSignals signals,
            String context) {
        if (signals.latestIncomingText().isEmpty()) {
            return;
        }
        String incoming = signals.latestIncomingText().get().trim();
        if (incoming.isBlank() || incoming.length() > 24) {
            return;
        }
        String cleanWord = incoming.replaceAll("[^a-zA-Z0-9'\\s]", "").trim();
        if (cleanWord.isBlank() || moderationService.analyze(cleanWord).flagged()) {
            return;
        }
        String[] words = cleanWord.split("\\s+");
        if (words.length > 3) {
            return;
        }

        boolean includeHinglish = "HINGLISH".equalsIgnoreCase(language) || "AUTO".equalsIgnoreCase(language);
        boolean includeEnglish = "ENGLISH".equalsIgnoreCase(language) || "AUTO".equalsIgnoreCase(language);
        String lower = cleanWord.toLowerCase(Locale.ROOT);

        if (lower.matches("^(lol|haha|hahaha|hehe|lmao|rofl)$")) {
            if (includeHinglish) {
                addHinglish(result, "DIRECT_REPLY", "PLAYFUL", "Banter",
                        "Itni hasi kis baat pe aa rahi hai, humein bhi batao! 😂",
                        "Matches laughing reaction with teasing banter.", context, 5);
                addHinglish(result, "DIRECT_REPLY", "WARM", "Banter",
                        "Aapko hasa ke accha laga! Ab batao aage kya scene hai? 😉",
                        "Positive acknowledgment keeping the conversation rolling.", context, 4);
            }
            if (includeEnglish) {
                add(result, "DIRECT_REPLY", "PLAYFUL", "Banter",
                        "Spill the tea, what made you laugh so hard? 😂",
                        "Playful inquiry into what made them laugh.", context, 5);
                add(result, "DIRECT_REPLY", "WARM", "Banter",
                        "Glad to see I made you smile! What's the latest update with you?",
                        "Warm check-in continuing the light mood.", context, 4);
            }
        } else if (lower.matches("^(hmm|hmmm|hmmmm)$")) {
            if (includeHinglish) {
                addHinglish(result, "DIRECT_REPLY", "PLAYFUL", "Curiosity",
                        "Yeh 'hmm' kis type ka hai — sochne wala ya agree karne wala? 😉",
                        "Playful probe into their short hmm response.", context, 5);
                addHinglish(result, "DIRECT_REPLY", "CURIOUS", "Curiosity",
                        "Itna deep soch vichar! Dimag mein kya chal raha hai batayein?",
                        "Gentle curiosity uncovering their thoughts.", context, 4);
            }
            if (includeEnglish) {
                add(result, "DIRECT_REPLY", "PLAYFUL", "Curiosity",
                        "Is that a thoughtful 'hmm' or a suspicious 'hmm'? 😉",
                        "Teasing interpretation of their one-word reply.", context, 5);
                add(result, "DIRECT_REPLY", "CURIOUS", "Curiosity",
                        "Penny for your thoughts! What's on your mind?",
                        "Curious opener encouraging them to elaborate.", context, 4);
            }
        } else if (lower.matches("^(ok|okk|okay|acha|theek|sahi hai)$")) {
            if (includeHinglish) {
                addHinglish(result, "DIRECT_REPLY", "PLAYFUL", "Quick Reply",
                        "Sirf '" + cleanWord + "'? Itna formal kyu ho rahe ho haha, kuch interesting batao!",
                        "Short reply ko tease karke baat open karta hai.", context, 5);
                addHinglish(result, "DIRECT_REPLY", "WARM", "Quick Reply",
                        "Chalo badhiya! Vaise aaj shaam ka kya plan ban raha hai?",
                        "Easy pivot to evening or upcoming plans.", context, 4);
            }
            if (includeEnglish) {
                add(result, "DIRECT_REPLY", "PLAYFUL", "Quick Reply",
                        "Just '" + cleanWord + "'? Don't give me the cold shoulder now haha, what's new?",
                        "Playfully calls out the short response.", context, 5);
                add(result, "DIRECT_REPLY", "WARM", "Quick Reply",
                        "Sounds good! How's the rest of your day shaping up?",
                        "Warmly transitions to current plans.", context, 4);
            }
        } else if (lower.matches("^(hi|hey|heyy|heyyy|hello|hola)$")) {
            if (includeHinglish) {
                addHinglish(result, "DIRECT_REPLY", "PLAYFUL", "Greeting",
                        "Hey there! Itna cheerful greeting matlab mood kaafi accha hai aaj? 😉",
                        "Playful greeting match.", context, 5);
                addHinglish(result, "DIRECT_REPLY", "WARM", "Greeting",
                        "Hello hello! Kaise ho aap aur aaj ka din kaisa chal raha hai?",
                        "Friendly and welcoming opening check-in.", context, 4);
            }
            if (includeEnglish) {
                add(result, "DIRECT_REPLY", "PLAYFUL", "Greeting",
                        "Hey! That's a high-energy greeting, how's your day looking? ✨",
                        "Matches their greeting with warmth.", context, 5);
                add(result, "DIRECT_REPLY", "WARM", "Greeting",
                        "Hey there! Good to hear from you, what are you up to today?",
                        "Warm and inviting opening.", context, 4);
            }
        } else {
            // General word mirroring for words like "bal", "suno", "yo", typos, etc.
            if (includeHinglish) {
                addHinglish(result, "DIRECT_REPLY", "PLAYFUL", cleanWord,
                        "Wait, '" + cleanWord + "'? 😂 Typo tha ya koi secret code word? Poori baat batao!",
                        "Playfully calls out their exact word to spark witty banter.", context, 5);
                addHinglish(result, "DIRECT_REPLY", "CURIOUS", cleanWord,
                        "'" + cleanWord + "' bolke suspense create kar diya haha! Aage toh bolo kya scene hai?",
                        "Directly mirrors the message and asks for the rest.", context, 4);
            }
            if (includeEnglish) {
                add(result, "DIRECT_REPLY", "PLAYFUL", cleanWord,
                        "Wait, '" + cleanWord + "'? Did autocorrect strike or is that an inside code? 😂",
                        "Playfully mirrors their exact word with humor.", context, 5);
                add(result, "DIRECT_REPLY", "CURIOUS", cleanWord,
                        "Leaving me on a cliffhanger with just '" + cleanWord + "'! What's the story?",
                        "Calls out the short teaser message and invites explanation.", context, 4);
            }
        }
    }

    private void addInterestSynergySuggestions(
            List<IceBreakerSuggestion> result,
            String language,
            List<String> mine,
            List<String> theirs,
            List<String> shared,
            String context) {
        boolean includeHinglish = "HINGLISH".equalsIgnoreCase(language) || "AUTO".equalsIgnoreCase(language);
        boolean includeEnglish = "ENGLISH".equalsIgnoreCase(language) || "AUTO".equalsIgnoreCase(language);

        if (!shared.isEmpty()) {
            String s = displayTopic(shared.getFirst());
            if (includeHinglish) {
                addHinglish(result, "COMMON_GROUND", "WARM", s,
                        "Jab hum dono ko " + s + " pasand hai, toh isme aapki favourite jagah ya experience kaunsa raha hai? ✨",
                        "Shared passion ko deepen karne ke liye favourite memory poochta hai.", context, 4);
                addHinglish(result, "COMMON_GROUND", "PLAYFUL", s,
                        "Hum dono " + s + " ke fan hain — lagta hai taste to already kaafi match karta hai humara 😉",
                        "Playful chemistry banata hai shared interest ke around.", context, 4);
            }
            if (includeEnglish) {
                add(result, "COMMON_GROUND", "WARM", s,
                        "Since we're both into " + s + ", what's your absolute favorite experience or go-to spot for it? ✨",
                        "Deepens connection around a shared passion.", context, 4);
                add(result, "COMMON_GROUND", "PLAYFUL", s,
                        "We both love " + s + " — I think that confirms our taste is already top tier 😉",
                        "Playfully celebrates shared interest.", context, 4);
            }
        }

        if (!mine.isEmpty() && !theirs.isEmpty()) {
            String m = displayTopic(mine.getFirst());
            String t = displayTopic(theirs.getFirst());
            if (!m.equalsIgnoreCase(t)) {
                if (includeHinglish) {
                    addHinglish(result, "DISCOVERY", "CURIOUS", t,
                            "Aapko " + t + " pasand hai aur mujhe " + m + ", lagta hai kaafi unique combination hai! Aap " + t + " kabse enjoy kar rahe ho?",
                            "Dono ke profile interests ko mix karke cross-discovery karta hai.", context, 4);
                }
                if (includeEnglish) {
                    add(result, "DISCOVERY", "CURIOUS", t,
                            "You're into " + t + " and I'm really big on " + m + " — that sounds like a fun mix! How did you first get into " + t + "?",
                            "Bridges both profiles to spark mutual discovery.", context, 4);
                }
            }
        }
    }

    private String displayTopic(String value) {
        if (value == null || value.isBlank()) {
            return "that";
        }
        String clean = value.trim().replaceAll("\\s+", " ");
        return clean.substring(0, 1).toUpperCase(Locale.ROOT) + clean.substring(1);
    }
}
