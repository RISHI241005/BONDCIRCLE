package com.datingapp.chat.icebreaker.service;

import com.datingapp.chat.message.entity.Message;
import com.datingapp.chat.moderation.service.LanguageModerationService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ConversationSignalExtractor {

    private static final Pattern WORD = Pattern.compile("[\\p{L}][\\p{L}'-]{2,}");
    private static final Set<String> STOP_WORDS = Set.of(
            "about", "after", "again", "also", "because", "been", "before", "being", "between",
            "could", "didn", "does", "doing", "don", "from", "going", "good", "great", "have",
            "hello", "here", "hey", "hiii", "how", "into", "just", "know", "like", "maybe",
            "more", "much", "nice", "okay", "really", "right", "should", "something", "that",
            "their", "them", "then", "there", "these", "they", "thing", "think", "this", "those",
            "today", "very", "want", "what", "when", "where", "which", "with", "would", "yeah",
            "your", "youre", "you", "yes", "thanks", "thank", "please", "sorry", "gmail", "email",
            "phone", "number", "http", "https", "www", "com", "aur", "hai", "haan", "nahi", "kya",
            "kaise", "mein", "mera", "meri", "tum", "aap", "acha", "accha", "theek", "bas"
    );

    private final LanguageModerationService moderationService;

    public ConversationSignalExtractor(LanguageModerationService moderationService) {
        this.moderationService = moderationService;
    }

    public ConversationSignals extract(
            List<Message> messages,
            Long currentUserId,
            List<String> knownInterests) {
        List<Message> chronological = new ArrayList<>(messages);
        chronological.sort(Comparator.comparing(
                Message::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));

        Optional<Message> latestIncoming = messages.isEmpty()
                || currentUserId.equals(messages.getFirst().getSenderId())
                ? Optional.empty()
                : Optional.of(messages.getFirst());
        Optional<String> latestTopic = latestIncoming
                .flatMap(message -> bestTopic(message.getContent(), knownInterests));

        Map<String, TopicStats> stats = new HashMap<>();
        for (Message message : chronological) {
            Set<String> topicsInMessage = new LinkedHashSet<>();
            String content = message.getContent() == null ? "" : message.getContent();
            String lowerContent = content.toLowerCase(Locale.ROOT);

            for (String interest : knownInterests) {
                if (lowerContent.contains(interest.toLowerCase(Locale.ROOT))) {
                    topicsInMessage.add(interest);
                }
            }
            topicsInMessage.addAll(tokens(content));

            for (String topic : topicsInMessage) {
                String key = topic.toLowerCase(Locale.ROOT);
                TopicStats value = stats.computeIfAbsent(key, ignored -> new TopicStats(topic));
                value.occurrences++;
                value.speakers.add(message.getSenderId());
            }
        }

        List<String> priorTopics = stats.values().stream()
                .filter(topic -> topic.occurrences >= 2 || topic.speakers.size() >= 2)
                .filter(topic -> !moderationService.analyze(topic.display).flagged())
                .sorted(Comparator
                        .comparingInt((TopicStats topic) -> topic.speakers.size()).reversed()
                        .thenComparing(Comparator.comparingInt((TopicStats topic) -> topic.occurrences).reversed())
                        .thenComparing(topic -> topic.display))
                .map(topic -> topic.display)
                .limit(8)
                .toList();

        return new ConversationSignals(latestTopic, priorTopics);
    }

    private Optional<String> bestTopic(String content, List<String> knownInterests) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        String lowerContent = content.toLowerCase(Locale.ROOT);
        Optional<String> known = knownInterests.stream()
                .filter(interest -> lowerContent.contains(interest.toLowerCase(Locale.ROOT)))
                .findFirst();
        if (known.isPresent()) {
            return known;
        }
        return tokens(content).stream().findFirst();
    }

    private List<String> tokens(String content) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Matcher matcher = WORD.matcher(content == null ? "" : content.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group().replace("'", "");
            if (token.length() < 4 || token.length() > 28 || STOP_WORDS.contains(token)) {
                continue;
            }
            if (!moderationService.analyze(token).flagged()) {
                result.add(token);
            }
        }
        return List.copyOf(result);
    }

    public record ConversationSignals(
            Optional<String> latestIncomingTopic,
            List<String> priorTopics
    ) {
    }

    private static final class TopicStats {
        private final String display;
        private int occurrences;
        private final Set<Long> speakers = new HashSet<>();

        private TopicStats(String display) {
            this.display = display;
        }
    }
}
