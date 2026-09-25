package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.model.ConversationContext;
import com.datingapp.chat.replycoach.model.ConversationEnvironment;
import com.datingapp.chat.replycoach.model.LatestMessageAnalysis;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LatestMessageAnalyzerTest {

    private final ConversationIntelligenceService intelligence = new ConversationIntelligenceService();
    private final LatestMessageAnalyzer analyzer = new LatestMessageAnalyzer();

    @Test
    void interpretsLatestIncomingMessageAsPrimaryReplyTarget() {
        ConversationContext context = context(List.of(
                message("1", true, "How was college today?"),
                message("2", false, "haan yaar aaj college mein itna kaam tha 😭")
        ));
        ConversationEnvironment environment = intelligence.analyzeEnvironment(context);

        LatestMessageAnalysis result = analyzer.analyze(
                context, intelligence.analyzeMessages(context), environment);

        assertThat(result.messageId()).isEqualTo("2");
        assertThat(result.topic()).isEqualTo("Studies & Academics");
        assertThat(result.emotionalSignal()).isTrue();
        assertThat(result.emotion()).isEqualTo("TIRED_OR_STRESSED");
        assertThat(result.language()).isIn("HINGLISH", "MIXED");
        assertThat(result.conversationOpportunity()).isEqualTo("EMPATHY_AND_FOLLOW_UP");
    }

    @Test
    void doesNotTreatOlderPartnerMessageAsNewWhenCurrentUserSpokeLast() {
        ConversationContext context = context(List.of(
                message("1", false, "How was your day?"),
                message("2", true, "Pretty good actually")
        ));

        LatestMessageAnalysis result = analyzer.analyze(
                context, intelligence.analyzeMessages(context), intelligence.analyzeEnvironment(context));

        assertThat(result.text()).isEmpty();
        assertThat(result.messageId()).isNull();
    }

    private ConversationContext context(List<ConversationContext.ContextMessage> messages) {
        return new ConversationContext("conv", 101L, 202L, "", "", messages);
    }

    private ConversationContext.ContextMessage message(String id, boolean currentUser, String text) {
        return new ConversationContext.ContextMessage(id, currentUser, text, Instant.now());
    }
}
