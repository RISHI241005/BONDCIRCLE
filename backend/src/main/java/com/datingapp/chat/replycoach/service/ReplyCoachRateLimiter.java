package com.datingapp.chat.replycoach.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ReplyCoachRateLimiter {

    private static final int DEFAULT_MAX_REQUESTS_PER_MINUTE = 20;
    private static final long WINDOW_MILLIS = 60_000L;

    private final Map<Long, Deque<Long>> userRequests = new ConcurrentHashMap<>();

    public boolean tryAcquire(Long userId) {
        return tryAcquire(userId, DEFAULT_MAX_REQUESTS_PER_MINUTE);
    }

    public boolean tryAcquire(Long userId, int maxRequestsPerMinute) {
        if (userId == null) return true;
        long now = System.currentTimeMillis();

        Deque<Long> timestamps = userRequests.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            // Evict timestamps older than 60s
            while (!timestamps.isEmpty() && (now - timestamps.peekFirst()) > WINDOW_MILLIS) {
                timestamps.pollFirst();
            }

            if (timestamps.size() < maxRequestsPerMinute) {
                timestamps.addLast(now);
                return true;
            }
            return false;
        }
    }
}
