package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.model.ConversationEnvironment;
import com.datingapp.chat.replycoach.model.ConversationEnvironment.Depth;
import com.datingapp.chat.replycoach.model.ConversationEnvironment.Direction;
import com.datingapp.chat.replycoach.model.ConversationEnvironment.EngagementSignal;
import com.datingapp.chat.replycoach.model.ConversationEnvironment.LastSpeaker;
import com.datingapp.chat.replycoach.model.ConversationEnvironment.Momentum;
import com.datingapp.chat.replycoach.model.ConversationEnvironment.QuestionState;
import com.datingapp.chat.replycoach.model.ConversationEnvironment.RelationshipSignal;
import com.datingapp.chat.replycoach.model.ConversationEnvironment.ResponseExpectation;
import com.datingapp.chat.replycoach.model.ConversationEnvironment.Stage;
import com.datingapp.chat.replycoach.model.ConversationEnvironment.Temperature;
import com.datingapp.chat.replycoach.model.ReplyStrategy;
import com.datingapp.chat.replycoach.model.UserWritingProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplyIntentPlannerTest {

    private ReplyIntentPlanner planner;
    private UserWritingProfile defaultProfile;

    @BeforeEach
    void setUp() {
        planner = new ReplyIntentPlanner();
        defaultProfile = UserWritingProfile.defaults();
    }

    private ConversationEnvironment createEnv(
            Stage stage,
            Temperature temp,
            Direction dir,
            boolean isDry,
            boolean hasUnansweredQ,
            String qText) {
        return new ConversationEnvironment(
                stage,
                Momentum.STABLE,
                RelationshipSignal.BUILDING,
                LastSpeaker.OTHER_USER,
                hasUnansweredQ ? ResponseExpectation.ANSWER_REQUIRED : ResponseExpectation.OPEN_TOPIC,
                temp,
                dir,
                hasUnansweredQ ? QuestionState.QUESTION_ASKED : QuestionState.NO_QUESTION,
                List.of(EngagementSignal.BALANCED_INITIATIVE),
                Depth.PERSONAL,
                "Movies",
                Collections.emptyList(),
                List.of("Weekend Plans"),
                "ENGLISH",
                isDry,
                hasUnansweredQ,
                qText,
                1,
                false,
                false
        );
    }

    @Test
    @DisplayName("Should plan distinct strategies for unanswered question")
    void testUnansweredQuestionPlanning() {
        ConversationEnvironment env = createEnv(
                Stage.GETTING_TO_KNOW,
                Temperature.WARM,
                Direction.CONTINUING_TOPIC,
                false,
                true,
                "What do you like to do on weekends?"
        );

        ReplyIntentPlanner.ReplyIntentPlan plan = planner.planIntents(env, defaultProfile);

        assertFalse(plan.intents().isEmpty());
        assertEquals(3, plan.intents().size());

        List<ReplyStrategy> strategies = plan.getStrategies();
        // Strategy list should have 3 distinct entries
        assertEquals(3, new HashSet<>(strategies).size());
        assertTrue(strategies.contains(ReplyStrategy.ANSWER));
        assertTrue(strategies.contains(ReplyStrategy.ASK_FOLLOWUP) || strategies.contains(ReplyStrategy.PLAYFUL));
    }

    @Test
    @DisplayName("Should plan revitalizing strategies when conversation becomes dry")
    void testDryConversationPlanning() {
        ConversationEnvironment env = createEnv(
                Stage.DRY,
                Temperature.COLD,
                Direction.CHANGING_TOPIC,
                true,
                false,
                null
        );

        ReplyIntentPlanner.ReplyIntentPlan plan = planner.planIntents(env, defaultProfile);

        assertEquals(3, plan.intents().size());
        List<ReplyStrategy> strategies = plan.getStrategies();
        assertEquals(3, new HashSet<>(strategies).size());

        assertTrue(strategies.contains(ReplyStrategy.RE_OPENER) || strategies.contains(ReplyStrategy.BANTER));
    }

    @Test
    @DisplayName("Should plan empathetic and supportive strategies during emotional moments")
    void testEmotionalSupportPlanning() {
        ConversationEnvironment env = createEnv(
                Stage.SUPPORTIVE,
                Temperature.EMOTIONAL,
                Direction.CONTINUING_TOPIC,
                false,
                false,
                null
        );

        ReplyIntentPlanner.ReplyIntentPlan plan = planner.planIntents(env, defaultProfile);

        assertEquals(3, plan.intents().size());
        List<ReplyStrategy> strategies = plan.getStrategies();
        assertEquals(3, new HashSet<>(strategies).size());

        assertTrue(strategies.contains(ReplyStrategy.EMPATHIZE) || strategies.contains(ReplyStrategy.SUPPORTIVE));
    }

    @Test
    @DisplayName("Should plan reconnection strategies after a long gap")
    void testReconnectionPlanning() {
        ConversationEnvironment env = createEnv(
                Stage.RECONNECTING,
                Temperature.WARM,
                Direction.RECONNECTING,
                false,
                false,
                null
        );

        ReplyIntentPlanner.ReplyIntentPlan plan = planner.planIntents(env, defaultProfile);

        assertEquals(3, plan.intents().size());
        List<ReplyStrategy> strategies = plan.getStrategies();
        assertEquals(3, new HashSet<>(strategies).size());

        assertTrue(strategies.contains(ReplyStrategy.RECONNECT));
    }

    @Test
    @DisplayName("Should honor user's rejected strategies and select suitable alternates")
    void testPlanningWithRejectedStrategies() {
        ConversationEnvironment env = createEnv(
                Stage.DRY,
                Temperature.COLD,
                Direction.CHANGING_TOPIC,
                true,
                false,
                null
        );

        // User dislikes BANTER and RE_OPENER
        UserWritingProfile customProfile = new UserWritingProfile(
                UserWritingProfile.LengthPreference.MEDIUM,
                UserWritingProfile.EmojiUsage.OCCASIONAL,
                "ENGLISH",
                UserWritingProfile.Formality.CASUAL,
                false,
                true,
                "Casual tone",
                30.0,
                25,
                Collections.emptySet(),
                Collections.emptyList(),
                0.0,
                Collections.emptySet(),
                Set.of(ReplyStrategy.BANTER, ReplyStrategy.RE_OPENER),
                "No banter"
        );

        ReplyIntentPlanner.ReplyIntentPlan plan = planner.planIntents(env, customProfile);

        assertEquals(3, plan.intents().size());
        List<ReplyStrategy> strategies = plan.getStrategies();
        assertFalse(strategies.contains(ReplyStrategy.BANTER));
        assertFalse(strategies.contains(ReplyStrategy.RE_OPENER));
    }
}
