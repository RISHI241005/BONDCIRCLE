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
        String replyTiming,
        String detectedMood,
        String conversationScenario
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
            boolean generatedLive,
            String shouldReply,
            String urgency,
            String decisionReason,
            String replyTiming) {
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
                shouldReply,
                urgency,
                decisionReason,
                replyTiming,
                "Casual & Relaxed",
                "General chat flow");
    }

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
                "Whenever you're ready",
                "Casual & Relaxed",
                "General chat flow");
    }
}
