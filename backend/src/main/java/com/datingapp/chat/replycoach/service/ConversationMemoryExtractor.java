package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.model.ConversationContext.ContextMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ConversationMemoryExtractor {

    private static final Pattern FUTURE_EVENT_PATTERN = Pattern.compile(
            "\\b(performance|exam|presentation|interview|flight|trip|concert|test|audition|match|game|wedding|event|vacation|party|going to|traveling to|milenge)\\b[\\s\\w]{0,25}\\b(on|at|this|next|tomorrow|friday|saturday|sunday|monday|tuesday|wednesday|thursday|weekend|kal|parso)?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PROMISE_PATTERN = Pattern.compile(
            "\\b(i'll|ill|i will|let you know|promise|send you|show you|kal batata|kal bataunga|yaad rakhna|dekh ke bataunga|batata hoon)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PREFERENCE_PATTERN = Pattern.compile(
            "\\b(my favorite|i love|i hate|obsessed with|huge fan of|allergic to|vegetarian|vegan|dog|cat|pet|studying|major in|work at|works at)\\b[\\s\\w]{1,35}",
            Pattern.CASE_INSENSITIVE
    );

    public record MemoryAnchor(String type, String snippet, boolean isPartnerFact) {
        @Override
        public String toString() {
            return (isPartnerFact ? "Partner " : "User ") + type + ": \"" + snippet + "\"";
        }
    }

    public List<String> extractMemories(List<ContextMessage> messages, int recentMessageThreshold) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        List<MemoryAnchor> anchors = new ArrayList<>();
        Set<String> seenSnippets = new HashSet<>();

        // If there are more messages than the immediate context window, scan older messages first
        int boundary = Math.max(0, messages.size() - recentMessageThreshold);
        List<ContextMessage> olderMessages = messages.subList(0, boundary);
        List<ContextMessage> messagesToScan = olderMessages.isEmpty() ? messages : olderMessages;

        for (ContextMessage msg : messagesToScan) {
            if (msg == null || msg.content() == null) continue;
            String content = msg.content().trim();
            if (content.length() < 6 || content.length() > 250) continue;

            boolean isPartner = !msg.isCurrentUser();

            // 1. Commitments / Promises (prioritize commitments)
            Matcher promiseMatcher = PROMISE_PATTERN.matcher(content);
            if (promiseMatcher.find()) {
                String clean = content.replaceAll("[\\r\\n]+", " ");
                if (seenSnippets.add(clean.toLowerCase(Locale.ROOT))) {
                    anchors.add(new MemoryAnchor("Promise/Commitment", clean, isPartner));
                    continue;
                }
            }

            // 2. Future events / milestones
            Matcher eventMatcher = FUTURE_EVENT_PATTERN.matcher(content);
            if (eventMatcher.find()) {
                String clean = content.replaceAll("[\\r\\n]+", " ");
                if (seenSnippets.add(clean.toLowerCase(Locale.ROOT))) {
                    anchors.add(new MemoryAnchor("Event/Plan", clean, isPartner));
                    continue;
                }
            }

            // 3. Personal preferences / facts
            Matcher prefMatcher = PREFERENCE_PATTERN.matcher(content);
            if (prefMatcher.find()) {
                String clean = content.replaceAll("[\\r\\n]+", " ");
                if (seenSnippets.add(clean.toLowerCase(Locale.ROOT))) {
                    anchors.add(new MemoryAnchor("Preference/Detail", clean, isPartner));
                }
            }
        }

        // Limit to top 5 most salient memory anchors
        return anchors.stream()
                .limit(5)
                .map(MemoryAnchor::toString)
                .toList();
    }
}
