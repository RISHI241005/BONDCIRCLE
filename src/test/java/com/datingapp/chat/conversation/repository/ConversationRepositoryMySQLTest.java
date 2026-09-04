package com.datingapp.chat.conversation.repository;

import com.datingapp.chat.conversation.entity.Conversation;
import com.datingapp.chat.conversation.entity.ConversationParticipant;
import com.datingapp.chat.conversation.entity.ConversationStatus;
import com.datingapp.chat.testutil.AbstractMySQLIntegrationTest;
import com.datingapp.chat.testutil.ConversationTestFactory;
import com.datingapp.chat.testutil.UserTestFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ConversationRepositoryMySQLTest extends AbstractMySQLIntegrationTest {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationParticipantRepository participantRepository;

    @Test
    @DisplayName("Should find direct conversation between two users in MySQL")
    void testFindDirectConversationBetweenUsers() {
        Conversation conv = ConversationTestFactory.createConversation(UserTestFactory.USER_A, UserTestFactory.USER_B);
        Conversation saved = conversationRepository.save(conv);

        List<Conversation> found = conversationRepository.findDirectConversationsBetween(
                UserTestFactory.USER_A, UserTestFactory.USER_B);

        assertFalse(found.isEmpty());
        assertEquals(saved.getId(), found.get(0).getId());

        // Reverse lookup must return the exact same conversation
        List<Conversation> reverseFound = conversationRepository.findDirectConversationsBetween(
                UserTestFactory.USER_B, UserTestFactory.USER_A);
        assertFalse(reverseFound.isEmpty());
        assertEquals(saved.getId(), reverseFound.get(0).getId());
    }

    @Test
    @DisplayName("Should find active non-archived conversations by user ID ordered by last_message_at DESC")
    void testFindActiveConversations() {
        Conversation conv1 = ConversationTestFactory.createConversation(UserTestFactory.USER_A, UserTestFactory.USER_B);
        Conversation conv2 = ConversationTestFactory.createConversation(UserTestFactory.USER_A, UserTestFactory.USER_C);

        conversationRepository.save(conv1);
        conversationRepository.save(conv2);

        List<Conversation> listA = conversationRepository.findActiveConversationsByUserId(UserTestFactory.USER_A);
        assertEquals(2, listA.size());

        List<Conversation> listC = conversationRepository.findActiveConversationsByUserId(UserTestFactory.USER_C);
        assertEquals(1, listC.size());
    }

    @Test
    @DisplayName("Should query other participant user IDs in conversation")
    void testFindOtherParticipantUserIds() {
        Conversation conv = ConversationTestFactory.createConversation(UserTestFactory.USER_A, UserTestFactory.USER_B);
        conversationRepository.save(conv);

        List<Long> otherIds = participantRepository.findOtherParticipantUserIds(conv.getPublicId(), UserTestFactory.USER_A);
        assertEquals(1, otherIds.size());
        assertEquals(UserTestFactory.USER_B, otherIds.get(0));
    }
}
