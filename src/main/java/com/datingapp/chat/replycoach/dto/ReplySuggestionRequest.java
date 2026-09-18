package com.datingapp.chat.replycoach.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class ReplySuggestionRequest {

    @NotBlank(message = "Conversation ID is required")
    private String conversationId;

    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 3, message = "Limit cannot exceed 3")
    private int limit = 3;

    public ReplySuggestionRequest() {
    }

    public ReplySuggestionRequest(String conversationId, int limit) {
        this.conversationId = conversationId;
        this.limit = limit > 0 && limit <= 3 ? limit : 3;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit > 0 && limit <= 3 ? limit : 3;
    }
}
