package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.dto.ReplySuggestionItem;
import com.datingapp.chat.replycoach.entity.FeedbackAction;
import com.datingapp.chat.replycoach.model.ReplyStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SuggestionGenerationSessionTest {

    private final SuggestionGenerationSession sessions = new SuggestionGenerationSession();

    @Test
    void refreshCarriesShownIdeasAndStrategiesForward() {
        SuggestionGenerationSession.Snapshot first = sessions.begin(101L, "conv", "msg-1", false);
        assertThat(first.generationNumber()).isEqualTo(1);

        sessions.recordGeneration(101L, "conv", "msg-1", List.of(
                new ReplySuggestionItem("s1", "What happened today?", "Day", "Curious", "CURIOUS", "Curious"),
                new ReplySuggestionItem("s2", "That sounds rough 😭", "Day", "Warm", "SUPPORTIVE", "Warm")
        ));

        SuggestionGenerationSession.Snapshot refresh = sessions.begin(101L, "conv", "msg-1", true);
        assertThat(refresh.generationNumber()).isEqualTo(2);
        assertThat(refresh.previousSuggestionTexts()).containsExactly(
                "What happened today?", "That sounds rough 😭");
        assertThat(refresh.previousStrategies()).containsExactlyInAnyOrder(
                ReplyStrategy.CURIOUS, ReplyStrategy.SUPPORTIVE);
    }

    @Test
    void newIncomingMessageStartsFreshSession() {
        sessions.begin(101L, "conv", "msg-1", false);
        sessions.recordGeneration(101L, "conv", "msg-1", List.of(
                new ReplySuggestionItem("s1", "Old idea", "General", "Warm", "THOUGHTFUL", "Warm")
        ));

        SuggestionGenerationSession.Snapshot next = sessions.begin(101L, "conv", "msg-2", true);

        assertThat(next.previousSuggestionTexts()).isEmpty();
        assertThat(next.previousStrategies()).isEmpty();
    }

    @Test
    void recordsRejectedAndSelectedFeedbackSeparately() {
        sessions.begin(101L, "conv", "msg-1", false);
        sessions.recordFeedback(101L, "conv", "No thanks", FeedbackAction.REJECTED);
        sessions.recordFeedback(101L, "conv", "This one", FeedbackAction.USED);

        SuggestionGenerationSession.Snapshot snapshot = sessions.begin(101L, "conv", "msg-1", true);
        assertThat(snapshot.rejectedSuggestions()).contains("No thanks");
        assertThat(snapshot.selectedSuggestions()).contains("This one");
    }
}
