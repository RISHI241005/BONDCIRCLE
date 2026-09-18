package com.datingapp.chat.replycoach.dto;

import java.util.Objects;

public class ReplySuggestionItem {

    private String id;
    private String text;
    private String topic;
    private String tone;
    private String strategy;
    private String style;

    public ReplySuggestionItem() {
    }

    public ReplySuggestionItem(String id, String text) {
        this.id = id;
        this.text = text;
    }

    public ReplySuggestionItem(String id, String text, String topic, String tone) {
        this.id = id;
        this.text = text;
        this.topic = topic;
        this.tone = tone;
        this.strategy = "CONVERSATIONAL";
        this.style = tone != null ? tone : "Casual";
    }

    public ReplySuggestionItem(String id, String text, String topic, String tone, String strategy, String style) {
        this.id = id;
        this.text = text;
        this.topic = topic;
        this.tone = tone;
        this.strategy = strategy;
        this.style = style;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
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

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReplySuggestionItem that = (ReplySuggestionItem) o;
        return Objects.equals(id, that.id) && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, text);
    }
}
