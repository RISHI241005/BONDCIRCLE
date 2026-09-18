package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.dto.ReplyFeedbackRequest;
import com.datingapp.chat.replycoach.dto.ReplySuggestionResponse;

import java.util.List;

public interface ReplyCoachService {

    /**
     * Analyzes recent two-sided conversation and suggests up to 3 natural, ready-to-send replies.
     */
    ReplySuggestionResponse getReplySuggestions(String conversationId, Long userId, int limit);

    /**
     * Regenerates up to 3 new replies while strictly avoiding rejected suggestions.
     */
    ReplySuggestionResponse regenerateReplySuggestions(
            String conversationId,
            Long userId,
            List<String> rejectedSuggestionIds,
            List<String> rejectedTexts,
            int limit);

    /**
     * Records lightweight user feedback (SHOWN, USED, REJECTED, COPIED, EDITED, SENT).
     */
    void recordFeedback(Long userId, ReplyFeedbackRequest request);
}
