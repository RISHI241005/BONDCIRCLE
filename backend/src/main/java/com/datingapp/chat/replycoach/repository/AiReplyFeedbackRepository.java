package com.datingapp.chat.replycoach.repository;

import com.datingapp.chat.replycoach.entity.AiReplyFeedback;
import com.datingapp.chat.replycoach.entity.FeedbackAction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiReplyFeedbackRepository extends JpaRepository<AiReplyFeedback, Long> {

    List<AiReplyFeedback> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<AiReplyFeedback> findByUserIdAndActionOrderByCreatedAtDesc(Long userId, FeedbackAction action, Pageable pageable);

    @Query("SELECT f.suggestionText FROM AiReplyFeedback f WHERE f.userId = :userId AND f.action = :action ORDER BY f.createdAt DESC")
    List<String> findRecentTextsByAction(@Param("userId") Long userId, @Param("action") FeedbackAction action, Pageable pageable);

    default List<String> findRecentUsedTexts(Long userId, Pageable pageable) {
        return findRecentTextsByAction(userId, FeedbackAction.USED, pageable);
    }

    @Query("SELECT f.suggestionText FROM AiReplyFeedback f WHERE f.userId = :userId AND f.action IN :actions ORDER BY f.createdAt DESC")
    List<String> findRecentTextsByActions(
            @Param("userId") Long userId,
            @Param("actions") List<FeedbackAction> actions,
            Pageable pageable);

    default List<String> findRecentPositiveTexts(Long userId, Pageable pageable) {
        return findRecentTextsByActions(userId,
                List.of(FeedbackAction.USED, FeedbackAction.COPIED, FeedbackAction.EDITED,
                        FeedbackAction.SENT, FeedbackAction.LIKED), pageable);
    }

    default List<String> findRecentRejectedTexts(Long userId, Pageable pageable) {
        return findRecentTextsByAction(userId, FeedbackAction.REJECTED, pageable);
    }

    @Query("SELECT DISTINCT f.suggestionText FROM AiReplyFeedback f WHERE f.suggestionId IN :suggestionIds")
    List<String> findSuggestionTextsByIds(@Param("suggestionIds") List<String> suggestionIds);
}
