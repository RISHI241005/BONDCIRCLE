package com.datingapp.chat.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * STOMP Channel Interceptor validating JWT credentials upon WebSocket CONNECT frame.
 */
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthChannelInterceptor.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public WebSocketAuthChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = getHeaderValue(accessor, AUTHORIZATION_HEADER);

            if (!StringUtils.hasText(authHeader)) {
                log.warn("STOMP CONNECT rejected: Missing Authorization header");
                throw new MessagingException("Missing Authorization header in STOMP CONNECT frame");
            }

            String token = authHeader.startsWith(BEARER_PREFIX)
                    ? authHeader.substring(BEARER_PREFIX.length()).trim()
                    : authHeader.trim();

            if (!jwtService.validateToken(token)) {
                log.warn("STOMP CONNECT rejected: Invalid or expired JWT token");
                throw new MessagingException("Invalid or expired JWT token in STOMP CONNECT frame");
            }

            Long userId = jwtService.extractUserId(token);
            StompPrincipal principal = new StompPrincipal(userId);
            accessor.setUser(principal);
            log.info("STOMP session connected and authenticated for user ID: {}", userId);
        }

        return message;
    }

    private String getHeaderValue(StompHeaderAccessor accessor, String headerName) {
        List<String> values = accessor.getNativeHeader(headerName);
        if (values != null && !values.isEmpty()) {
            return values.get(0);
        }
        return null;
    }
}
