package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.dto.ReplySuggestionItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ReplyCoachQualityFilter {

    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 300;

    // Safety / Toxic communication patterns
    private static final Pattern TOXIC_PATTERNS = Pattern.compile(
            "\\b(why did you ignore me|why did you disappear|you never text back|you hate me|you owe me|answer me now|unblock me|creep|kill yourself|die|bitch|bastard|hate you|shut up)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Anti-hallucination patterns (avoid claiming user did something specific without basis)
    private static final Pattern FABRICATED_EXPERIENCE_PATTERNS = Pattern.compile(
            "\\b(i watched that yesterday|i was just there yesterday|i bought that yesterday|i visited that last week|i met them yesterday)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public List<ReplySuggestionItem> filter(
            List<ReplySuggestionItem> suggestions,
            List<String> rejectedTexts,
            int limit) {

        int maxCount = Math.max(1, Math.min(limit, 3));
        if (suggestions == null || suggestions.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> normalizedRejected = new HashSet<>();
        if (rejectedTexts != null) {
            for (String r : rejectedTexts) {
                if (r != null && !r.isBlank()) {
                    normalizedRejected.add(normalize(r));
                }
            }
        }

        List<ReplySuggestionItem> passed = new ArrayList<>();
        Set<String> seenNormalized = new HashSet<>();

        for (ReplySuggestionItem item : suggestions) {
            if (item == null || item.getText() == null) continue;
            String text = item.getText().trim();
            if (text.length() < MIN_LENGTH || text.length() > MAX_LENGTH) continue;

            // Safety and toxic filter
            if (TOXIC_PATTERNS.matcher(text).find()) continue;

            // Anti-hallucination check
            if (FABRICATED_EXPERIENCE_PATTERNS.matcher(text).find()) continue;

            String norm = normalize(text);
            if (seenNormalized.contains(norm)) continue;
            if (normalizedRejected.contains(norm)) continue;

            // Check near-duplicate (starts with identical 20 characters or high overlap)
            boolean isNearDup = seenNormalized.stream()
                    .anyMatch(s -> norm.length() > 20 && s.length() > 20 && norm.substring(0, Math.min(20, norm.length())).equals(s.substring(0, Math.min(20, s.length()))));
            if (isNearDup) continue;

            seenNormalized.add(norm);
            passed.add(item);

            if (passed.size() >= maxCount) {
                break;
            }
        }

        return passed;
    }

    private String normalize(String s) {
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-zA-Z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
