package com.datingapp.chat.websocket.controller;

import com.datingapp.chat.common.exception.BaseException;
import com.datingapp.chat.common.exception.ErrorCode;
import com.datingapp.chat.conversation.repository.ConversationParticipantRepository;
import com.datingapp.chat.conversation.service.ConversationService;
import com.datingapp.chat.message.dto.SendMessageRequest;
import com.datingapp.chat.message.service.MessageService;
import com.datingapp.chat.security.StompPrincipal;
import com.datingapp.chat.websocket.dto.WsMessagePayload;
import com.datingapp.chat.websocket.dto.WsTypingPayload;
import com.datingapp.chat.websocket.service.WebSocketBroadcastService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;

/**
 * Controller handling inbound STOMP messages (/app/chat.send, /app/chat.typing).
 */
@Controller
public class ChatWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketController.class);

    private final MessageService messageService;
    private final ConversationService conversationService;
    private final ConversationParticipantRepository participantRepository;
    private final WebSocketBroadcastService broadcastService;
    private final com.datingapp.chat.message.service.ReceiptService receiptService;

    public ChatWebSocketController(
            MessageService messageService,
            ConversationService conversationService,
            ConversationParticipantRepository participantRepository,
            WebSocketBroadcastService broadcastService,
            com.datingapp.chat.message.service.ReceiptService receiptService) {
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.participantRepository = participantRepository;
        this.broadcastService = broadcastService;
        this.receiptService = receiptService;
    }

    /**
     * Inbound handler for realtime message sending: /app/chat.send
     */
    @MessageMapping("/chat.send")
    public void handleSendMessage(@Valid @Payload WsMessagePayload payload, Principal principal) {
        Long senderId = extractUserId(principal);
        log.info("Received WebSocket chat.send from user {} for conversation {}", senderId, payload.getConversationId());

        SendMessageRequest request = new SendMessageRequest();
        request.setContent(payload.getContent());
        request.setClientMessageId(payload.getClientMessageId());
        request.setReplyToMessageId(payload.getReplyToMessageId());
        request.setType(payload.getType());

        messageService.sendMessage(payload.getConversationId(), senderId, request);
    }

    /**
     * Inbound handler for realtime delivery acknowledgment: /app/chat.delivered
     */
    @MessageMapping("/chat.delivered")
    public void handleDeliveryReceipt(@Valid @Payload com.datingapp.chat.websocket.dto.WsDeliveryAckPayload payload, Principal principal) {
        Long recipientId = extractUserId(principal);
        log.debug("Received WebSocket chat.delivered from user {} for message {} in conv {}",
                recipientId, payload.getMessageId(), payload.getConversationId());
        receiptService.processDeliveryReceipt(payload.getConversationId(), payload.getMessageId(), recipientId);
    }

    /**
     * Inbound handler for realtime read receipt: /app/chat.read
     */
    @MessageMapping("/chat.read")
    public void handleReadReceipt(@Valid @Payload com.datingapp.chat.websocket.dto.WsReadReceiptPayload payload, Principal principal) {
        Long readerId = extractUserId(principal);
        log.debug("Received WebSocket chat.read from user {} for message {} in conv {}",
                readerId, payload.getMessageId(), payload.getConversationId());
        receiptService.processReadReceipt(payload.getConversationId(), payload.getMessageId(), readerId);
    }

    /**
     * Inbound handler for realtime typing indicator: /app/chat.typing
     */
    @MessageMapping("/chat.typing")
    public void handleTyping(@Valid @Payload WsTypingPayload payload, Principal principal) {
        Long senderId = extractUserId(principal);
        log.debug("Received WebSocket chat.typing from user {} in conv {} (typing={})",
                senderId, payload.getConversationId(), payload.isTyping());

        // Verify sender is an active participant in this conversation
        conversationService.validateUserIsParticipant(payload.getConversationId(), senderId);

        // Find recipient participants to notify
        List<Long> otherParticipantIds = participantRepository.findOtherParticipantUserIds(
                payload.getConversationId(), senderId);

        // Broadcast ephemeral typing event to conversation partners
        broadcastService.broadcastTyping(payload.getConversationId(), senderId, payload.isTyping(), otherParticipantIds);
    }

    @MessageExceptionHandler
    public void handleException(Exception ex, Principal principal) {
        Long userId = (principal != null) ? extractUserId(principal) : null;
        log.error("Exception occurred during WebSocket frame processing for user {}: {}", userId, ex.getMessage(), ex);

        if (userId != null) {
            String errorCode = (ex instanceof BaseException baseEx)
                    ? baseEx.getErrorCode().getCode()
                    : ErrorCode.INTERNAL_SERVER_ERROR.getCode();
            broadcastService.sendErrorMessageToUser(userId, errorCode, ex.getMessage());
        }
    }

    private Long extractUserId(Principal principal) {
        if (principal instanceof StompPrincipal stompPrincipal) {
            return stompPrincipal.getUserId();
        }
        return Long.parseLong(principal.getName());
    }
}
