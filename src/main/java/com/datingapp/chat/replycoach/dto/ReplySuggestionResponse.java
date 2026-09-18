package com.datingapp.chat.replycoach.dto;

import java.util.ArrayList;
import java.util.List;

public class ReplySuggestionResponse {

    private String conversationId;
    private List<ReplySuggestionItem> suggestions = new ArrayList<>();
    private ConversationStateDto conversationState;

    public ReplySuggestionResponse() {
    }

    public ReplySuggestionResponse(String conversationId, List<ReplySuggestionItem> suggestions, ConversationStateDto conversationState) {
        this.conversationId = conversationId;
        this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
        this.conversationState = conversationState;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public List<ReplySuggestionItem> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<ReplySuggestionItem> suggestions) {
        this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
    }

    public ConversationStateDto getConversationState() {
        return conversationState;
    }

    public void setConversationState(ConversationStateDto conversationState) {
        this.conversationState = conversationState;
    }
}
