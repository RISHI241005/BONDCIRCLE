package com.datingapp.chat.websocket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Inbound payload sent by Flutter client to /app/chat.delivered.
 */
@Schema(description = "Inbound WebSocket delivery acknowledgment payload")
public class WsDeliveryAckPayload {

    @NotBlank(message = "Conversation ID is required")
    private String conversationId;

    @NotBlank(message = "Message ID is required")
    private String messageId;

    public WsDeliveryAckPayload() {
    }

    public WsDeliveryAckPayload(String conversationId, String messageId) {
        this.conversationId = conversationId;
        this.messageId = messageId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
}
