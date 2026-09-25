package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.config.ReplyRankingProperties;
import com.datingapp.chat.replycoach.dto.ReplySuggestionItem;
import com.datingapp.chat.replycoach.model.ConversationEnvironment;
import com.datingapp.chat.replycoach.model.LatestMessageAnalysis;
import com.datingapp.chat.replycoach.model.PartnerCommunicationProfile;
import com.datingapp.chat.replycoach.model.ReplyStrategy;
import com.datingapp.chat.replycoach.model.UserWritingProfile;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final ReplyRankingProperties weights;
    private final ReplySemanticSimilarity semanticSimilarity;

    @Autowired
    public ReplyRanker(ReplyRankingProperties weights, ReplySemanticSimilarity semanticSimilarity) {
        this.weights = weights != null ? weights : new ReplyRankingProperties();
        this.semanticSimilarity = semanticSimilarity != null ? semanticSimilarity : new ReplySemanticSimilarity();
    }

    public ReplyRanker(ReplyRankingProperties weights) {
        this(weights, new ReplySemanticSimilarity());
    }

    public ReplyRanker() {
        this(new ReplyRankingProperties());
    }

    public record ScoredReply(ReplySuggestionItem item, double score) {}

    public List<ReplySuggestionItem> rankAndFilter(
            List<ReplySuggestionItem> candidates,
            ConversationEnvironment env,
            UserWritingProfile styleProfile,
            List<ReplyStrategy> plannedStrategies,
            List<String> rejectedTexts,
            int targetLimit) {
        return rankAndFilter(candidates, env, styleProfile, null, null,
                plannedStrategies, rejectedTexts, targetLimit);
    }

    public List<ReplySuggestionItem> rankAndFilter(
            List<ReplySuggestionItem> candidates,
            ConversationEnvironment env,
            UserWritingProfile styleProfile,
            PartnerCommunicationProfile partnerStyle,
            LatestMessageAnalysis latest,
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
            double score = scoreCandidate(item, env, styleProfile, partnerStyle, latest,
                    plannedStrategies, rejectedTexts);
            scoredList.add(new ScoredReply(item, score));
        }

        // Sort descending by score
        scoredList.sort((a, b) -> Double.compare(b.score(), a.score()));

        // Diversity selection
        List<ReplySuggestionItem> selected = new ArrayList<>();
        Set<String> selectedStrategies = new HashSet<>();
        int questionCount = 0;

        for (ScoredReply sr : scoredList) {
            ReplySuggestionItem candidate = sr.item();
            String text = candidate.getText();
            String strat = candidate.getStrategy() != null ? candidate.getStrategy().toUpperCase(Locale.ROOT) : "";

            boolean isTooSimilar = selected.stream()
                    .anyMatch(existing -> semanticSimilarity.areSemanticallySimilar(candidate, existing));
            if (isTooSimilar) continue;
            if (text.contains("?") && questionCount >= 2) continue;

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
            if (text.contains("?")) questionCount++;

            if (selected.size() >= maxResults) {
                break;
            }
        }

        // If strict diversity pruned too many, backfill remaining best candidates
        if (selected.size() < maxResults) {
            for (ScoredReply sr : scoredList) {
                if (!selected.contains(sr.item()) && !sr.item().getText().contains("?")
                        && selected.stream().noneMatch(existing -> semanticSimilarity.areSemanticallySimilar(sr.item(), existing))) {
                    selected.add(sr.item());
                    if (selected.size() >= maxResults) break;
                }
            }
        }
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
        return scoreCandidate(item, env, styleProfile, null, null, plannedStrategies, rejectedTexts);
    }

    public double scoreCandidate(
            ReplySuggestionItem item,
            ConversationEnvironment env,
            UserWritingProfile styleProfile,
            PartnerCommunicationProfile partnerStyle,
            LatestMessageAnalysis latest,
            List<ReplyStrategy> plannedStrategies,
            List<String> rejectedTexts) {

        if (item == null || item.getText() == null) return 0.0;
        String text = item.getText();

        double conversationRelevance = calculateRelevance(text, env);
        double contextRelevance = latest == null ? conversationRelevance
                : (0.35 * conversationRelevance) + (0.65 * calculateLatestMessageRelevance(item, latest));
        double userStyleMatch = calculateStyleMatch(text, styleProfile);
        if (partnerStyle != null) {
            userStyleMatch = (0.75 * userStyleMatch) + (0.25 * calculatePartnerStyleMatch(text, partnerStyle));
        }
        double languageMatch = calculateLanguageMatch(text, env != null ? env.language() : "ENGLISH");
        double toneMatch = calculateToneMatch(item.getTone(), env);
        double strategyFit = calculateStrategyFit(item.getStrategy(), plannedStrategies);
        double engagementPotential = text.contains("?") ? 1.0 : (text.contains("!") ? 0.7 : 0.5);
        double personalization = calculatePersonalization(text, styleProfile);
        double novelty = calculateNovelty(text, rejectedTexts);
        double feedbackPreference = calculateFeedbackPref(item.getStrategy(), styleProfile);

        double total = weights.totalWeight();
        if (total <= 0.0) total = 1.0;
        return ((weights.getContextRelevance() * contextRelevance)
                + (weights.getUserStyleMatch() * userStyleMatch)
                + (weights.getLanguageMatch() * languageMatch)
                + (weights.getToneMatch() * toneMatch)
                + (weights.getStrategyFit() * strategyFit)
                + (weights.getEngagementPotential() * engagementPotential)
                + (weights.getPersonalization() * personalization)
                + (weights.getNovelty() * novelty)
                + (weights.getFeedbackPreference() * feedbackPreference)) / total;
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

    private double calculateLatestMessageRelevance(ReplySuggestionItem item, LatestMessageAnalysis latest) {
        if (latest == null || latest.text() == null || latest.text().isBlank()) return 0.5;
        double score = 0.35;
        String strategy = item.getStrategy() == null ? "" : item.getStrategy().toUpperCase(Locale.ROOT);
        if ((latest.question() || latest.implicitQuestion() || latest.request())
                && (strategy.equals("ANSWER") || strategy.equals("PLAN") || strategy.equals("INVITATION"))) {
            score += 0.4;
        }
        if (latest.emotionalSignal() && Set.of("EMPATHIZE", "SUPPORTIVE", "THOUGHTFUL", "ACKNOWLEDGE").contains(strategy)) {
            score += 0.4;
        }
        if (latest.humor() > 0.55 && Set.of("PLAYFUL", "BANTER", "TEASE").contains(strategy)) {
            score += 0.3;
        }
        if (latest.flirting() > 0.55 && Set.of("LIGHT_FLIRTING", "BANTER", "TEASE").contains(strategy)) {
            score += 0.3;
        }
        String replyTopic = item.getTopic() == null ? "" : item.getTopic().toLowerCase(Locale.ROOT);
        String latestTopic = latest.topic() == null ? "" : latest.topic().toLowerCase(Locale.ROOT);
        if (!replyTopic.isBlank() && !latestTopic.isBlank()
                && (replyTopic.contains(latestTopic) || latestTopic.contains(replyTopic))) {
            score += 0.2;
        }
        return Math.min(1.0, score);
    }

    private double calculatePartnerStyleMatch(String text, PartnerCommunicationProfile profile) {
        double score = 0.55;
        if (profile.length() == UserWritingProfile.LengthPreference.SHORT && text.length() <= 45) score += 0.2;
        if (profile.length() == UserWritingProfile.LengthPreference.LONG && text.length() >= 55) score += 0.15;
        boolean emoji = text.codePoints().anyMatch(Character::isEmoji);
        if (profile.emojiUsage() == UserWritingProfile.EmojiUsage.FREQUENT && emoji) score += 0.15;
        if (profile.emojiUsage() == UserWritingProfile.EmojiUsage.NONE && !emoji) score += 0.1;
        if (profile.slangTokens() != null) {
            String lower = text.toLowerCase(Locale.ROOT);
            if (profile.slangTokens().stream().anyMatch(lower::contains)) score += 0.1;
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
            if (rej != null && (clean.contains(rej.trim().toLowerCase(Locale.ROOT))
                    || semanticSimilarity.areSemanticallySimilar(text, rej))) {
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
