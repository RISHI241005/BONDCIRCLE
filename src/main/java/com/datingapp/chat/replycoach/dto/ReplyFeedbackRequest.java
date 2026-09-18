package com.datingapp.chat.replycoach.dto;

import com.datingapp.chat.replycoach.entity.FeedbackAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ReplyFeedbackRequest {

    @NotBlank(message = "Suggestion ID is required")
    private String suggestionId;

    @NotBlank(message = "Conversation ID is required")
    private String conversationId;

    @NotNull(message = "Action is required")
    private FeedbackAction action;

    private String suggestionText;

    public ReplyFeedbackRequest() {
    }

    public ReplyFeedbackRequest(String suggestionId, String conversationId, FeedbackAction action, String suggestionText) {
        this.suggestionId = suggestionId;
        this.conversationId = conversationId;
        this.action = action;
        this.suggestionText = suggestionText;
    }

    public String getSuggestionId() {
        return suggestionId;
    }

    public void setSuggestionId(String suggestionId) {
        this.suggestionId = suggestionId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public FeedbackAction getAction() {
        return action;
    }

    public void setAction(FeedbackAction action) {
        this.action = action;
    }

    public String getSuggestionText() {
        return suggestionText;
    }

    public void setSuggestionText(String suggestionText) {
        this.suggestionText = suggestionText;
    }
}
