package com.datingapp.chat.conversation.dto;

import com.datingapp.chat.conversation.entity.ConversationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Detailed conversation response representation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Detailed conversation representation")
public class ConversationDetailResponse {

    @Schema(description = "Public unique UUID of the conversation", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
    private final String conversationId;

    @Schema(description = "Conversation status", example = "ACTIVE")
    private final ConversationStatus status;

    @Schema(description = "Timestamp when the conversation was created", example = "2026-08-26T19:00:00.000Z")
    private final Instant createdAt;

    @Schema(description = "Timestamp when the conversation was last updated", example = "2026-08-26T20:15:30.120Z")
    private final Instant updatedAt;

    @Schema(description = "List of conversation participants")
    private final List<ParticipantResponse> participants;

    public ConversationDetailResponse(
            String conversationId,
            ConversationStatus status,
            Instant createdAt,
            Instant updatedAt,
            List<ParticipantResponse> participants) {
        this.conversationId = conversationId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.participants = participants;
    }

    public String getConversationId() {
        return conversationId;
    }

    public ConversationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<ParticipantResponse> getParticipants() {
        return participants;
    }
}
