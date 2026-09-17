package com.datingapp.chat.icebreaker.dto;

import java.util.List;

public record IceBreakerResponse(
        String conversationId,
        String context,
        String guidance,
        List<String> sharedInterests,
        List<String> priorTopics,
        List<IceBreakerCircle> circles,
        List<IceBreakerSuggestion> suggestions,
        String source,
        String language,
        String mode,
        boolean generatedLive,
        String shouldReply,
        String urgency,
        String decisionReason,
        String replyTiming
) {
    public IceBreakerResponse(
            String conversationId,
            String context,
            String guidance,
            List<String> sharedInterests,
            List<String> priorTopics,
            List<IceBreakerCircle> circles,
            List<IceBreakerSuggestion> suggestions,
            String source,
            String language,
            String mode,
            boolean generatedLive) {
        this(
                conversationId,
                context,
                guidance,
                sharedInterests,
                priorTopics,
                circles,
                suggestions,
                source,
                language,
                mode,
                generatedLive,
                "RECOMMENDED",
                "MEDIUM",
                guidance,
                "Whenever you're ready");
    }
}
