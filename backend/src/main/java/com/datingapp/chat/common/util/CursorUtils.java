package com.datingapp.chat.common.util;

import com.datingapp.chat.common.exception.BadRequestException;
import com.datingapp.chat.common.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Utility for encoding and decoding composite opaque pagination cursors.
 * Format: Base64(createdAtEpochMilli:id)
 */
public final class CursorUtils {

    private CursorUtils() {
    }

    public static String encodeCursor(Instant createdAt, Long id) {
        if (createdAt == null || id == null) {
            return null;
        }
        String raw = createdAt.toEpochMilli() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static CursorPayload decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = raw.split(":");
            if (parts.length != 2) {
                throw new BadRequestException("Invalid cursor format", ErrorCode.INVALID_CURSOR);
            }
            long epochMilli = Long.parseLong(parts[0]);
            long id = Long.parseLong(parts[1]);
            return new CursorPayload(Instant.ofEpochMilli(epochMilli), id);
        } catch (Exception e) {
            throw new BadRequestException("Failed to decode cursor: " + e.getMessage(), ErrorCode.INVALID_CURSOR);
        }
    }

    public static class CursorPayload {
        private final Instant createdAt;
        private final Long id;

        public CursorPayload(Instant createdAt, Long id) {
            this.createdAt = createdAt;
            this.id = id;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public Long getId() {
            return id;
        }
    }
}
