package com.datingapp.chat.report.service;

import com.datingapp.chat.report.dto.CreateReportRequest;
import com.datingapp.chat.report.dto.ReportResponse;

/**
 * Service handling submission and recording of user moderation reports.
 */
public interface ReportService {

    /**
     * Submits a report against a conversation or a specific message.
     */
    ReportResponse createReport(String conversationPublicId, Long reporterUserId, CreateReportRequest request);
}
