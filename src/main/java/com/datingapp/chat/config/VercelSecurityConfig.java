package com.datingapp.chat.config;

import com.datingapp.chat.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@Profile("vercel")
public class VercelSecurityConfig {
    @Bean
    @DependsOnDatabaseInitialization
    public JwtService jwtService(JdbcTemplate db,
            @Value("${chat.jwt.issuer}") String issuer,
            @Value("${chat.jwt.expiration-ms}") long expiration) {
        String key = db.queryForObject("SELECT value FROM app_secrets WHERE name='jwt'", String.class);
        if (key == null || key.length() < 64) throw new IllegalStateException("Missing signing key");
        return new JwtService(key, issuer, expiration);
    }
}
