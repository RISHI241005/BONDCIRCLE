package com.datingapp.chat.replycoach.model;

import java.util.Collections;
import java.util.List;

public record ConversationAnalysis(
        String primaryTopic,
        List<String> secondaryTopics,
        Intent intent,
        String tone,
        Momentum momentum,
        Stage stage,
        String language,
        boolean isDry,
        boolean hasUnansweredQuestion,
        String lastQuestionText
) {
    public enum Intent {
        QUESTION,
        STATEMENT,
        JOKE,
        COMPLIMENT,
        INVITATION,
        EMOTIONAL_SUPPORT,
        FLIRTING,
        TOPIC_CHANGE,
        RECONNECTING
    }

    public enum Momentum {
        HIGH,
        MEDIUM,
        LOW
    }

    public enum Stage {
        NEW_MATCH,
        GETTING_TO_KNOW,
        CASUAL,
        DEEP,
        PLAYFUL,
        FLIRTING,
        PLANNING,
        DRY,
        RECONNECTING
    }

    public static ConversationAnalysis empty(String language) {
        return new ConversationAnalysis(
                "Fresh Match",
                Collections.emptyList(),
                Intent.STATEMENT,
                "Friendly",
                Momentum.MEDIUM,
                Stage.NEW_MATCH,
                language != null ? language : "ENGLISH",
                false,
                false,
                null
        );
    }
}
