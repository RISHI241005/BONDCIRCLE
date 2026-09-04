package com.datingapp.chat.presence.listener;

import com.datingapp.chat.presence.service.PresenceService;
import com.datingapp.chat.security.StompPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * Event listener tracking WebSocket connect and disconnect events to maintain real-time user presence.
 */
@Component
public class WebSocketPresenceEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketPresenceEventListener.class);

    private final PresenceService presenceService;

    public WebSocketPresenceEventListener(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headerAccessor.getUser();
        String sessionId = headerAccessor.getSessionId();

        if (principal != null && sessionId != null) {
            Long userId = extractUserId(principal);
            if (userId != null) {
                presenceService.registerSession(userId, sessionId);
            }
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        if (sessionId != null) {
            presenceService.unregisterSession(sessionId);
        }
    }

    private Long extractUserId(Principal principal) {
        if (principal instanceof StompPrincipal stompPrincipal) {
            return stompPrincipal.getUserId();
        }
        try {
            return Long.parseLong(principal.getName());
        } catch (NumberFormatException e) {
            log.warn("Could not parse user ID from principal name: {}", principal.getName());
            return null;
        }
    }
}
