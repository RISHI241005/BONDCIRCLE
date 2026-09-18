package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.dto.ReplySuggestionItem;
import com.datingapp.chat.replycoach.model.ConversationEnvironment;
import com.datingapp.chat.replycoach.model.ReplyStrategy;
import com.datingapp.chat.replycoach.model.UserWritingProfile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ReplyRanker {

    public record ScoredReply(ReplySuggestionItem item, double score) {}

    public List<ReplySuggestionItem> rankAndFilter(
            List<ReplySuggestionItem> candidates,
            ConversationEnvironment env,
            UserWritingProfile styleProfile,
            List<ReplyStrategy> plannedStrategies,
            List<String> rejectedTexts,
            int targetLimit) {

        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        int maxResults = Math.max(1, Math.min(targetLimit, 3));

        // Score each candidate
        List<ScoredReply> scoredList = new ArrayList<>();
        for (ReplySuggestionItem item : candidates) {
            double score = scoreCandidate(item, env, styleProfile, plannedStrategies, rejectedTexts);
            scoredList.add(new ScoredReply(item, score));
        }

        // Sort descending by score
        scoredList.sort((a, b) -> Double.compare(b.score(), a.score()));

        // Diversity selection
        List<ReplySuggestionItem> selected = new ArrayList<>();
        Set<String> selectedStrategies = new HashSet<>();
        List<Set<String>> selectedWordSets = new ArrayList<>();

        for (ScoredReply sr : scoredList) {
            ReplySuggestionItem candidate = sr.item();
            String text = candidate.getText();
            Set<String> words = tokenize(text);
            String strat = candidate.getStrategy() != null ? candidate.getStrategy().toUpperCase(Locale.ROOT) : "";

            // Check lexical similarity with already selected replies (Jaccard threshold 0.5)
            boolean isTooSimilar = false;
            for (Set<String> existing : selectedWordSets) {
                if (jaccardSimilarity(words, existing) > 0.45) {
                    isTooSimilar = true;
                    break;
                }
            }
            if (isTooSimilar) continue;

            // Strategy diversity preference: prefer different strategies unless candidates run out
            if (!strat.isEmpty() && selectedStrategies.contains(strat) && selected.size() < maxResults) {
                // If we still have other candidates with unselected strategies, defer this one
                boolean hasAlternativeStrategy = scoredList.stream()
                        .skip(selected.size())
                        .anyMatch(other -> {
                            String otherStrat = other.item().getStrategy() != null ? other.item().getStrategy().toUpperCase(Locale.ROOT) : "";
                            return !selectedStrategies.contains(otherStrat);
                        });
                if (hasAlternativeStrategy && selected.size() < 2) {
                    continue;
                }
            }

            selected.add(candidate);
            if (!strat.isEmpty()) selectedStrategies.add(strat);
            selectedWordSets.add(words);

            if (selected.size() >= maxResults) {
                break;
            }
        }

        // If strict diversity pruned too many, backfill remaining best candidates
        if (selected.size() < maxResults) {
            for (ScoredReply sr : scoredList) {
                if (!selected.contains(sr.item())) {
                    selected.add(sr.item());
                    if (selected.size() >= maxResults) break;
                }
            }
        }

        return selected;
    }

    public double scoreCandidate(
            ReplySuggestionItem item,
            ConversationEnvironment env,
            UserWritingProfile styleProfile,
            List<ReplyStrategy> plannedStrategies,
            List<String> rejectedTexts) {

        if (item == null || item.getText() == null) return 0.0;
        String text = item.getText();

        double contextRelevance = calculateRelevance(text, env);
        double userStyleMatch = calculateStyleMatch(text, styleProfile);
        double languageMatch = calculateLanguageMatch(text, env != null ? env.language() : "ENGLISH");
        double toneMatch = calculateToneMatch(item.getTone(), env);
        double strategyFit = calculateStrategyFit(item.getStrategy(), plannedStrategies);
        double engagementPotential = text.contains("?") ? 1.0 : (text.contains("!") ? 0.7 : 0.5);
        double personalization = calculatePersonalization(text, styleProfile);
        double novelty = calculateNovelty(text, rejectedTexts);
        double feedbackPreference = calculateFeedbackPref(item.getStrategy(), styleProfile);

        return (0.25 * contextRelevance)
                + (0.15 * userStyleMatch)
                + (0.10 * languageMatch)
                + (0.10 * toneMatch)
                + (0.10 * strategyFit)
                + (0.10 * engagementPotential)
                + (0.10 * personalization)
                + (0.05 * novelty)
                + (0.05 * feedbackPreference);
    }

    private double calculateRelevance(String text, ConversationEnvironment env) {
        if (env == null) return 0.5;
        double score = 0.5;
        String lower = text.toLowerCase(Locale.ROOT);

        if (env.hasUnansweredQuestion()) {
            // Direct answer relevance
            score += 0.3;
        }
        if (env.primaryTopic() != null && !env.primaryTopic().equals("Catching up")) {
            String topic = env.primaryTopic().toLowerCase(Locale.ROOT);
            for (String tPart : topic.split("[\\s&]+")) {
                if (lower.contains(tPart)) {
                    score += 0.2;
                    break;
                }
            }
        }
        return Math.min(1.0, score);
    }

    private double calculateStyleMatch(String text, UserWritingProfile profile) {
        if (profile == null) return 0.7;
        double score = 0.6;
        int len = text.length();

        if (profile.length() == UserWritingProfile.LengthPreference.SHORT) {
            if (len <= 35) score += 0.3;
            else if (len > 70) score -= 0.3;
        } else if (profile.length() == UserWritingProfile.LengthPreference.LONG) {
            if (len >= 60) score += 0.3;
            else if (len < 25) score -= 0.2;
        }

        boolean hasEmoji = text.codePoints().anyMatch(Character::isEmoji);
        if (profile.emojiUsage() == UserWritingProfile.EmojiUsage.FREQUENT && hasEmoji) {
            score += 0.2;
        } else if (profile.emojiUsage() == UserWritingProfile.EmojiUsage.NONE && !hasEmoji) {
            score += 0.1;
        }

        return Math.max(0.0, Math.min(1.0, score));
    }

    private double calculateLanguageMatch(String text, String language) {
        boolean isHinglishExpected = "HINGLISH".equalsIgnoreCase(language) || "MIXED".equalsIgnoreCase(language);
        boolean hasHinglishTokens = text.matches("(?i).*\\b(kya|hai|nahi|yaar|chal|accha|acha|haan|han|bhai|theek|thik|kaise|kaisa|kaha|batao|mast|sahi|arre|scene)\\b.*");

        if (isHinglishExpected && hasHinglishTokens) return 1.0;
        if (!isHinglishExpected && !hasHinglishTokens) return 1.0;
        return 0.6;
    }

    private double calculateToneMatch(String tone, ConversationEnvironment env) {
        if (tone == null || env == null) return 0.6;
        if (env.temperature() == ConversationEnvironment.Temperature.EMOTIONAL && tone.toLowerCase().contains("empath")) return 1.0;
        if (env.temperature() == ConversationEnvironment.Temperature.PLAYFUL && tone.toLowerCase().contains("playful")) return 1.0;
        if (env.temperature() == ConversationEnvironment.Temperature.FLIRTY && tone.toLowerCase().contains("flirt")) return 1.0;
        return 0.7;
    }

    private double calculateStrategyFit(String itemStrategy, List<ReplyStrategy> plannedStrategies) {
        if (itemStrategy == null || plannedStrategies == null || plannedStrategies.isEmpty()) return 0.6;
        for (ReplyStrategy s : plannedStrategies) {
            if (s.name().equalsIgnoreCase(itemStrategy)) return 1.0;
        }
        return 0.5;
    }

    private double calculatePersonalization(String text, UserWritingProfile profile) {
        if (profile == null) return 0.5;
        double score = 0.5;
        if (profile.slangTokens() != null) {
            String lower = text.toLowerCase(Locale.ROOT);
            for (String slang : profile.slangTokens()) {
                if (lower.contains(slang)) {
                    score += 0.3;
                    break;
                }
            }
        }
        return Math.min(1.0, score);
    }

    private double calculateNovelty(String text, List<String> rejectedTexts) {
        if (rejectedTexts == null || rejectedTexts.isEmpty()) return 1.0;
        String clean = text.trim().toLowerCase(Locale.ROOT);
        for (String rej : rejectedTexts) {
            if (rej != null && clean.contains(rej.trim().toLowerCase(Locale.ROOT))) {
                return 0.0;
            }
        }
        return 1.0;
    }

    private double calculateFeedbackPref(String strategyStr, UserWritingProfile profile) {
        if (strategyStr == null || profile == null) return 0.5;
        try {
            ReplyStrategy strat = ReplyStrategy.valueOf(strategyStr.toUpperCase(Locale.ROOT));
            if (profile.preferredStrategies() != null && profile.preferredStrategies().contains(strat)) {
                return 1.0;
            }
            if (profile.rejectedStrategies() != null && profile.rejectedStrategies().contains(strat)) {
                return 0.1;
            }
        } catch (Exception ignored) {}
        return 0.5;
    }

    private Set<String> tokenize(String text) {
        if (text == null) return Collections.emptySet();
        return new HashSet<>(Arrays.asList(text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", "").split("\\s+")));
    }

    private double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }
}
