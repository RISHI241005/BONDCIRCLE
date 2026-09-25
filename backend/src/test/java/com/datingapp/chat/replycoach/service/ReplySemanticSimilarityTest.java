package com.datingapp.chat.replycoach.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplySemanticSimilarityTest {

    private final ReplySemanticSimilarity similarity = new ReplySemanticSimilarity();

    @Test
    void recognizesDayQuestionParaphrasesAsSameIdea() {
        assertThat(similarity.areSemanticallySimilar(
                "How was your day?", "How did today go for you?"))
                .isTrue();
    }

    @Test
    void keepsMeaningfullyDifferentAssignmentAngles() {
        assertThat(similarity.areSemanticallySimilar(
                "Are the assignments done now?", "Which subject was the worst?"))
                .isFalse();
    }

    @Test
    void identifiesRoboticGenericReplies() {
        assertThat(similarity.isGeneric("Tell me more.")).isTrue();
        assertThat(similarity.isGeneric("Which assignment was the worst?")).isFalse();
    }
}
