package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.model.ConversationContext.ContextMessage;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserStyleEngineTest {

    @Mock
    private AiReplyFeedbackRepository feedbackRepository;

    private UserStyleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new UserStyleEngine(feedbackRepository);
    }

    @Test
    @DisplayName("Learns short length preference from feedback history")
    void testLearnsFromFeedback() {
        when(feedbackRepository.findRecentUsedTexts(eq(101L), any(Pageable.class)))
                .thenReturn(List.of("Yeah!", "Sounds good", "Totally 😊"));

        List<ContextMessage> messages = List.of(
                new ContextMessage("1", true, "Hey!", Instant.now())
        );

        UserWritingProfile profile = engine.analyzeStyle(101L, messages, "ENGLISH");
        assertThat(profile.length()).isEqualTo(UserWritingProfile.LengthPreference.SHORT);
        assertThat(profile.emojiUsage()).isEqualTo(UserWritingProfile.EmojiUsage.FREQUENT);
        assertThat(profile.promptDirectives()).contains("short, punchy replies");
    }

    @Test
    @DisplayName("Analyzes user sentence length and emoji usage from sent messages")
    void testAnalyzesSentMessages() {
        when(feedbackRepository.findRecentUsedTexts(eq(101L), any(Pageable.class)))
                .thenReturn(List.of());

        List<ContextMessage> messages = List.of(
                new ContextMessage("1", false, "Hey what's up?", Instant.now().minusSeconds(30)),
                new ContextMessage("2", true, "Not much, just finished work! 😊", Instant.now().minusSeconds(10))
        );

        UserWritingProfile profile = engine.analyzeStyle(101L, messages, "ENGLISH");
        assertThat(profile.language()).isEqualTo("ENGLISH");
        assertThat(profile.promptDirectives()).isNotEmpty();
    }
}
