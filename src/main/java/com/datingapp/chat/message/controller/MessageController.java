package com.datingapp.chat.message.controller;

import com.datingapp.chat.common.response.ApiResponse;
import com.datingapp.chat.message.dto.MessageCursorPage;
import com.datingapp.chat.message.dto.MessageResponse;
import com.datingapp.chat.message.dto.SendMessageRequest;
import com.datingapp.chat.message.service.MessageService;
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
 * REST controller for message history queries and HTTP fallback message sending.
 */
@RestController
@RequestMapping("/api/v1/chats/{conversationId}/messages")
@Tag(name = "Messages", description = "Message history and sending APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    @Operation(summary = "Get message history", description = "Retrieves cursor-paginated messages in reverse chronological order.")
    public ResponseEntity<ApiResponse<MessageCursorPage>> getMessageHistory(
            @PathVariable String conversationId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "30") int limit,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        MessageCursorPage history = messageService.getMessageHistory(
                conversationId, principal.getUserId(), cursor, limit);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @PostMapping
    @Operation(summary = "Send message (REST fallback)", description = "Sends a message and stores it persistently in MySQL. Idempotency is supported via clientMessageId.")
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @PathVariable String conversationId,
            @Valid @RequestBody SendMessageRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        MessageResponse response = messageService.sendMessage(conversationId, principal.getUserId(), request);
        return new ResponseEntity<>(ApiResponse.success(response, "Message sent successfully"), HttpStatus.CREATED);
    }

    @PatchMapping("/{messageId}")
    @Operation(summary = "Edit sent message", description = "Allows original sender to update text content of a sent message.")
    public ResponseEntity<ApiResponse<MessageResponse>> editMessage(
            @PathVariable String conversationId,
            @PathVariable String messageId,
            @Valid @RequestBody com.datingapp.chat.message.dto.EditMessageRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        MessageResponse response = messageService.editMessage(conversationId, messageId, principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(response, "Message updated successfully"));
    }

    @DeleteMapping("/{messageId}")
    @Operation(summary = "Delete message", description = "Soft-deletes a message owned by the authenticated sender.")
    public ResponseEntity<ApiResponse<MessageResponse>> deleteMessage(
            @PathVariable String conversationId,
            @PathVariable String messageId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        MessageResponse response = messageService.deleteMessage(conversationId, messageId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response, "Message deleted successfully"));
    }
}
