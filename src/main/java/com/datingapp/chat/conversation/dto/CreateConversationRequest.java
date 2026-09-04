package com.datingapp.chat.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request payload to initiate a conversation with another user.
 */
@Schema(description = "Initiate conversation payload")
public class CreateConversationRequest {

    @NotNull(message = "Participant ID is required")
    @Positive(message = "Participant ID must be a positive integer")
    @Schema(description = "External user ID of the other chat participant", example = "204")
    private Long participantId;

    public CreateConversationRequest() {
    }

    public CreateConversationRequest(Long participantId) {
        this.participantId = participantId;
    }

    public Long getParticipantId() {
        return participantId;
    }

    public void setParticipantId(Long participantId) {
        this.participantId = participantId;
    }
}
