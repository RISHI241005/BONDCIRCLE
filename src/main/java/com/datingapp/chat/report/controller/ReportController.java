package com.datingapp.chat.report.controller;

import com.datingapp.chat.common.response.ApiResponse;
import com.datingapp.chat.report.dto.CreateReportRequest;
import com.datingapp.chat.report.dto.ReportResponse;
import com.datingapp.chat.report.service.ReportService;
import com.datingapp.chat.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for reporting offensive behavior and messages.
 */
@RestController
@RequestMapping("/api/v1/chats/{conversationId}/reports")
@Tag(name = "Moderation", description = "Reporting and moderation APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    @Operation(summary = "Submit moderation report", description = "Submits a report on a user or message for administrator review.")
    public ResponseEntity<ApiResponse<ReportResponse>> submitReport(
            @PathVariable String conversationId,
            @Valid @RequestBody CreateReportRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        ReportResponse response = reportService.createReport(conversationId, principal.getUserId(), request);
        return new ResponseEntity<>(ApiResponse.success(response, "Report submitted successfully"), HttpStatus.CREATED);
    }
}
