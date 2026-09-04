package com.datingapp.chat.websocket;

import com.datingapp.chat.conversation.dto.ConversationDetailResponse;
import com.datingapp.chat.conversation.service.ConversationService;
import com.datingapp.chat.message.dto.MessageResponse;
import com.datingapp.chat.security.JwtService;
import com.datingapp.chat.testutil.AbstractMySQLIntegrationTest;
import com.datingapp.chat.testutil.UserTestFactory;
import com.datingapp.chat.websocket.dto.WsMessagePayload;
import com.datingapp.chat.websocket.dto.event.WsEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class LiveWebSocketAuthenticationAndMessagingTest extends AbstractMySQLIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ObjectMapper objectMapper;

    private String wsUrl;
    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUpClient() {
        wsUrl = "ws://localhost:" + port + "/ws";
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);
    }

    @Test
    @DisplayName("Should successfully authenticate and establish live STOMP WebSocket session with valid JWT")
    void testLiveStompConnectSuccess() throws Exception {
        String token = UserTestFactory.createRawToken(jwtService, UserTestFactory.USER_A);
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
        stompClient.connectAsync(wsUrl, (org.springframework.web.socket.WebSocketHttpHeaders) null, connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                sessionFuture.complete(session);
            }
        });

        StompSession session = sessionFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(session);
        assertTrue(session.isConnected());
        session.disconnect();
    }

    @Test
    @DisplayName("Should exchange real-time messages over live STOMP connection and persist to MySQL")
    void testLiveRealtimeMessageExchange() throws Exception {
        // 1. Setup conversation between User A and User B
        ConversationDetailResponse conv = conversationService.createOrGetConversation(
                UserTestFactory.USER_A, UserTestFactory.USER_B);
        String conversationId = conv.getConversationId();

        // 2. Connect User B to live STOMP
        String tokenB = UserTestFactory.createRawToken(jwtService, UserTestFactory.USER_B);
        StompHeaders headersB = new StompHeaders();
        headersB.add("Authorization", "Bearer " + tokenB);

        CompletableFuture<StompSession> sessionBFuture = new CompletableFuture<>();
        stompClient.connectAsync(wsUrl, (org.springframework.web.socket.WebSocketHttpHeaders) null, headersB, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                sessionBFuture.complete(session);
            }
        });
        StompSession sessionB = sessionBFuture.get(5, TimeUnit.SECONDS);

        // 3. User B subscribes to /user/queue/messages
        CompletableFuture<WsEvent> receivedMessageFuture = new CompletableFuture<>();
        sessionB.subscribe("/user/queue/messages", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return WsEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedMessageFuture.complete((WsEvent) payload);
            }
        });

        // 4. Connect User A to live STOMP
        String tokenA = UserTestFactory.createRawToken(jwtService, UserTestFactory.USER_A);
        StompHeaders headersA = new StompHeaders();
        headersA.add("Authorization", "Bearer " + tokenA);

        CompletableFuture<StompSession> sessionAFuture = new CompletableFuture<>();
        stompClient.connectAsync(wsUrl, (org.springframework.web.socket.WebSocketHttpHeaders) null, headersA, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                sessionAFuture.complete(session);
            }
        });
        StompSession sessionA = sessionAFuture.get(5, TimeUnit.SECONDS);

        // 5. User A sends message to /app/chat.send
        WsMessagePayload sendPayload = new WsMessagePayload(conversationId, "Realtime STOMP test message!");
        sendPayload.setClientMessageId("live-ws-msg-1");
        sessionA.send("/app/chat.send", sendPayload);

        // 6. User B receives the message over WebSocket
        WsEvent event = receivedMessageFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(event);
        assertEquals("NEW_MESSAGE", event.getEventType().name());

        // 7. Verify message row is stored in MySQL database
        Thread.sleep(300);
        Integer msgCountInDb = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE client_message_id = 'live-ws-msg-1'", Integer.class);
        assertEquals(1, msgCountInDb);

        sessionA.disconnect();
        sessionB.disconnect();
    }
}
