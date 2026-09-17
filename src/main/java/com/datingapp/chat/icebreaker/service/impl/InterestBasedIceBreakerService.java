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
                "Chalo, mujhe " + topic + " ki kahani ka agla episode sunao jaldi 😄",
                "Playful callback se baat me maza aata hai.", context, 1);
    }

    private void addHinglishSharedInterestSuggestions(List<IceBreakerSuggestion> result, String interest, String context) {
        addHinglish(result, "COMMON_GROUND", "WARM", interest,
                "Hum dono ko " + interest + " pasand hai — isse judi tumhari sabse memorable baat kya hai?",
                "Shared interest par personal aur warm discussion shuru karta hai.", context, 4);
        addHinglish(result, "COMMON_GROUND", "CURIOUS", interest,
                interest + " ke baare mein aisi kaunsi baat hai jo kam logon ko samajh aati hai?",
                "Dono ki favorite cheez par deep opinion invite karta hai.", context, 3);
        addHinglish(result, "COMMON_GROUND", "PLAYFUL", interest,
                "Agar hume " + interest + " ke liye ek poora free din mil jaye, toh plan kya banega?",
                "Ek fun aur imaginative shared plan banata hai.", context, 2);
    }

    private void addHinglishDiscoverySuggestions(List<IceBreakerSuggestion> result, String interest, String context) {
        addHinglish(result, "DISCOVERY", "CURIOUS", interest,
                "Maine dekha tumhe " + interest + " ka shauk hai — iski shuruaat kaise hui?",
                "Unke interest ke peeche ki story jaan ne me madad karta hai.", context, 4);
        addHinglish(result, "DISCOVERY", "PLAYFUL", interest,
                "Quick choice: ek relaxed sa " + interest + " day ya kuch super adventurous?",
                "Aasan either-or sawal jiska jawab dena exciting hai.", context, 3);
        addHinglish(result, "DISCOVERY", "THOUGHTFUL", interest,
                interest + " mein tumhe sabse zyada kya pasand hai jo log miss kar dete hain?",
                "Surface level se aage badhkar thoughtful sawal poochta hai.", context, 2);
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
        addHinglish(result, "LIGHT_TOUCH", "WARM", "Aaj ka din",
                "Aaj ke din ka sabse best part kya raha?",
                "Warm aur natural sawal jiska jawab dena bohot aasan hai.", context, 3);
        addHinglish(result, "LIGHT_TOUCH", "PLAYFUL", "Chai / Coffee",
                "Chai ya coffee? Aur shaam ka kya scene hai?",
                "Lighthearted aur relatable icebreaker.", context, 4);
        addHinglish(result, "LIGHT_TOUCH", "CURIOUS", "Favorite baatein",
                "Aisi kaunsi cheez hai jiske baare mein tum bina thake ghanto baat kar sakte ho?",
                "Unki real passion jaan ne ka accha tarika.", context, 2);
        addHinglish(result, "LIGHT_TOUCH", "THOUGHTFUL", "Upcoming plans",
                "Aane wale dinon mein aisi kaunsi cheez hai jiska sabse zyada intezaar hai?",
                "Future-focused aur positive mood maintain karta hai.", context, 4);
        addHinglish(result, "LIGHT_TOUCH", "PLAYFUL", "Vibe choice",
                "Pick one: subah ki chai, der raat tak baatein, ya lazy weekend afternoon?",
                "Teen choices se jawab dena effortless ho jata hai.", context, 3);
        addHinglish(result, "LIGHT_TOUCH", "WARM", "Small wins",
                "Koi aisi chhoti si cheez jisne aaj tumhare chehre par smile la di?",
                "Positive story encourage karta hai bina over-personal hue.", context, 2);
        addHinglish(result, "LIGHT_TOUCH", "CURIOUS", "Recommendations",
                "Koi aisi movie, gaana ya jagah jo tum bina soche recommend karoge?",
                "Recommendations se specific topics par baat nikal aati hai.", context, 2);
        addHinglish(result, "LIGHT_TOUCH", "THOUGHTFUL", "Goals",
                "Is saal aisi kaunsi naye cheez seekhne ka plan hai?",
                "Respectful aur inspiring discussion start karta hai.", context, 1);
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
