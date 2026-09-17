package com.datingapp.chat.icebreaker.service;

import java.util.List;
import java.util.Optional;

public interface LiveConversationAssistant {

    Optional<ReplyBatch> generate(GenerationRequest request);

    record ConversationTurn(String speaker, String text) {
    }

    record GenerationRequest(
            Long userId,
            String conversationId,
            String context,
            String language,
            String tone,
            String mode,
            int count,
            int variation,
            List<String> myInterests,
            List<String> theirInterests,
            List<ConversationTurn> history) {
    }

    record Reply(
            String text,
            String topic,
            String reason,
            String circle,
            String tone,
            String language) {
    }

    record ReplyBatch(
            String guidance,
            List<Reply> replies,
            String shouldReply,
            String urgency,
            String decisionReason,
            String replyTiming,
            String detectedMood,
            String conversationScenario) {
        public ReplyBatch(
                String guidance,
                List<Reply> replies,
                String shouldReply,
                String urgency,
                String decisionReason,
                String replyTiming) {
            this(guidance, replies, shouldReply, urgency, decisionReason, replyTiming, "Casual & Natural", "General flow");
        }

        public ReplyBatch(String guidance, List<Reply> replies) {
            this(guidance, replies, "RECOMMENDED", "MEDIUM", guidance, "Whenever you're ready", "Casual & Natural", "General flow");
        }
    }
}
