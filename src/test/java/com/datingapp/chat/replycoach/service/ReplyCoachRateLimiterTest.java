package com.datingapp.chat.replycoach.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplyCoachRateLimiterTest {

    private ReplyCoachRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new ReplyCoachRateLimiter();
    }

    @Test
    @DisplayName("Allows requests under max limit")
    void testAllowsUnderLimit() {
        Long userId = 999L;
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.tryAcquire(userId, 5)).isTrue();
        }
        // 6th request should be blocked
        assertThat(rateLimiter.tryAcquire(userId, 5)).isFalse();
    }

    @Test
    @DisplayName("Different users have independent quotas")
    void testIndependentUsers() {
        Long user1 = 1L;
        Long user2 = 2L;

        for (int i = 0; i < 3; i++) {
            assertThat(rateLimiter.tryAcquire(user1, 3)).isTrue();
        }
        assertThat(rateLimiter.tryAcquire(user1, 3)).isFalse();

        // user2 should still be allowed
        assertThat(rateLimiter.tryAcquire(user2, 3)).isTrue();
    }
}
