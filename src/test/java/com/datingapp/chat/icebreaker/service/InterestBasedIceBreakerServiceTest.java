package com.datingapp.chat.icebreaker.service;

import com.datingapp.chat.common.exception.ForbiddenException;
import com.datingapp.chat.conversation.entity.Conversation;
import com.datingapp.chat.conversation.repository.ConversationParticipantRepository;
import com.datingapp.chat.conversation.repository.ConversationRepository;
import com.datingapp.chat.icebreaker.dto.IceBreakerResponse;
import com.datingapp.chat.icebreaker.service.impl.InterestBasedIceBreakerService;
import com.datingapp.chat.message.repository.MessageRepository;
import com.datingapp.chat.security.User;
import com.datingapp.chat.security.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterestBasedIceBreakerServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationParticipantRepository participantRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private UserRepository userRepository;

    private IceBreakerService service;

    @BeforeEach
    void setUp() {
        service = new InterestBasedIceBreakerService(
                conversationRepository,
                participantRepository,
                messageRepository,
                userRepository);
    }

    @Test
    void suggestsSharedInterestForANewConversation() {
        Conversation conversation = new Conversation();
        conversation.setId(44L);
        conversation.setPublicId("conversation-44");
        User currentUser = user(1L, "Asha", List.of("Travel", "Cooking"));
        User otherUser = user(2L, "Ravi", List.of("Travel", "Photography"));

        when(conversationRepository.findByPublicId("conversation-44")).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversation_PublicIdAndUserId("conversation-44", 1L)).thenReturn(true);
        when(participantRepository.findOtherParticipantUserIds("conversation-44", 1L)).thenReturn(List.of(2L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
        when(messageRepository.findRecentMessages(44L, 12)).thenReturn(List.of());

        IceBreakerResponse response = service.getSuggestions("conversation-44", 1L);

        assertEquals("NEW_CONVERSATION", response.context());
        assertEquals(List.of("Travel"), response.sharedInterests());
        assertEquals(3, response.suggestions().size());
        assertTrue(response.suggestions().getFirst().text().contains("Travel"));
    }

    @Test
    void blocksSuggestionsForANonParticipant() {
        Conversation conversation = new Conversation();
        conversation.setId(44L);
        when(conversationRepository.findByPublicId("conversation-44")).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversation_PublicIdAndUserId("conversation-44", 99L)).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> service.getSuggestions("conversation-44", 99L));
    }

    private User user(Long id, String name, List<String> interests) {
        User user = new User();
        user.setId(id);
        user.setFullName(name);
        user.setInterestList(interests);
        return user;
    }
}
