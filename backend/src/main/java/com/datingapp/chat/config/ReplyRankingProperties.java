package com.datingapp.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configurable scoring weights for reply candidate ranking. */
@Configuration
@ConfigurationProperties(prefix = "chat.ai-assistant.ranking")
public class ReplyRankingProperties {

    private double contextRelevance = 0.25;
    private double userStyleMatch = 0.15;
    private double languageMatch = 0.10;
    private double toneMatch = 0.10;
    private double strategyFit = 0.10;
    private double engagementPotential = 0.10;
    private double personalization = 0.10;
    private double novelty = 0.05;
    private double feedbackPreference = 0.05;

    public double getContextRelevance() { return contextRelevance; }
    public void setContextRelevance(double value) { contextRelevance = nonNegative(value); }
    public double getUserStyleMatch() { return userStyleMatch; }
    public void setUserStyleMatch(double value) { userStyleMatch = nonNegative(value); }
    public double getLanguageMatch() { return languageMatch; }
    public void setLanguageMatch(double value) { languageMatch = nonNegative(value); }
    public double getToneMatch() { return toneMatch; }
    public void setToneMatch(double value) { toneMatch = nonNegative(value); }
    public double getStrategyFit() { return strategyFit; }
    public void setStrategyFit(double value) { strategyFit = nonNegative(value); }
    public double getEngagementPotential() { return engagementPotential; }
    public void setEngagementPotential(double value) { engagementPotential = nonNegative(value); }
    public double getPersonalization() { return personalization; }
    public void setPersonalization(double value) { personalization = nonNegative(value); }
    public double getNovelty() { return novelty; }
    public void setNovelty(double value) { novelty = nonNegative(value); }
    public double getFeedbackPreference() { return feedbackPreference; }
    public void setFeedbackPreference(double value) { feedbackPreference = nonNegative(value); }

    public double totalWeight() {
        return contextRelevance + userStyleMatch + languageMatch + toneMatch + strategyFit
                + engagementPotential + personalization + novelty + feedbackPreference;
    }

    private double nonNegative(double value) {
        return Math.max(0.0, value);
    }
}
