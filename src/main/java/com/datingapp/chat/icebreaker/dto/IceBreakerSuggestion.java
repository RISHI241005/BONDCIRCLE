package com.datingapp.chat.icebreaker.dto;

public record IceBreakerSuggestion(
        String text,
        String topic,
        String reason,
        String circle,
        String circleLabel,
        String tone,
        int relevanceScore
) {
}
