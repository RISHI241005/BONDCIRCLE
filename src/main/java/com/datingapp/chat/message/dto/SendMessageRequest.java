package com.datingapp.chat.message.dto;

import com.datingapp.chat.message.entity.MessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for sending a chat message.
 */
@Schema(description = "Send message payload")
public class SendMessageRequest {

    @NotBlank(message = "Message content cannot be blank")
    @Size(min = 1, max = 2000, message = "Message content must be between 1 and 2000 characters")
    @Schema(description = "Text content of the message", example = "Hey, are you free tonight?")
    private String content;

    @Schema(description = "Optional client-generated UUID for idempotency / offline queue deduplication", example = "client-msg-uuid-999")
    private String clientMessageId;

    @Schema(description = "Optional public UUID of the message being replied to", example = "7b8d1b32-8df2-4f1e-9273-df16a7f34c22")
    private String replyToMessageId;

    @Schema(description = "Message type", example = "TEXT", defaultValue = "TEXT")
    private MessageType type = MessageType.TEXT;

    public SendMessageRequest() {
    }

    public SendMessageRequest(String content) {
        this.content = content;
    }

    public SendMessageRequest(String content, String clientMessageId, String replyToMessageId) {
        this.content = content;
        this.clientMessageId = clientMessageId;
        this.replyToMessageId = replyToMessageId;
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
