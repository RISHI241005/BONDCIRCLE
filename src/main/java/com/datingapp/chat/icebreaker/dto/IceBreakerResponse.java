package com.datingapp.chat.icebreaker.dto;

import java.util.List;

public record IceBreakerResponse(
        String conversationId,
        String context,
        String guidance,
        List<String> sharedInterests,
        List<IceBreakerSuggestion> suggestions
) {
}
