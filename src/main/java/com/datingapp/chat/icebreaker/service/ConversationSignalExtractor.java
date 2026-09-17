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
            "kaise", "mein", "mera", "meri", "tum", "aap", "acha", "accha", "theek", "bas",
            "kuch", "bahut", "bohot", "bhi", "toh", "ab", "kab", "kahan", "hoga", "hogi",
            "raha", "rahi", "bhai", "yaar", "chalo", "suno", "waise"
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

        String incomingText = latestIncoming.map(Message::getContent).orElse("");
        boolean isQuestion = isQuestion(incomingText);
        String detectedMood = detectMood(messages, incomingText);
        String scenarioSummary = describeScenario(messages, currentUserId, incomingText, detectedMood, latestTopic);
        boolean isHinglish = isHinglishConversation(messages);

        return new ConversationSignals(
                latestTopic,
                priorTopics,
                detectedMood,
                scenarioSummary,
                latestIncoming.map(Message::getContent),
                isQuestion,
                isHinglish);
    }

    public boolean isHinglishConversation(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        String combined = messages.stream()
                .limit(8)
                .map(m -> m.getContent() == null ? "" : m.getContent().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.joining(" "));
        return combined.matches(".*\\b(kya|hai|nahi|kaise|kuch|yaar|batao|accha|theek|shukriya|bhai|karo|hoga|hogi|raha|rahi|milte|chal|sahi|arre|arrey|haan|kaun|kab|kahan|bohot|bahut|thoda|fasa|baat|shaam|subah|suno|suniye|bhi|toh|kal|aaj|shandar|badhiya|poora|waise|pata|soch|karenge|bata|karein|karna|hote)\\b.*");
    }

    private String detectMood(List<Message> messages, String latestText) {
        String combined = messages.stream()
                .limit(5)
                .map(m -> m.getContent() == null ? "" : m.getContent().toLowerCase(Locale.ROOT))
                .reduce("", (a, b) -> a + " " + b)
                + " " + latestText.toLowerCase(Locale.ROOT);

        if (combined.matches(".*\\b(hectic|tired|thak|thaka|thaki|exhausted|deadline|stress|headache|bura din|fasa|kaam|work|office|pareshan|dimag kharab)\\b.*")) {
            return "Exhausted & Stressed";
        }
        if (combined.matches(".*\\b(haha|lmao|lol|rofl|kidding|joke|masti|pagal|funny|mzaak|mazak|bakwaas|dramebaaz|arre yaar|arrey yaar|😂|🤣|😄|😜)\\b.*")) {
            return "Playful & Teasing";
        }
        if (combined.matches(".*\\b(cute|handsome|beautiful|sweet|miss you|coffee|date|dinner|meet|milte|romance|khoobsurat|pyaari|pyaara|taareef|yaad|❤️|😍|blush)\\b.*")) {
            return "Flirtatious & Warm";
        }
        if (combined.matches(".*\\b(trip|travel|excited|can't wait|super|awesome|party|concert|shandar|badhiya|maza|dhamaka|kya baat|zabardast|🔥|🎉)\\b.*")) {
            return "Excited & Enthusiastic";
        }
        if (combined.matches(".*\\b(night|sleepy|bed|neend|soja|thak gaya|goodnight|gn|chalo bye|shubh ratri)\\b.*")) {
            return "Late-night & Cozy";
        }
        if (combined.matches(".*\\b(feel|life|think|deep|sach|heart|sad|upset|advice|bura|problem|alone|akela|pareshaan|samajh)\\b.*")) {
            return "Reflective & Sincere";
        }
        return "Casual & Engaging";
    }

    private String describeScenario(
            List<Message> messages,
            Long currentUserId,
            String incomingText,
            String mood,
            Optional<String> topic) {
        if (messages.isEmpty()) {
            return "Brand new conversation; opening with warmth and mutual discovery.";
        }
        boolean isIncoming = !incomingText.isBlank();
        if (!isIncoming) {
            return "Waiting on their response; giving comfortable breathing room.";
        }
        String topicName = topic.orElse("recent chat");
        return switch (mood) {
            case "Exhausted & Stressed" -> "They are tired or venting about " + topicName + "; an empathetic, supportive reply creates safety.";
            case "Playful & Teasing" -> "Banter-heavy dynamic; matching their witty, teasing vibe builds instant chemistry.";
            case "Flirtatious & Warm" -> "Flirtatious undertones; charming and attentive replies will deepen the spark.";
            case "Excited & Enthusiastic" -> "High energy regarding " + topicName + "; leaning into their excitement keeps the momentum vibrant.";
            case "Late-night & Cozy" -> "Winding down late at night; gentle, soothing, low-pressure warmth fits best.";
            case "Reflective & Sincere" -> "A meaningful, deeper exchange; authentic listening and thoughtfulness is key.";
            default -> isQuestion(incomingText)
                    ? "They asked a direct question regarding " + topicName + "; an engaging answer advances the dialogue."
                    : "Natural conversation flow around " + topicName + "; maintain effortless, two-way engagement.";
        };
    }

    private boolean isQuestion(String text) {
        return text != null && (text.contains("?") || text.toLowerCase(Locale.ROOT)
                .matches(".*\\b(what|when|where|why|how|who|kya|kab|kaise|free|plan|batao)\\b.*"));
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
            List<String> priorTopics,
            String detectedMood,
            String scenarioSummary,
            Optional<String> latestIncomingText,
            boolean isQuestion,
            boolean isHinglish
    ) {
        public ConversationSignals(Optional<String> latestIncomingTopic, List<String> priorTopics) {
            this(latestIncomingTopic, priorTopics, "Casual & Engaging", "General conversation flow", Optional.empty(), false, false);
        }

        public ConversationSignals(
                Optional<String> latestIncomingTopic,
                List<String> priorTopics,
                String detectedMood,
                String scenarioSummary,
                Optional<String> latestIncomingText,
                boolean isQuestion) {
            this(latestIncomingTopic, priorTopics, detectedMood, scenarioSummary, latestIncomingText, isQuestion, false);
        }
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
