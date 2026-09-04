package com.datingapp.chat.conversation.controller;

import com.datingapp.chat.common.response.ApiResponse;
import com.datingapp.chat.conversation.dto.ConversationDetailResponse;
import com.datingapp.chat.conversation.dto.ConversationSummaryResponse;
import com.datingapp.chat.conversation.dto.CreateConversationRequest;
import com.datingapp.chat.conversation.service.ConversationService;
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

import java.util.List;

/**
 * REST controller for managing conversations and participant interactions.
 */
@RestController
@RequestMapping("/api/v1/chats")
@Tag(name = "Conversations", description = "Conversation management APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class ConversationController {

    private final ConversationService conversationService;
    private final com.datingapp.chat.message.service.ReceiptService receiptService;

    public ConversationController(
            ConversationService conversationService,
            com.datingapp.chat.message.service.ReceiptService receiptService) {
        this.conversationService = conversationService;
        this.receiptService = receiptService;
    }

    @GetMapping
    @Operation(summary = "Get user's conversations", description = "Retrieves all active conversations for the authenticated user.")
    public ResponseEntity<ApiResponse<List<ConversationSummaryResponse>>> getMyConversations(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        List<ConversationSummaryResponse> conversations = conversationService.getUserConversations(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(conversations));
    }

    @GetMapping("/{conversationId}")
    @Operation(summary = "Get conversation details", description = "Returns metadata and participants for a specific conversation.")
    public ResponseEntity<ApiResponse<ConversationDetailResponse>> getConversationDetails(
            @PathVariable String conversationId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        ConversationDetailResponse details = conversationService.getConversationDetails(conversationId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(details));
    }

    @PostMapping
    @Operation(summary = "Create or get conversation", description = "Initiates a new conversation with a matched user or returns existing.")
    public ResponseEntity<ApiResponse<ConversationDetailResponse>> createOrGetConversation(
            @Valid @RequestBody CreateConversationRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        ConversationDetailResponse response = conversationService.createOrGetConversation(
                principal.getUserId(), request.getParticipantId());
        return new ResponseEntity<>(ApiResponse.success(response, "Conversation ready"), HttpStatus.OK);
    }

    @DeleteMapping("/{conversationId}")
    @Operation(summary = "Leave/Archive conversation", description = "Archives the conversation for the authenticated user.")
    public ResponseEntity<ApiResponse<Void>> leaveOrArchiveConversation(
            @PathVariable String conversationId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        conversationService.leaveOrArchiveConversation(conversationId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null, "Conversation archived successfully"));
    }

    @PostMapping("/{conversationId}/read")
    @Operation(summary = "Mark messages as read (REST fallback)", description = "Updates read watermark and marks messages as READ up to the specified message ID.")
    public ResponseEntity<ApiResponse<Void>> markMessagesAsRead(
            @PathVariable String conversationId,
            @Valid @RequestBody com.datingapp.chat.message.dto.ReadReceiptRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        receiptService.processReadReceipt(conversationId, request.getMessageId(), principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null, "Messages marked as read successfully"));
    }
}
