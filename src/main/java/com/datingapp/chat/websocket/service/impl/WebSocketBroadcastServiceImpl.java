package com.datingapp.chat.websocket.service.impl;

import com.datingapp.chat.message.dto.MessageResponse;
import com.datingapp.chat.message.entity.MessageStatus;
import com.datingapp.chat.websocket.dto.event.WsEvent;
import com.datingapp.chat.websocket.dto.event.WsEventType;
import com.datingapp.chat.websocket.service.WebSocketBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WebSocketBroadcastServiceImpl implements WebSocketBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(WebSocketBroadcastServiceImpl.class);

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketBroadcastServiceImpl(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void broadcastNewMessage(String conversationPublicId, MessageResponse message, List<Long> participantUserIds) {
        WsEvent<MessageResponse> event = WsEvent.of(WsEventType.NEW_MESSAGE, message);
        for (Long userId : participantUserIds) {
            log.debug("Dispatching new message [{}] to user [{}] queue", message.getId(), userId);
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/messages",
                    event
            );
        }
    }

    @Override
    public void broadcastTyping(String conversationPublicId, Long senderUserId, boolean isTyping, List<Long> recipientUserIds) {
        WsEvent<?> event = WsEvent.typing(conversationPublicId, senderUserId, isTyping);
        for (Long userId : recipientUserIds) {
            log.debug("Dispatching typing event (conv={}, user={}, typing={}) to recipient [{}]",
                    conversationPublicId, senderUserId, isTyping, userId);
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/typing",
                    event
            );
        }
    }

    @Override
    public void broadcastMessageStatusUpdate(
            String conversationPublicId,
            String messagePublicId,
            MessageStatus status,
            Long updatedByUserId,
            List<Long> participantUserIds) {
        WsEvent<?> event = WsEvent.statusUpdate(conversationPublicId, messagePublicId, status.name(), updatedByUserId);
        for (Long userId : participantUserIds) {
            log.debug("Dispatching status update [{}] for message [{}] to user [{}]",
                    status, messagePublicId, userId);
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/messages",
                    event
            );
        }
    }

    @Override
    public void sendErrorMessageToUser(Long userId, String errorCode, String message) {
        WsEvent<?> event = WsEvent.error(errorCode, message);
        log.warn("Dispatching error event [{}] to user [{}]", errorCode, userId);
        messagingTemplate.convertAndSendToUser(
                String.valueOf(userId),
                "/queue/errors",
                event
        );
    }
}
