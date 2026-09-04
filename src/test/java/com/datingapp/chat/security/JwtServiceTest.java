package com.datingapp.chat.security;

import com.datingapp.chat.common.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private final String testSecret = "test-only-secret-key-change-before-production-0123456789abcdef";
    private final String testIssuer = "dating-app-auth-service";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(testSecret, testIssuer, 3600000);
    }

    @Test
    @DisplayName("Should generate and validate JWT token successfully")
    void testGenerateAndValidateToken() {
        Long userId = 101L;
        List<String> roles = List.of("ROLE_USER");

        String token = jwtService.generateToken(userId, roles);
        assertNotNull(token);
        assertTrue(jwtService.validateToken(token));

        Long extractedId = jwtService.extractUserId(token);
        assertEquals(userId, extractedId);

        List<String> extractedRoles = jwtService.extractRoles(token);
        assertEquals(1, extractedRoles.size());
        assertEquals("ROLE_USER", extractedRoles.getFirst());
    }

    @Test
    @DisplayName("Should throw UnauthorizedException on invalid token signature")
    void testInvalidToken() {
        String invalidToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMDEifQ.invalidSignature";
        assertFalse(jwtService.validateToken(invalidToken));
        assertThrows(UnauthorizedException.class, () -> jwtService.extractClaims(invalidToken));
    }

    @Test
    @DisplayName("Should throw UnauthorizedException on expired token")
    void testExpiredToken() {
        // Create service with negative expiration (immediately expired)
        JwtService expiredService = new JwtService(testSecret, testIssuer, -1000);
        String expiredToken = expiredService.generateToken(101L, List.of("ROLE_USER"));

        assertFalse(jwtService.validateToken(expiredToken));
        assertThrows(UnauthorizedException.class, () -> jwtService.extractClaims(expiredToken));
    }
}
