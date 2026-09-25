package com.datingapp.chat.replycoach.model;

/**
 * Deterministic interpretation of the latest incoming message. This is kept
 * separate from the conversation-wide environment so generation can weight
 * the immediate reply target more strongly than older context.
 */
public record LatestMessageAnalysis(
        String messageId,
        String text,
        MessageIntelligence.Intent intent,
        String topic,
        MessageIntelligence.Sentiment sentiment,
        String emotion,
        String tone,
        String language,
        boolean question,
        boolean implicitQuestion,
        boolean request,
        boolean informationShared,
        boolean emotionalSignal,
        double humor,
        double sarcasm,
        double excitement,
        double frustration,
        double curiosity,
        double flirting,
        double hesitation,
        double uncertainty,
        double openness,
        String engagementLevel,
        String conversationOpportunity,
        ConversationEnvironment.ResponseExpectation expectedResponseType
) {
    public static LatestMessageAnalysis none() {
        return new LatestMessageAnalysis(
                null, "", MessageIntelligence.Intent.STATEMENT, "Fresh Match",
                MessageIntelligence.Sentiment.NEUTRAL, "NEUTRAL", "CASUAL", "ENGLISH",
                false, false, false, false, false,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                "LOW", "OPEN_TOPIC", ConversationEnvironment.ResponseExpectation.OPEN_TOPIC
        );
    }
}
