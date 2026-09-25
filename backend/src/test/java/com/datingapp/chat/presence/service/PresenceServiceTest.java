package com.datingapp.chat.presence.service;

import com.datingapp.chat.presence.dto.UserPresenceResponse;
import com.datingapp.chat.presence.model.PresenceStatus;
import com.datingapp.chat.presence.service.impl.InMemoryPresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PresenceServiceTest {

    private PresenceService presenceService;

    @BeforeEach
    void setUp() {
        presenceService = new InMemoryPresenceService();
    }

    @Test
    @DisplayName("Should track multi-device sessions and maintain ONLINE status until all sessions disconnect")
    void testMultiDevicePresenceLifecycle() {
        Long userId = 101L;
        String androidSession = "session-android-1";
        String iosSession = "session-ios-2";

        // Initial state -> OFFLINE
        UserPresenceResponse initial = presenceService.getUserPresence(userId, 202L);
        assertEquals(PresenceStatus.OFFLINE, initial.getStatus());
        assertFalse(presenceService.isUserOnline(userId));

        // 1. Android connects -> ONLINE
        presenceService.registerSession(userId, androidSession);
        assertTrue(presenceService.isUserOnline(userId));
        UserPresenceResponse onlineResponse = presenceService.getUserPresence(userId, 202L);
        assertEquals(PresenceStatus.ONLINE, onlineResponse.getStatus());
        assertNull(onlineResponse.getLastSeenAt());

        // 2. iOS connects concurrently -> remains ONLINE
        presenceService.registerSession(userId, iosSession);
        assertTrue(presenceService.isUserOnline(userId));

        // 3. Android disconnects -> iOS still active -> remains ONLINE
        presenceService.unregisterSession(androidSession);
        assertTrue(presenceService.isUserOnline(userId));
        assertEquals(PresenceStatus.ONLINE, presenceService.getUserPresence(userId, 202L).getStatus());

        // 4. iOS disconnects -> last session closed -> transitions to OFFLINE with lastSeenAt timestamp
        presenceService.unregisterSession(iosSession);
        assertFalse(presenceService.isUserOnline(userId));
        UserPresenceResponse offlineResponse = presenceService.getUserPresence(userId, 202L);
        assertEquals(PresenceStatus.OFFLINE, offlineResponse.getStatus());
        assertNotNull(offlineResponse.getLastSeenAt());
    }
}
