package com.datingapp.chat.moderation.dto;

import java.util.List;

public record ModerationResult(boolean flagged, List<String> matchedTerms) {

    public ModerationResult {
        matchedTerms = matchedTerms == null ? List.of() : List.copyOf(matchedTerms);
    }
}
