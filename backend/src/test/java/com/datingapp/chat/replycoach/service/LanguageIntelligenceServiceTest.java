package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.model.ConversationContext.ContextMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageIntelligenceServiceTest {

    private LanguageIntelligenceService service;

    @BeforeEach
    void setUp() {
        service = new LanguageIntelligenceService();
    }

    @Test
    @DisplayName("Detects pure English dialogue accurately")
    void testEnglishDetection() {
        List<ContextMessage> messages = List.of(
                new ContextMessage("1", true, "Hey, how was your day?", Instant.now()),
                new ContextMessage("2", false, "It was quite busy, but good overall!", Instant.now())
        );

        LanguageIntelligenceService.LanguageProfile profile = service.analyzeLanguage(messages);
        assertThat(profile.dominantLanguage()).isEqualTo("ENGLISH");
        assertThat(profile.hinglishRatio()).isLessThan(0.05);
        assertThat(profile.hasLanguageSwitch()).isFalse();
    }

    @Test
    @DisplayName("Detects natural urban Hinglish with Roman Hindi words")
    void testHinglishDetection() {
        List<ContextMessage> messages = List.of(
                new ContextMessage("1", false, "Arre yaar aaj toh kaafi badiya din tha!", Instant.now()),
                new ContextMessage("2", true, "Sach me? Aisa kya hua bhai?", Instant.now())
        );

        LanguageIntelligenceService.LanguageProfile profile = service.analyzeLanguage(messages);
        assertThat(profile.dominantLanguage()).isEqualTo("HINGLISH");
        assertThat(profile.hinglishRatio()).isGreaterThanOrEqualTo(0.20);
    }

    @Test
    @DisplayName("Detects mixed English-Hinglish conversation")
    void testMixedLanguageDetection() {
        List<ContextMessage> messages = List.of(
                new ContextMessage("1", false, "Honestly aaj college presentation ka scene kaafi intense tha", Instant.now()),
                new ContextMessage("2", true, "Did you manage to survive it? 😂", Instant.now())
        );

        LanguageIntelligenceService.LanguageProfile profile = service.analyzeLanguage(messages);
        assertThat(profile.dominantLanguage()).isIn("MIXED", "HINGLISH");
    }

    @Test
    @DisplayName("Detects common conversational slang tokens")
    void testSlangDetection() {
        List<ContextMessage> messages = List.of(
                new ContextMessage("1", false, "Bro that was actually insane tbh", Instant.now()),
                new ContextMessage("2", true, "Yaar same, pure chill vibes rn", Instant.now())
        );

        LanguageIntelligenceService.LanguageProfile profile = service.analyzeLanguage(messages);
        assertThat(profile.detectedSlang()).contains("bro", "tbh", "yaar", "chill");
    }

    @Test
    @DisplayName("Detects language switching behavior between participants")
    void testLanguageSwitching() {
        List<ContextMessage> messages = List.of(
                new ContextMessage("1", false, "Good morning, how are you doing today?", Instant.now()),
                new ContextMessage("2", true, "Arre yaar aaj toh bohot neend aa rahi hai", Instant.now())
        );

        LanguageIntelligenceService.LanguageProfile profile = service.analyzeLanguage(messages);
        assertThat(profile.hasLanguageSwitch()).isTrue();
    }
}
