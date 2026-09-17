package com.datingapp.chat.icebreaker.dto;

import java.util.List;

public record IceBreakerResponse(
        String conversationId,
        String context,
        String guidance,
        List<String> sharedInterests,
        List<String> priorTopics,
        List<IceBreakerCircle> circles,
        List<IceBreakerSuggestion> suggestions
) {
}
