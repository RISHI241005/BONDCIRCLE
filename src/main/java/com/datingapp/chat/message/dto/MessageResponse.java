package com.datingapp.chat.message.dto;

import com.datingapp.chat.message.entity.MessageStatus;
import com.datingapp.chat.message.entity.MessageType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Standard message response representation for REST and WebSocket streams.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Message response payload")
public class MessageResponse {

    @Schema(description = "Public UUID of the message", example = "7b8d1b32-8df2-4f1e-9273-df16a7f34c22")
    private final String id;

    @Schema(description = "Public UUID of the conversation", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
    private final String conversationId;

    @Schema(description = "Sender user ID", example = "101")
    private final Long senderId;

    @Schema(description = "Client message ID if provided", example = "client-msg-uuid-999")
    private final String clientMessageId;

    @Schema(description = "Message body content", example = "Hey, are you free tonight?")
    private final String content;

    @Schema(description = "Message type", example = "TEXT")
    private final MessageType type;

    @Schema(description = "Message status", example = "SENT")
    private final MessageStatus status;

    @Schema(description = "Referenced reply message snippet if applicable")
    private final ReplyMessageSnippet replyTo;

    @Schema(description = "Creation timestamp in UTC", example = "2026-08-26T20:16:00.000Z")
    private final Instant createdAt;

    @Schema(description = "Last update timestamp in UTC", example = "2026-08-26T20:16:00.000Z")
    private final Instant updatedAt;

    @Schema(description = "Indicates whether this message has been soft deleted", example = "false")
    private final boolean deleted;

    public MessageResponse(
            String id,
            String conversationId,
            Long senderId,
            String clientMessageId,
            String content,
            MessageType type,
            MessageStatus status,
            ReplyMessageSnippet replyTo,
            Instant createdAt,
            Instant updatedAt,
            boolean deleted) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.clientMessageId = clientMessageId;
        this.content = content;
        this.type = type;
        this.status = status;
        this.replyTo = replyTo;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deleted = deleted;
    }

    public String getId() {
        return id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public String getContent() {
        return content;
    }

    public MessageType getType() {
        return type;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public ReplyMessageSnippet getReplyTo() {
        return replyTo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }
}
