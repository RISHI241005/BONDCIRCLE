package com.datingapp.chat.presence.service.impl;

import com.datingapp.chat.presence.dto.UserPresenceResponse;
import com.datingapp.chat.presence.model.PresenceStatus;
import com.datingapp.chat.presence.model.UserPresence;
import com.datingapp.chat.presence.service.PresenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory thread-safe implementation of PresenceService.
 * Supports multiple concurrent device sessions per user (e.g. Android phone + iOS tablet).
 */
@Service
public class InMemoryPresenceService implements PresenceService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryPresenceService.class);

    private final Map<Long, UserPresence> userPresenceMap = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionUserMap = new ConcurrentHashMap<>();

    @Override
    public void registerSession(Long userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return;
        }

        sessionUserMap.put(sessionId, userId);
        UserPresence presence = userPresenceMap.computeIfAbsent(userId, UserPresence::new);

        boolean wasOffline = !presence.isOnline();
        presence.addSession(sessionId);

        if (wasOffline) {
            log.info("User {} entered ONLINE state (Session: {})", userId, sessionId);
        } else {
            log.debug("User {} added secondary active session: {} (Total sessions: {})",
                    userId, sessionId, presence.getSessionCount());
        }
    }

    @Override
    public void unregisterSession(String sessionId) {
        if (sessionId == null) {
            return;
        }

        Long userId = sessionUserMap.remove(sessionId);
        if (userId != null) {
            UserPresence presence = userPresenceMap.get(userId);
            if (presence != null) {
                presence.removeSession(sessionId);
                if (!presence.isOnline()) {
                    presence.setLastSeenAt(Instant.now());
                    log.info("User {} is now OFFLINE. Last seen updated to: {}", userId, presence.getLastSeenAt());
                } else {
                    log.debug("User {} closed session: {}. Remaining sessions: {}",
                            userId, sessionId, presence.getSessionCount());
                }
            }
        }
    }

    @Override
    public UserPresenceResponse getUserPresence(Long targetUserId, Long requesterUserId) {
        UserPresence presence = userPresenceMap.get(targetUserId);
        if (presence == null) {
            return new UserPresenceResponse(targetUserId, PresenceStatus.OFFLINE, null);
        }

        Instant lastSeen = presence.isOnline() ? null : presence.getLastSeenAt();
        return new UserPresenceResponse(targetUserId, presence.getStatus(), lastSeen);
    }

    @Override
    public boolean isUserOnline(Long userId) {
        if (userId == null) {
            return false;
        }
        UserPresence presence = userPresenceMap.get(userId);
        return presence != null && presence.isOnline();
    }
}
