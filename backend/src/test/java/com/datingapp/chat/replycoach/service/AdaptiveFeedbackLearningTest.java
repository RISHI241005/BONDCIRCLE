package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.model.ConversationContext.ContextMessage;
import com.datingapp.chat.replycoach.model.ConversationEnvironment;
import com.datingapp.chat.replycoach.model.ReplyStrategy;
import com.datingapp.chat.replycoach.model.UserWritingProfile;
import com.datingapp.chat.replycoach.repository.AiReplyFeedbackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdaptiveFeedbackLearningTest {

    @Mock
    private AiReplyFeedbackRepository feedbackRepository;

    private UserStyleEngine userStyleEngine;
    private ReplyCoachPromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        userStyleEngine = new UserStyleEngine(feedbackRepository);
        promptBuilder = new ReplyCoachPromptBuilder();
    }

    @Test
    @DisplayName("Should learn negative directives when user repeatedly rejects formal or robotic suggestions")
    void testNegativeDirectivesFromRejectedFeedback() {
        Long userId = 42L;
        List<String> rejectedTexts = List.of(
                "That sounds truly delightful and fascinating!",
                "Could you please elaborate on your thoughts?",
                "How are you doing today? Tell me more."
        );

        when(feedbackRepository.findRecentUsedTexts(eq(userId), any(Pageable.class)))
                .thenReturn(List.of());
        when(feedbackRepository.findRecentRejectedTexts(eq(userId), any(Pageable.class)))
                .thenReturn(rejectedTexts);

        UserWritingProfile profile = userStyleEngine.analyzeStyle(userId, List.of(), "ENGLISH");

        assertFalse(profile.negativeDirectives().isBlank());
        assertTrue(profile.negativeDirectives().contains("Avoid formal or overly enthusiastic assistant tone"));
    }

    @Test
    @DisplayName("Should learn positive preferences from used feedback (short length, emojis, strategies)")
    void testPositiveLearningFromUsedFeedback() {
        Long userId = 42L;
        List<String> usedTexts = List.of(
                "Haha nice one 😂",
                "Kya chal raha hai?",
                "Btw which movie? 😂"
        );

        when(feedbackRepository.findRecentUsedTexts(eq(userId), any(Pageable.class)))
                .thenReturn(usedTexts);
        when(feedbackRepository.findRecentRejectedTexts(eq(userId), any(Pageable.class)))
                .thenReturn(List.of());

        UserWritingProfile profile = userStyleEngine.analyzeStyle(userId, List.of(), "HINGLISH");

        assertTrue(profile.length() == UserWritingProfile.LengthPreference.SHORT);
        assertTrue(profile.emojiUsage() == UserWritingProfile.EmojiUsage.FREQUENT);
        assertTrue(profile.preferredStrategies().contains(ReplyStrategy.PLAYFUL)
                || profile.preferredStrategies().contains(ReplyStrategy.ASK_FOLLOWUP));
    }

    @Test
    @DisplayName("Prompt builder should inject negative directives and learned style into the prompt")
    void testPromptBuilderInjectsLearnedDirectives() {
        Long userId = 42L;
        List<String> rejected = List.of("That sounds delightful!");
        List<String> used = List.of("Haha nice 😂");

        when(feedbackRepository.findRecentUsedTexts(eq(userId), any(Pageable.class))).thenReturn(used);
        when(feedbackRepository.findRecentRejectedTexts(eq(userId), any(Pageable.class))).thenReturn(rejected);

        List<ContextMessage> messages = List.of(
                new ContextMessage(1L, true, "Bro what's up?", Instant.now().minusSeconds(60))
        );

        UserWritingProfile profile = userStyleEngine.analyzeStyle(userId, messages, "ENGLISH");
        ConversationEnvironment env = ConversationEnvironment.freshMatch("ENGLISH");

        String systemPrompt = promptBuilder.buildSystemPrompt(3, env, profile, null);

        assertTrue(systemPrompt.contains("NEGATIVE SIGNALS (USER HISTORICALLY DISLIKES)"));
        assertTrue(systemPrompt.contains("Avoid formal or overly enthusiastic assistant tone"));
        assertTrue(systemPrompt.contains("USER'S STYLE & PREFERENCES"));
    }
}
