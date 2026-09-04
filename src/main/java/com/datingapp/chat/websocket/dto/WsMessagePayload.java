package com.datingapp.chat.websocket.dto;

import com.datingapp.chat.message.entity.MessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound payload sent by Flutter client to /app/chat.send.
 */
@Schema(description = "Inbound WebSocket message payload")
public class WsMessagePayload {

    @NotBlank(message = "Conversation ID is required")
    private String conversationId;

    @NotBlank(message = "Message content cannot be blank")
    @Size(min = 1, max = 2000, message = "Message content must be between 1 and 2000 characters")
    private String content;

    private String clientMessageId;

    private String replyToMessageId;

    private MessageType type = MessageType.TEXT;

    public WsMessagePayload() {
    }

    public WsMessagePayload(String conversationId, String content) {
        this.conversationId = conversationId;
        this.content = content;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }

    public String getReplyToMessageId() {
        return replyToMessageId;
    }

    public void setReplyToMessageId(String replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type != null ? type : MessageType.TEXT;
    }
}
