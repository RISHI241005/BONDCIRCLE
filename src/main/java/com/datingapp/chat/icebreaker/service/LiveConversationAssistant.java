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

    record ReplyBatch(String guidance, List<Reply> replies) {
    }
}
