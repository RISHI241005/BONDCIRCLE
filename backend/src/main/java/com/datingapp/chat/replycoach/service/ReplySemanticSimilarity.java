package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.dto.ReplySuggestionItem;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Deterministic semantic fallback used when embeddings are unavailable. */
@Component
public class ReplySemanticSimilarity {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "did", "do", "does", "to", "for",
            "of", "in", "on", "at", "it", "that", "this", "and", "or", "i", "you", "your", "me",
            "my", "tum", "tumhara", "hai", "tha", "thi", "ho", "ka", "ki", "ke", "se", "toh"
    );
    private static final Pattern GENERIC = Pattern.compile(
            "^(nice|cool|interesting|that'?s interesting|tell me more|what else|how are you|how are you doing|sounds good)[!.? ]*$",
            Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> ALIASES = aliases();

    public boolean isGeneric(String text) {
        return text != null && GENERIC.matcher(text.trim()).matches();
    }

    public boolean areSemanticallySimilar(String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) return false;
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        if (normalizedLeft.equals(normalizedRight)) return true;

        String leftConcept = concept(normalizedLeft);
        String rightConcept = concept(normalizedRight);
        if (!leftConcept.isEmpty() && leftConcept.equals(rightConcept)) return true;

        Set<String> a = canonicalTokens(normalizedLeft);
        Set<String> b = canonicalTokens(normalizedRight);
        double lexical = jaccard(a, b);
        if (lexical >= 0.52) return true;

        return questionType(normalizedLeft).equals(questionType(normalizedRight))
                && !questionType(normalizedLeft).equals("NONE")
                && lexical >= 0.38;
    }

    public boolean areSemanticallySimilar(ReplySuggestionItem left, ReplySuggestionItem right) {
        if (left == null || right == null) return false;
        if (areSemanticallySimilar(left.getText(), right.getText())) return true;

        String leftStrategy = safeUpper(left.getStrategy());
        String rightStrategy = safeUpper(right.getStrategy());
        String leftTopic = normalize(left.getTopic());
        String rightTopic = normalize(right.getTopic());
        double lexical = jaccard(canonicalTokens(normalize(left.getText())), canonicalTokens(normalize(right.getText())));
        return !leftStrategy.isEmpty() && leftStrategy.equals(rightStrategy)
                && !leftTopic.isEmpty() && leftTopic.equals(rightTopic)
                && questionType(normalize(left.getText())).equals(questionType(normalize(right.getText())))
                && lexical >= 0.25;
    }

    private String concept(String text) {
        if (containsAny(text, "day", "today", "aaj") && containsAny(text, "how", "kaisa", "kaisi", "went", "go")) {
            return "DAY_CHECKIN";
        }
        if (containsAny(text, "what happened", "tell more", "full story", "phir kya", "kya hua")) {
            return "EVENT_DETAILS";
        }
        if (containsAny(text, "okay", "alright", "fine") && containsAny(text, "you", "everything", "sab")) {
            return "WELLBEING_CHECK";
        }
        if (text.contains("assignment") && containsAny(text, "done", "finish", "complete", "khatam")) {
            return "ASSIGNMENT_STATUS";
        }
        if (text.contains("assignment") && containsAny(text, "which", "topic", "subject", "worst", "kaunsa")) {
            return "ASSIGNMENT_DETAIL";
        }
        if (containsAny(text, "tired", "exhaust", "long day", "hectic", "thak")) {
            return "TIRED_REACTION";
        }
        if (containsAny(text, "what time", "which time", "kitne baje", "kab") && containsAny(text, "meet", "mil", "plan", "free")) {
            return "PLAN_TIME";
        }
        if (containsAny(text, "where", "place", "kaha") && containsAny(text, "meet", "mil", "plan", "going")) {
            return "PLAN_PLACE";
        }
        return "";
    }

    private String questionType(String text) {
        if (!text.contains("?") && !text.matches(".*\\b(what|why|how|when|where|who|kya|kyun|kaise|kab|kaha)\\b.*")) {
            return "NONE";
        }
        if (containsAny(text, "why", "kyun")) return "WHY";
        if (containsAny(text, "when", "kab", "what time", "kitne baje")) return "WHEN";
        if (containsAny(text, "where", "kaha", "which place")) return "WHERE";
        if (containsAny(text, "who", "kaun")) return "WHO";
        if (containsAny(text, "how", "kaise", "kaisa", "kaisi")) return "HOW";
        if (containsAny(text, "what", "kya", "which", "kaunsa")) return "WHAT";
        return "YES_NO";
    }

    private Set<String> canonicalTokens(String value) {
        Set<String> tokens = new HashSet<>();
        for (String token : value.split("\\s+")) {
            if (token.isBlank() || STOP_WORDS.contains(token)) continue;
            String canonical = ALIASES.getOrDefault(token, stem(token));
            if (!canonical.isBlank()) tokens.add(canonical);
        }
        return tokens;
    }

    private String stem(String token) {
        if (token.length() > 5 && token.endsWith("ing")) return token.substring(0, token.length() - 3);
        if (token.length() > 4 && token.endsWith("ed")) return token.substring(0, token.length() - 2);
        if (token.length() > 4 && token.endsWith("s")) return token.substring(0, token.length() - 1);
        return token;
    }

    private double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String safeUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static Map<String, String> aliases() {
        Map<String, String> values = new HashMap<>();
        for (String word : Set.of("went", "gone", "going")) values.put(word, "go");
        for (String word : Set.of("finished", "complete", "completed", "khatam")) values.put(word, "done");
        for (String word : Set.of("exhausted", "hectic", "thaka", "thaki")) values.put(word, "tired");
        for (String word : Set.of("story", "happened", "hua")) values.put(word, "event");
        return Map.copyOf(values);
    }
}
