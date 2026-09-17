package com.datingapp.chat.icebreaker.controller;

import com.datingapp.chat.common.response.ApiResponse;
import com.datingapp.chat.icebreaker.dto.IceBreakerResponse;
import com.datingapp.chat.icebreaker.service.IceBreakerService;
import com.datingapp.chat.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chats/{conversationId}/ice-breakers")
@Tag(name = "Ice breakers", description = "Interest-aware conversation suggestions")
@SecurityRequirement(name = "Bearer Authentication")
public class IceBreakerController {

    private final IceBreakerService iceBreakerService;

    public IceBreakerController(IceBreakerService iceBreakerService) {
        this.iceBreakerService = iceBreakerService;
    }

    @GetMapping
    @Operation(summary = "Generate live conversation replies", description = "Uses the configured AI provider to create private English or Hinglish drafts from recent chat history and interests, with a deterministic fallback when AI is unavailable.")
    public ResponseEntity<ApiResponse<IceBreakerResponse>> getSuggestions(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "12") int limit,
            @RequestParam(defaultValue = "ALL") String tone,
            @RequestParam(defaultValue = "0") int variant,
            @RequestParam(defaultValue = "AUTO") String language,
            @RequestParam(defaultValue = "SUGGEST") String mode,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                iceBreakerService.getSuggestions(
                        conversationId,
                        principal.getUserId(),
                        limit,
                        tone,
                        variant,
                        language,
                        mode)));
    }
}
