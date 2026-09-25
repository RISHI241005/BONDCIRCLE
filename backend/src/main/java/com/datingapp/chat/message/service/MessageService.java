package com.datingapp.chat.message.service;

import com.datingapp.chat.message.dto.MessageCursorPage;
import com.datingapp.chat.message.dto.MessageResponse;
import com.datingapp.chat.message.dto.SendMessageRequest;
import com.datingapp.chat.message.entity.Message;

/**
 * Service managing message lifecycle, ingestion, cursor history retrieval, and validation.
 */
public interface MessageService {

    /**
     * Sends a new message within a conversation (or returns existing message if clientMessageId repeated).
     */
    MessageResponse sendMessage(String conversationPublicId, Long senderId, SendMessageRequest request);

    /**
     * Retrieves cursor-paginated message history in reverse chronological order.
     */
    MessageCursorPage getMessageHistory(String conversationPublicId, Long userId, String cursor, int limit);

    /**
     * Finds internal Message JPA entity by public UUID.
     */
    Message getMessageEntity(String messagePublicId);

    /**
     * Edits content of an existing message owned by the sender.
     */
    MessageResponse editMessage(
            String conversationPublicId,
            String messagePublicId,
            Long userId,
            com.datingapp.chat.message.dto.EditMessageRequest request);

    /**
     * Soft-deletes a message owned by the sender.
     */
    MessageResponse deleteMessage(String conversationPublicId, String messagePublicId, Long userId);

    /**
     * Maps Message entity to client DTO.
     */
    MessageResponse mapToResponse(Message message);
}
