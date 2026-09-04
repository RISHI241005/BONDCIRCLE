package com.datingapp.chat.presence.model;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory representation of user presence supporting multiple concurrent device sessions.
 */
public class UserPresence {

    private final Long userId;
    private final Set<String> sessionIds = ConcurrentHashMap.newKeySet();
    private Instant lastSeenAt;

    public UserPresence(Long userId) {
        this.userId = userId;
        this.lastSeenAt = Instant.now();
    }

    public void addSession(String sessionId) {
        sessionIds.add(sessionId);
    }

    public void removeSession(String sessionId) {
        sessionIds.remove(sessionId);
        if (sessionIds.isEmpty()) {
            this.lastSeenAt = Instant.now();
        }
    }

    public boolean isOnline() {
        return !sessionIds.isEmpty();
    }

    public PresenceStatus getStatus() {
        return isOnline() ? PresenceStatus.ONLINE : PresenceStatus.OFFLINE;
    }

    public Long getUserId() {
        return userId;
    }

    public int getSessionCount() {
        return sessionIds.size();
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
