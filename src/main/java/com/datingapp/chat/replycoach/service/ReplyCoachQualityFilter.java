package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.dto.ReplySuggestionItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ReplyCoachQualityFilter {

    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 300;

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

            String norm = normalize(text);
            if (seenNormalized.contains(norm)) continue;
            if (normalizedRejected.contains(norm)) continue;

            // Check near-duplicate (starts with identical 25 characters)
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
