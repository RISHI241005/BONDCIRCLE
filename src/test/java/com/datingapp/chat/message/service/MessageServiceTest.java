package com.datingapp.chat.message.service;

import com.datingapp.chat.common.exception.BadRequestException;
import com.datingapp.chat.common.util.CursorUtils;
import com.datingapp.chat.config.RateLimitProperties;
import com.datingapp.chat.conversation.entity.Conversation;
import com.datingapp.chat.conversation.repository.ConversationRepository;
import com.datingapp.chat.conversation.service.ConversationService;
import com.datingapp.chat.message.dto.MessageCursorPage;
import com.datingapp.chat.message.dto.MessageResponse;
import com.datingapp.chat.message.dto.SendMessageRequest;
import com.datingapp.chat.message.entity.Message;
import com.datingapp.chat.message.entity.MessageStatus;
import com.datingapp.chat.message.entity.MessageType;
import com.datingapp.chat.message.repository.MessageRepository;
import com.datingapp.chat.message.service.impl.IdempotencyServiceImpl;
import com.datingapp.chat.message.service.impl.MessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationService conversationService;

    @Mock
    private com.datingapp.chat.websocket.service.WebSocketBroadcastService webSocketBroadcastService;

    @Mock
    private com.datingapp.chat.block.service.BlockService blockService;

    private IdempotencyService idempotencyService;
    private RateLimitProperties rateLimitProperties;
    private MessageService messageService;

    @BeforeEach
    void setUp() {
        org.springframework.transaction.PlatformTransactionManager txManager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        org.mockito.Mockito.lenient().when(txManager.getTransaction(any())).thenReturn(mock(org.springframework.transaction.TransactionStatus.class));

        idempotencyService = new IdempotencyServiceImpl(messageRepository);
        rateLimitProperties = new RateLimitProperties();
        messageService = new MessageServiceImpl(
                messageRepository,
                conversationRepository,
                conversationService,
                idempotencyService,
                rateLimitProperties,
                webSocketBroadcastService,
                blockService,
                txManager
        );
    }

    @Test
    @DisplayName("Should send message successfully and update conversation lastMessage pointer")
    void testSendMessageSuccess() {
        String convPublicId = "conv-uuid-1";
        Long senderId = 101L;

        Conversation conv = new Conversation();
        conv.setId(10L);
        conv.setPublicId(convPublicId);

        when(conversationService.getConversationEntity(convPublicId)).thenReturn(conv);

        SendMessageRequest request = new SendMessageRequest("Hello there!", "client-msg-1", null);

        Message savedMsg = new Message();
        savedMsg.setId(100L);
        savedMsg.setPublicId(UUID.randomUUID().toString());
        savedMsg.setConversation(conv);
        savedMsg.setSenderId(senderId);
        savedMsg.setClientMessageId("client-msg-1");
        savedMsg.setContent("Hello there!");
        savedMsg.setMessageType(MessageType.TEXT);
        savedMsg.setStatus(MessageStatus.SENT);
        savedMsg.setCreatedAt(Instant.now());
        savedMsg.setUpdatedAt(Instant.now());

        when(messageRepository.save(any(Message.class))).thenReturn(savedMsg);

        MessageResponse response = messageService.sendMessage(convPublicId, senderId, request);

        assertNotNull(response);
        assertEquals(savedMsg.getPublicId(), response.getId());
        assertEquals("Hello there!", response.getContent());
        assertEquals(MessageStatus.SENT, response.getStatus());

        verify(messageRepository, times(1)).save(any(Message.class));
        verify(conversationRepository, times(1)).save(conv);
    }

    @Test
    @DisplayName("Should return existing message if clientMessageId is repeated (idempotency)")
    void testSendMessageIdempotency() {
        String convPublicId = "conv-uuid-1";
        Long senderId = 101L;
        String clientMsgId = "client-msg-1";

        Conversation conv = new Conversation();
        conv.setId(10L);
        conv.setPublicId(convPublicId);

        Message existing = new Message();
        existing.setId(100L);
        existing.setPublicId("existing-msg-uuid");
        existing.setConversation(conv);
        existing.setSenderId(senderId);
        existing.setClientMessageId(clientMsgId);
        existing.setContent("Existing message content");
        existing.setMessageType(MessageType.TEXT);
        existing.setStatus(MessageStatus.SENT);
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        when(messageRepository.findBySenderIdAndClientMessageId(senderId, clientMsgId))
                .thenReturn(Optional.of(existing));

        SendMessageRequest request = new SendMessageRequest("Attempt duplicate send", clientMsgId, null);

        MessageResponse response = messageService.sendMessage(convPublicId, senderId, request);

        assertEquals("existing-msg-uuid", response.getId());
        assertEquals("Existing message content", response.getContent());
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    @DisplayName("Should reject message with blank content")
    void testBlankMessageContent() {
        SendMessageRequest request = new SendMessageRequest("   ");
        assertThrows(BadRequestException.class, () -> messageService.sendMessage("conv-uuid", 101L, request));
    }

    @Test
    @DisplayName("Should retrieve cursor-paginated messages")
    void testGetMessageHistory() {
        String convPublicId = "conv-uuid-1";
        Long userId = 101L;

        Conversation conv = new Conversation();
        conv.setId(10L);
        conv.setPublicId(convPublicId);

        when(conversationService.getConversationEntity(convPublicId)).thenReturn(conv);

        Message m1 = new Message();
        m1.setId(2L);
        m1.setPublicId("msg-2");
        m1.setConversation(conv);
        m1.setSenderId(userId);
        m1.setContent("Second message");
        m1.setCreatedAt(Instant.now());
        m1.setUpdatedAt(Instant.now());

        Message m2 = new Message();
        m2.setId(1L);
        m2.setPublicId("msg-1");
        m2.setConversation(conv);
        m2.setSenderId(202L);
        m2.setContent("First message");
        m2.setCreatedAt(Instant.now().minusSeconds(10));
        m2.setUpdatedAt(Instant.now().minusSeconds(10));

        when(messageRepository.findInitialMessagesByConversationId(eq(10L), any(Pageable.class)))
                .thenReturn(List.of(m1, m2));

        MessageCursorPage page = messageService.getMessageHistory(convPublicId, userId, null, 10);

        assertNotNull(page);
        assertEquals(2, page.getMessages().size());
        assertFalse(page.isHasMore());
        assertNull(page.getNextCursor());
    }
}
