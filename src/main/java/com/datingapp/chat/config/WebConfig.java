package com.datingapp.chat.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC Configuration configuring Cross-Origin Resource Sharing (CORS)
 * for mobile clients, web admin panels, and Swagger UI.
 * 
 * CORS is profile-aware: development uses permissive origins, production
 * uses specific allowed origins with credentials.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final Environment environment;
    private final String[] allowedOrigins;

    public WebConfig(
            Environment environment,
            @Value("${CORS_ALLOWED_ORIGINS:}") String allowedOrigins) {
        this.environment = environment;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        var mapping = registry.addMapping("/**")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);

        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            mapping.allowedOriginPatterns("*").allowCredentials(false);
        } else if (allowedOrigins.length > 0) {
            mapping.allowedOrigins(allowedOrigins).allowCredentials(true);
        }
    }
}
