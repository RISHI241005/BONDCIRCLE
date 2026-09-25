package com.datingapp.chat.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for acknowledging read receipts via REST.
 */
@Schema(description = "Read receipt acknowledgment payload")
public class ReadReceiptRequest {

    @NotBlank(message = "Message ID is required")
    @Schema(description = "Public UUID of the last read message", example = "7b8d1b32-8df2-4f1e-9273-df16a7f34c22")
    private String messageId;

    public ReadReceiptRequest() {
    }

    public ReadReceiptRequest(String messageId) {
        this.messageId = messageId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
}
