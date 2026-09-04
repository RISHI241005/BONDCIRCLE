package com.datingapp.chat.concurrency;

import com.datingapp.chat.conversation.dto.ConversationDetailResponse;
import com.datingapp.chat.conversation.service.ConversationService;
import com.datingapp.chat.message.dto.MessageResponse;
import com.datingapp.chat.message.dto.SendMessageRequest;
import com.datingapp.chat.message.service.MessageService;
import com.datingapp.chat.testutil.AbstractMySQLIntegrationTest;
import com.datingapp.chat.testutil.UserTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConcurrencyAndIdempotencyTest extends AbstractMySQLIntegrationTest {

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private MessageService messageService;

    private String convIdAB;

    @BeforeEach
    void setUp() {
        ConversationDetailResponse conv = conversationService.createOrGetConversation(
                UserTestFactory.USER_A, UserTestFactory.USER_B);
        convIdAB = conv.getConversationId();
    }

    @Test
    @DisplayName("Should handle 10 concurrent duplicate message requests idempotently with exactly 1 DB record")
    void testConcurrentIdempotentMessageSending() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<MessageResponse> responses = Collections.synchronizedList(new ArrayList<>());
        String clientMessageId = "concurrent-idemp-1";

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    SendMessageRequest req = new SendMessageRequest("Concurrent message content", clientMessageId, null);
                    MessageResponse resp = messageService.sendMessage(convIdAB, UserTestFactory.USER_A, req);
                    responses.add(resp);
                } catch (Exception e) {
                    // Handled
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown(); // Release all threads at once
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // 1. Verify all threads received a valid response
        assertEquals(threadCount, responses.size());

        // 2. Verify all responses returned the same public ID
        String expectedMessageId = responses.get(0).getId();
        for (MessageResponse r : responses) {
            assertEquals(expectedMessageId, r.getId());
        }

        // 3. Verify exactly ONE database record exists in MySQL
        Integer countInDb = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE client_message_id = ?", Integer.class, clientMessageId);
        assertEquals(1, countInDb);
    }

    @Test
    @DisplayName("Should handle 10 concurrent conversation creation attempts returning the exact same conversation")
    void testConcurrentConversationCreation() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<ConversationDetailResponse> responses = Collections.synchronizedList(new ArrayList<>());
        Long userX = 777L;
        Long userY = 888L;

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    ConversationDetailResponse resp = conversationService.createOrGetConversation(userX, userY);
                    responses.add(resp);
                } catch (Exception e) {
                    // Handled
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount, responses.size());
        String expectedConvId = responses.get(0).getConversationId();
        for (ConversationDetailResponse r : responses) {
            assertEquals(expectedConvId, r.getConversationId());
        }

        // Verify only ONE conversation exists between 777 and 888
        Integer countInDb = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM conversations c " +
                "JOIN conversation_participants p1 ON p1.conversation_id = c.id " +
                "JOIN conversation_participants p2 ON p2.conversation_id = c.id " +
                "WHERE p1.user_id = 777 AND p2.user_id = 888", Integer.class);
        assertEquals(1, countInDb);
    }
}
