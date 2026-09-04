package com.datingapp.chat.conversation.dto;

import com.datingapp.chat.conversation.entity.ConversationStatus;
import com.datingapp.chat.presence.model.PresenceStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Summary DTO for chat inbox list views.
 * Includes other participant details, online status, and last message content.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Conversation summary representation for chat list")
public class ConversationSummaryResponse {

    @Schema(description = "Public unique UUID of the conversation", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
    private final String conversationId;

    @Schema(description = "User ID of the other chat participant", example = "204")
    private final Long otherParticipantId;

    @Schema(description = "Full name of the other chat participant", example = "John Doe")
    private final String otherUserName;

    @Schema(description = "Phone number of the other chat participant", example = "+1234567890")
    private final String otherUserPhone;

    @Schema(description = "Online status of the other chat participant", example = "ONLINE")
    private final String otherUserOnlineStatus;

    @Schema(description = "Content of the last message in the conversation", example = "Hello there!")
    private final String lastMessageContent;

    @Schema(description = "Conversation status", example = "ACTIVE")
    private final ConversationStatus status;

    @Schema(description = "Number of unread messages for the authenticated user", example = "2")
    private final long unreadCount;

    @Schema(description = "Whether the user muted this conversation", example = "false")
    private final boolean isMuted;

    @Schema(description = "Timestamp of the last message sent", example = "2026-08-26T20:15:30.120Z")
    private final Instant lastMessageAt;

    @Schema(description = "Timestamp when the conversation was last updated", example = "2026-08-26T20:15:30.120Z")
    private final Instant updatedAt;

    public ConversationSummaryResponse(
            String conversationId,
            Long otherParticipantId,
            String otherUserName,
            String otherUserPhone,
            String otherUserOnlineStatus,
            String lastMessageContent,
            ConversationStatus status,
            long unreadCount,
            boolean isMuted,
            Instant lastMessageAt,
            Instant updatedAt) {
        this.conversationId = conversationId;
        this.otherParticipantId = otherParticipantId;
        this.otherUserName = otherUserName;
        this.otherUserPhone = otherUserPhone;
        this.otherUserOnlineStatus = otherUserOnlineStatus;
        this.lastMessageContent = lastMessageContent;
        this.status = status;
        this.unreadCount = unreadCount;
        this.isMuted = isMuted;
        this.lastMessageAt = lastMessageAt;
        this.updatedAt = updatedAt;
    }

    public String getConversationId() {
        return conversationId;
    }

    public Long getOtherParticipantId() {
        return otherParticipantId;
    }

    public String getOtherUserName() {
        return otherUserName;
    }

    public String getOtherUserPhone() {
        return otherUserPhone;
    }

    public String getOtherUserOnlineStatus() {
        return otherUserOnlineStatus;
    }

    public String getLastMessageContent() {
        return lastMessageContent;
    }

    public ConversationStatus getStatus() {
        return status;
    }

    public long getUnreadCount() {
        return unreadCount;
    }

    public boolean isMuted() {
        return isMuted;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
