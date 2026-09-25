package com.datingapp.chat.common.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * High-precision ISO-8601 UTC date and time formatting utilities.
 */
public final class DateTimeUtils {

    public static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private DateTimeUtils() {
    }

    public static String formatIso(Instant instant) {
        if (instant == null) {
            return null;
        }
        return ISO_FORMATTER.format(instant);
    }

    public static Instant parseIso(String isoString) {
        if (isoString == null || isoString.isBlank()) {
            return null;
        }
        return Instant.parse(isoString);
    }
}
