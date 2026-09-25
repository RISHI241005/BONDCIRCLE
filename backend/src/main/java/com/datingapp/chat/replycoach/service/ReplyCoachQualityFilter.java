package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.dto.ReplySuggestionItem;
import com.datingapp.chat.replycoach.model.ConversationContext;
import org.springframework.beans.factory.annotation.Autowired;
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
            "\\b(why did you ignore me|why did you disappear|you never text back|you hate me|you owe me|answer me now|unblock me|if you cared|prove you love me|you have to|do it or else|creep|kill yourself|die|bitch|bastard|hate you|shut up)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Anti-hallucination patterns (avoid claiming user did something specific without basis)
    private static final Pattern FABRICATED_EXPERIENCE_PATTERNS = Pattern.compile(
            "\\b(i watched that yesterday|i was just there yesterday|i bought that yesterday|i visited that last week|i met them yesterday)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern FIRST_PERSON_FACT_PATTERN = Pattern.compile(
            "\\b(i (?:watched|saw|went|visited|bought|met|finished|studied|live in|work at|was at|have been to|caught the highlights|'?m free)|yes i did|haan dekha tha|almost done|(?:today|tomorrow|tonight|saturday|sunday) (?:(?:morning|afternoon|evening) )?works (?:great )?for me)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final ReplySemanticSimilarity semanticSimilarity;

    @Autowired
    public ReplyCoachQualityFilter(ReplySemanticSimilarity semanticSimilarity) {
        this.semanticSimilarity = semanticSimilarity != null ? semanticSimilarity : new ReplySemanticSimilarity();
    }

    public ReplyCoachQualityFilter() {
        this(new ReplySemanticSimilarity());
    }

    public List<ReplySuggestionItem> filter(
            List<ReplySuggestionItem> suggestions,
            List<String> rejectedTexts,
            int limit) {
        return filter(suggestions, rejectedTexts, null, limit);
    }

    public List<ReplySuggestionItem> filter(
            List<ReplySuggestionItem> suggestions,
            List<String> rejectedTexts,
            ConversationContext context,
            int limit) {

        // This filter is also used on the internal candidate pool before the
        // ranker. The API layer still caps the final response at three.
        int maxCount = Math.max(1, Math.min(limit, 12));
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
        List<ReplySuggestionItem> seenItems = new ArrayList<>();

        for (ReplySuggestionItem item : suggestions) {
            if (item == null || item.getText() == null) continue;
            String text = item.getText().trim();
            if (text.length() < MIN_LENGTH || text.length() > MAX_LENGTH) continue;

            // Safety and toxic filter
            if (TOXIC_PATTERNS.matcher(text).find()) continue;

            // Anti-hallucination check
            if (FABRICATED_EXPERIENCE_PATTERNS.matcher(text).find()) continue;
            if (appearsToInventUserFact(text, context)) continue;
            if (context != null && !context.isEmpty() && semanticSimilarity.isGeneric(text)) continue;
            if (isEmotionallyInappropriate(text, context)) continue;

            String norm = normalize(text);
            if (seenNormalized.contains(norm)) continue;
            if (normalizedRejected.contains(norm)) continue;
            if (rejectedTexts != null && rejectedTexts.stream()
                    .filter(java.util.Objects::nonNull)
                    .anyMatch(rejected -> semanticSimilarity.areSemanticallySimilar(text, rejected))) continue;

            // Semantic and lexical duplicate prevention across the candidate set.
            boolean isNearDup = seenItems.stream()
                    .anyMatch(seen -> semanticSimilarity.areSemanticallySimilar(item, seen));
            if (isNearDup) continue;

            seenNormalized.add(norm);
            seenItems.add(item);
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

    private boolean appearsToInventUserFact(String text, ConversationContext context) {
        if (!FIRST_PERSON_FACT_PATTERN.matcher(text).find()) return false;
        if (context == null) return true;
        String normalizedSuggestion = normalize(text);
        String knownUserFacts = ((context.currentUserInterests() == null ? "" : context.currentUserInterests()) + " "
                + context.messages().stream()
                .filter(ConversationContext.ContextMessage::isCurrentUser)
                .map(ConversationContext.ContextMessage::content)
                .filter(java.util.Objects::nonNull)
                .reduce("", (left, right) -> left + " " + right)).toLowerCase(Locale.ROOT);

        // Require at least two meaningful words from the claimed draft to be
        // supported by facts the current user actually supplied.
        long supportedWords = tokenize(normalizedSuggestion).stream()
                .filter(word -> word.length() >= 4)
                .filter(knownUserFacts::contains)
                .count();
        return supportedWords < 2;
    }

    private boolean isEmotionallyInappropriate(String text, ConversationContext context) {
        if (context == null || context.isEmpty()) return false;
        ConversationContext.ContextMessage latest = context.getLastMessage();
        if (latest == null || latest.isCurrentUser() || latest.content() == null) return false;
        String incoming = latest.content().toLowerCase(Locale.ROOT);
        boolean negative = incoming.matches(".*\\b(bad|horrible|awful|sad|hurt|stressed|tired|exhausted|kharab|bura|pareshan)\\b.*")
                || incoming.contains("😢") || incoming.contains("😭");
        if (!negative) return false;
        String reply = text.toLowerCase(Locale.ROOT).trim();
        return reply.matches("^(nice|great|awesome|amazing|love that|sounds fun)[!. ]*$");
    }

    private double similarity(String left, String right) {
        Set<String> a = tokenize(left);
        Set<String> b = tokenize(right);
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    private Set<String> tokenize(String value) {
        Set<String> tokens = new HashSet<>();
        for (String token : normalize(value).split("\\s+")) {
            if (!token.isBlank() && !Set.of("the", "a", "an", "to", "is", "it", "that", "this", "and", "or", "i").contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
