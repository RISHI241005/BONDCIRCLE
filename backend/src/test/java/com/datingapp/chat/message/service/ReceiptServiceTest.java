package com.datingapp.chat.message.service;

import com.datingapp.chat.conversation.entity.Conversation;
import com.datingapp.chat.conversation.entity.ConversationParticipant;
import com.datingapp.chat.conversation.repository.ConversationParticipantRepository;
import com.datingapp.chat.conversation.service.ConversationService;
import com.datingapp.chat.message.entity.Message;
import com.datingapp.chat.message.entity.MessageStatus;
import com.datingapp.chat.message.repository.MessageRepository;
import com.datingapp.chat.message.service.impl.ReceiptServiceImpl;
import com.datingapp.chat.presence.service.impl.InMemoryPresenceService;
import com.datingapp.chat.websocket.service.WebSocketBroadcastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationParticipantRepository participantRepository;

    @Mock
    private ConversationService conversationService;

    @Mock
    private WebSocketBroadcastService broadcastService;

    @Mock
    private InMemoryPresenceService presenceService;

    private ReceiptService receiptService;

    @BeforeEach
    void setUp() {
        receiptService = new ReceiptServiceImpl(
                messageRepository, participantRepository, conversationService, broadcastService, presenceService);
    }

    @Test
    @DisplayName("Should transition SENT message to DELIVERED upon recipient delivery receipt")
    void testProcessDeliveryReceipt() {
        when(presenceService.isUserOnline(anyLong())).thenReturn(true);

        String convPublicId = "conv-uuid-1";
        String msgPublicId = "msg-uuid-10";
        Long senderId = 101L;
        Long recipientId = 202L;

        Conversation conv = new Conversation();
        conv.setId(1L);
        conv.setPublicId(convPublicId);
        conv.addParticipant(new ConversationParticipant(conv, senderId));
        conv.addParticipant(new ConversationParticipant(conv, recipientId));

        Message msg = new Message();
        msg.setId(50L);
        msg.setPublicId(msgPublicId);
        msg.setConversation(conv);
        msg.setSenderId(senderId);
        msg.setStatus(MessageStatus.SENT);

        when(messageRepository.findByPublicId(msgPublicId)).thenReturn(Optional.of(msg));

        receiptService.processDeliveryReceipt(convPublicId, msgPublicId, recipientId);

        assertEquals(MessageStatus.DELIVERED, msg.getStatus());
        verify(messageRepository, times(1)).save(msg);
        verify(broadcastService, times(1)).broadcastMessageStatusUpdate(
                eq(convPublicId), eq(msgPublicId), eq(MessageStatus.DELIVERED), eq(recipientId), any());
    }

    @Test
    @DisplayName("Should update participant watermark and bulk mark messages as READ")
    void testProcessReadReceipt() {
        String convPublicId = "conv-uuid-1";
        String msgPublicId = "msg-uuid-10";
        Long senderId = 101L;
        Long readerId = 202L;

        Conversation conv = new Conversation();
        conv.setId(1L);
        conv.setPublicId(convPublicId);
        conv.addParticipant(new ConversationParticipant(conv, senderId));
        ConversationParticipant readerParticipant = new ConversationParticipant(conv, readerId);
        conv.addParticipant(readerParticipant);

        Message msg = new Message();
        msg.setId(50L);
        msg.setPublicId(msgPublicId);
        msg.setConversation(conv);
        msg.setSenderId(senderId);
        msg.setStatus(MessageStatus.SENT);

        when(messageRepository.findByPublicId(msgPublicId)).thenReturn(Optional.of(msg));
        when(participantRepository.findByConversation_PublicIdAndUserId(convPublicId, readerId))
                .thenReturn(Optional.of(readerParticipant));

        receiptService.processReadReceipt(convPublicId, msgPublicId, readerId);

        assertEquals(50L, readerParticipant.getLastReadMessageId());
        verify(participantRepository, times(1)).save(readerParticipant);
        verify(messageRepository, times(1)).markMessagesAsRead(
                eq(1L), eq(50L), eq(readerId), eq(MessageStatus.READ), any(Instant.class));
        verify(broadcastService, times(1)).broadcastMessageStatusUpdate(
                eq(convPublicId), eq(msgPublicId), eq(MessageStatus.READ), eq(readerId), any());
    }
}