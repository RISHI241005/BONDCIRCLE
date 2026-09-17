package com.datingapp.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "chat.ai-assistant")
public class AiAssistantProperties {

    private boolean enabled = true;
    private String apiKey = "";
    private String baseUrl = "https://api.openai.com/v1";
    private String model = "gpt-4o-mini";
    private int timeoutSeconds = 25;
    private int historyMessages = 40;
    private int maxSuggestions = 12;
    private int maxOutputTokens = 1400;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getHistoryMessages() {
        return historyMessages;
    }

    public void setHistoryMessages(int historyMessages) {
        this.historyMessages = historyMessages;
    }

    public int getMaxSuggestions() {
        return maxSuggestions;
    }

    public void setMaxSuggestions(int maxSuggestions) {
        this.maxSuggestions = maxSuggestions;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public String resolveBaseUrl(String provider) {
        if (provider == null || provider.isBlank()) return baseUrl;
        return switch (provider.toUpperCase(java.util.Locale.ROOT)) {
            case "GEMINI" -> "https://generativelanguage.googleapis.com/v1beta/openai";
            case "GROQ" -> "https://api.groq.com/openai/v1";
            case "OPENAI" -> "https://api.openai.com/v1";
            default -> baseUrl;
        };
    }

    public String resolveModel(String provider, String requestedModel) {
        if (requestedModel != null && !requestedModel.isBlank()) {
            return requestedModel.trim();
        }
        if (provider == null || provider.isBlank()) return model;
        return switch (provider.toUpperCase(java.util.Locale.ROOT)) {
            case "GEMINI" -> "gemini-1.5-flash";
            case "GROQ" -> "llama-3.3-70b-versatile";
            case "OPENAI" -> "gpt-4o-mini";
            default -> model;
        };
    }
}
