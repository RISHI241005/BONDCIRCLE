package com.datingapp.chat.replycoach.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

public class ReplyRegenerateRequest {

    @NotBlank(message = "Conversation ID is required")
    private String conversationId;

    private List<String> rejectedSuggestionIds = new ArrayList<>();

    private List<String> rejectedTexts = new ArrayList<>();

    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 3, message = "Limit cannot exceed 3")
    private int limit = 3;

    public ReplyRegenerateRequest() {
    }

    public ReplyRegenerateRequest(String conversationId, List<String> rejectedSuggestionIds, int limit) {
        this.conversationId = conversationId;
        this.rejectedSuggestionIds = rejectedSuggestionIds != null ? rejectedSuggestionIds : new ArrayList<>();
        this.limit = limit > 0 && limit <= 3 ? limit : 3;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public List<String> getRejectedSuggestionIds() {
        return rejectedSuggestionIds;
    }

    public void setRejectedSuggestionIds(List<String> rejectedSuggestionIds) {
        this.rejectedSuggestionIds = rejectedSuggestionIds != null ? rejectedSuggestionIds : new ArrayList<>();
    }

    public List<String> getRejectedTexts() {
        return rejectedTexts;
    }

    public void setRejectedTexts(List<String> rejectedTexts) {
        this.rejectedTexts = rejectedTexts != null ? rejectedTexts : new ArrayList<>();
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit > 0 && limit <= 3 ? limit : 3;
    }
}
