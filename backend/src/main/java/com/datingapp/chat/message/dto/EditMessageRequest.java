package com.datingapp.chat.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound payload to edit an existing message.
 */
@Schema(description = "Edit message payload")
public class EditMessageRequest {

    @NotBlank(message = "Updated content cannot be blank")
    @Size(min = 1, max = 2000, message = "Updated content must be between 1 and 2000 characters")
    @Schema(description = "New message text content", example = "Hey, are you free at 7:30?")
    private String content;

    @Schema(description = "Explicit confirmation to save text that was flagged by the safety check", defaultValue = "false")
    private boolean moderationOverride;

    public EditMessageRequest() {
    }

    public EditMessageRequest(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isModerationOverride() {
        return moderationOverride;
    }

    public void setModerationOverride(boolean moderationOverride) {
        this.moderationOverride = moderationOverride;
    }
}
