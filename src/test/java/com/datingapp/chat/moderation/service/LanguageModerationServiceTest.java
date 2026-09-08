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
    void detectsPronunciationBasedSpellingsAndTypos() {
        assertTrue(service.analyze("tu bevakuf hai").flagged());
        assertTrue(service.analyze("what the phuck").flagged());
        assertTrue(service.analyze("you idoit").flagged());
        assertTrue(service.analyze("kitna kameenaa hai").flagged());
        assertTrue(service.analyze("bh0sd1ke").flagged());
    }

    @Test
    void detectsAdditionalEnglishAndHinglishAbuse() {
        assertTrue(service.analyze("you scumbag").flagged());
        assertTrue(service.analyze("nikamma kahi ka").flagged());
        assertTrue(service.analyze("buddhoo mat bano").flagged());
        assertTrue(service.analyze("what a chhapri").flagged());
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
        ModerationResult officeSupplies = service.analyze("This batch of sheets is for the class.");
        assertFalse(officeSupplies.flagged(), officeSupplies.matchedTerms().toString());
        assertFalse(service.analyze("The beach is lovely and the witch story is classic.").flagged());
        assertFalse(service.analyze("She is passionate about assessment quality.").flagged());
        assertFalse(service.analyze("Where is the whole group meeting?").flagged());
    }

    @Test
    void blankContentIsNotFlagged() {
        assertFalse(service.analyze(null).flagged());
        assertFalse(service.analyze("   ").flagged());
    }
}
