package com.datingapp.chat.security;

import com.bondcircle.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private final String testSecret = "test-only-secret-key-change-before-production-0123456789abcdef";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", testSecret);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", 3600000L);
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
        assertEquals("ROLE_USER", extractedRoles.get(0));
    }

    @Test
    @DisplayName("Should validate token and return false on invalid token signature")
    void testInvalidToken() {
        String invalidToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMDEifQ.invalidSignature";
        assertFalse(jwtService.validateToken(invalidToken));
    }

    @Test
    @DisplayName("Should return false on expired token")
    void testExpiredToken() {
        JwtService expiredService = new JwtService();
        ReflectionTestUtils.setField(expiredService, "secretKey", testSecret);
        ReflectionTestUtils.setField(expiredService, "jwtExpirationMs", -1000L);
        String expiredToken = expiredService.generateToken(101L, List.of("ROLE_USER"));

        assertFalse(jwtService.validateToken(expiredToken));
    }
}
