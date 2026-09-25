package com.datingapp.chat.message.repository;

import com.datingapp.chat.conversation.entity.Conversation;
import com.datingapp.chat.conversation.repository.ConversationRepository;
import com.datingapp.chat.message.entity.Message;
import com.datingapp.chat.message.entity.MessageStatus;
import com.datingapp.chat.testutil.AbstractMySQLIntegrationTest;
import com.datingapp.chat.testutil.ConversationTestFactory;
import com.datingapp.chat.testutil.MessageTestFactory;
import com.datingapp.chat.testutil.UserTestFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MessageRepositoryMySQLTest extends AbstractMySQLIntegrationTest {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Test
    @DisplayName("Should paginate messages in MySQL using cursor with composite index ordering")
    void testCursorPaginationQueries() throws Exception {
        Conversation conv = conversationRepository.save(
                ConversationTestFactory.createConversation(UserTestFactory.USER_A, UserTestFactory.USER_B));

        // Insert 10 messages with staggered timestamps
        for (int i = 1; i <= 10; i++) {
            Message msg = MessageTestFactory.createMessage(conv, UserTestFactory.USER_A, "Message " + i);
            messageRepository.save(msg);
            Thread.sleep(10);
        }

        // 1. Initial page of 5 items (ORDER BY created_at DESC, id DESC)
        List<Message> firstPage = messageRepository.findInitialMessagesByConversationId(
                conv.getId(), PageRequest.of(0, 5));
        assertEquals(5, firstPage.size());
        assertEquals("Message 10", firstPage.get(0).getContent());
        assertEquals("Message 6", firstPage.get(4).getContent());

        // 2. Query second page using cursor of Message 6
        Message cursorMessage = firstPage.get(4);
        List<Message> secondPage = messageRepository.findMessagesBeforeCursor(
                conv.getId(), cursorMessage.getCreatedAt(), cursorMessage.getId(), PageRequest.of(0, 5));
        assertEquals(5, secondPage.size());
        assertEquals("Message 5", secondPage.get(0).getContent());
        assertEquals("Message 1", secondPage.get(4).getContent());
    }

    @Test
    @DisplayName("Should accurately calculate unread message count and bulk mark as READ in MySQL")
    @Transactional
    void testUnreadCountAndBulkRead() {
        Conversation conv = conversationRepository.save(
                ConversationTestFactory.createConversation(UserTestFactory.USER_A, UserTestFactory.USER_B));

        // User A sends 3 messages
        Message m1 = messageRepository.save(MessageTestFactory.createMessage(conv, UserTestFactory.USER_A, "Msg 1"));
        Message m2 = messageRepository.save(MessageTestFactory.createMessage(conv, UserTestFactory.USER_A, "Msg 2"));
        Message m3 = messageRepository.save(MessageTestFactory.createMessage(conv, UserTestFactory.USER_A, "Msg 3"));

        // User B's unread count initially should be 3
        long unreadB = messageRepository.countUnreadMessages(conv.getId(), null, UserTestFactory.USER_B);
        assertEquals(3, unreadB);

        // User A's unread count should be 0 (cannot count own messages)
        long unreadA = messageRepository.countUnreadMessages(conv.getId(), null, UserTestFactory.USER_A);
        assertEquals(0, unreadA);

        // User B reads up to m2
        int updated = messageRepository.markMessagesAsRead(
                conv.getId(), m2.getId(), UserTestFactory.USER_B, MessageStatus.READ, Instant.now());
        assertEquals(2, updated);

        // Check unread count for B after reading up to m2
        long remainingUnreadB = messageRepository.countUnreadMessages(conv.getId(), m2.getId(), UserTestFactory.USER_B);
        assertEquals(1, remainingUnreadB);
    }
}
