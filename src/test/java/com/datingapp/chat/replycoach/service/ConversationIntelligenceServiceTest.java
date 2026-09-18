package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.model.ConversationAnalysis;
import com.datingapp.chat.replycoach.model.ConversationContext;
import com.datingapp.chat.replycoach.model.ConversationContext.ContextMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationIntelligenceServiceTest {

    private ConversationIntelligenceService service;

    @BeforeEach
    void setUp() {
        service = new ConversationIntelligenceService();
    }

    @Test
    @DisplayName("Empty context returns safe empty analysis")
    void testEmptyContext() {
        ConversationAnalysis analysis = service.analyze(null);
        assertThat(analysis).isNotNull();
        assertThat(analysis.stage()).isEqualTo(ConversationAnalysis.Stage.NEW_MATCH);
        assertThat(analysis.primaryTopic()).isEqualTo("Fresh Match");
        assertThat(analysis.hasUnansweredQuestion()).isFalse();
    }

    @Test
    @DisplayName("Detects question asked by other user")
    void testQuestionDetection() {
        ContextMessage m1 = new ContextMessage("1", true, "Hey there!", Instant.now().minusSeconds(60));
        ContextMessage m2 = new ContextMessage("2", false, "Hey! Are you free this weekend?", Instant.now().minusSeconds(30));

        ConversationContext ctx = new ConversationContext(
                "conv-1", 101L, 102L, List.of(m1, m2), "Coffee", "Hiking"
        );

        ConversationAnalysis analysis = service.analyze(ctx);
        assertThat(analysis.hasUnansweredQuestion()).isTrue();
        assertThat(analysis.lastQuestionText()).contains("free this weekend?");
        assertThat(analysis.stage()).isEqualTo(ConversationAnalysis.Stage.NEW_MATCH);
    }

    @Test
    @DisplayName("Detects dry low-effort messages")
    void testDryDetection() {
        ContextMessage m1 = new ContextMessage("1", true, "How was your day at work?", Instant.now().minusSeconds(60));
        ContextMessage m2 = new ContextMessage("2", false, "k", Instant.now().minusSeconds(10));

        ConversationContext ctx = new ConversationContext(
                "conv-1", 101L, 102L, List.of(m1, m2), null, null
        );

        ConversationAnalysis analysis = service.analyze(ctx);
        assertThat(analysis.isDry()).isTrue();
        assertThat(analysis.tone()).isEqualTo("Re-energizing");
        assertThat(analysis.momentum()).isEqualTo(ConversationAnalysis.Momentum.LOW);
    }

    @Test
    @DisplayName("Detects Hinglish language mix")
    void testHinglishDetection() {
        ContextMessage m1 = new ContextMessage("1", false, "Arre yaar aaj toh kaafi badiya din tha!", Instant.now().minusSeconds(60));
        ContextMessage m2 = new ContextMessage("2", true, "Sach me? Kya hua waise?", Instant.now().minusSeconds(30));

        ConversationContext ctx = new ConversationContext(
                "conv-1", 101L, 102L, List.of(m1, m2), null, null
        );

        ConversationAnalysis analysis = service.analyze(ctx);
        assertThat(analysis.language()).isEqualTo("HINGLISH");
    }

    @Test
    @DisplayName("Detects primary topic such as Travel or Movies")
    void testTopicDetection() {
        ContextMessage m1 = new ContextMessage("1", false, "I'm planning a trip to the mountains next month!", Instant.now().minusSeconds(60));
        ContextMessage m2 = new ContextMessage("2", true, "That vacation sounds amazing, are you taking a flight?", Instant.now().minusSeconds(30));

        ConversationContext ctx = new ConversationContext(
                "conv-1", 101L, 102L, List.of(m1, m2), null, null
        );

        ConversationAnalysis analysis = service.analyze(ctx);
        assertThat(analysis.primaryTopic()).isEqualTo("Travel & Adventures");
    }
}
