package com.datingapp.chat.conversation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Participant response representation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Participant details")
public class ParticipantResponse {

    @Schema(description = "User ID", example = "204")
    private final Long userId;

    @Schema(description = "Timestamp when user joined the conversation", example = "2026-08-26T19:00:00.000Z")
    private final Instant joinedAt;

    @Schema(description = "Whether the user muted notifications for this chat", example = "false")
    private final boolean isMuted;

    @Schema(description = "Whether the user archived this chat", example = "false")
    private final boolean isArchived;

    public ParticipantResponse(Long userId, Instant joinedAt, boolean isMuted, boolean isArchived) {
        this.userId = userId;
        this.joinedAt = joinedAt;
        this.isMuted = isMuted;
        this.isArchived = isArchived;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public boolean isMuted() {
        return isMuted;
    }

    public boolean isArchived() {
        return isArchived;
    }
}
