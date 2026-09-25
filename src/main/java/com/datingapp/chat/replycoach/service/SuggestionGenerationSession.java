package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.dto.ReplySuggestionItem;
import com.datingapp.chat.replycoach.entity.FeedbackAction;
import com.datingapp.chat.replycoach.model.ReplyStrategy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived server-side memory for refreshes. Sessions are scoped to a user,
 * conversation and latest-message id, expire automatically, and never replace
 * durable feedback storage.
 */
@Service
public class SuggestionGenerationSession {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final int MAX_SESSIONS = 2_000;

    private final ConcurrentHashMap<SessionKey, State> sessions = new ConcurrentHashMap<>();

    public Snapshot begin(
            Long userId,
            String conversationId,
            String latestMessageId,
            boolean regeneration) {
        cleanupIfNeeded();
        SessionKey key = new SessionKey(userId, conversationId);
        State state = sessions.compute(key, (ignored, current) -> {
            boolean stale = current == null
                    || current.isExpired()
                    || !safeEquals(current.latestMessageId, latestMessageId)
                    || !regeneration;
            if (stale) return new State(conversationId, latestMessageId, regeneration ? 2 : 1);
            synchronized (current) {
                current.generationNumber++;
                current.updatedAt = Instant.now();
            }
            return current;
        });
        return state.snapshot();
    }

    public void recordGeneration(
            Long userId,
            String conversationId,
            String latestMessageId,
            List<ReplySuggestionItem> suggestions) {
        State state = sessions.get(new SessionKey(userId, conversationId));
        if (state == null || !safeEquals(state.latestMessageId, latestMessageId) || suggestions == null) return;
        synchronized (state) {
            for (ReplySuggestionItem item : suggestions) {
                if (item == null) continue;
                if (item.getText() != null && !item.getText().isBlank()) {
                    state.previousSuggestionTexts.add(item.getText().trim());
                }
                ReplyStrategy strategy = parseStrategy(item.getStrategy());
                if (strategy != null) state.previousStrategies.add(strategy);
            }
            state.updatedAt = Instant.now();
        }
    }

    public void recordRejected(Long userId, String conversationId, List<String> rejectedTexts) {
        State state = sessions.get(new SessionKey(userId, conversationId));
        if (state == null || rejectedTexts == null) return;
        synchronized (state) {
            rejectedTexts.stream()
                    .filter(text -> text != null && !text.isBlank())
                    .map(String::trim)
                    .forEach(state.rejectedSuggestions::add);
            state.updatedAt = Instant.now();
        }
    }

    public void recordFeedback(
            Long userId,
            String conversationId,
            String suggestionText,
            FeedbackAction action) {
        State state = sessions.get(new SessionKey(userId, conversationId));
        if (state == null || suggestionText == null || suggestionText.isBlank() || action == null) return;
        synchronized (state) {
            String text = suggestionText.trim();
            if (action == FeedbackAction.REJECTED) {
                state.rejectedSuggestions.add(text);
            } else if (action == FeedbackAction.USED || action == FeedbackAction.COPIED
                    || action == FeedbackAction.EDITED || action == FeedbackAction.SENT
                    || action == FeedbackAction.LIKED) {
                state.selectedSuggestions.add(text);
            }
            state.updatedAt = Instant.now();
        }
    }

    private ReplyStrategy parseStrategy(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return ReplyStrategy.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void cleanupIfNeeded() {
        if (sessions.size() < MAX_SESSIONS) {
            sessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
            return;
        }
        sessions.clear();
    }

    private boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    public record Snapshot(
            String conversationId,
            String latestMessageId,
            List<String> previousSuggestionTexts,
            Set<ReplyStrategy> previousStrategies,
            int generationNumber,
            List<String> rejectedSuggestions,
            List<String> selectedSuggestions
    ) {}

    private record SessionKey(Long userId, String conversationId) {}

    private static final class State {
        private final String conversationId;
        private final String latestMessageId;
        private final LinkedHashSet<String> previousSuggestionTexts = new LinkedHashSet<>();
        private final LinkedHashSet<ReplyStrategy> previousStrategies = new LinkedHashSet<>();
        private final LinkedHashSet<String> rejectedSuggestions = new LinkedHashSet<>();
        private final LinkedHashSet<String> selectedSuggestions = new LinkedHashSet<>();
        private int generationNumber;
        private Instant updatedAt = Instant.now();

        private State(String conversationId, String latestMessageId, int generationNumber) {
            this.conversationId = conversationId;
            this.latestMessageId = latestMessageId;
            this.generationNumber = generationNumber;
        }

        private boolean isExpired() {
            return updatedAt.plus(TTL).isBefore(Instant.now());
        }

        private synchronized Snapshot snapshot() {
            return new Snapshot(
                    conversationId,
                    latestMessageId,
                    List.copyOf(previousSuggestionTexts),
                    Set.copyOf(previousStrategies),
                    generationNumber,
                    new ArrayList<>(rejectedSuggestions),
                    new ArrayList<>(selectedSuggestions)
            );
        }
    }
}
