package com.datingapp.chat.report.dto;

import com.datingapp.chat.report.entity.ReportReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Inbound payload to submit a moderation report.
 */
@Schema(description = "Moderation report submission payload")
public class CreateReportRequest {

    @Schema(description = "Optional public UUID of the offending message", example = "7b8d1b32-8df2-4f1e-9273-df16a7f34c22")
    private String messageId;

    @NotNull(message = "Report reason is required")
    @Schema(description = "Reason category for report", example = "HARASSMENT")
    private ReportReason reason;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    @Schema(description = "Optional description details", example = "Inappropriate and threatening comments made.")
    private String description;

    public CreateReportRequest() {
    }

    public CreateReportRequest(String messageId, ReportReason reason, String description) {
        this.messageId = messageId;
        this.reason = reason;
        this.description = description;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public ReportReason getReason() {
        return reason;
    }

    public void setReason(ReportReason reason) {
        this.reason = reason;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
