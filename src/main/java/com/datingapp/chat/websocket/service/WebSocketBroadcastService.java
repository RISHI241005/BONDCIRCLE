package com.datingapp.chat.websocket.service;

import com.datingapp.chat.message.dto.MessageResponse;
import com.datingapp.chat.message.entity.MessageStatus;

import java.util.List;

/**
 * Service for broadcasting realtime events to user-specific STOMP subscription queues.
 */
public interface WebSocketBroadcastService {

    /**
     * Broadcasts a newly sent message to all conversation participants.
     */
    void broadcastNewMessage(String conversationPublicId, MessageResponse message, List<Long> participantUserIds);

    /**
     * Broadcasts typing status to other participants in a conversation.
     */
    void broadcastTyping(String conversationPublicId, Long senderUserId, boolean isTyping, List<Long> recipientUserIds);

    /**
     * Broadcasts a status transition (DELIVERED, READ, EDITED, DELETED) to conversation participants.
     */
    void broadcastMessageStatusUpdate(
            String conversationPublicId,
            String messagePublicId,
            MessageStatus status,
            Long updatedByUserId,
            List<Long> participantUserIds);

    /**
     * Dispatches an asynchronous error event to a specific user's error queue.
     */
    void sendErrorMessageToUser(Long userId, String errorCode, String message);
}
