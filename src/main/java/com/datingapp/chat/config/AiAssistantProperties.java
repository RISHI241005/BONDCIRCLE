package com.datingapp.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "chat.ai-assistant")
public class AiAssistantProperties {

    private boolean enabled = true;
    private String apiKey = "";
    private String baseUrl = "https://ai-gateway.vercel.sh/v1";
    private String model = "openai/gpt-5.4-mini";
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
}
