package com.datingapp.chat.icebreaker.service;

import com.datingapp.chat.message.entity.Message;
import com.datingapp.chat.moderation.service.LanguageModerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationSignalExtractorTest {

    private ConversationSignalExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new ConversationSignalExtractor(new LanguageModerationService());
    }

    @Test
    void detectsHinglishConversationCorrectly() {
        Message msg1 = new Message();
        msg1.setId(1L);
        msg1.setSenderId(101L);
        msg1.setContent("Arre bhai kya scene hai?");
        msg1.setCreatedAt(Instant.now());

        Message msg2 = new Message();
        msg2.setId(2L);
        msg2.setSenderId(202L);
        msg2.setContent("Bas chill chal raha hai, tu bata!");
        msg2.setCreatedAt(Instant.now());

        assertTrue(extractor.isHinglishConversation(List.of(msg1, msg2)));

        Message englishMsg = new Message();
        englishMsg.setId(3L);
        englishMsg.setSenderId(101L);
        englishMsg.setContent("Are you available for the presentation tomorrow morning?");
        englishMsg.setCreatedAt(Instant.now());

        assertFalse(extractor.isHinglishConversation(List.of(englishMsg)));
    }

    @Test
    void extractsSignalsWithHinglishMoodDetection() {
        Message msg = new Message();
        msg.setId(1L);
        msg.setSenderId(202L);
        msg.setContent("Aaj office me bohot kaam tha yaar, kaafi thak gaya");
        msg.setCreatedAt(Instant.now());

        ConversationSignalExtractor.ConversationSignals signals =
                extractor.extract(List.of(msg), 101L, List.of("Office"));

        assertTrue(signals.isHinglish());
        assertEquals("Exhausted & Stressed", signals.detectedMood());
    }

    @Test
    void detectsPlayfulHinglishMood() {
        Message msg = new Message();
        msg.setId(1L);
        msg.setSenderId(202L);
        msg.setContent("Haha pagal hai kya, itni bakwaas mat kar 😂");
        msg.setCreatedAt(Instant.now());

        ConversationSignalExtractor.ConversationSignals signals =
                extractor.extract(List.of(msg), 101L, List.of());

        assertTrue(signals.isHinglish());
        assertEquals("Playful & Teasing", signals.detectedMood());
    }
}
