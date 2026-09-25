package com.datingapp.chat.concurrency;

import com.datingapp.chat.common.exception.BadRequestException;
import com.datingapp.chat.conversation.dto.ConversationDetailResponse;
import com.datingapp.chat.conversation.service.ConversationService;
import com.datingapp.chat.message.dto.SendMessageRequest;
import com.datingapp.chat.message.service.MessageService;
import com.datingapp.chat.testutil.AbstractMySQLIntegrationTest;
import com.datingapp.chat.testutil.UserTestFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class TransactionRollbackTest extends AbstractMySQLIntegrationTest {

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private MessageService messageService;

    @Test
    @DisplayName("Should rollback transaction and leave no orphan records when operation fails validation")
    void testTransactionRollbackOnInvalidReply() {
        ConversationDetailResponse conv = conversationService.createOrGetConversation(
                UserTestFactory.USER_A, UserTestFactory.USER_B);
        String convId = conv.getConversationId();

        // Count messages before
        Integer countBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE conversation_id = " +
                "(SELECT id FROM conversations WHERE public_id = ?)", Integer.class, convId);
        assertEquals(0, countBefore);

        // Attempt sending message with non-existent replyToMessageId
        SendMessageRequest invalidReplyReq = new SendMessageRequest(
                "Trying to reply to ghost", "ghost-reply-1", "non-existent-reply-uuid");

        assertThrows(Exception.class, () ->
                messageService.sendMessage(convId, UserTestFactory.USER_A, invalidReplyReq));

        // Verify count remains 0 in MySQL (no partial / orphan insert)
        Integer countAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE conversation_id = " +
                "(SELECT id FROM conversations WHERE public_id = ?)", Integer.class, convId);
        assertEquals(0, countAfter);
    }
}
