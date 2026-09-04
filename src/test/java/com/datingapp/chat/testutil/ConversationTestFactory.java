package com.datingapp.chat.testutil;

import com.datingapp.chat.conversation.dto.CreateConversationRequest;
import com.datingapp.chat.conversation.entity.Conversation;
import com.datingapp.chat.conversation.entity.ConversationParticipant;
import com.datingapp.chat.conversation.entity.ConversationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Deterministic Conversation test factory.
 */
public class ConversationTestFactory {

    public static Conversation createConversation(Long user1, Long user2) {
        Conversation conversation = new Conversation();
        conversation.setPublicId(UUID.randomUUID().toString());
        conversation.setStatus(ConversationStatus.ACTIVE);
        conversation.setCreatedAt(Instant.now());
        conversation.setUpdatedAt(Instant.now());

        ConversationParticipant p1 = new ConversationParticipant(conversation, user1);
        ConversationParticipant p2 = new ConversationParticipant(conversation, user2);

        conversation.addParticipant(p1);
        conversation.addParticipant(p2);

        return conversation;
    }

    public static CreateConversationRequest createRequest(Long participantId) {
        return new CreateConversationRequest(participantId);
    }
}
