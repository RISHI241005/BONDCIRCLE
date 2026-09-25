package com.datingapp.chat.security;

import java.security.Principal;
import java.util.Objects;

/**
 * Principal representation for authenticated WebSocket / STOMP sessions.
 */
public class StompPrincipal implements Principal {

    private final String name;
    private final Long userId;

    public StompPrincipal(Long userId) {
        this.userId = userId;
        this.name = String.valueOf(userId);
    }

    @Override
    public String getName() {
        return name;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StompPrincipal that = (StompPrincipal) o;
        return Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    @Override
    public String toString() {
        return "StompPrincipal{userId=" + userId + "}";
    }
}
