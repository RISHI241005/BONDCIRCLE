package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.dto.ReplySuggestionItem;
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
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplyRankerTest {

    private ReplyRanker ranker;
    private ConversationEnvironment env;
    private UserWritingProfile profile;

    @BeforeEach
    void setUp() {
        ranker = new ReplyRanker();
        env = new ConversationEnvironment(
                Stage.GETTING_TO_KNOW,
                Momentum.STABLE,
                RelationshipSignal.BUILDING,
                LastSpeaker.OTHER_USER,
                ResponseExpectation.ANSWER_REQUIRED,
                Temperature.WARM,
                Direction.CONTINUING_TOPIC,
                QuestionState.QUESTION_ASKED,
                List.of(EngagementSignal.BALANCED_INITIATIVE),
                Depth.PERSONAL,
                "Movies",
                Collections.emptyList(),
                List.of("Cinema", "Directors"),
                "ENGLISH",
                false,
                true,
                "What kind of movies do you enjoy?",
                1,
                false,
                false
        );
        profile = UserWritingProfile.defaults();
    }

    @Test
    @DisplayName("Should score higher for replies matching topic and planned strategy")
    void testScoringRelevanceAndStrategy() {
        ReplySuggestionItem relevantItem = new ReplySuggestionItem(
                "1",
                "I really love thriller movies, how about you?",
                "Movies",
                "CASUAL",
                "ANSWER",
                "Casual"
        );

        ReplySuggestionItem offTopicItem = new ReplySuggestionItem(
                "2",
                "Yeah okay cool.",
                "Movies",
                "DRY",
                "ACKNOWLEDGE",
                "Dry"
        );

        double score1 = ranker.scoreCandidate(relevantItem, env, profile, List.of(ReplyStrategy.ANSWER), Collections.emptyList());
        double score2 = ranker.scoreCandidate(offTopicItem, env, profile, List.of(ReplyStrategy.ANSWER), Collections.emptyList());

        assertTrue(score1 > score2, "Relevant answer should score significantly higher than dry off-topic reply");
    }

    @Test
    @DisplayName("Should prune lexically near-identical candidates using Jaccard similarity")
    void testDiversityPruningJaccard() {
        ReplySuggestionItem item1 = new ReplySuggestionItem(
                "1",
                "Haha that sounds awesome, which movie was it?",
                "Movies",
                "PLAYFUL",
                "ASK_FOLLOWUP",
                "Playful"
        );
        ReplySuggestionItem item2 = new ReplySuggestionItem(
                "2",
                "Haha that sounds awesome, what movie was that?",
                "Movies",
                "PLAYFUL",
                "ASK_FOLLOWUP",
                "Playful"
        );
        ReplySuggestionItem item3 = new ReplySuggestionItem(
                "3",
                "I am a huge Christopher Nolan fan, especially Interstellar!",
                "Movies",
                "ENTHUSIASTIC",
                "ANSWER",
                "Enthusiastic"
        );

        List<ReplySuggestionItem> ranked = ranker.rankAndFilter(
                List.of(item1, item2, item3),
                env,
                profile,
                List.of(ReplyStrategy.ANSWER, ReplyStrategy.ASK_FOLLOWUP),
                Collections.emptyList(),
                2
        );

        // One of the near-duplicates should be filtered out, leaving item3 and one of item1/item2
        assertEquals(2, ranked.size());
        assertTrue(ranked.contains(item3));
        assertFalse(ranked.contains(item1) && ranked.contains(item2), "Ranker should not select both near duplicates");
    }

    @Test
    @DisplayName("Should penalize candidates that match previously rejected replies")
    void testNoveltyPenalizesRejected() {
        String rejectedText = "I really love thriller movies, how about you?";
        ReplySuggestionItem rejectedItem = new ReplySuggestionItem(
                "1",
                rejectedText,
                "Movies",
                "CASUAL",
                "ANSWER",
                "Casual"
        );

        double scoreWithRejection = ranker.scoreCandidate(rejectedItem, env, profile, List.of(ReplyStrategy.ANSWER), List.of(rejectedText));
        double scoreWithoutRejection = ranker.scoreCandidate(rejectedItem, env, profile, List.of(ReplyStrategy.ANSWER), Collections.emptyList());

        assertTrue(scoreWithRejection < scoreWithoutRejection, "Previously rejected text should receive a novelty penalty");
    }

    @Test
    @DisplayName("Should reward preferred strategies and penalize rejected strategies")
    void testFeedbackPreferenceScoring() {
        UserWritingProfile customProfile = new UserWritingProfile(
                UserWritingProfile.LengthPreference.MEDIUM,
                UserWritingProfile.EmojiUsage.OCCASIONAL,
                "ENGLISH",
                UserWritingProfile.Formality.CASUAL,
                false,
                true,
                "Casual",
                35.0,
                30,
                Collections.emptySet(),
                Collections.emptyList(),
                0.0,
                Set.of(ReplyStrategy.ANSWER),
                Set.of(ReplyStrategy.BANTER),
                "No banter"
        );

        ReplySuggestionItem answerItem = new ReplySuggestionItem("1", "I love sci-fi!", "Movies", "WARM", "ANSWER", "Warm");
        ReplySuggestionItem banterItem = new ReplySuggestionItem("2", "Only if you have good taste!", "Movies", "PLAYFUL", "BANTER", "Playful");

        double answerScore = ranker.scoreCandidate(answerItem, env, customProfile, List.of(ReplyStrategy.ANSWER), Collections.emptyList());
        double banterScore = ranker.scoreCandidate(banterItem, env, customProfile, List.of(ReplyStrategy.ANSWER), Collections.emptyList());

        assertTrue(answerScore > banterScore, "Preferred strategy ANSWER should score higher than rejected strategy BANTER");
    }
}
