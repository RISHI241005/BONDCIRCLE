package com.datingapp.chat.icebreaker.service;

import com.datingapp.chat.common.exception.ForbiddenException;
import com.datingapp.chat.config.AiAssistantProperties;
import com.datingapp.chat.config.IceBreakerProperties;
import com.datingapp.chat.conversation.entity.Conversation;
import com.datingapp.chat.conversation.repository.ConversationParticipantRepository;
import com.datingapp.chat.conversation.repository.ConversationRepository;
import com.datingapp.chat.icebreaker.dto.IceBreakerResponse;
import com.datingapp.chat.icebreaker.service.impl.InterestBasedIceBreakerService;
import com.datingapp.chat.message.repository.MessageRepository;
import com.datingapp.chat.message.entity.Message;
import com.datingapp.chat.moderation.service.LanguageModerationService;
import com.datingapp.chat.security.User;
import com.datingapp.chat.security.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
        LanguageModerationService moderationService = new LanguageModerationService();
        service = new InterestBasedIceBreakerService(
                conversationRepository,
                participantRepository,
                messageRepository,
                userRepository,
                new ConversationCircleRulesEngine(),
                new ConversationSignalExtractor(moderationService),
                moderationService,
                new IceBreakerProperties(),
                new AiAssistantProperties(),
                request -> Optional.empty());
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
        when(messageRepository.findRecentMessages(44L, 80)).thenReturn(List.of());

        IceBreakerResponse response = service.getSuggestions("conversation-44", 1L);

        assertEquals("NEW_CONVERSATION", response.context());
        assertEquals(List.of("Travel"), response.sharedInterests());
        assertEquals(4, response.suggestions().size());
        assertTrue(response.suggestions().stream().anyMatch(value -> value.text().contains("Travel")));
        assertTrue(response.circles().stream().anyMatch(value -> value.code().equals("COMMON_GROUND")));

        IceBreakerResponse playful = service.getSuggestions("conversation-44", 1L, 3, "PLAYFUL", 1);
        assertEquals(3, playful.suggestions().size());
        assertTrue(playful.suggestions().stream().allMatch(value -> value.tone().equals("PLAYFUL")));
    }

    @Test
    void usesLatestMessageAndRepeatedPriorTopics() {
        Conversation conversation = new Conversation();
        conversation.setId(44L);
        conversation.setPublicId("conversation-44");
        User currentUser = user(1L, "Asha", List.of("Travel"));
        User otherUser = user(2L, "Ravi", List.of("Photography"));
        Message latest = message(2L, "My photography trip was surprisingly eventful", Instant.now());
        Message earlierMine = message(1L, "How is the trip planning going?", Instant.now().minus(1, ChronoUnit.DAYS));
        Message earlierTheirs = message(2L, "The trip planning is almost done", Instant.now().minus(2, ChronoUnit.DAYS));

        when(conversationRepository.findByPublicId("conversation-44")).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversation_PublicIdAndUserId("conversation-44", 1L)).thenReturn(true);
        when(participantRepository.findOtherParticipantUserIds("conversation-44", 1L)).thenReturn(List.of(2L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
        when(messageRepository.findRecentMessages(44L, 80)).thenReturn(List.of(latest, earlierMine, earlierTheirs));

        IceBreakerResponse response = service.getSuggestions("conversation-44", 1L, 16, "ALL", 0);

        assertTrue(response.priorTopics().stream().anyMatch(value -> value.equalsIgnoreCase("trip")));
        assertTrue(response.suggestions().stream().anyMatch(value -> value.circle().equals("DIRECT_REPLY")));
        assertTrue(response.suggestions().stream().anyMatch(value -> value.circle().equals("CALLBACK")));

        Message latestOutgoing = message(1L, "That sounds like a memorable trip", Instant.now().plusSeconds(1));
        when(messageRepository.findRecentMessages(44L, 80))
                .thenReturn(List.of(latestOutgoing, latest, earlierMine, earlierTheirs));
        IceBreakerResponse afterReply = service.getSuggestions("conversation-44", 1L, 16, "ALL", 0);
        assertTrue(afterReply.suggestions().stream().noneMatch(value -> value.circle().equals("DIRECT_REPLY")));
    }

    @Test
    void blocksSuggestionsForANonParticipant() {
        Conversation conversation = new Conversation();
        conversation.setId(44L);
        when(conversationRepository.findByPublicId("conversation-44")).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversation_PublicIdAndUserId("conversation-44", 99L)).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> service.getSuggestions("conversation-44", 99L));
    }

    @Test
    void returnsFreshHinglishRepliesFromLiveAssistant() {
        Conversation conversation = new Conversation();
        conversation.setId(44L);
        conversation.setPublicId("conversation-44");
        User currentUser = user(1L, "Asha", List.of("Travel"));
        User otherUser = user(2L, "Ravi", List.of("Photography"));
        Message latest = message(2L, "Aaj ka din kaafi hectic tha", Instant.now());

        when(conversationRepository.findByPublicId("conversation-44")).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversation_PublicIdAndUserId("conversation-44", 1L)).thenReturn(true);
        when(participantRepository.findOtherParticipantUserIds("conversation-44", 1L)).thenReturn(List.of(2L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
        when(messageRepository.findRecentMessages(44L, 80)).thenReturn(List.of(latest));

        LanguageModerationService moderationService = new LanguageModerationService();
        service = new InterestBasedIceBreakerService(
                conversationRepository,
                participantRepository,
                messageRepository,
                userRepository,
                new ConversationCircleRulesEngine(),
                new ConversationSignalExtractor(moderationService),
                moderationService,
                new IceBreakerProperties(),
                new AiAssistantProperties(),
                request -> {
                    assertEquals(7, request.variation());
                    return Optional.of(new LiveConversationAssistant.ReplyBatch(
                            "Acknowledge their day and invite an easy next detail.",
                            List.of(new LiveConversationAssistant.Reply(
                                    "Oh no, hectic kyun tha? Ab thoda relax kar pa rahe ho?",
                                    "Their day",
                                    "Responds directly and naturally.",
                                    "DIRECT_REPLY",
                                    "WARM",
                                    "HINGLISH"))));
                });

        IceBreakerResponse response = service.getSuggestions(
                "conversation-44", 1L, 4, "ALL", 7, "HINGLISH", "WRITE_FOR_ME");

        assertTrue(response.generatedLive());
        assertEquals("LIVE_AI", response.source());
        assertEquals("HINGLISH", response.suggestions().getFirst().language());
        assertTrue(response.suggestions().getFirst().aiGenerated());
        assertTrue(response.suggestions().getFirst().text().contains("kyun"));
        assertEquals("RECOMMENDED", response.shouldReply());
    }

    @Test
    void advisesUserWhenQuestionIsAskedOrWhenUserSentLastMessage() {
        Conversation conversation = new Conversation();
        conversation.setId(44L);
        conversation.setPublicId("conversation-44");
        User currentUser = user(1L, "Asha", List.of("Travel"));
        User otherUser = user(2L, "Ravi", List.of("Photography"));
        Message questionFromOther = message(2L, "Are you free this weekend?", Instant.now());

        when(conversationRepository.findByPublicId("conversation-44")).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversation_PublicIdAndUserId("conversation-44", 1L)).thenReturn(true);
        when(participantRepository.findOtherParticipantUserIds("conversation-44", 1L)).thenReturn(List.of(2L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
        when(messageRepository.findRecentMessages(44L, 80)).thenReturn(List.of(questionFromOther));

        IceBreakerResponse response = service.getSuggestions("conversation-44", 1L);

        assertEquals("RECOMMENDED", response.shouldReply());
        assertEquals("HIGH", response.urgency());
        assertTrue(response.decisionReason().contains("direct question"));

        // When user sent the last message, advice should be NO_RUSH
        Message myReply = message(1L, "Yes I might be free!", Instant.now().plusSeconds(1));
        when(messageRepository.findRecentMessages(44L, 80)).thenReturn(List.of(myReply, questionFromOther));

        IceBreakerResponse myLastResponse = service.getSuggestions("conversation-44", 1L);
        assertEquals("NO_RUSH", myLastResponse.shouldReply());
        assertEquals("LOW", myLastResponse.urgency());
        assertTrue(myLastResponse.decisionReason().contains("You sent the last message"));
    }

    @Test
    void providesPoliteBusyRepliesInUnableToTalkMode() {
        Conversation conversation = new Conversation();
        conversation.setId(44L);
        conversation.setPublicId("conversation-44");
        User currentUser = user(1L, "Asha", List.of("Travel"));
        User otherUser = user(2L, "Ravi", List.of("Photography"));
        Message messageFromOther = message(2L, "Hey are you there?", Instant.now());

        when(conversationRepository.findByPublicId("conversation-44")).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversation_PublicIdAndUserId("conversation-44", 1L)).thenReturn(true);
        when(participantRepository.findOtherParticipantUserIds("conversation-44", 1L)).thenReturn(List.of(2L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
        when(messageRepository.findRecentMessages(44L, 80)).thenReturn(List.of(messageFromOther));

        IceBreakerResponse response = service.getSuggestions(
                "conversation-44", 1L, 4, "ALL", 0, "ENGLISH", "UNABLE_TO_TALK");

        assertEquals("UNABLE_TO_TALK", response.mode());
        assertTrue(response.suggestions().stream().anyMatch(s -> s.text().toLowerCase().contains("caught up") || s.text().toLowerCase().contains("busy") || s.text().toLowerCase().contains("tied up")));
    }

    private User user(Long id, String name, List<String> interests) {
        User user = new User();
        user.setId(id);
        user.setFullName(name);
        user.setInterestList(interests);
        return user;
    }

    private Message message(Long senderId, String content, Instant createdAt) {
        Message message = new Message();
        message.setSenderId(senderId);
        message.setContent(content);
        message.setCreatedAt(createdAt);
        return message;
    }
}
