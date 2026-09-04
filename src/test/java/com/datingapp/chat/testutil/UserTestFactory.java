package com.datingapp.chat.testutil;

import com.datingapp.chat.security.JwtService;

import java.util.List;

/**
 * Deterministic User test factory for unit, integration, and E2E tests.
 */
public class UserTestFactory {

    public static final Long USER_A = 101L;
    public static final Long USER_B = 202L;
    public static final Long USER_C = 303L;
    public static final Long BLOCKED_USER = 404L;
    public static final Long ADMIN_USER = 999L;

    public static String createBearerToken(JwtService jwtService, Long userId) {
        return "Bearer " + jwtService.generateToken(userId, List.of("ROLE_USER"));
    }

    public static String createAdminBearerToken(JwtService jwtService) {
        return "Bearer " + jwtService.generateToken(ADMIN_USER, List.of("ROLE_ADMIN", "ROLE_USER"));
    }

    public static String createRawToken(JwtService jwtService, Long userId) {
        return jwtService.generateToken(userId, List.of("ROLE_USER"));
    }
}
