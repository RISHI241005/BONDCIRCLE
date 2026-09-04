package com.datingapp.chat.e2e;

import com.datingapp.chat.conversation.dto.ConversationDetailResponse;
import com.datingapp.chat.conversation.service.ConversationService;
import com.datingapp.chat.message.dto.EditMessageRequest;
import com.datingapp.chat.message.dto.MessageResponse;
import com.datingapp.chat.message.service.MessageService;
import com.datingapp.chat.security.JwtService;
import com.datingapp.chat.testutil.AbstractMySQLIntegrationTest;
import com.datingapp.chat.testutil.UserTestFactory;
import com.datingapp.chat.websocket.dto.WsDeliveryAckPayload;
import com.datingapp.chat.websocket.dto.WsMessagePayload;
import com.datingapp.chat.websocket.dto.WsReadReceiptPayload;
import com.datingapp.chat.websocket.dto.event.WsEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CompleteE2EChatWorkflowTest extends AbstractMySQLIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ObjectMapper objectMapper;

    private String wsUrl;
    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUpClient() {
        wsUrl = "ws://localhost:" + port + "/ws";

        stompClient = new WebSocketStompClient(
                new StandardWebSocketClient()
        );

        MappingJackson2MessageConverter converter =
                new MappingJackson2MessageConverter();

        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);
    }

    @Test
    @DisplayName(
            "Complete E2E Chat Scenario (Section 54): " +
            "Create Chat -> Connect WS -> Send -> Deliver Ack -> " +
            "Read Receipt -> Edit -> Delete -> MySQL Verify"
    )
    void testCompleteE2EChatScenario() throws Exception {

        // ============================================================
        // Step 1: Create Tokens for User A and User B
        // ============================================================

        String tokenA = UserTestFactory.createRawToken(
                jwtService,
                UserTestFactory.USER_A
        );

        String tokenB = UserTestFactory.createRawToken(
                jwtService,
                UserTestFactory.USER_B
        );

        String bearerA = "Bearer " + tokenA;
        String bearerB = "Bearer " + tokenB;


        // ============================================================
        // Step 2: Create conversation via REST
        // ============================================================

        ConversationDetailResponse conv =
                conversationService.createOrGetConversation(
                        UserTestFactory.USER_A,
                        UserTestFactory.USER_B
                );

        String convId = conv.getConversationId();


        // ============================================================
        // Step 3: A and B load conversation through REST
        // ============================================================

        mockMvc.perform(
                        get("/api/v1/chats/" + convId)
                                .header("Authorization", bearerA)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.conversationId")
                                .value(convId)
                );

        mockMvc.perform(
                        get("/api/v1/chats/" + convId)
                                .header("Authorization", bearerB)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.conversationId")
                                .value(convId)
                );


        // ============================================================
        // Step 4: Connect User A & User B via WebSocket STOMP
        // ============================================================

        StompHeaders headersA = new StompHeaders();
        headersA.add("Authorization", bearerA);

        CompletableFuture<StompSession> sessionAFuture =
                new CompletableFuture<>();

        stompClient.connectAsync(
                wsUrl,
                (org.springframework.web.socket.WebSocketHttpHeaders) null,
                headersA,
                new StompSessionHandlerAdapter() {

                    @Override
                    public void afterConnected(
                            StompSession session,
                            StompHeaders connectedHeaders
                    ) {
                        sessionAFuture.complete(session);
                    }
                }
        );

        StompSession sessionA =
                sessionAFuture.get(5, TimeUnit.SECONDS);


        StompHeaders headersB = new StompHeaders();
        headersB.add("Authorization", bearerB);

        CompletableFuture<StompSession> sessionBFuture =
                new CompletableFuture<>();

        stompClient.connectAsync(
                wsUrl,
                (org.springframework.web.socket.WebSocketHttpHeaders) null,
                headersB,
                new StompSessionHandlerAdapter() {

                    @Override
                    public void afterConnected(
                            StompSession session,
                            StompHeaders connectedHeaders
                    ) {
                        sessionBFuture.complete(session);
                    }
                }
        );

        StompSession sessionB =
                sessionBFuture.get(5, TimeUnit.SECONDS);


        // ============================================================
        // Step 5: B & A subscribe to /user/queue/messages
        // ============================================================

        BlockingQueue<WsEvent> queueB =
                new LinkedBlockingQueue<>();

        sessionB.subscribe(
                "/user/queue/messages",
                new StompFrameHandler() {

                    @Override
                    public Type getPayloadType(
                            StompHeaders headers
                    ) {
                        return WsEvent.class;
                    }

                    @Override
                    public void handleFrame(
                            StompHeaders headers,
                            Object payload
                    ) {
                        queueB.offer((WsEvent) payload);
                    }
                }
        );


        BlockingQueue<WsEvent> queueA =
                new LinkedBlockingQueue<>();

        sessionA.subscribe(
                "/user/queue/messages",
                new StompFrameHandler() {

                    @Override
                    public Type getPayloadType(
                            StompHeaders headers
                    ) {
                        return WsEvent.class;
                    }

                    @Override
                    public void handleFrame(
                            StompHeaders headers,
                            Object payload
                    ) {
                        queueA.offer((WsEvent) payload);
                    }
                }
        );


        // ============================================================
        // Step 6: User A sends message over WebSocket
        // ============================================================

        WsMessagePayload sendPayload =
                new WsMessagePayload(
                        convId,
                        "Hey B, welcome to the dating app!"
                );

        sendPayload.setClientMessageId("e2e-msg-01");

        sessionA.send(
                "/app/chat.send",
                sendPayload
        );


        // ============================================================
        // Step 7: B receives realtime message
        // ============================================================

        WsEvent newMsgEvent =
                queueB.poll(5, TimeUnit.SECONDS);

        assertNotNull(newMsgEvent);

        assertEquals(
                "NEW_MESSAGE",
                newMsgEvent.getEventType().name()
        );


        // ============================================================
        // Extract message ID from MySQL
        // ============================================================

        String messagePublicId =
                jdbcTemplate.queryForObject(
                        "SELECT public_id " +
                        "FROM messages " +
                        "WHERE client_message_id = 'e2e-msg-01'",
                        String.class
                );

        assertNotNull(messagePublicId);


        // ============================================================
        // Step 8: B acknowledges delivery
        // ============================================================

        WsDeliveryAckPayload deliveryAck =
                new WsDeliveryAckPayload(
                        convId,
                        messagePublicId
                );

        sessionB.send(
                "/app/chat.delivered",
                deliveryAck
        );


        // ============================================================
        // Verify status eventually becomes DELIVERED
        // ============================================================

        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {

                    String statusAfterDelivery =
                            jdbcTemplate.queryForObject(
                                    "SELECT status " +
                                    "FROM messages " +
                                    "WHERE public_id = ?",
                                    String.class,
                                    messagePublicId
                            );

                    assertEquals(
                            "DELIVERED",
                            statusAfterDelivery
                    );
                });


        // ============================================================
        // Step 9: B opens conversation and sends READ receipt
        // ============================================================

        WsReadReceiptPayload readPayload =
                new WsReadReceiptPayload(
                        convId,
                        messagePublicId
                );

        sessionB.send(
                "/app/chat.read",
                readPayload
        );


        // ============================================================
        // Verify status eventually becomes READ
        // ============================================================

        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {

                    String statusAfterRead =
                            jdbcTemplate.queryForObject(
                                    "SELECT status " +
                                    "FROM messages " +
                                    "WHERE public_id = ?",
                                    String.class,
                                    messagePublicId
                            );

                    assertEquals(
                            "READ",
                            statusAfterRead
                    );
                });


        // ============================================================
        // Step 10: A edits their own message via REST
        // ============================================================

        EditMessageRequest editReq =
                new EditMessageRequest(
                        "Hey B, free for coffee this Friday?"
                );

        mockMvc.perform(
                        patch(
                                "/api/v1/chats/" +
                                convId +
                                "/messages/" +
                                messagePublicId
                        )
                                .header(
                                        "Authorization",
                                        bearerA
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                editReq
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.status")
                                .value("EDITED")
                )
                .andExpect(
                        jsonPath("$.data.content")
                                .value(
                                        "Hey B, free for coffee this Friday?"
                                )
                );


        // ============================================================
        // Step 11: A deletes their message via REST
        // Soft delete
        // ============================================================

        mockMvc.perform(
                        delete(
                                "/api/v1/chats/" +
                                convId +
                                "/messages/" +
                                messagePublicId
                        )
                                .header(
                                        "Authorization",
                                        bearerA
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.status")
                                .value("DELETED")
                )
                .andExpect(
                        jsonPath("$.data.deleted")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.content")
                                .value(
                                        "This message was deleted."
                                )
                );


        // ============================================================
        // Step 12: Verify final database row state in MySQL
        // ============================================================

        String finalStatusInDb =
                jdbcTemplate.queryForObject(
                        "SELECT status " +
                        "FROM messages " +
                        "WHERE public_id = ?",
                        String.class,
                        messagePublicId
                );

        String finalDeletedAtInDb =
                jdbcTemplate.queryForObject(
                        "SELECT deleted_at " +
                        "FROM messages " +
                        "WHERE public_id = ?",
                        String.class,
                        messagePublicId
                );

        assertEquals(
                "DELETED",
                finalStatusInDb
        );

        assertNotNull(finalDeletedAtInDb);


        // ============================================================
        // Cleanup
        // ============================================================

        sessionA.disconnect();
        sessionB.disconnect();
    }
}