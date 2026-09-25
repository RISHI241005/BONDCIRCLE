package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.model.ConversationContext;
import com.datingapp.chat.replycoach.model.ConversationEnvironment;
import com.datingapp.chat.replycoach.model.LatestMessageAnalysis;
import com.datingapp.chat.replycoach.model.MessageIntelligence;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Interprets the immediate incoming message before reply planning begins. */
@Service
public class LatestMessageAnalyzer {

    private static final Pattern IMPLICIT_QUESTION = Pattern.compile(
            "\\b(kal toh main free|i(?:'m| am) free|what about you|tum free|you free|batao|your thoughts|any chance)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REQUEST = Pattern.compile(
            "\\b(can you|could you|would you|please|pls|send me|tell me|bata do|bhej do|kar sakte)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SARCASM = Pattern.compile(
            "\\b(yeah right|sure jan|as if|obviously not|wah kya baat|great, just great)\\b|🙄",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CURIOSITY = Pattern.compile(
            "\\b(wonder|curious|tell me|what|why|how|kya|kyun|kaise|phir)\\b|\\?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HESITATION = Pattern.compile(
            "\\b(umm+|uh+|maybe|i guess|not sure|shayad|pata nahi|dekhenge)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FRUSTRATION = Pattern.compile(
            "\\b(annoyed|frustrated|irritated|hate|fed up|so done|pak gaya|pareshan|torture)\\b|😤",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SHARING = Pattern.compile(
            "\\b(i |i'm|i am|i've|my |mera|meri|mujhe|aaj|today|yesterday|kal|finally|just )",
            Pattern.CASE_INSENSITIVE);

    public LatestMessageAnalysis analyze(
            ConversationContext context,
            List<MessageIntelligence> messageSignals,
            ConversationEnvironment environment) {

        if (context == null || context.isEmpty()) return LatestMessageAnalysis.none();
        ConversationContext.ContextMessage latest = context.getLastMessage();
        if (latest == null || latest.isCurrentUser()) return LatestMessageAnalysis.none();

        MessageIntelligence signal = findLatestSignal(messageSignals, latest);
        if (signal == null) return LatestMessageAnalysis.none();

        String text = signal.content() == null ? "" : signal.content().trim();
        String lower = text.toLowerCase(Locale.ROOT);
        boolean implicitQuestion = !text.contains("?") && IMPLICIT_QUESTION.matcher(text).find();
        boolean request = REQUEST.matcher(text).find();
        boolean emotional = signal.sentiment() == MessageIntelligence.Sentiment.STRESSED
                || signal.sentiment() == MessageIntelligence.Sentiment.NEGATIVE
                || signal.intent() == MessageIntelligence.Intent.EMOTIONAL_VENT;
        double sarcasm = SARCASM.matcher(text).find() ? 0.85 : 0.05;
        double frustration = FRUSTRATION.matcher(text).find()
                ? 0.9 : (emotional ? 0.55 : 0.1);
        double curiosity = CURIOSITY.matcher(text).find() ? 0.85 : 0.2;
        double hesitation = HESITATION.matcher(text).find() ? 0.85 : signal.uncertainty() * 0.6;
        boolean informationShared = SHARING.matcher(text).find()
                || signal.intent() == MessageIntelligence.Intent.STATEMENT
                || signal.intent() == MessageIntelligence.Intent.EMOTIONAL_VENT;

        String engagement = switch (signal.conversationalEffort()) {
            case HIGH -> "HIGH";
            case MODERATE -> "MEDIUM_HIGH";
            case LOW -> "MEDIUM";
            case VERY_LOW -> "LOW";
        };
        String opportunity = determineOpportunity(signal, environment, request, emotional);
        String emotion = determineEmotion(signal, frustration);
        String tone = determineTone(signal, sarcasm);
        ConversationEnvironment.ResponseExpectation expectation = environment != null
                ? environment.responseExpectation()
                : (signal.hasQuestion() || implicitQuestion || request
                ? ConversationEnvironment.ResponseExpectation.ANSWER_REQUIRED
                : ConversationEnvironment.ResponseExpectation.FOLLOW_UP_NEEDED);

        return new LatestMessageAnalysis(
                latest.publicId() != null ? latest.publicId() : String.valueOf(latest.id()),
                text,
                signal.intent(),
                signal.topic(),
                signal.sentiment(),
                emotion,
                tone,
                signal.language(),
                signal.hasQuestion(),
                implicitQuestion,
                request,
                informationShared,
                emotional,
                signal.humorLevel(),
                sarcasm,
                signal.enthusiasm(),
                frustration,
                curiosity,
                signal.flirtLevel(),
                hesitation,
                signal.uncertainty(),
                signal.openness(),
                engagement,
                opportunity,
                expectation
        );
    }

    private MessageIntelligence findLatestSignal(
            List<MessageIntelligence> signals,
            ConversationContext.ContextMessage latest) {
        if (signals == null || signals.isEmpty()) return null;
        MessageIntelligence last = signals.get(signals.size() - 1);
        return !last.isCurrentUser() && latest.content().equals(last.content()) ? last : null;
    }

    private String determineOpportunity(
            MessageIntelligence signal,
            ConversationEnvironment environment,
            boolean request,
            boolean emotional) {
        if (signal.hasQuestion() || request) return "DIRECT_ANSWER";
        if (signal.intent() == MessageIntelligence.Intent.INVITATION) return "PLAN_OR_INVITATION";
        if (emotional) return "EMPATHY_AND_FOLLOW_UP";
        if (signal.intent() == MessageIntelligence.Intent.COMPLIMENT) return "ACKNOWLEDGE_AND_BANTER";
        if (signal.intent() == MessageIntelligence.Intent.JOKE || signal.humorLevel() > 0.6) return "PLAYFUL_CONTINUATION";
        if (signal.intent() == MessageIntelligence.Intent.FLIRTING || signal.flirtLevel() > 0.6) return "RECIPROCAL_LIGHT_FLIRTING";
        if (environment != null && environment.isDry()) return "CONTEXTUAL_RE_OPENER";
        return "REACTION_AND_TOPIC_CONTINUATION";
    }

    private String determineEmotion(MessageIntelligence signal, double frustration) {
        if (frustration > 0.7) return "FRUSTRATED";
        return switch (signal.sentiment()) {
            case STRESSED -> "TIRED_OR_STRESSED";
            case NEGATIVE -> "UPSET";
            case EXCITED -> "EXCITED";
            case POSITIVE -> "POSITIVE";
            case NEUTRAL -> "NEUTRAL";
        };
    }

    private String determineTone(MessageIntelligence signal, double sarcasm) {
        if (sarcasm > 0.7) return "SARCASTIC";
        if (signal.flirtLevel() > 0.6) return "FLIRTY";
        if (signal.humorLevel() > 0.6) return "PLAYFUL";
        if (signal.seriousness() > 0.7) return "SERIOUS";
        return "CASUAL";
    }
}
