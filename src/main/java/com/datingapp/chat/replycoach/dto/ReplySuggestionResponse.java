package com.datingapp.chat.replycoach.dto;

import java.util.ArrayList;
import java.util.List;

public class ReplySuggestionResponse {

    private String generationId;
    private String conversationId;
    private List<ReplySuggestionItem> suggestions = new ArrayList<>();
    private ConversationStateDto conversationState;

    public ReplySuggestionResponse() {
    }

    public ReplySuggestionResponse(String conversationId, List<ReplySuggestionItem> suggestions, ConversationStateDto conversationState) {
        this("gen_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12), conversationId, suggestions, conversationState);
    }

    public ReplySuggestionResponse(String generationId, String conversationId, List<ReplySuggestionItem> suggestions, ConversationStateDto conversationState) {
        this.generationId = generationId != null ? generationId : "gen_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        this.conversationId = conversationId;
        this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
        this.conversationState = conversationState;
    }

    public String getGenerationId() {
        return generationId;
    }

    public void setGenerationId(String generationId) {
        this.generationId = generationId;
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
