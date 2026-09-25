package com.datingapp.chat.websocket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Inbound payload sent by Flutter client to /app/chat.typing.
 */
@Schema(description = "Inbound WebSocket typing indicator payload")
public class WsTypingPayload {

    @NotBlank(message = "Conversation ID is required")
    private String conversationId;

    private boolean typing;

    public WsTypingPayload() {
    }

    public WsTypingPayload(String conversationId, boolean typing) {
        this.conversationId = conversationId;
        this.typing = typing;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public boolean isTyping() {
        return typing;
    }

    public void setTyping(boolean typing) {
        this.typing = typing;
    }
}
