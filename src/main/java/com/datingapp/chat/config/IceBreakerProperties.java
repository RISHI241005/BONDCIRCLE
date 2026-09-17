package com.datingapp.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "chat.icebreaker")
public class IceBreakerProperties {

    private int historyLookback = 80;
    private int defaultSuggestions = 4;
    private int maxSuggestions = 6;
    private int quietAfterHours = 6;

    public int getHistoryLookback() {
        return historyLookback;
    }

    public void setHistoryLookback(int historyLookback) {
        this.historyLookback = historyLookback;
    }

    public int getDefaultSuggestions() {
        return defaultSuggestions;
    }

    public void setDefaultSuggestions(int defaultSuggestions) {
        this.defaultSuggestions = defaultSuggestions;
    }

    public int getMaxSuggestions() {
        return maxSuggestions;
    }

    public void setMaxSuggestions(int maxSuggestions) {
        this.maxSuggestions = maxSuggestions;
    }

    public int getQuietAfterHours() {
        return quietAfterHours;
    }

    public void setQuietAfterHours(int quietAfterHours) {
        this.quietAfterHours = quietAfterHours;
    }
}
