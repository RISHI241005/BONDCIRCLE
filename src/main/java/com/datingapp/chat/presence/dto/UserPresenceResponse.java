package com.datingapp.chat.presence.dto;

import com.datingapp.chat.presence.model.PresenceStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * User presence DTO returned to Flutter clients.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "User presence status and last seen timestamp")
public class UserPresenceResponse {

    @Schema(description = "User ID", example = "204")
    private final Long userId;

    @Schema(description = "Online status", example = "ONLINE")
    private final PresenceStatus status;

    @Schema(description = "Last seen timestamp in UTC (null if user is currently online)", example = "2026-08-26T20:15:30.120Z")
    private final Instant lastSeenAt;

    public UserPresenceResponse(Long userId, PresenceStatus status, Instant lastSeenAt) {
        this.userId = userId;
        this.status = status;
        this.lastSeenAt = lastSeenAt;
    }

    public Long getUserId() {
        return userId;
    }

    public PresenceStatus getStatus() {
        return status;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
