package com.datingapp.chat.icebreaker.dto;

public record IceBreakerSuggestion(
        String text,
        String topic,
        String reason,
        String circle,
        String circleLabel,
        String tone,
        int relevanceScore,
        String language,
        boolean aiGenerated
) {
    public IceBreakerSuggestion(
            String text,
            String topic,
            String reason,
            String circle,
            String circleLabel,
            String tone,
            int relevanceScore) {
        this(text, topic, reason, circle, circleLabel, tone, relevanceScore, "ENGLISH", false);
    }
}
