package com.datingapp.chat.replycoach.model;

import java.util.Collections;
import java.util.List;

public record ConversationEnvironment(
        Stage stage,
        Momentum momentum,
        RelationshipSignal relationshipSignal,
        LastSpeaker lastSpeaker,
        ResponseExpectation responseExpectation,
        Temperature temperature,
        Direction direction,
        QuestionState questionState,
        List<EngagementSignal> engagementSignals,
        Depth depth,
        String primaryTopic,
        List<String> secondaryTopics,
        List<String> potentialNextTopics,
        String language,
        boolean isDry,
        boolean hasUnansweredQuestion,
        String unansweredQuestionText,
        long timingGapHours,
        boolean isRapidExchange,
        boolean isSuddenReturn
) {

    public enum Stage {
        NEW_MATCH,
        GETTING_TO_KNOW,
        CASUAL,
        PLAYFUL,
        FLIRTING,
        DEEP,
        SUPPORTIVE,
        PLANNING,
        RECONNECTING,
        DRY,
        ENDING
    }

    public enum Momentum {
        RISING,
        HIGH,
        STABLE,
        DECLINING,
        LOW
    }

    public enum RelationshipSignal {
        LOW_FAMILIARITY,
        BUILDING,
        COMFORTABLE,
        HIGH_COMFORT
    }

    public enum LastSpeaker {
        CURRENT_USER,
        OTHER_USER
    }

    public enum ResponseExpectation {
        ANSWER_REQUIRED,
        ACKNOWLEDGEMENT,
        FOLLOW_UP_NEEDED,
        EMOTIONAL_RESPONSE,
        PLAYFUL_RESPONSE,
        OPEN_TOPIC,
        NO_RESPONSE_REQUIRED,
        RECONNECT
    }

    public enum Temperature {
        COLD,
        WARM,
        PLAYFUL,
        EMOTIONAL,
        FLIRTY,
        SERIOUS
    }

    public enum Direction {
        CONTINUING_TOPIC,
        EXPANDING_TOPIC,
        CHANGING_TOPIC,
        RECONNECTING,
        CLOSING
    }

    public enum QuestionState {
        NO_QUESTION,
        QUESTION_ASKED,
        MULTIPLE_QUESTIONS,
        IMPLIED_QUESTION
    }

    public enum EngagementSignal {
        HIGH_INITIATIVE,
        BALANCED_INITIATIVE,
        USER_CARRYING,
        PARTNER_CARRYING,
        MUTUAL_ENGAGEMENT,
        LOW_EFFORT,
        REPEATED_SHORT_REPLIES,
        DELAYED_RESPONSES,
        RAPID_EXCHANGE
    }

    public enum Depth {
        SURFACE,
        PERSONAL,
        EMOTIONAL,
        VALUES,
        EXPERIENCES,
        PLANS,
        DEEP
    }

    public static ConversationEnvironment freshMatch(String language) {
        return new ConversationEnvironment(
                Stage.NEW_MATCH,
                Momentum.STABLE,
                RelationshipSignal.LOW_FAMILIARITY,
                LastSpeaker.OTHER_USER,
                ResponseExpectation.OPEN_TOPIC,
                Temperature.WARM,
                Direction.EXPANDING_TOPIC,
                QuestionState.NO_QUESTION,
                List.of(EngagementSignal.BALANCED_INITIATIVE),
                Depth.SURFACE,
                "Fresh Match",
                Collections.emptyList(),
                List.of("Interests", "Weekend Plans", "Music"),
                language != null ? language : "ENGLISH",
                false,
                false,
                null,
                0,
                false,
                false
        );
    }
}
