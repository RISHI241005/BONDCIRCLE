package com.datingapp.chat.moderation.service;

import com.datingapp.chat.moderation.dto.ModerationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageModerationServiceTest {

    private final LanguageModerationService service = new LanguageModerationService();

    @Test
    void detectsCommonEnglishAbuse() {
        ModerationResult result = service.analyze("You are such an IDIOT.");

        assertTrue(result.flagged());
        assertTrue(result.matchedTerms().contains("idiot"));
    }

    @Test
    void detectsRomanizedHindiAndCommonSpellingVariants() {
        assertTrue(service.analyze("Tu kitna bewakoof hai yaar").flagged());
        assertTrue(service.analyze("kya paagal harkat hai").flagged());
        assertTrue(service.analyze("bewaquf mat bano").flagged());
    }

    @Test
    void detectsLeetspeakRepeatedLettersAndSeparatedLetters() {
        assertTrue(service.analyze("you are a b1tch").flagged());
        assertTrue(service.analyze("fuuuuuck this").flagged());
        assertTrue(service.analyze("f.u.c.k that").flagged());
    }

    @Test
    void doesNotMatchSafeWordsOrSubstrings() {
        assertFalse(service.analyze("The assistant discussed class assignments.").flagged());
        assertFalse(service.analyze("Pagalpanti is a movie title").flagged());
        assertFalse(service.analyze("Let's meet at five near the station.").flagged());
    }

    @Test
    void blankContentIsNotFlagged() {
        assertFalse(service.analyze(null).flagged());
        assertFalse(service.analyze("   ").flagged());
    }
}
