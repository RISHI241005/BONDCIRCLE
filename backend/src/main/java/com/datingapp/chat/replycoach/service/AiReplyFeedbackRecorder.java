package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.entity.AiReplyFeedback;
import com.datingapp.chat.replycoach.entity.FeedbackAction;
import com.datingapp.chat.replycoach.repository.AiReplyFeedbackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class AiReplyFeedbackRecorder {

    private static final Logger log = LoggerFactory.getLogger(AiReplyFeedbackRecorder.class);
    private final AiReplyFeedbackRepository feedbackRepository;

    public AiReplyFeedbackRecorder(AiReplyFeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBatch(List<AiReplyFeedback> records) {
        if (records == null || records.isEmpty()) return;
        try {
            feedbackRepository.saveAll(records);
            log.debug("Persisted {} feedback records in dedicated transaction", records.size());
        } catch (Exception ex) {
            log.warn("Failed to persist AI reply feedback batch: {}", ex.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSingle(Long userId, String conversationId, String suggestionId, String text, FeedbackAction action) {
        try {
            feedbackRepository.save(new AiReplyFeedback(
                    userId,
                    conversationId,
                    suggestionId,
                    text != null ? text : "",
                    action != null ? action : FeedbackAction.SHOWN
            ));
            log.debug("Persisted feedback record: user={}, action={}", userId, action);
        } catch (Exception ex) {
            log.warn("Failed to persist AI reply feedback single record: {}", ex.getMessage());
        }
    }
}
