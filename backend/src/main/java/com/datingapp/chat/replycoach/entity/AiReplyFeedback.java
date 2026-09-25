package com.datingapp.chat.replycoach.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "ai_reply_feedback", indexes = {
        @Index(name = "idx_feedback_user_action", columnList = "user_id, action, created_at DESC"),
        @Index(name = "idx_feedback_conv", columnList = "conversation_id")
})
public class AiReplyFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "conversation_id", nullable = false, length = 36)
    private String conversationId;

    @Column(name = "suggestion_id", nullable = false, length = 36)
    private String suggestionId;

    @Column(name = "suggestion_text", nullable = false, length = 1000)
    private String suggestionText;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private FeedbackAction action;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AiReplyFeedback() {
    }

    public AiReplyFeedback(Long userId, String conversationId, String suggestionId, String suggestionText, FeedbackAction action) {
        this.userId = userId;
        this.conversationId = conversationId;
        this.suggestionId = suggestionId;
        this.suggestionText = suggestionText;
        this.action = action;
    }

    @PrePersist
    public void onPrePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getSuggestionId() {
        return suggestionId;
    }

    public void setSuggestionId(String suggestionId) {
        this.suggestionId = suggestionId;
    }

    public String getSuggestionText() {
        return suggestionText;
    }

    public void setSuggestionText(String suggestionText) {
        this.suggestionText = suggestionText;
    }

    public FeedbackAction getAction() {
        return action;
    }

    public void setAction(FeedbackAction action) {
        this.action = action;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AiReplyFeedback that = (AiReplyFeedback) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
