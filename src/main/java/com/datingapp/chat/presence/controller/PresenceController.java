package com.datingapp.chat.presence.controller;

import com.datingapp.chat.common.response.ApiResponse;
import com.datingapp.chat.presence.dto.UserPresenceResponse;
import com.datingapp.chat.presence.service.PresenceService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for querying user online presence and last seen state.
 */
@RestController
@RequestMapping("/api/v1/presence")
@Tag(name = "Presence", description = "User presence and last seen APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class PresenceController {

    private final PresenceService presenceService;

    public PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user presence", description = "Returns online status and last seen timestamp for a participant.")
    public ResponseEntity<ApiResponse<UserPresenceResponse>> getUserPresence(
            @PathVariable Long userId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        UserPresenceResponse presence = presenceService.getUserPresence(userId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(presence));
    }
}
