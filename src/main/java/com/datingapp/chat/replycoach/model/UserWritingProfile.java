package com.datingapp.chat.replycoach.model;

public record UserWritingProfile(
        LengthPreference length,
        EmojiUsage emojiUsage,
        String language,
        Formality formality,
        boolean usesSlang,
        boolean usesHumor,
        String promptDirectives
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
}
