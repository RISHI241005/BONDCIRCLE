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
    private static final Pattern INVITATION_PATTERN = Pattern.compile("\\b(coffee|dinner|drinks|meet|meeting up|hangout|hang out|plans|free this|free tomorrow|milte|mil sakte|milna|chalna|movie)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMOTIONAL_SUPPORT_PATTERN = Pattern.compile("\\b(sad|tired|bad day|exhausted|stress|stressed|terrible|crying|hurt|bura|pareshaan)\\b|😭|😢|🥺", Pattern.CASE_INSENSITIVE);
    private static final Pattern FLIRTING_PATTERN = Pattern.compile("\\b(flirt|crush|date me|cutie|handsome|pretty|blush|wink|attractive|marry me)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENDING_PATTERN = Pattern.compile("\\b(good ?night|gotta go|have to go|talk later|catch you later|bye|gn|sleep now|sona hai)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMPLIED_QUESTION_PATTERN = Pattern.compile("\\b(you free|any chance|wondering if|your thoughts|what about you|tum free|mil sakte|chalega|batao na)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DISCLOSURE_PATTERN = Pattern.compile("\\b(what do you|how do you|tell me about|your favorite|your favourite|tumhara|tumhari|aapka|aapki)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern URGENCY_PATTERN = Pattern.compile("\\b(urgent|asap|right now|jaldi|immediately|emergency)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNCERTAINTY_PATTERN = Pattern.compile("\\b(maybe|perhaps|not sure|i guess|shayad|pata nahi|idk)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern REQUEST_PATTERN = Pattern.compile("\\b(can you|could you|would you|please|pls|send me|tell me|bata do|bhej do|kar sakte)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPERIENCE_PATTERN = Pattern.compile("\\b(i |i'm|i am|i've|my |mera|meri|mujhe|aaj|today|yesterday|finally|just got|just finished)\\b", Pattern.CASE_INSENSITIVE);

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

        List<MessageIntelligence> messageSignals = analyzeMessages(context);

        // 2. Question detection
        boolean hasUnansweredQuestion = false;
        String unansweredQuestionText = null;
        if (lastMsg != null && !lastMsg.isCurrentUser()) {
            if (lastText.contains("?") || QUESTION_PATTERN.matcher(lastText).find()
                    || IMPLIED_QUESTION_PATTERN.matcher(lastText).find()) {
                hasUnansweredQuestion = true;
                unansweredQuestionText = lastText;
            }
        }

        long questionMarks = lastText.chars().filter(ch -> ch == '?').count();
        ConversationEnvironment.QuestionState questionState;
        if (!hasUnansweredQuestion) {
            questionState = ConversationEnvironment.QuestionState.NO_QUESTION;
        } else if (questionMarks > 1) {
            questionState = ConversationEnvironment.QuestionState.MULTIPLE_QUESTIONS;
        } else if (!lastText.contains("?") && IMPLIED_QUESTION_PATTERN.matcher(lastText).find()) {
            questionState = ConversationEnvironment.QuestionState.IMPLIED_QUESTION;
        } else {
            questionState = ConversationEnvironment.QuestionState.QUESTION_ASKED;
        }

        // 3. Last speaker
        ConversationEnvironment.LastSpeaker lastSpeaker = (lastMsg != null && lastMsg.isCurrentUser())
                ? ConversationEnvironment.LastSpeaker.CURRENT_USER
                : ConversationEnvironment.LastSpeaker.OTHER_USER;

        // 4. Response expectation
        ConversationEnvironment.ResponseExpectation responseExpectation;
        if (lastMsg != null && lastMsg.isCurrentUser()) {
            responseExpectation = ConversationEnvironment.ResponseExpectation.NO_RESPONSE_REQUIRED;
        } else if (ENDING_PATTERN.matcher(lastText).find()) {
            responseExpectation = ConversationEnvironment.ResponseExpectation.NO_RESPONSE_REQUIRED;
        } else if (hasUnansweredQuestion) {
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
        ConversationEnvironment.Momentum momentum = detectMomentum(messageSignals, isDry, isLongGap, isRapidExchange);

        ConversationEnvironment.Direction direction = ENDING_PATTERN.matcher(lastText).find()
                ? ConversationEnvironment.Direction.CLOSING
                : (isLongGap ? ConversationEnvironment.Direction.RECONNECTING
                : (isDry ? ConversationEnvironment.Direction.CHANGING_TOPIC
                : (hasUnansweredQuestion ? ConversationEnvironment.Direction.CONTINUING_TOPIC
                : ConversationEnvironment.Direction.EXPANDING_TOPIC)));

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

    /** Builds deterministic, per-message signals before conversation-level inference. */
    public List<MessageIntelligence> analyzeMessages(ConversationContext context) {
        if (context == null || context.messages() == null) return List.of();
        List<MessageIntelligence> result = new ArrayList<>();
        String previousTopic = null;
        for (ContextMessage message : context.messages()) {
            String content = message.content() == null ? "" : message.content().trim();
            String lower = content.toLowerCase(Locale.ROOT);
            boolean question = content.contains("?") || QUESTION_PATTERN.matcher(content).find()
                    || IMPLIED_QUESTION_PATTERN.matcher(content).find();
            String topic = detectPrimaryTopic(List.of(message));
            boolean topicChanged = previousTopic != null && !"Catching up".equals(topic) && !topic.equals(previousTopic);
            boolean referencesEarlier = lower.matches(".*\\b(that|it|again|earlier|last time|us din|woh|phir)\\b.*");

            MessageIntelligence.Sentiment sentiment = detectMessageSentiment(lower);
            double humor = JOKE_PATTERN.matcher(content).find() ? 0.9 : 0.1;
            double flirt = FLIRTING_PATTERN.matcher(content).find() ? 0.9 : 0.0;
            double serious = sentiment == MessageIntelligence.Sentiment.STRESSED
                    || sentiment == MessageIntelligence.Sentiment.NEGATIVE ? 0.85 : 0.35;
            double urgency = URGENCY_PATTERN.matcher(content).find() ? 0.9 : 0.1;
            double enthusiasm = content.contains("!") || sentiment == MessageIntelligence.Sentiment.EXCITED ? 0.8 : 0.4;
            double friendliness = lower.matches(".*\\b(hey|hi|thanks|thank you|please|yaar|bro|haha|lol)\\b.*") ? 0.8 : 0.55;
            double uncertainty = UNCERTAINTY_PATTERN.matcher(content).find() ? 0.85 : 0.1;
            double openness = question || content.length() > 45 ? 0.8 : (content.length() < 8 ? 0.2 : 0.5);
            MessageIntelligence.ConversationalEffort effort = content.length() <= 3
                    ? MessageIntelligence.ConversationalEffort.VERY_LOW
                    : content.length() <= 12 ? MessageIntelligence.ConversationalEffort.LOW
                    : content.length() <= 60 ? MessageIntelligence.ConversationalEffort.MODERATE
                    : MessageIntelligence.ConversationalEffort.HIGH;
            MessageIntelligence.Intent intent = detectMessageIntent(content, sentiment, question);
            MessageIntelligence.Intensity intensity = urgency > 0.7 || serious > 0.8 || enthusiasm > 0.7
                    ? MessageIntelligence.Intensity.HIGH
                    : content.length() < 12 ? MessageIntelligence.Intensity.LOW : MessageIntelligence.Intensity.MEDIUM;
            MessageIntelligence.QuestionImportance importance = !question ? MessageIntelligence.QuestionImportance.NONE
                    : (INVITATION_PATTERN.matcher(content).find() || DISCLOSURE_PATTERN.matcher(content).find()
                    ? MessageIntelligence.QuestionImportance.HIGH : MessageIntelligence.QuestionImportance.MEDIUM);
            String messageLanguage = languageIntelligenceService.analyzeLanguage(List.of(message)).dominantLanguage();

            result.add(new MessageIntelligence(
                    message.senderId(), message.isCurrentUser(), content, content.length(), message.createdAt(),
                    sentiment, intensity, intent, topic, question, importance, humor, flirt, serious,
                    urgency, friendliness, enthusiasm, uncertainty, openness, effort,
                    topicChanged, referencesEarlier, DISCLOSURE_PATTERN.matcher(content).find(),
                    !message.isCurrentUser() && (question || !ENDING_PATTERN.matcher(content).find()), messageLanguage));
            if (!"Catching up".equals(topic)) previousTopic = topic;
        }
        return result;
    }

    private boolean detectDryConversation(ConversationContext context) {
        ContextMessage lastMsg = context.getLastMessage();
        if (lastMsg == null) return false;

        // A single short greeting/acknowledgement is not enough to infer disinterest.
        if (!lastMsg.isCurrentUser()) {
            String content = lastMsg.content() != null ? lastMsg.content().trim().toLowerCase(Locale.ROOT) : "";
            if (Set.of("k", "kk").contains(content)) {
                return true;
            }
        }

        // Require a repeated low-effort trend across recent partner messages.
        int shortPartnerMessages = 0;
        int checked = 0;
        List<ContextMessage> msgs = context.messages();
        for (int i = msgs.size() - 1; i >= 0 && checked < 5; i--) {
            ContextMessage m = msgs.get(i);
            if (!m.isCurrentUser()) {
                checked++;
                String c = m.content() != null ? m.content().trim() : "";
                if (DRY_WORDS.contains(c.toLowerCase(Locale.ROOT)) || (c.length() <= 5 && !c.contains("?"))) {
                    shortPartnerMessages++;
                }
            }
        }
        return checked >= 2 && shortPartnerMessages >= 2;
    }

    private String detectPrimaryTopic(List<ContextMessage> messages) {
        if (messages == null || messages.isEmpty()) return "Catching up";
        java.util.Map<String, Double> scores = new java.util.LinkedHashMap<>();
        for (int i = 0; i < messages.size(); i++) {
            String text = messages.get(i).content() == null ? "" : messages.get(i).content().toLowerCase(Locale.ROOT);
            double weight = 1.0 + (2.0 * (i + 1) / messages.size());
            scoreTopic(scores, "Sports & Fitness", text, weight, "football", "cricket", "soccer", "match", "gym", "workout", "fitness", "messi", "ronaldo");
            scoreTopic(scores, "Food & Drinks", text, weight, "coffee", "tea", "dinner", "food", "restaurant", "pizza");
            scoreTopic(scores, "Travel & Adventures", text, weight, "trip", "travel", "flight", "vacation", "mountains", "beach", "goa");
            scoreTopic(scores, "Studies & Academics", text, weight, "study", "studying", "college", "university", "exam", "degree", "major", "presentation", "assignment", "course");
            scoreTopic(scores, "Movies & Shows", text, weight, "movie", "series", "netflix", "cinema", "film", "anime", "binge");
            scoreTopic(scores, "Music", text, weight, "song", "music", "concert", "band", "album");
            scoreTopic(scores, "Work & Career", text, weight, "work", "office", "boss", "job", "meeting", "client");
            scoreTopic(scores, "Weekend Plans", text, weight, "weekend", "sunday", "saturday", "plans");
        }
        return scores.entrySet().stream().max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey).orElse("Catching up");
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
        if (ENDING_PATTERN.matcher(lastText).find()) return ConversationEnvironment.Stage.ENDING;
        if (INVITATION_PATTERN.matcher(lastText).find()) return ConversationEnvironment.Stage.PLANNING;
        if (EMOTIONAL_SUPPORT_PATTERN.matcher(lastText).find()) return ConversationEnvironment.Stage.SUPPORTIVE;
        if (FLIRTING_PATTERN.matcher(lastText).find()) return ConversationEnvironment.Stage.FLIRTING;
        if (JOKE_PATTERN.matcher(lastText).find()) return ConversationEnvironment.Stage.PLAYFUL;
        if (isDry) return ConversationEnvironment.Stage.DRY;
        if (messageCount <= 2) return ConversationEnvironment.Stage.NEW_MATCH;
        if (messageCount <= 6) return ConversationEnvironment.Stage.GETTING_TO_KNOW;
        return ConversationEnvironment.Stage.CASUAL;
    }

    private void scoreTopic(java.util.Map<String, Double> scores, String topic, String text, double weight, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                scores.merge(topic, weight, Double::sum);
                return;
            }
        }
    }

    private ConversationEnvironment.Momentum detectMomentum(
            List<MessageIntelligence> signals, boolean isDry, boolean isLongGap, boolean rapid) {
        if (isDry || isLongGap) return ConversationEnvironment.Momentum.LOW;
        if (rapid) return ConversationEnvironment.Momentum.RISING;
        if (signals.size() < 4) return ConversationEnvironment.Momentum.STABLE;
        List<MessageIntelligence> recent = signals.subList(Math.max(0, signals.size() - 6), signals.size());
        double first = recent.subList(0, recent.size() / 2).stream().mapToInt(MessageIntelligence::length).average().orElse(0);
        double second = recent.subList(recent.size() / 2, recent.size()).stream().mapToInt(MessageIntelligence::length).average().orElse(0);
        if (second > first * 1.35) return ConversationEnvironment.Momentum.RISING;
        if (second < first * 0.65) return ConversationEnvironment.Momentum.DECLINING;
        return recent.size() >= 5 ? ConversationEnvironment.Momentum.HIGH : ConversationEnvironment.Momentum.STABLE;
    }

    private MessageIntelligence.Sentiment detectMessageSentiment(String lower) {
        if (lower.matches(".*\\b(stress|stressed|tired|exhausted|anxious|overwhelmed)\\b.*") || lower.contains("😭")) return MessageIntelligence.Sentiment.STRESSED;
        if (lower.matches(".*\\b(sad|hurt|terrible|awful|angry|upset|bura)\\b.*") || lower.contains("😢")) return MessageIntelligence.Sentiment.NEGATIVE;
        if (lower.matches(".*\\b(amazing|excited|awesome|great|love|mast)\\b.*") || lower.contains("🔥")) return MessageIntelligence.Sentiment.EXCITED;
        if (lower.matches(".*\\b(good|nice|happy|glad|fun)\\b.*") || lower.contains("😊")) return MessageIntelligence.Sentiment.POSITIVE;
        return MessageIntelligence.Sentiment.NEUTRAL;
    }

    private MessageIntelligence.Intent detectMessageIntent(String content, MessageIntelligence.Sentiment sentiment, boolean question) {
        if (INVITATION_PATTERN.matcher(content).find()) return MessageIntelligence.Intent.INVITATION;
        if (REQUEST_PATTERN.matcher(content).find()) return MessageIntelligence.Intent.REQUEST;
        if (FLIRTING_PATTERN.matcher(content).find()) return MessageIntelligence.Intent.FLIRTING;
        if (sentiment == MessageIntelligence.Sentiment.STRESSED || sentiment == MessageIntelligence.Sentiment.NEGATIVE) return MessageIntelligence.Intent.EMOTIONAL_VENT;
        if (COMPLIMENT_PATTERN.matcher(content).find()) return MessageIntelligence.Intent.COMPLIMENT;
        if (JOKE_PATTERN.matcher(content).find()) return MessageIntelligence.Intent.JOKE;
        if (question) return MessageIntelligence.Intent.QUESTION;
        if (EXPERIENCE_PATTERN.matcher(content).find()) return MessageIntelligence.Intent.SHARING_EXPERIENCE;
        return MessageIntelligence.Intent.STATEMENT;
    }

    private ConversationEnvironment.RelationshipSignal detectRelationshipSignal(int messageCount, boolean isDry) {
        if (messageCount <= 4) return ConversationEnvironment.RelationshipSignal.LOW_FAMILIARITY;
        if (messageCount <= 12) return ConversationEnvironment.RelationshipSignal.BUILDING;
        if (isDry) return ConversationEnvironment.RelationshipSignal.BUILDING;
        if (messageCount >= 30) return ConversationEnvironment.RelationshipSignal.HIGH_COMFORT;
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
