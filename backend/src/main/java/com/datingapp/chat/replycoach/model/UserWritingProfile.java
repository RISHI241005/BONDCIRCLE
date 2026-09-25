package com.datingapp.chat.replycoach.model;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public record UserWritingProfile(
        LengthPreference length,
        EmojiUsage emojiUsage,
        String language,
        Formality formality,
        boolean usesSlang,
        boolean usesHumor,
        String promptDirectives,
        double averageLength,
        int medianLength,
        Set<String> slangTokens,
        List<String> favoriteEmojis,
        double hinglishRatio,
        Set<ReplyStrategy> preferredStrategies,
        Set<ReplyStrategy> rejectedStrategies,
        String negativeDirectives
) {

    public enum LengthPreference {
        SHORT,
        MEDIUM,
        LONG
    }

    public enum EmojiUsage {
        NONE,
        OCCASIONAL,
        FREQUENT
    }

    public enum Formality {
        CASUAL,
        PUNCTUATED,
        FORMAL
    }

    public UserWritingProfile(
            LengthPreference length,
            EmojiUsage emojiUsage,
            String language,
            Formality formality,
            boolean usesSlang,
            boolean usesHumor,
            String promptDirectives) {
        this(
                length,
                emojiUsage,
                language,
                formality,
                usesSlang,
                usesHumor,
                promptDirectives,
                length == LengthPreference.SHORT ? 25.0 : (length == LengthPreference.LONG ? 80.0 : 45.0),
                length == LengthPreference.SHORT ? 22 : (length == LengthPreference.LONG ? 75 : 40),
                Collections.emptySet(),
                Collections.emptyList(),
                "HINGLISH".equalsIgnoreCase(language) ? 0.4 : 0.0,
                Collections.emptySet(),
                Collections.emptySet(),
                ""
        );
    }

    public static UserWritingProfile defaultProfile(String language) {
        return new UserWritingProfile(
                LengthPreference.MEDIUM,
                EmojiUsage.OCCASIONAL,
                language != null ? language : "ENGLISH",
                Formality.CASUAL,
                false,
                true,
                "Keep replies conversational, relaxed and natural."
        );
    }

    public static UserWritingProfile defaults() {
        return defaultProfile("ENGLISH");
    }
}
