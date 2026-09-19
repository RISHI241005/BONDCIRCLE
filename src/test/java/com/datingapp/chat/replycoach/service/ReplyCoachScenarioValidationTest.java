package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.block.repository.BlockRepository;
import com.datingapp.chat.conversation.entity.Conversation;
import com.datingapp.chat.conversation.repository.ConversationParticipantRepository;
import com.datingapp.chat.conversation.repository.ConversationRepository;
import com.datingapp.chat.message.entity.Message;
import com.datingapp.chat.message.repository.MessageRepository;
import com.datingapp.chat.replycoach.dto.ReplySuggestionItem;
import com.datingapp.chat.replycoach.dto.ReplySuggestionResponse;
import com.datingapp.chat.replycoach.model.ConversationAnalysis;
import com.datingapp.chat.replycoach.model.ConversationContext;
import com.datingapp.chat.replycoach.model.ConversationContext.ContextMessage;
import com.datingapp.chat.replycoach.model.UserWritingProfile;
import com.datingapp.chat.replycoach.provider.AIReplyProvider;
import com.datingapp.chat.replycoach.repository.AiReplyFeedbackRepository;
import com.datingapp.chat.replycoach.service.impl.ReplyCoachServiceImpl;
import com.datingapp.chat.security.User;
import com.datingapp.chat.security.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ReplyCoachScenarioValidationTest {

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

    private ConversationIntelligenceService intelligenceService;
    private UserStyleEngine userStyleEngine;
    private ReplyCoachQualityFilter qualityFilter;
    private ReplyCoachRateLimiter rateLimiter;
    private ReplyCoachServiceImpl replyCoachService;

    private final String conversationId = "conv-scenarios-001";
    private final Long currentUserId = 101L;
    private final Long partnerUserId = 202L;
    private Conversation conversation;
    private User currentUser;
    private User partnerUser;

    @BeforeEach
    void setUp() {
        intelligenceService = new ConversationIntelligenceService();
        userStyleEngine = new UserStyleEngine(feedbackRepository);
        qualityFilter = new ReplyCoachQualityFilter();
        rateLimiter = new ReplyCoachRateLimiter();

        replyCoachService = new ReplyCoachServiceImpl(
                conversationRepository,
                participantRepository,
                messageRepository,
                userRepository,
                blockRepository,
                feedbackRepository,
                aiReplyProvider,
                new AiReplyFeedbackRecorder(feedbackRepository),
                intelligenceService,
                userStyleEngine,
                qualityFilter,
                rateLimiter
        );

        conversation = new Conversation();
        ReflectionTestUtils.setField(conversation, "id", 10L);
        conversation.setPublicId(conversationId);

        currentUser = new User("Alice", "999101", "alice@dating.com", "hash");
        ReflectionTestUtils.setField(currentUser, "id", currentUserId);
        currentUser.setInterests("Coffee|Hiking|Indie Music");

        partnerUser = new User("Bob", "999202", "bob@dating.com", "hash");
        ReflectionTestUtils.setField(partnerUser, "id", partnerUserId);
        partnerUser.setInterests("Football|Travel|Photography");

        when(conversationRepository.findByPublicId(conversationId)).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversation_PublicIdAndUserId(conversationId, currentUserId)).thenReturn(true);
        when(participantRepository.findOtherParticipantUserIds(conversationId, currentUserId)).thenReturn(List.of(partnerUserId));
        when(blockRepository.isBlockedBetween(currentUserId, partnerUserId)).thenReturn(false);
        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(partnerUserId)).thenReturn(Optional.of(partnerUser));
        when(aiReplyProvider.generateSuggestions(any(), any(), any(), anyList(), eq(3))).thenReturn(Optional.empty());
    }

    private Message createMsg(Long senderId, String content, Instant createdAt) {
        Message m = new Message();
        m.setSenderId(senderId);
        m.setContent(content);
        m.setCreatedAt(createdAt);
        m.setConversation(conversation);
        ReflectionTestUtils.setField(m, "id", (long) (Math.random() * 10000));
        return m;
    }

    @Test
    @DisplayName("Scenario 1 — Normal Chat: Other says 'Just got home 😭', suggestions empathize and continue naturally")
    void testScenario1_NormalChat() {
        List<Message> msgs = List.of(
                createMsg(partnerUserId, "Just got home 😭", Instant.now().minusSeconds(10)),
                createMsg(currentUserId, "Hey! How was work today?", Instant.now().minusSeconds(30))
        );
        when(messageRepository.findRecentMessages(10L, 40)).thenReturn(msgs);

        ReplySuggestionResponse response = replyCoachService.getReplySuggestions(conversationId, currentUserId, 3);

        assertThat(response).isNotNull();
        assertThat(response.getGenerationId()).startsWith("gen_");
        assertThat(response.getSuggestions()).isNotEmpty().hasSizeLessThanOrEqualTo(3);
        assertThat(response.getConversationState()).isNotNull();
        boolean hasSupportiveOrEmpathy = response.getSuggestions().stream()
                .anyMatch(s -> s.getStrategy().contains("EMPATHIZE") || s.getStrategy().contains("SUPPORTIVE") || s.getTone().contains("Warm") || s.getText().contains("rough") || s.getText().contains("relax"));
        assertThat(hasSupportiveOrEmpathy).isTrue();
    }

    @Test
    @DisplayName("Scenario 2 — Question: Other asks 'What are you studying?', suggestions directly answer and follow up")
    void testScenario2_QuestionAnswering() {
        List<Message> msgs = List.of(
                createMsg(partnerUserId, "What are you studying in college?", Instant.now().minusSeconds(15)),
                createMsg(currentUserId, "I'm at university right now.", Instant.now().minusSeconds(45))
        );
        when(messageRepository.findRecentMessages(10L, 40)).thenReturn(msgs);

        ReplySuggestionResponse response = replyCoachService.getReplySuggestions(conversationId, currentUserId, 3);

        assertThat(response.getSuggestions()).isNotEmpty();
        assertThat(response.getConversationState().hasUnansweredQuestion()).isTrue();
        assertThat(response.getConversationState().getUnansweredQuestionText()).contains("What are you studying");
        assertThat(response.getConversationState().getTopic()).isEqualTo("Studies & Academics");
    }

    @Test
    @DisplayName("Scenario 3 — Existing Topic: Conversation discusses football, replies maintain topic continuity")
    void testScenario3_ExistingTopicContinuity() {
        List<Message> msgs = List.of(
                createMsg(partnerUserId, "Did you watch the football match last night?", Instant.now().minusSeconds(10)),
                createMsg(currentUserId, "Yes! What a game that was!", Instant.now().minusSeconds(50))
        );
        when(messageRepository.findRecentMessages(10L, 40)).thenReturn(msgs);

        ReplySuggestionResponse response = replyCoachService.getReplySuggestions(conversationId, currentUserId, 3);

        assertThat(response.getConversationState().getTopic()).isEqualTo("Sports & Fitness");
        boolean mentionsSportsOrGame = response.getSuggestions().stream()
                .anyMatch(s -> s.getText().toLowerCase().contains("match") || s.getText().toLowerCase().contains("team") || s.getText().toLowerCase().contains("game"));
        assertThat(mentionsSportsOrGame).isTrue();
    }

    @Test
    @DisplayName("Scenario 4 — Dry Chat: Partner sends 'yeah', system detects dry momentum and revitalizes conversation")
    void testScenario4_DryChatRecovery() {
        List<Message> msgs = List.of(
                createMsg(partnerUserId, "yeah", Instant.now().minusSeconds(10)),
                createMsg(partnerUserId, "ok", Instant.now().minusSeconds(20)),
                createMsg(partnerUserId, "nice", Instant.now().minusSeconds(30))
        );
        when(messageRepository.findRecentMessages(10L, 40)).thenReturn(msgs);

        ReplySuggestionResponse response = replyCoachService.getReplySuggestions(conversationId, currentUserId, 3);

        assertThat(response.getConversationState().isDry()).isTrue();
        assertThat(response.getConversationState().getTone()).isEqualTo("Re-energizing");
        boolean hasBanter = response.getSuggestions().stream()
                .anyMatch(s -> s.getStrategy().contains("BANTER") || s.getStrategy().contains("PLAYFUL") || s.getText().contains("concise") || s.getText().contains("smile") || s.getText().contains("details"));
        assertThat(hasBanter).isTrue();
    }

    @Test
    @DisplayName("Scenario 5 — Emotional Context: Other says 'Today was honestly terrible.', suggestions are empathetic")
    void testScenario5_EmotionalContext() {
        List<Message> msgs = List.of(
                createMsg(partnerUserId, "Today was honestly terrible and exhausting.", Instant.now().minusSeconds(10))
        );
        when(messageRepository.findRecentMessages(10L, 40)).thenReturn(msgs);

        ReplySuggestionResponse response = replyCoachService.getReplySuggestions(conversationId, currentUserId, 3);

        assertThat(response.getConversationState().getTone()).isEqualTo("Empathetic");
        boolean hasEmpatheticReply = response.getSuggestions().stream()
                .anyMatch(s -> s.getStrategy().contains("EMPATHIZE") || s.getStrategy().contains("SUPPORTIVE") || s.getText().contains("rough") || s.getText().contains("vent"));
        assertThat(hasEmpatheticReply).isTrue();
    }

    @Test
    @DisplayName("Scenario 6 — New Conversation: Empty chat uses partner profile interests for contextual icebreakers")
    void testScenario6_NewConversationIcebreakers() {
        when(messageRepository.findRecentMessages(10L, 40)).thenReturn(Collections.emptyList());

        ReplySuggestionResponse response = replyCoachService.getReplySuggestions(conversationId, currentUserId, 3);

        assertThat(response.getConversationState().getStage()).isEqualTo("NEW_MATCH");
        assertThat(response.getSuggestions()).isNotEmpty();
        boolean mentionsInterestOrIcebreaker = response.getSuggestions().stream()
                .anyMatch(s -> s.getText().contains("Football") || s.getText().contains("Coffee") || s.getText().contains("travel"));
        assertThat(mentionsInterestOrIcebreaker).isTrue();
    }

    @Test
    @DisplayName("Scenario 7 — Rejection: Regenerating with rejected suggestions strictly avoids previous ideas")
    void testScenario7_RejectionAndRegeneration() {
        List<Message> msgs = List.of(
                createMsg(partnerUserId, "Hey! How is your week going?", Instant.now().minusSeconds(10))
        );
        when(messageRepository.findRecentMessages(10L, 40)).thenReturn(msgs);

        List<String> rejectedTexts = List.of(
                "Haha no way! What happened after that?",
                "That sounds awesome! I completely agree with you on that."
        );

        ReplySuggestionResponse response = replyCoachService.regenerateReplySuggestions(
                conversationId, currentUserId, List.of("sug-old-1", "sug-old-2"), rejectedTexts, 3
        );

        for (ReplySuggestionItem item : response.getSuggestions()) {
            assertThat(rejectedTexts).doesNotContain(item.getText());
        }
    }

    @Test
    @DisplayName("Scenario 8 — User Personalization: Style profile reflects user preference for short replies")
    void testScenario8_UserPersonalization() {
        when(feedbackRepository.findRecentUsedTexts(eq(currentUserId), any(PageRequest.class)))
                .thenReturn(List.of("Haha definitely!", "Sounds fun 😄", "Coffee works!"));

        ContextMessage cm = new ContextMessage(1L, true, "Hey!", Instant.now());
        UserWritingProfile profile = userStyleEngine.analyzeStyle(currentUserId, List.of(cm), "ENGLISH");

        assertThat(profile.length()).isEqualTo(UserWritingProfile.LengthPreference.SHORT);
        assertThat(profile.promptDirectives()).contains("short");
    }

    @Test
    @DisplayName("Scenario 9 — Conversation Gap / Reconnection: Inactive for 3 days results in RECONNECTING stage")
    void testScenario9_ReconnectionAfterGap() {
        Instant threeDaysAgo = Instant.now().minus(72, ChronoUnit.HOURS);
        List<Message> msgs = List.of(
                createMsg(partnerUserId, "See you soon!", threeDaysAgo),
                createMsg(currentUserId, "Awesome, take care!", threeDaysAgo.minusSeconds(60))
        );
        when(messageRepository.findRecentMessages(10L, 40)).thenReturn(msgs);

        ReplySuggestionResponse response = replyCoachService.getReplySuggestions(conversationId, currentUserId, 3);

        assertThat(response.getConversationState().getStage()).isEqualTo("RECONNECTING");
        boolean hasReconnectionText = response.getSuggestions().stream()
                .anyMatch(s -> s.getText().contains("How have") || s.getText().contains("what have you been up to"));
        assertThat(hasReconnectionText).isTrue();
        assertThat(response.getSuggestions()).noneMatch(s ->
                s.getText().contains("finally") || s.getText().contains("remembered me"));
    }

    @Test
    @DisplayName("Scenario 10 — Language & Hinglish: Detects Hinglish and drafts natural Hinglish replies")
    void testScenario10_HinglishLanguageReplies() {
        List<Message> msgs = List.of(
                createMsg(partnerUserId, "Arre yaar aaj ka din bohot mast raha!", Instant.now().minusSeconds(10)),
                createMsg(currentUserId, "Sach me? Aisa kya hua bhai?", Instant.now().minusSeconds(40))
        );
        when(messageRepository.findRecentMessages(10L, 40)).thenReturn(msgs);

        ReplySuggestionResponse response = replyCoachService.getReplySuggestions(conversationId, currentUserId, 3);

        assertThat(response.getConversationState().getLanguage()).isEqualTo("HINGLISH");
        boolean hasHinglish = response.getSuggestions().stream()
                .anyMatch(s -> s.getText().contains("waah") || s.getText().contains("batao") || s.getText().contains("sahi") || s.getText().contains("kaha"));
        assertThat(hasHinglish).isTrue();
    }
}
