package com.datingapp.chat.moderation.controller;

import com.datingapp.chat.common.response.ApiResponse;
import com.datingapp.chat.moderation.dto.ModerationCheckRequest;
import com.datingapp.chat.moderation.dto.ModerationResult;
import com.datingapp.chat.moderation.service.LanguageModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/moderation")
@Tag(name = "Moderation", description = "Pre-send message safety checks")
@SecurityRequirement(name = "Bearer Authentication")
public class ModerationController {

    private final LanguageModerationService moderationService;

    public ModerationController(LanguageModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @PostMapping("/check")
    @Operation(summary = "Check a draft message for common English and Hinglish abusive language")
    public ResponseEntity<ApiResponse<ModerationResult>> check(@Valid @RequestBody ModerationCheckRequest request) {
        return ResponseEntity.ok(ApiResponse.success(moderationService.analyze(request.getContent())));
    }
}
