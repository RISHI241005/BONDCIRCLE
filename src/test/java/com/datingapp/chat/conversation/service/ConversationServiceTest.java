package com.datingapp.chat.conversation.service;

import com.datingapp.chat.common.exception.ForbiddenException;
import com.datingapp.chat.common.exception.ResourceNotFoundException;
import com.datingapp.chat.conversation.dto.ConversationDetailResponse;
import com.datingapp.chat.conversation.dto.ConversationSummaryResponse;
import com.datingapp.chat.conversation.entity.Conversation;
import com.datingapp.chat.conversation.entity.ConversationParticipant;
import com.datingapp.chat.conversation.entity.ConversationStatus;
import com.datingapp.chat.conversation.repository.ConversationParticipantRepository;
import com.datingapp.chat.conversation.repository.ConversationRepository;
import com.datingapp.chat.message.repository.MessageRepository;
import com.datingapp.chat.presence.service.PresenceService;
import com.datingapp.chat.security.UserRepository;
import com.datingapp.chat.conversation.service.impl.ConversationServiceImpl;
import com.datingapp.chat.conversation.service.impl.DefaultChatAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

@Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationParticipantRepository participantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.datingapp.chat.message.repository.MessageRepository messageRepository;

    @Mock
    private PresenceService presenceService;

    private ChatAuthorizationService chatAuthorizationService;

    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        org.springframework.transaction.PlatformTransactionManager txManager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        org.mockito.Mockito.lenient().when(txManager.getTransaction(any())).thenReturn(mock(org.springframework.transaction.TransactionStatus.class));

        chatAuthorizationService = new DefaultChatAuthorizationService();
        conversationService = new ConversationServiceImpl(
                conversationRepository, participantRepository, chatAuthorizationService, userRepository, presenceService, messageRepository, txManager);
    }

    @Test
    @DisplayName("Should create new conversation between two users when no direct conversation exists")
    void testCreateNewConversation() {
        Long userA = 100L;
        Long userB = 200L;

        when(conversationRepository.findDirectConversationsBetween(userA, userB))
                .thenReturn(Collections.emptyList());

        Conversation savedConv = new Conversation();
        savedConv.setId(1L);
        savedConv.setPublicId(UUID.randomUUID().toString());
        savedConv.setStatus(ConversationStatus.ACTIVE);
        savedConv.setCreatedAt(Instant.now());
        savedConv.setUpdatedAt(Instant.now());
        savedConv.addParticipant(new ConversationParticipant(savedConv, userA));
        savedConv.addParticipant(new ConversationParticipant(savedConv, userB));

        when(conversationRepository.save(any(Conversation.class))).thenReturn(savedConv);

        ConversationDetailResponse response = conversationService.createOrGetConversation(userA, userB);

        assertNotNull(response);
        assertEquals(savedConv.getPublicId(), response.getConversationId());
        assertEquals(2, response.getParticipants().size());
        verify(conversationRepository, times(1)).save(any(Conversation.class));
    }

    @Test
    @DisplayName("Should return existing conversation if users already have an active conversation")
    void testReturnExistingConversation() {
        Long userA = 100L;
        Long userB = 200L;

        Conversation existingConv = new Conversation();
        existingConv.setId(1L);
        existingConv.setPublicId("existing-uuid");
        existingConv.setStatus(ConversationStatus.ACTIVE);
        existingConv.setCreatedAt(Instant.now());
        existingConv.setUpdatedAt(Instant.now());
        existingConv.addParticipant(new ConversationParticipant(existingConv, userA));
        existingConv.addParticipant(new ConversationParticipant(existingConv, userB));

        when(conversationRepository.findDirectConversationsBetween(userA, userB))
                .thenReturn(List.of(existingConv));

        ConversationDetailResponse response = conversationService.createOrGetConversation(userA, userB);

        assertEquals("existing-uuid", response.getConversationId());
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    @DisplayName("Should reject self-conversation initiation with ForbiddenException")
    void testRejectSelfConversation() {
        Long userA = 100L;
        assertThrows(ForbiddenException.class, () -> conversationService.createOrGetConversation(userA, userA));
    }

    @Test
    @DisplayName("Should throw ForbiddenException when non-participant requests details")
    void testNonParticipantAccess() {
        Long userA = 100L;
        Long userB = 200L;
        Long outsider = 300L;

        Conversation conv = new Conversation();
        conv.setId(1L);
        conv.setPublicId("conv-uuid");
        conv.addParticipant(new ConversationParticipant(conv, userA));
        conv.addParticipant(new ConversationParticipant(conv, userB));

        when(conversationRepository.findByPublicId("conv-uuid")).thenReturn(Optional.of(conv));

        assertThrows(ForbiddenException.class, () -> conversationService.getConversationDetails("conv-uuid", outsider));
    }
}
