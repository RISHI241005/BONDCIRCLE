package com.datingapp.chat.report.dto;

import com.datingapp.chat.report.entity.ReportReason;
import com.datingapp.chat.report.entity.ReportStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Moderation report response representation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Report submission response")
public class ReportResponse {

    @Schema(description = "Public UUID of created report", example = "rep-9988-7766")
    private final String reportId;

    @Schema(description = "Public UUID of conversation", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
    private final String conversationId;

    @Schema(description = "User ID who created the report", example = "101")
    private final Long reporterUserId;

    @Schema(description = "User ID being reported", example = "204")
    private final Long reportedUserId;

    @Schema(description = "Report reason", example = "HARASSMENT")
    private final ReportReason reason;

    @Schema(description = "Review status", example = "PENDING")
    private final ReportStatus status;

    @Schema(description = "Submission timestamp in UTC", example = "2026-08-26T20:19:00.000Z")
    private final Instant createdAt;

    public ReportResponse(
            String reportId,
            String conversationId,
            Long reporterUserId,
            Long reportedUserId,
            ReportReason reason,
            ReportStatus status,
            Instant createdAt) {
        this.reportId = reportId;
        this.conversationId = conversationId;
        this.reporterUserId = reporterUserId;
        this.reportedUserId = reportedUserId;
        this.reason = reason;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getReportId() {
        return reportId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public Long getReporterUserId() {
        return reporterUserId;
    }

    public Long getReportedUserId() {
        return reportedUserId;
    }

    public ReportReason getReason() {
        return reason;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
