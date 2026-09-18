package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.model.ConversationContext.ContextMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationMemoryExtractorTest {

    private ConversationMemoryExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new ConversationMemoryExtractor();
    }

    @Test
    @DisplayName("Should extract future events like dance performance or job interview")
    void testExtractFutureEvents() {
        List<ContextMessage> messages = List.of(
                new ContextMessage(1L, false, "Hey there!", Instant.now().minusSeconds(500)),
                new ContextMessage(2L, false, "Hey! My dance performance is on Friday so super busy", Instant.now().minusSeconds(400)),
                new ContextMessage(3L, true, "Oh wow, all the best!", Instant.now().minusSeconds(300)),
                new ContextMessage(4L, false, "Thanks!", Instant.now().minusSeconds(100))
        );

        List<String> memories = extractor.extractMemories(messages, 2);

        assertFalse(memories.isEmpty());
        assertTrue(memories.stream().anyMatch(m -> m.contains("Event/Plan") && m.contains("dance performance")));
    }

    @Test
    @DisplayName("Should extract commitments and promises like 'kal batata hoon'")
    void testExtractPromises() {
        List<ContextMessage> messages = List.of(
                new ContextMessage(1L, true, "Did you find that song?", Instant.now().minusSeconds(600)),
                new ContextMessage(2L, false, "Haan kal batata hoon pakka", Instant.now().minusSeconds(500)),
                new ContextMessage(3L, true, "I will send you the link later tonight", Instant.now().minusSeconds(400))
        );

        List<String> memories = extractor.extractMemories(messages, 1);

        assertFalse(memories.isEmpty());
        assertTrue(memories.stream().anyMatch(m -> m.contains("Promise/Commitment") && m.contains("kal batata")));
    }

    @Test
    @DisplayName("Should extract personal preferences like favorite coffee or foods")
    void testExtractPreferences() {
        List<ContextMessage> messages = List.of(
                new ContextMessage(1L, false, "I love cold coffee with hazelnut syrup so much", Instant.now().minusSeconds(800)),
                new ContextMessage(2L, true, "Nice! I love dark roast espresso", Instant.now().minusSeconds(700))
        );

        List<String> memories = extractor.extractMemories(messages, 1);

        assertFalse(memories.isEmpty());
        assertTrue(memories.stream().anyMatch(m -> m.contains("Preference/Detail") && m.contains("cold coffee")));
    }

    @Test
    @DisplayName("Should ignore short noise messages and empty lists")
    void testIgnoreNoiseMessages() {
        List<ContextMessage> noise = List.of(
                new ContextMessage(1L, true, "lol", Instant.now().minusSeconds(300)),
                new ContextMessage(2L, false, "haha", Instant.now().minusSeconds(200)),
                new ContextMessage(3L, true, "ok", Instant.now().minusSeconds(100))
        );

        List<String> memories = extractor.extractMemories(noise, 2);
        assertTrue(memories.isEmpty());

        List<String> empty = extractor.extractMemories(null, 2);
        assertTrue(empty.isEmpty());
    }

    @Test
    @DisplayName("Should focus on older messages when conversation history is deep")
    void testOlderMessagesExtraction() {
        List<ContextMessage> longChat = new ArrayList<>();
        // Older message with event
        longChat.add(new ContextMessage(1L, false, "I have a big job interview tomorrow morning", Instant.now().minusSeconds(10000)));
        // Recent dialogue
        for (int i = 2; i <= 10; i++) {
            longChat.add(new ContextMessage((long) i, i % 2 == 0, "Recent chit chat " + i, Instant.now().minusSeconds(1000 - i * 50)));
        }

        List<String> memories = extractor.extractMemories(longChat, 5);

        assertFalse(memories.isEmpty());
        assertTrue(memories.stream().anyMatch(m -> m.contains("interview")));
    }
}
