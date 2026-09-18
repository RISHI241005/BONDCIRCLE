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
        ConversationalEffort conversationalEffort,
        boolean expectsResponse
) {

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
