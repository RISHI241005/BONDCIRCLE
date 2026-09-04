package com.datingapp.chat.websocket.controller;

import com.datingapp.chat.conversation.repository.ConversationParticipantRepository;
import com.datingapp.chat.conversation.service.ConversationService;
import com.datingapp.chat.message.dto.SendMessageRequest;
import com.datingapp.chat.message.entity.MessageType;
import com.datingapp.chat.message.service.MessageService;
import com.datingapp.chat.message.service.ReceiptService;
import com.datingapp.chat.security.StompPrincipal;
import com.datingapp.chat.websocket.dto.WsDeliveryAckPayload;
import com.datingapp.chat.websocket.dto.WsMessagePayload;
import com.datingapp.chat.websocket.dto.WsReadReceiptPayload;
import com.datingapp.chat.websocket.dto.WsTypingPayload;
import com.datingapp.chat.websocket.service.WebSocketBroadcastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketControllerTest {

    @Mock
    private MessageService messageService;

    @Mock
    private ConversationService conversationService;

    @Mock
    private ConversationParticipantRepository participantRepository;

    @Mock
    private WebSocketBroadcastService broadcastService;

    @Mock
    private ReceiptService receiptService;

    private ChatWebSocketController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatWebSocketController(
                messageService,
                conversationService,
                participantRepository,
                broadcastService,
                receiptService
        );
    }

    @Test
    @DisplayName("Should extract sender from StompPrincipal and invoke messageService.sendMessage on chat.send")
    void testHandleSendMessage() {
        StompPrincipal principal = new StompPrincipal(101L);
        WsMessagePayload payload = new WsMessagePayload("conv-uuid-1", "Hello via WebSocket!");
        payload.setClientMessageId("client-ws-1");

        controller.handleSendMessage(payload, principal);

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService, times(1)).sendMessage(eq("conv-uuid-1"), eq(101L), requestCaptor.capture());

        SendMessageRequest captured = requestCaptor.getValue();
        assertEquals("Hello via WebSocket!", captured.getContent());
        assertEquals("client-ws-1", captured.getClientMessageId());
        assertEquals(MessageType.TEXT, captured.getType());
    }

    @Test
    @DisplayName("Should validate participant and broadcast typing status on chat.typing")
    void testHandleTyping() {
        StompPrincipal principal = new StompPrincipal(101L);
        WsTypingPayload payload = new WsTypingPayload("conv-uuid-1", true);

        when(participantRepository.findOtherParticipantUserIds("conv-uuid-1", 101L))
                .thenReturn(List.of(202L));

        controller.handleTyping(payload, principal);

        verify(conversationService, times(1)).validateUserIsParticipant("conv-uuid-1", 101L);
        verify(broadcastService, times(1)).broadcastTyping("conv-uuid-1", 101L, true, List.of(202L));
    }

    @Test
    @DisplayName("Should invoke receiptService.processDeliveryReceipt on chat.delivered")
    void testHandleDeliveryReceipt() {
        StompPrincipal principal = new StompPrincipal(202L);
        WsDeliveryAckPayload payload = new WsDeliveryAckPayload("conv-uuid-1", "msg-uuid-10");

        controller.handleDeliveryReceipt(payload, principal);

        verify(receiptService, times(1)).processDeliveryReceipt("conv-uuid-1", "msg-uuid-10", 202L);
    }

    @Test
    @DisplayName("Should invoke receiptService.processReadReceipt on chat.read")
    void testHandleReadReceipt() {
        StompPrincipal principal = new StompPrincipal(202L);
        WsReadReceiptPayload payload = new WsReadReceiptPayload("conv-uuid-1", "msg-uuid-10");

        controller.handleReadReceipt(payload, principal);

        verify(receiptService, times(1)).processReadReceipt("conv-uuid-1", "msg-uuid-10", 202L);
    }
}
