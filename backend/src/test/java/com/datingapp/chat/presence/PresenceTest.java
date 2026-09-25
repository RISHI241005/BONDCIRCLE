package com.datingapp.chat.presence;

import com.datingapp.chat.presence.service.PresenceService;
import com.datingapp.chat.presence.service.impl.InMemoryPresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Presence unit tests verifying online/offline state tracking,
 * multiple session handling, and presence DTO generation.
 */
@ExtendWith(MockitoExtension.class)
class PresenceTest {

    private com.datingapp.chat.presence.service.impl.InMemoryPresenceService presence;

    @BeforeEach
    void setUp() {
        presence = new com.datingapp.chat.presence.service.impl.InMemoryPresenceService();
    }

    @Test
    @DisplayName("User goes ONLINE when WebSocket connection established")
    void testUserGoesOnline() {
        presence.registerSession(101L, "session-1");

        assertTrue(presence.isUserOnline(101L));
        assertEquals(com.datingapp.chat.presence.model.PresenceStatus.ONLINE, presence.getUserPresence(101L, null).getStatus());
    }

    @Test
    @DisplayName("User stays ONLINE when one of multiple connections closes")
    void testUserStaysOnlineWithMultipleConnections() {
        presence.registerSession(101L, "session-1");
        presence.registerSession(101L, "session-2");

        assertTrue(presence.isUserOnline(101L));
        assertTrue(presence.getUserPresence(101L, null).getStatus() == com.datingapp.chat.presence.model.PresenceStatus.ONLINE);

        // Close one connection
        presence.unregisterSession("session-1");

        assertTrue(presence.isUserOnline(101L)); // Should still be online
    }

    @Test
    @DisplayName("User goes OFFLINE when last connection closes")
    void testUserGoesOfflineWhenLastConnectionCloses() {
        presence.registerSession(101L, "session-1");

        // Close the only connection
        presence.unregisterSession("session-1");

        assertFalse(presence.isUserOnline(101L));
        assertEquals(com.datingapp.chat.presence.model.PresenceStatus.OFFLINE, presence.getUserPresence(101L, null).getStatus());
        assertNotNull(presence.getUserPresence(101L, null).getLastSeenAt());
    }

    @Test
    @DisplayName("User can reconnect and go back ONLINE")
    void testUserReconnectsGoesOnline() {
        // User was online, then disconnects
        presence.registerSession(101L, "session-1");
        presence.unregisterSession("session-1");

        assertFalse(presence.isUserOnline(101L));
        assertEquals(com.datingapp.chat.presence.model.PresenceStatus.OFFLINE, presence.getUserPresence(101L, null).getStatus());

        // User reconnects
        presence.registerSession(101L, "session-2");

        assertTrue(presence.isUserOnline(101L));
        assertEquals(com.datingapp.chat.presence.model.PresenceStatus.ONLINE, presence.getUserPresence(101L, null).getStatus());
    }

    @Test
    @DisplayName("Online user presence is returned correctly (lastSeenAt null when online)")
    void testOnlineUserPresence() {
        presence.registerSession(101L, "session-1");

        var p = presence.getUserPresence(101L, null);
        assertNotNull(p);
        assertEquals(com.datingapp.chat.presence.model.PresenceStatus.ONLINE, p.getStatus());
        assertNull(p.getLastSeenAt()); // null when online
    }

    @Test
    @DisplayName("Offline user presence is returned correctly")
    void testOfflineUserPresence() {
        // No sessions registered - user is offline by default (not in the system)

        var p = presence.getUserPresence(101L, null);
        assertNotNull(p);
        // User not in the system returns null lastSeenAt
        assertEquals(com.datingapp.chat.presence.model.PresenceStatus.OFFLINE, p.getStatus());
        assertNull(p.getLastSeenAt());
    }

    @Test
    @DisplayName("Multiple users have independent presence states")
    void testMultipleUsersIndependentPresence() {
        // User 101 is online
        presence.registerSession(101L, "session-1");

        // User 202 is offline (no sessions)
        var user202Presence = presence.getUserPresence(202L, null);

        assertEquals(com.datingapp.chat.presence.model.PresenceStatus.ONLINE, presence.getUserPresence(101L, null).getStatus());
        assertEquals(com.datingapp.chat.presence.model.PresenceStatus.OFFLINE, user202Presence.getStatus());
    }
}