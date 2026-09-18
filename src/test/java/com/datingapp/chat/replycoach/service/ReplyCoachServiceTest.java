package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.block.repository.BlockRepository;
import com.datingapp.chat.common.exception.ForbiddenException;
import com.datingapp.chat.common.exception.ResourceNotFoundException;
import com.datingapp.chat.conversation.entity.Conversation;
import com.datingapp.chat.conversation.repository.ConversationParticipantRepository;
import com.datingapp.chat.conversation.repository.ConversationRepository;
import com.datingapp.chat.message.entity.Message;
import com.datingapp.chat.message.repository.MessageRepository;
import com.datingapp.chat.replycoach.dto.ConversationStateDto;
import com.datingapp.chat.replycoach.dto.ReplyFeedbackRequest;
import com.datingapp.chat.replycoach.dto.ReplySuggestionItem;
import com.datingapp.chat.replycoach.dto.ReplySuggestionResponse;
import com.datingapp.chat.replycoach.entity.AiReplyFeedback;
import com.datingapp.chat.replycoach.entity.FeedbackAction;
import com.datingapp.chat.replycoach.provider.AIReplyProvider;
import com.datingapp.chat.replycoach.repository.AiReplyFeedbackRepository;
import com.datingapp.chat.replycoach.service.impl.ReplyCoachServiceImpl;
import com.datingapp.chat.security.User;
import com.datingapp.chat.security.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReplyCoachServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationParticipantRepository participantRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BlockRepository blockRepository;
    @Mock
    private AiReplyFeedbackRepository feedbackRepository;
    @Mock
    private AIReplyProvider aiReplyProvider;

    private ReplyCoachServiceImpl service;

    private final String conversationId = "conv-123";
    private final Long userId = 101L;
    private final Long otherUserId = 202L;
    private Conversation conversation;
    private User currentUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        service = new ReplyCoachServiceImpl(
                conversationRepository,
                participantRepository,
                messageRepository,
                userRepository,
                blockRepository,
                feedbackRepository,
                aiReplyProvider
        );

        conversation = new Conversation();
        ReflectionTestUtils.setField(conversation, "id", 1L);
        conversation.setPublicId(conversationId);

        currentUser = new User("Alice", "111111", "alice@example.com", "hash");
        ReflectionTestUtils.setField(currentUser, "id", userId);
        currentUser.setInterests("Coffee|Travel");

        otherUser = new User("Bob", "222222", "bob@example.com", "hash");
        ReflectionTestUtils.setField(otherUser, "id", otherUserId);
        otherUser.setInterests("Photography|Hiking");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException if conversation does not exist")
    void testConversationNotFound() {
        when(conversationRepository.findByPublicId(conversationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReplySuggestions(conversationId, userId, 3))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw ForbiddenException if user is not in conversation")
    void testNotParticipant() {
        when(conversationRepository.findByPublicId(conversationId)).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversation_PublicIdAndUserId(conversationId, userId)).thenReturn(false);

        assertThatThrownBy(() -> service.getReplySuggestions(conversationId, userId, 3))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("Should throw ForbiddenException if participants are blocked")
    void testBlockedUsers() {
        when(conversationRepository.findByPublicId(conversationId)).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversation_PublicIdAndUserId(conversationId, userId)).thenReturn(true);
        when(participantRepository.findOtherParticipantUserIds(conversationId, userId)).thenReturn(List.of(otherUserId));
        when(blockRepository.isBlockedBetween(userId, otherUserId)).thenReturn(true);

        assertThatThrownBy(() -> service.getReplySuggestions(conversationId, userId, 3))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("Should return AI generated suggestions when provider succeeds")
    void testSuccessfulAiSuggestions() {
        when(conversationRepository.findByPublicId(conversationId)).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversation_PublicIdAndUserId(conversationId, userId)).thenReturn(true);
        when(participantRepository.findOtherParticipantUserIds(conversationId, userId)).thenReturn(List.of(otherUserId));
        when(blockRepository.isBlockedBetween(userId, otherUserId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(otherUserId)).thenReturn(Optional.of(otherUser));

        Message m1 = new Message();
        m1.setConversation(conversation);
        m1.setSenderId(otherUserId);
        m1.setContent("Hey! Are you free for coffee this weekend?");
        m1.setCreatedAt(Instant.now().minusSeconds(60));

        when(messageRepository.findRecentMessages(eq(1L), anyInt())).thenReturn(List.of(m1));

        List<ReplySuggestionItem> aiItems = List.of(
                new ReplySuggestionItem("s1", "Yes! Saturday afternoon works great for me.", "Plans", "Warm"),
                new ReplySuggestionItem("s2", "I'd love that! Any favorite coffee spot in mind?", "Spot", "Curious"),
                new ReplySuggestionItem("s3", "Definitely! Coffee is always a good idea ☕", "Enthusiastic", "Playful")
        );
        ConversationStateDto aiState = new ConversationStateDto("Weekend Plans", "Friendly", "HIGH", "ENGLISH", false);

        when(aiReplyProvider.generateSuggestions(anyList(), anyString(), anyBoolean(), anyList(), anyString(), anyString(), anyString(), eq(3)))
                .thenReturn(Optional.of(new AIReplyProvider.GenerationResult(aiItems, aiState)));

        ReplySuggestionResponse response = service.getReplySuggestions(conversationId, userId, 3);

        assertThat(response).isNotNull();
        assertThat(response.getConversationId()).isEqualTo(conversationId);
        assertThat(response.getSuggestions()).hasSize(3);
        assertThat(response.getSuggestions().get(0).getText()).contains("Saturday afternoon");
        assertThat(response.getConversationState().getTopic()).isEqualTo("Weekend Plans");

        // Verify SHOWN feedback is recorded
        verify(feedbackRepository, atLeastOnce()).save(any(AiReplyFeedback.class));
    }

    @Test
    @DisplayName("Should provide intelligent fallback suggestions when AI provider is unavailable")
    void testFallbackSuggestionsWhenAiOffline() {
        when(conversationRepository.findByPublicId(conversationId)).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversation_PublicIdAndUserId(conversationId, userId)).thenReturn(true);
        when(participantRepository.findOtherParticipantUserIds(conversationId, userId)).thenReturn(List.of(otherUserId));
        when(blockRepository.isBlockedBetween(userId, otherUserId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(otherUserId)).thenReturn(Optional.of(otherUser));

        Message m1 = new Message();
        m1.setConversation(conversation);
        m1.setSenderId(otherUserId);
        m1.setContent("What did you think of the new cafe?");
        m1.setCreatedAt(Instant.now().minusSeconds(30));

        when(messageRepository.findRecentMessages(eq(1L), anyInt())).thenReturn(List.of(m1));
        when(aiReplyProvider.generateSuggestions(anyList(), anyString(), anyBoolean(), anyList(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(Optional.empty());

        ReplySuggestionResponse response = service.getReplySuggestions(conversationId, userId, 3);

        assertThat(response).isNotNull();
        assertThat(response.getSuggestions()).isNotEmpty();
        assertThat(response.getSuggestions().size()).isLessThanOrEqualTo(3);
        for (ReplySuggestionItem item : response.getSuggestions()) {
            assertThat(item.getText()).isNotBlank();
        }
    }

    @Test
    @DisplayName("Should detect dry conversation and suggest engaging re-openers")
    void testDryConversationHandling() {
        when(conversationRepository.findByPublicId(conversationId)).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversation_PublicIdAndUserId(conversationId, userId)).thenReturn(true);
        when(participantRepository.findOtherParticipantUserIds(conversationId, userId)).thenReturn(List.of(otherUserId));
        when(blockRepository.isBlockedBetween(userId, otherUserId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(otherUserId)).thenReturn(Optional.of(otherUser));

        Message dryMsg = new Message();
        dryMsg.setConversation(conversation);
        dryMsg.setSenderId(otherUserId);
        dryMsg.setContent("k");
        dryMsg.setCreatedAt(Instant.now().minusSeconds(10));

        when(messageRepository.findRecentMessages(eq(1L), anyInt())).thenReturn(List.of(dryMsg));
        when(aiReplyProvider.generateSuggestions(anyList(), anyString(), eq(true), anyList(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(Optional.empty()); // Trigger fallback with dry=true

        ReplySuggestionResponse response = service.getReplySuggestions(conversationId, userId, 3);

        assertThat(response).isNotNull();
        assertThat(response.getConversationState().isDry()).isTrue();
        assertThat(response.getSuggestions()).isNotEmpty();
    }

    @Test
    @DisplayName("Should filter out rejected suggestions on regeneration")
    void testRegenerationFiltersRejectedTexts() {
        when(conversationRepository.findByPublicId(conversationId)).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversation_PublicIdAndUserId(conversationId, userId)).thenReturn(true);
        when(participantRepository.findOtherParticipantUserIds(conversationId, userId)).thenReturn(List.of(otherUserId));
        when(blockRepository.isBlockedBetween(userId, otherUserId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(otherUserId)).thenReturn(Optional.of(otherUser));

        Message m = new Message();
        m.setConversation(conversation);
        m.setSenderId(otherUserId);
        m.setContent("Let's grab lunch tomorrow.");
        m.setCreatedAt(Instant.now());

        when(messageRepository.findRecentMessages(eq(1L), anyInt())).thenReturn(List.of(m));

        String rejectedText = "Haha no way! What happened after that?";
        List<ReplySuggestionItem> aiItems = List.of(
                new ReplySuggestionItem("s1", rejectedText, "Story", "Curious"),
                new ReplySuggestionItem("s2", "Sounds like a plan! Where were you thinking?", "Lunch", "Warm")
        );
        when(aiReplyProvider.generateSuggestions(anyList(), anyString(), anyBoolean(), anyList(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(Optional.of(new AIReplyProvider.GenerationResult(aiItems, new ConversationStateDto("Lunch", "Warm", "BALANCED", "ENGLISH", false))));

        ReplySuggestionResponse response = service.regenerateReplySuggestions(
                conversationId, userId, List.of("s1"), List.of(rejectedText), 3);

        assertThat(response.getSuggestions())
                .extracting(ReplySuggestionItem::getText)
                .doesNotContain(rejectedText);
    }

    @Test
    @DisplayName("Should record feedback accurately")
    void testRecordFeedback() {
        when(participantRepository.existsByConversation_PublicIdAndUserId(conversationId, userId)).thenReturn(true);

        ReplyFeedbackRequest req = new ReplyFeedbackRequest("s-uuid-1", conversationId, FeedbackAction.USED, "Sounds great!");
        service.recordFeedback(userId, req);

        ArgumentCaptor<AiReplyFeedback> captor = ArgumentCaptor.forClass(AiReplyFeedback.class);
        verify(feedbackRepository).save(captor.capture());

        AiReplyFeedback saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getConversationId()).isEqualTo(conversationId);
        assertThat(saved.getSuggestionId()).isEqualTo("s-uuid-1");
        assertThat(saved.getAction()).isEqualTo(FeedbackAction.USED);
        assertThat(saved.getSuggestionText()).isEqualTo("Sounds great!");
    }
}
