package com.datingapp.chat.replycoach.dto;

public class ConversationStateDto {

    private String topic;
    private String tone;
    private String engagement;
    private String language;
    private boolean dry;

    public ConversationStateDto() {
    }

    public ConversationStateDto(String topic, String tone, String engagement, String language, boolean dry) {
        this.topic = topic;
        this.tone = tone;
        this.engagement = engagement;
        this.language = language;
        this.dry = dry;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getTone() {
        return tone;
    }

    public void setTone(String tone) {
        this.tone = tone;
    }

    public String getEngagement() {
        return engagement;
    }

    public void setEngagement(String engagement) {
        this.engagement = engagement;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public boolean isDry() {
        return dry;
    }

    public void setDry(boolean dry) {
        this.dry = dry;
    }
}
