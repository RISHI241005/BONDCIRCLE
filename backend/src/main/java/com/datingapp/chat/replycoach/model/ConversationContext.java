package com.datingapp.chat.replycoach.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

public record ConversationContext(
        String conversationId,
        Long currentUserId,
        Long partnerUserId,
        String currentUserInterests,
        String partnerInterests,
        List<ContextMessage> messages,
        List<String> longTermMemories
) {
    public ConversationContext(
            String conversationId,
            Long currentUserId,
            Long partnerUserId,
            String currentUserInterests,
            String partnerInterests,
            List<ContextMessage> messages) {
        this(conversationId, currentUserId, partnerUserId, currentUserInterests, partnerInterests, messages, Collections.emptyList());
    }

    public ConversationContext(
            String conversationId,
            Long currentUserId,
            Long partnerUserId,
            List<ContextMessage> messages,
            String currentUserInterests,
            String partnerInterests) {
        this(conversationId, currentUserId, partnerUserId, currentUserInterests, partnerInterests, messages, Collections.emptyList());
    }

    public record ContextMessage(
            Long id,
            String publicId,
            Long senderId,
            String content,
            boolean isCurrentUser,
            Instant createdAt
    ) {
        public ContextMessage(String publicId, boolean isCurrentUser, String content, Instant createdAt) {
            this(null, publicId, null, content, isCurrentUser, createdAt);
        }

        public ContextMessage(Long id, boolean isCurrentUser, String content, Instant createdAt) {
            this(id, id != null ? id.toString() : null, null, content, isCurrentUser, createdAt);
        }
    }

    public boolean isEmpty() {
        return messages == null || messages.isEmpty();
    }

    public ContextMessage getLastMessage() {
        if (isEmpty()) return null;
        return messages.get(messages.size() - 1);
    }
}
