package com.datingapp.chat.replycoach.controller;

import com.datingapp.chat.common.response.ApiResponse;
import com.datingapp.chat.replycoach.dto.ReplyFeedbackRequest;
import com.datingapp.chat.replycoach.dto.ReplyRegenerateRequest;
import com.datingapp.chat.replycoach.dto.ReplySuggestionRequest;
import com.datingapp.chat.replycoach.dto.ReplySuggestionResponse;
import com.datingapp.chat.replycoach.service.ReplyCoachService;
import com.datingapp.chat.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping({"/api/ai/reply-suggestions", "/api/v1/ai/reply-suggestions"})
@Tag(name = "AI Reply Coach", description = "Context-aware conversational suggestions and reply coaching")
@SecurityRequirement(name = "Bearer Authentication")
public class ReplyCoachController {

    private final ReplyCoachService replyCoachService;

    public ReplyCoachController(ReplyCoachService replyCoachService) {
        this.replyCoachService = replyCoachService;
    }

    @PostMapping
    @Operation(summary = "Generate reply suggestions", description = "Analyzes two-sided conversation context and generates up to 3 natural, ready-to-send replies.")
    public ResponseEntity<ApiResponse<ReplySuggestionResponse>> getReplySuggestions(
            @Valid @RequestBody ReplySuggestionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        ReplySuggestionResponse response = replyCoachService.getReplySuggestions(
                request.getConversationId(),
                principal.getUserId(),
                request.getLimit()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/regenerate")
    @Operation(summary = "Regenerate reply suggestions", description = "Produces up to 3 new suggestions while strictly avoiding previously rejected suggestions.")
    public ResponseEntity<ApiResponse<ReplySuggestionResponse>> regenerateSuggestions(
            @Valid @RequestBody ReplyRegenerateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        ReplySuggestionResponse response = replyCoachService.regenerateReplySuggestions(
                request.getConversationId(),
                principal.getUserId(),
                request.getRejectedSuggestionIds(),
                request.getRejectedTexts(),
                request.getLimit()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/feedback")
    @Operation(summary = "Record suggestion feedback", description = "Tracks user interaction (SHOWN, USED, REJECTED, COPIED, EDITED, SENT) for personalization.")
    public ResponseEntity<ApiResponse<Map<String, String>>> recordFeedback(
            @Valid @RequestBody ReplyFeedbackRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        replyCoachService.recordFeedback(principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "status", "recorded",
                "suggestionId", request.getSuggestionId(),
                "action", request.getAction().name()
        )));
    }
}
