package com.datingapp.chat.replycoach.model;

import java.util.Collections;
import java.util.Set;

/** Lightweight description of how the other participant communicates. */
public record PartnerCommunicationProfile(
        UserWritingProfile.LengthPreference length,
        UserWritingProfile.EmojiUsage emojiUsage,
        String language,
        UserWritingProfile.Formality formality,
        Set<String> slangTokens,
        double humorLevel,
        double enthusiasm,
        double questionFrequency,
        String promptDirectives
) {
    public static PartnerCommunicationProfile defaults(String language) {
        return new PartnerCommunicationProfile(
                UserWritingProfile.LengthPreference.MEDIUM,
                UserWritingProfile.EmojiUsage.OCCASIONAL,
                language == null ? "ENGLISH" : language,
                UserWritingProfile.Formality.CASUAL,
                Collections.emptySet(),
                0.3,
                0.5,
                0.3,
                "Mirror the other person's energy without copying them mechanically."
        );
    }
}
