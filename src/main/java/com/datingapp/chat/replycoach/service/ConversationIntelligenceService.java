package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.model.ConversationAnalysis;
import com.datingapp.chat.replycoach.model.ConversationAnalysis.Intent;
import com.datingapp.chat.replycoach.model.ConversationAnalysis.Momentum;
import com.datingapp.chat.replycoach.model.ConversationAnalysis.Stage;
import com.datingapp.chat.replycoach.model.ConversationContext;
import com.datingapp.chat.replycoach.model.ConversationContext.ContextMessage;
import com.datingapp.chat.replycoach.model.ConversationEnvironment;
import com.datingapp.chat.replycoach.model.MessageIntelligence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ConversationIntelligenceService {

    private static final Set<String> DRY_WORDS = Set.of(
            "k", "ok", "okay", "kk", "hmm", "hmmm", "nice", "cool", "yeah", "yep", "yes", "no", "nah", "fine",
            "acha", "accha", "theek", "thik", "sahi", "haan", "han", "bye", "gn"
    );

    private static final Pattern QUESTION_PATTERN = Pattern.compile("\\?|\\b(what|where|when|why|who|how|kya|kaise|kaha|kab|kyun)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern JOKE_PATTERN = Pattern.compile("😂|🤣|lmao|lol|haha|hehe|joke|funny|mazak", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPLIMENT_PATTERN = Pattern.compile("\\b(cute|pretty|handsome|gorgeous|smart|nice|amazing|love your|awesome|tareef)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern INVITATION_PATTERN = Pattern.compile("\\b(coffee|dinner|drinks|meet|hangout|hang out|plans|free this|milte|chalna|movie)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMOTIONAL_SUPPORT_PATTERN = Pattern.compile("\\b(sad|tired|bad day|exhausted|stress|stressed|terrible|crying|hurt|bura|pareshaan)\\b|😭|😢|🥺", Pattern.CASE_INSENSITIVE);
    private static final Pattern FLIRTING_PATTERN = Pattern.compile("\\b(flirt|crush|date me|cutie|handsome|pretty|blush|wink|attractive|marry me)\\b", Pattern.CASE_INSENSITIVE);

    private final LanguageIntelligenceService languageIntelligenceService;

    @Autowired
    public ConversationIntelligenceService(LanguageIntelligenceService languageIntelligenceService) {
        this.languageIntelligenceService = languageIntelligenceService != null ? languageIntelligenceService : new LanguageIntelligenceService();
    }

    public ConversationIntelligenceService() {
        this(new LanguageIntelligenceService());
    }

    public ConversationAnalysis analyze(ConversationContext context) {
        ConversationEnvironment env = analyzeEnvironment(context);
        return ConversationAnalysis.fromEnvironment(env);
    }

    public ConversationEnvironment analyzeEnvironment(ConversationContext context) {
        if (context == null || context.isEmpty()) {
            return ConversationEnvironment.freshMatch("ENGLISH");
        }

        List<ContextMessage> messages = context.messages();
        String language = detectLanguage(messages);
        boolean isDry = detectDryConversation(context);

        ContextMessage lastMsg = context.getLastMessage();
        String lastText = lastMsg != null && lastMsg.content() != null ? lastMsg.content().trim() : "";

        // 1. Timing analysis
        long hoursSinceLastMessage = 0;
        boolean isLongGap = false;
        boolean isRapidExchange = false;
        boolean isSuddenReturn = false;

        if (lastMsg != null && lastMsg.createdAt() != null && messages.size() >= 2) {
            hoursSinceLastMessage = Duration.between(lastMsg.createdAt(), Instant.now()).toHours();
            if (hoursSinceLastMessage >= 48) {
                isLongGap = true;
            }

            ContextMessage secondLastMsg = messages.get(messages.size() - 2);
            if (secondLastMsg.createdAt() != null) {
                long secondsBetween = Math.abs(Duration.between(secondLastMsg.createdAt(), lastMsg.createdAt()).toSeconds());
                if (secondsBetween < 60) {
                    isRapidExchange = true;
                }
                long gapBeforeLast = Duration.between(secondLastMsg.createdAt(), lastMsg.createdAt()).toHours();
                if (gapBeforeLast >= 48 && hoursSinceLastMessage < 12) {
                    isSuddenReturn = true;
                }
            }
        }

        // 2. Question detection
        boolean hasUnansweredQuestion = false;
        String unansweredQuestionText = null;
        if (lastMsg != null && !lastMsg.isCurrentUser()) {
            if (lastText.contains("?") || QUESTION_PATTERN.matcher(lastText).find()) {
                hasUnansweredQuestion = true;
                unansweredQuestionText = lastText;
            }
        }

        ConversationEnvironment.QuestionState questionState = hasUnansweredQuestion
                ? (lastText.contains("?") && QUESTION_PATTERN.matcher(lastText).find() ? ConversationEnvironment.QuestionState.QUESTION_ASKED : ConversationEnvironment.QuestionState.QUESTION_ASKED)
                : ConversationEnvironment.QuestionState.NO_QUESTION;

        // 3. Last speaker
        ConversationEnvironment.LastSpeaker lastSpeaker = (lastMsg != null && lastMsg.isCurrentUser())
                ? ConversationEnvironment.LastSpeaker.CURRENT_USER
                : ConversationEnvironment.LastSpeaker.OTHER_USER;

        // 4. Response expectation
        ConversationEnvironment.ResponseExpectation responseExpectation;
        if (hasUnansweredQuestion) {
            responseExpectation = ConversationEnvironment.ResponseExpectation.ANSWER_REQUIRED;
        } else if (isLongGap || isSuddenReturn) {
            responseExpectation = ConversationEnvironment.ResponseExpectation.RECONNECT;
        } else if (EMOTIONAL_SUPPORT_PATTERN.matcher(lastText).find()) {
            responseExpectation = ConversationEnvironment.ResponseExpectation.EMOTIONAL_RESPONSE;
        } else if (INVITATION_PATTERN.matcher(lastText).find()) {
            responseExpectation = ConversationEnvironment.ResponseExpectation.ANSWER_REQUIRED;
        } else if (JOKE_PATTERN.matcher(lastText).find()) {
            responseExpectation = ConversationEnvironment.ResponseExpectation.PLAYFUL_RESPONSE;
        } else if (isDry) {
            responseExpectation = ConversationEnvironment.ResponseExpectation.OPEN_TOPIC;
        } else {
            responseExpectation = ConversationEnvironment.ResponseExpectation.FOLLOW_UP_NEEDED;
        }

        // 5. Topics
        String primaryTopic = detectPrimaryTopic(messages);
        List<String> secondaryTopics = detectSecondaryTopics(messages, primaryTopic);
        List<String> potentialNext = detectPotentialNextTopics(primaryTopic);

        // 6. Temperature & Depth
        ConversationEnvironment.Temperature temperature = detectTemperature(lastText, isDry, isLongGap);
        ConversationEnvironment.Depth depth = detectDepth(messages, primaryTopic);

        // 7. Momentum & Direction
        ConversationEnvironment.Momentum momentum = isDry ? ConversationEnvironment.Momentum.LOW
                : (isLongGap ? ConversationEnvironment.Momentum.LOW
                : (isRapidExchange ? ConversationEnvironment.Momentum.RISING
                : (messages.size() >= 5 ? ConversationEnvironment.Momentum.HIGH : ConversationEnvironment.Momentum.STABLE)));

        ConversationEnvironment.Direction direction = isLongGap ? ConversationEnvironment.Direction.RECONNECTING
                : (isDry ? ConversationEnvironment.Direction.CHANGING_TOPIC
                : (hasUnansweredQuestion ? ConversationEnvironment.Direction.CONTINUING_TOPIC
                : ConversationEnvironment.Direction.EXPANDING_TOPIC));

        // 8. Stage & Relationship Signals
        ConversationEnvironment.Stage stage = detectEnvironmentStage(messages.size(), isDry, isLongGap, lastText, hasUnansweredQuestion);
        ConversationEnvironment.RelationshipSignal relationshipSignal = detectRelationshipSignal(messages.size(), isDry);

        // 9. Engagement signals
        List<ConversationEnvironment.EngagementSignal> engagementSignals = detectEngagementSignals(messages, isDry, isRapidExchange);

        return new ConversationEnvironment(
                stage,
                momentum,
                relationshipSignal,
                lastSpeaker,
                responseExpectation,
                temperature,
                direction,
                questionState,
                engagementSignals,
                depth,
                primaryTopic,
                secondaryTopics,
                potentialNext,
                language,
                isDry,
                hasUnansweredQuestion,
                unansweredQuestionText,
                hoursSinceLastMessage,
                isRapidExchange,
                isSuddenReturn
        );
    }

    public String detectLanguage(List<ContextMessage> messages) {
        return languageIntelligenceService.analyzeLanguage(messages).dominantLanguage();
    }

    private boolean detectDryConversation(ConversationContext context) {
        ContextMessage lastMsg = context.getLastMessage();
        if (lastMsg == null) return false;

        // Partner spoke last and gave a 1-word or short dry response
        if (!lastMsg.isCurrentUser()) {
            String content = lastMsg.content() != null ? lastMsg.content().trim().toLowerCase(Locale.ROOT) : "";
            if (DRY_WORDS.contains(content) || (content.length() <= 8 && !content.contains("?"))) {
                return true;
            }
        }

        // Check if last 3 messages from partner were <= 12 characters
        int shortPartnerMessages = 0;
        int checked = 0;
        List<ContextMessage> msgs = context.messages();
        for (int i = msgs.size() - 1; i >= 0 && checked < 5; i--) {
            ContextMessage m = msgs.get(i);
            if (!m.isCurrentUser()) {
                checked++;
                String c = m.content() != null ? m.content().trim() : "";
                if (c.length() <= 12) {
                    shortPartnerMessages++;
                }
            }
        }
        return shortPartnerMessages >= 3;
    }

    private String detectPrimaryTopic(List<ContextMessage> messages) {
        String allText = messages.stream()
                .map(m -> m.content() != null ? m.content().toLowerCase(Locale.ROOT) : "")
                .reduce("", (a, b) -> a + " " + b);

        if (allText.contains("football") || allText.contains("cricket") || allText.contains("soccer") || allText.contains("match") || allText.contains("gym") || allText.contains("workout") || allText.contains("fitness") || allText.contains("messi") || allText.contains("ronaldo")) return "Sports & Fitness";
        if (allText.contains("coffee") || allText.contains("tea") || allText.contains("dinner") || allText.contains("food") || allText.contains("restaurant") || allText.contains("pizza")) return "Food & Drinks";
        if (allText.contains("trip") || allText.contains("travel") || allText.contains("flight") || allText.contains("vacation") || allText.contains("mountains") || allText.contains("beach") || allText.contains("goa")) return "Travel & Adventures";
        if (allText.contains("study") || allText.contains("studying") || allText.contains("college") || allText.contains("university") || allText.contains("exam") || allText.contains("degree") || allText.contains("major") || allText.contains("presentation") || allText.contains("assignment")) return "Studies & Academics";
        if (allText.contains("movie") || allText.contains("series") || allText.contains("netflix") || allText.contains("cinema") || allText.contains("film") || allText.contains("anime") || allText.contains("binge")) return "Movies & Shows";
        if (allText.contains("song") || allText.contains("music") || allText.contains("concert") || allText.contains("band") || allText.contains("album")) return "Music";
        if (allText.contains("work") || allText.contains("office") || allText.contains("boss") || allText.contains("job") || allText.contains("meeting") || allText.contains("client")) return "Work & Career";
        if (allText.contains("weekend") || allText.contains("sunday") || allText.contains("saturday") || allText.contains("plans")) return "Weekend Plans";

        return "Catching up";
    }

    private List<String> detectSecondaryTopics(List<ContextMessage> messages, String primaryTopic) {
        Set<String> topics = new HashSet<>();
        for (ContextMessage msg : messages) {
            String c = msg.content() != null ? msg.content().toLowerCase(Locale.ROOT) : "";
            if (c.contains("work") && !primaryTopic.equals("Work & Career")) topics.add("Work");
            if (c.contains("weekend") && !primaryTopic.equals("Weekend Plans")) topics.add("Weekend");
            if (c.contains("music") && !primaryTopic.equals("Music")) topics.add("Music");
            if (c.contains("travel") && !primaryTopic.equals("Travel & Adventures")) topics.add("Travel");
            if ((c.contains("football") || c.contains("cricket")) && !primaryTopic.equals("Sports & Fitness")) topics.add("Sports");
        }
        return new ArrayList<>(topics);
    }

    private List<String> detectPotentialNextTopics(String primaryTopic) {
        return switch (primaryTopic) {
            case "Studies & Academics" -> List.of("Weekend Plans", "Coffee Breaks", "College Life");
            case "Sports & Fitness" -> List.of("Favorite Teams", "Weekend Matches", "Workouts");
            case "Travel & Adventures" -> List.of("Dream Destinations", "Road Trips", "Food Spots");
            case "Movies & Shows" -> List.of("Recommendations", "Upcoming Releases", "Favorite Genres");
            default -> List.of("Weekend Plans", "Interests", "Music");
        };
    }

    private ConversationEnvironment.Temperature detectTemperature(String lastText, boolean isDry, boolean isLongGap) {
        if (isDry) return ConversationEnvironment.Temperature.COLD;
        if (isLongGap) return ConversationEnvironment.Temperature.WARM;
        if (EMOTIONAL_SUPPORT_PATTERN.matcher(lastText).find()) return ConversationEnvironment.Temperature.EMOTIONAL;
        if (FLIRTING_PATTERN.matcher(lastText).find()) return ConversationEnvironment.Temperature.FLIRTY;
        if (JOKE_PATTERN.matcher(lastText).find()) return ConversationEnvironment.Temperature.PLAYFUL;
        return ConversationEnvironment.Temperature.WARM;
    }

    private ConversationEnvironment.Depth detectDepth(List<ContextMessage> messages, String topic) {
        int count = messages.size();
        if (count <= 4) return ConversationEnvironment.Depth.SURFACE;
        if (count <= 10) return ConversationEnvironment.Depth.PERSONAL;
        if (topic.equals("Studies & Academics") || topic.equals("Work & Career")) return ConversationEnvironment.Depth.EXPERIENCES;
        return ConversationEnvironment.Depth.PERSONAL;
    }

    private ConversationEnvironment.Stage detectEnvironmentStage(
            int messageCount,
            boolean isDry,
            boolean isLongGap,
            String lastText,
            boolean hasQuestion) {
        if (isLongGap) return ConversationEnvironment.Stage.RECONNECTING;
        if (isDry) return ConversationEnvironment.Stage.DRY;
        if (messageCount <= 2) return ConversationEnvironment.Stage.NEW_MATCH;
        if (messageCount <= 6) return ConversationEnvironment.Stage.GETTING_TO_KNOW;
        if (FLIRTING_PATTERN.matcher(lastText).find()) return ConversationEnvironment.Stage.FLIRTING;
        if (INVITATION_PATTERN.matcher(lastText).find()) return ConversationEnvironment.Stage.PLANNING;
        if (EMOTIONAL_SUPPORT_PATTERN.matcher(lastText).find()) return ConversationEnvironment.Stage.SUPPORTIVE;
        if (JOKE_PATTERN.matcher(lastText).find()) return ConversationEnvironment.Stage.PLAYFUL;
        return ConversationEnvironment.Stage.CASUAL;
    }

    private ConversationEnvironment.RelationshipSignal detectRelationshipSignal(int messageCount, boolean isDry) {
        if (messageCount <= 4) return ConversationEnvironment.RelationshipSignal.LOW_FAMILIARITY;
        if (messageCount <= 12) return ConversationEnvironment.RelationshipSignal.BUILDING;
        if (isDry) return ConversationEnvironment.RelationshipSignal.BUILDING;
        return ConversationEnvironment.RelationshipSignal.COMFORTABLE;
    }

    private List<ConversationEnvironment.EngagementSignal> detectEngagementSignals(
            List<ContextMessage> messages,
            boolean isDry,
            boolean isRapidExchange) {
        List<ConversationEnvironment.EngagementSignal> signals = new ArrayList<>();
        if (isDry) {
            signals.add(ConversationEnvironment.EngagementSignal.LOW_EFFORT);
            signals.add(ConversationEnvironment.EngagementSignal.REPEATED_SHORT_REPLIES);
        }
        if (isRapidExchange) {
            signals.add(ConversationEnvironment.EngagementSignal.RAPID_EXCHANGE);
        }

        int userMsgs = 0;
        int partnerMsgs = 0;
        for (ContextMessage m : messages) {
            if (m.isCurrentUser()) userMsgs++;
            else partnerMsgs++;
        }

        if (userMsgs > partnerMsgs * 2) {
            signals.add(ConversationEnvironment.EngagementSignal.USER_CARRYING);
        } else if (partnerMsgs > userMsgs * 2) {
            signals.add(ConversationEnvironment.EngagementSignal.PARTNER_CARRYING);
        } else {
            signals.add(ConversationEnvironment.EngagementSignal.BALANCED_INITIATIVE);
            signals.add(ConversationEnvironment.EngagementSignal.MUTUAL_ENGAGEMENT);
        }

        return signals;
    }
}
