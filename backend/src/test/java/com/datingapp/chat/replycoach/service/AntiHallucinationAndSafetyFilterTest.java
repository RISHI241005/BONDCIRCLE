package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.dto.ReplySuggestionItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiHallucinationAndSafetyFilterTest {

    private ReplyCoachQualityFilter qualityFilter;

    @BeforeEach
    void setUp() {
        qualityFilter = new ReplyCoachQualityFilter();
    }

    @Test
    @DisplayName("Should filter out toxic, desperate, and harassing messages")
    void testFilterToxicAndHarassingSuggestions() {
        List<ReplySuggestionItem> candidates = List.of(
                new ReplySuggestionItem("1", "Why did you ignore me all day?", "Chat", "ANNOYED", "CONFRONT", "Toxic"),
                new ReplySuggestionItem("2", "Answer me now please!", "Chat", "DESPERATE", "CONFRONT", "Harassing"),
                new ReplySuggestionItem("3", "Hey, hope you had a good week!", "Chat", "WARM", "RECONNECT", "Safe healthy check-in"),
                new ReplySuggestionItem("4", "You never text back anyway", "Chat", "PASSIVE_AGGRESSIVE", "GUILT", "Guilt tripping")
        );

        List<ReplySuggestionItem> result = qualityFilter.filter(candidates, Collections.emptyList(), 3);

        assertEquals(1, result.size());
        assertEquals("Hey, hope you had a good week!", result.get(0).getText());
    }

    @Test
    @DisplayName("Should filter out fabricated experiences (anti-hallucination)")
    void testFilterFabricatedExperiences() {
        List<ReplySuggestionItem> candidates = List.of(
                new ReplySuggestionItem("1", "Oh yeah, I watched that yesterday with my friends!", "Movies", "ENTHUSIASTIC", "AGREE", "Fabricated fact"),
                new ReplySuggestionItem("2", "That sounds really interesting, what did you like most about it?", "Movies", "CURIOUS", "ASK_FOLLOWUP", "Legitimate question"),
                new ReplySuggestionItem("3", "I was just there yesterday, it was wild!", "Places", "EXCITED", "SHARE", "Fabricated event")
        );

        List<ReplySuggestionItem> result = qualityFilter.filter(candidates, Collections.emptyList(), 3);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getText().contains("what did you like most about it"));
    }

    @Test
    @DisplayName("Should filter out previously rejected text and near duplicates")
    void testFilterRejectedAndDuplicates() {
        String rejected = "Sounds fun, let me know!";
        List<ReplySuggestionItem> candidates = List.of(
                new ReplySuggestionItem("1", "Sounds fun, let me know!", "Plans", "CASUAL", "ACKNOWLEDGE", "Exact match to rejected"),
                new ReplySuggestionItem("2", "That sounds really fun, let's definitely plan it!", "Plans", "ENTHUSIASTIC", "INVITATION", "Original"),
                new ReplySuggestionItem("3", "That sounds really fun, let's definitely plan something!", "Plans", "ENTHUSIASTIC", "INVITATION", "Near duplicate prefix"),
                new ReplySuggestionItem("4", "Haha totally up for it!", "Plans", "PLAYFUL", "AGREE", "Distinct")
        );

        List<ReplySuggestionItem> result = qualityFilter.filter(candidates, List.of(rejected), 3);

        assertEquals(2, result.size());
        assertFalse(result.stream().anyMatch(r -> r.getText().equals(rejected)));
        // Should contain only one of the near duplicates (candidate 2) and candidate 4
        long countNearDups = result.stream().filter(r -> r.getText().startsWith("That sounds really fun")).count();
        assertEquals(1, countNearDups);
    }
}
