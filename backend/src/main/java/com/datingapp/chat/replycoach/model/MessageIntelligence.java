package com.datingapp.chat.replycoach.model;

import java.time.Instant;

public record MessageIntelligence(
        Long senderId,
        boolean isCurrentUser,
        String content,
        int length,
        Instant createdAt,
        Sentiment sentiment,
        Intensity intensity,
        Intent intent,
        String topic,
        boolean hasQuestion,
        QuestionImportance questionImportance,
        double humorLevel,
        double flirtLevel,
        double seriousness,
        double urgency,
        double friendliness,
        double enthusiasm,
        double uncertainty,
        double openness,
        ConversationalEffort conversationalEffort,
        boolean introducesNewTopic,
        boolean referencesEarlierTopic,
        boolean asksForDisclosure,
        boolean expectsResponse,
        String language
) {

    /**
     * Backward-compatible constructor retained for callers that only need the
     * original compact signal set.
     */
    public MessageIntelligence(
            Long senderId,
            boolean isCurrentUser,
            String content,
            int length,
            Instant createdAt,
            Sentiment sentiment,
            Intensity intensity,
            Intent intent,
            String topic,
            boolean hasQuestion,
            QuestionImportance questionImportance,
            double humorLevel,
            double flirtLevel,
            double seriousness,
            ConversationalEffort conversationalEffort,
            boolean expectsResponse) {
        this(senderId, isCurrentUser, content, length, createdAt, sentiment, intensity, intent, topic,
                hasQuestion, questionImportance, humorLevel, flirtLevel, seriousness,
                0.0, 0.5, 0.5, 0.0, 0.5, conversationalEffort,
                false, false, false, expectsResponse, "ENGLISH");
    }

    public enum Sentiment {
        POSITIVE,
        NEUTRAL,
        NEGATIVE,
        STRESSED,
        EXCITED
    }

    public enum Intensity {
        LOW,
        MEDIUM,
        HIGH
    }

    public enum Intent {
        QUESTION,
        STATEMENT,
        JOKE,
        COMPLIMENT,
        INVITATION,
        REQUEST,
        SHARING_EXPERIENCE,
        EMOTIONAL_VENT,
        FLIRTING,
        TOPIC_CHANGE,
        RECONNECTING
    }

    public enum QuestionImportance {
        NONE,
        LOW,
        MEDIUM,
        HIGH
    }

    public enum ConversationalEffort {
        VERY_LOW,
        LOW,
        MODERATE,
        HIGH
    }
}
