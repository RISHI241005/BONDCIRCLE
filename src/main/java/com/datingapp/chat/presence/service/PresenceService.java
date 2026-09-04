package com.datingapp.chat.presence.service;

import com.datingapp.chat.presence.dto.UserPresenceResponse;

/**
 * Presence abstraction service tracking user online/offline status and last seen timestamps.
 * Designed for seamless transition to Redis in distributed environments.
 */
public interface PresenceService {

    /**
     * Registers a new active WebSocket session for a user.
     */
    void registerSession(Long userId, String sessionId);

    /**
     * Unregisters an active WebSocket session when disconnected.
     */
    void unregisterSession(String sessionId);

    /**
     * Retrieves presence state and last seen details for a target user.
     */
    UserPresenceResponse getUserPresence(Long targetUserId, Long requesterUserId);

    /**
     * Checks if a user has at least one active connection.
     */
    boolean isUserOnline(Long userId);
}
