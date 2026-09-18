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

    public static ConversationAnalysis fromEnvironment(ConversationEnvironment env) {
        if (env == null) return empty("ENGLISH");
        Stage stage;
        try {
            stage = Stage.valueOf(env.stage().name());
        } catch (Exception e) {
            stage = Stage.CASUAL;
        }

        Momentum momentum;
        try {
            momentum = switch (env.momentum()) {
                case RISING, HIGH -> Momentum.HIGH;
                case STABLE -> Momentum.MEDIUM;
                case DECLINING, LOW -> Momentum.LOW;
            };
        } catch (Exception e) {
            momentum = Momentum.MEDIUM;
        }

        Intent intent = env.hasUnansweredQuestion() ? Intent.QUESTION :
                (env.stage() == ConversationEnvironment.Stage.RECONNECTING ? Intent.RECONNECTING :
                (env.stage() == ConversationEnvironment.Stage.FLIRTING ? Intent.FLIRTING :
                (env.stage() == ConversationEnvironment.Stage.PLANNING ? Intent.INVITATION :
                (env.stage() == ConversationEnvironment.Stage.SUPPORTIVE ? Intent.EMOTIONAL_SUPPORT : Intent.STATEMENT))));

        String tone = switch (env.temperature()) {
            case PLAYFUL -> "Playful";
            case EMOTIONAL -> "Empathetic";
            case FLIRTY -> "Playful & Flirty";
            case SERIOUS -> "Thoughtful";
            case COLD -> "Re-energizing";
            case WARM -> "Warm & Conversational";
        };

        return new ConversationAnalysis(
                env.primaryTopic(),
                env.secondaryTopics(),
                intent,
                tone,
                momentum,
                stage,
                env.language(),
                env.isDry(),
                env.hasUnansweredQuestion(),
                env.unansweredQuestionText()
        );
    }
}
