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
    @Operation(summary = "Suggest conversation starters", description = "Returns three private draft suggestions based on participant interests and recent conversation shape.")
    public ResponseEntity<ApiResponse<IceBreakerResponse>> getSuggestions(
            @PathVariable String conversationId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                iceBreakerService.getSuggestions(conversationId, principal.getUserId())));
    }
}
