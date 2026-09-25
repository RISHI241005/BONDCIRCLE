package com.datingapp.chat.config;

import java.util.Arrays;

import com.datingapp.chat.security.WebSocketAuthChannelInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Spring WebSocket and STOMP message broker configuration.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthChannelInterceptor authChannelInterceptor;
    private final Environment environment;
    private final String[] allowedOrigins;

    public WebSocketConfig(
            WebSocketAuthChannelInterceptor authChannelInterceptor,
            Environment environment,
            @Value("${CORS_ALLOWED_ORIGINS:}") String allowedOrigins) {
        this.authChannelInterceptor = authChannelInterceptor;
        this.environment = environment;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        StompWebSocketEndpointRegistration nativeEndpoint = registry.addEndpoint("/ws");
        configureOrigins(nativeEndpoint);

        StompWebSocketEndpointRegistration sockJsEndpoint = registry.addEndpoint("/ws");
        configureOrigins(sockJsEndpoint);
        sockJsEndpoint.withSockJS();
    }

    private void configureOrigins(StompWebSocketEndpointRegistration endpoint) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            endpoint.setAllowedOriginPatterns("*");
        } else if (allowedOrigins.length > 0) {
            endpoint.setAllowedOrigins(allowedOrigins);
        }
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Inbound destinations handled by @MessageMapping methods
        registry.setApplicationDestinationPrefixes("/app");

        // Prefix for user-specific messaging (e.g. /user/queue/messages)
        registry.setUserDestinationPrefix("/user");

        // In-memory message broker destinations for subscriptions
        registry.enableSimpleBroker("/queue", "/topic");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Enforce JWT authentication on inbound STOMP frames (CONNECT)
        registration.interceptors(authChannelInterceptor);
    }
}
