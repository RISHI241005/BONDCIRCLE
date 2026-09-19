package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.dto.ReplySuggestionItem;
import com.datingapp.chat.replycoach.model.ConversationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ReplyCoachQualityFilterTest {

    private ReplyCoachQualityFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ReplyCoachQualityFilter();
    }

    @Test
    @DisplayName("Filters out too short or too long suggestions")
    void testLengthFilter() {
        List<ReplySuggestionItem> input = List.of(
                new ReplySuggestionItem("1", "ok"), // too short (< 3 chars)
                new ReplySuggestionItem("2", "Valid reply!"),
                new ReplySuggestionItem("3", "a".repeat(400)) // too long (> 300 chars)
        );

        List<ReplySuggestionItem> result = filter.filter(input, List.of(), 3);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getText()).isEqualTo("Valid reply!");
    }

    @Test
    @DisplayName("Filters out rejected texts (normalized matching)")
    void testRejectedFilter() {
        List<ReplySuggestionItem> input = List.of(
                new ReplySuggestionItem("1", "Hey there, how are you?"),
                new ReplySuggestionItem("2", "Doing great today!")
        );

        List<ReplySuggestionItem> result = filter.filter(
                input, List.of("hey there how are you"), 3
        );
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getText()).isEqualTo("Doing great today!");
    }

    @Test
    @DisplayName("Deduplicates near identical suggestions")
    void testDeduplication() {
        List<ReplySuggestionItem> input = List.of(
                new ReplySuggestionItem("1", "That sounds like an amazing plan for Sunday afternoon!"),
                new ReplySuggestionItem("2", "That sounds like an amazing plan for Sunday evening!"),
                new ReplySuggestionItem("3", "Completely different idea here!")
        );

        List<ReplySuggestionItem> result = filter.filter(input, List.of(), 3);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getText()).contains("Sunday afternoon");
        assertThat(result.get(1).getText()).contains("Completely different idea");
    }

    @Test
    @DisplayName("Rejects close paraphrases of a previously rejected suggestion")
    void testRejectedParaphraseFilter() {
        List<ReplySuggestionItem> result = filter.filter(List.of(
                        new ReplySuggestionItem("1", "How did your day go today?"),
                        new ReplySuggestionItem("2", "What was the funniest part of college today?")
                ), List.of("How was your day today?"), 3);

        assertThat(result).extracting(ReplySuggestionItem::getId).containsExactly("2");
    }

    @Test
    @DisplayName("Blocks unsupported first-person experiences")
    void testUnsupportedFirstPersonFactFilter() {
        ConversationContext context = new ConversationContext(
                "conv", 1L, 2L, null, null,
                List.of(new ConversationContext.ContextMessage("1", false,
                        "Did you watch the match?", Instant.now())));

        List<ReplySuggestionItem> result = filter.filter(List.of(
                        new ReplySuggestionItem("1", "Yes I did! I watched it yesterday."),
                        new ReplySuggestionItem("2", "Which part of the match was best? 👀")
                ), List.of(), context, 3);

        assertThat(result).extracting(ReplySuggestionItem::getId).containsExactly("2");
    }
}
