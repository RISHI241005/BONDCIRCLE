package com.datingapp.chat.websocket.service;

import com.datingapp.chat.message.dto.MessageResponse;
import com.datingapp.chat.message.entity.MessageStatus;
import com.datingapp.chat.message.entity.MessageType;
import com.datingapp.chat.websocket.dto.event.WsEvent;
import com.datingapp.chat.websocket.service.impl.WebSocketBroadcastServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebSocketBroadcastServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private WebSocketBroadcastService broadcastService;

    @BeforeEach
    void setUp() {
        broadcastService = new WebSocketBroadcastServiceImpl(messagingTemplate);
    }

    @Test
    @DisplayName("Should broadcast new message to each participant's user queue")
    void testBroadcastNewMessage() {
        MessageResponse msg = new MessageResponse(
                "msg-uuid-1", "conv-uuid-1", 101L, "client-id", "Hi",
                MessageType.TEXT, MessageStatus.SENT, null, Instant.now(), Instant.now(), false
        );
        List<Long> participants = List.of(101L, 202L);

        broadcastService.broadcastNewMessage("conv-uuid-1", msg, participants);

        ArgumentCaptor<WsEvent<?>> eventCaptor = ArgumentCaptor.forClass(WsEvent.class);
        verify(messagingTemplate, times(1)).convertAndSendToUser(eq("101"), eq("/queue/messages"), eventCaptor.capture());
        verify(messagingTemplate, times(1)).convertAndSendToUser(eq("202"), eq("/queue/messages"), eventCaptor.capture());

        assertNotNull(eventCaptor.getValue());
    }

    @Test
    @DisplayName("Should broadcast typing event to target recipients")
    void testBroadcastTyping() {
        broadcastService.broadcastTyping("conv-uuid-1", 101L, true, List.of(202L));

        verify(messagingTemplate, times(1)).convertAndSendToUser(
                eq("202"), eq("/queue/typing"), org.mockito.ArgumentMatchers.any(WsEvent.class));
    }
}
