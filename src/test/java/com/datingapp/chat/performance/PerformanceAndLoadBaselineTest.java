package com.datingapp.chat.performance;

import com.datingapp.chat.conversation.dto.ConversationDetailResponse;
import com.datingapp.chat.conversation.service.ConversationService;
import com.datingapp.chat.message.dto.MessageCursorPage;
import com.datingapp.chat.message.dto.SendMessageRequest;
import com.datingapp.chat.message.service.MessageService;
import com.datingapp.chat.testutil.AbstractMySQLIntegrationTest;
import com.datingapp.chat.testutil.UserTestFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceAndLoadBaselineTest extends AbstractMySQLIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PerformanceAndLoadBaselineTest.class);

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private MessageService messageService;

    @Test
    @DisplayName("Should maintain sub-20ms cursor pagination latency over 150 messages in MySQL")
    void testCursorPaginationPerformance() throws Exception {
        ConversationDetailResponse conv = conversationService.createOrGetConversation(
                UserTestFactory.USER_A, UserTestFactory.USER_B);
        String convId = conv.getConversationId();

        // 1. Bulk insert 150 messages
        long insertStart = System.currentTimeMillis();
        for (int i = 1; i <= 150; i++) {
            SendMessageRequest req = new SendMessageRequest("Bulk test message " + i, "bulk-perf-" + i, null);
            messageService.sendMessage(convId, UserTestFactory.USER_A, req);
        }
        long insertDuration = System.currentTimeMillis() - insertStart;
        log.info("Inserted 150 messages in {} ms (~{} ms/msg)", insertDuration, insertDuration / 150.0);

        // 2. Measure first page query latency
        long page1Start = System.nanoTime();
        MessageCursorPage page1 = messageService.getMessageHistory(convId, UserTestFactory.USER_A, null, 30);
        long page1LatencyMs = (System.nanoTime() - page1Start) / 1_000_000;
        log.info("Page 1 (30 items) retrieval latency: {} ms", page1LatencyMs);

        assertNotNull(page1);
        assertEquals(30, page1.getMessages().size());
        assertTrue(page1.isHasMore());
        assertNotNull(page1.getNextCursor());
        assertTrue(page1LatencyMs < 500, "Pagination latency must be sub-500ms");

        // 3. Measure cursor-based second page query latency
        long page2Start = System.nanoTime();
        MessageCursorPage page2 = messageService.getMessageHistory(
                convId, UserTestFactory.USER_A, page1.getNextCursor(), 30);
        long page2LatencyMs = (System.nanoTime() - page2Start) / 1_000_000;
        log.info("Page 2 (30 items via cursor) retrieval latency: {} ms", page2LatencyMs);

        assertNotNull(page2);
        assertEquals(30, page2.getMessages().size());
        assertTrue(page2.isHasMore());
        assertTrue(page2LatencyMs < 500, "Cursor pagination query must be sub-500ms");
    }
}
