package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.model.ConversationAnalysis;
import com.datingapp.chat.replycoach.model.ConversationAnalysis.Intent;
import com.datingapp.chat.replycoach.model.ConversationAnalysis.Momentum;
import com.datingapp.chat.replycoach.model.ConversationAnalysis.Stage;
import com.datingapp.chat.replycoach.model.ConversationContext;
import com.datingapp.chat.replycoach.model.ConversationContext.ContextMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ConversationIntelligenceService {

    private static final Set<String> DRY_WORDS = Set.of(
            "k", "ok", "okay", "kk", "hmm", "hmmm", "nice", "cool", "yeah", "yep", "yes", "no", "nah", "fine",
            "acha", "accha", "theek", "thik", "sahi", "haan", "han", "bye", "gn"
    );

    private static final Pattern HINGLISH_PATTERN = Pattern.compile(
            "\\b(kya|hai|nahi|nahin|yaar|chal|accha|acha|haan|han|bhai|theek|thik|kaise|kaisa|kaha|kahan|kuch|hoga|raha|rahi|rahe|meri|mera|mere|tere|tera|teri|apna|apni|sab|matlab|shuru|suno|sun|aaj|kal|parso|waise|badiya|mast|fasa|bata|batao)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern QUESTION_PATTERN = Pattern.compile("\\?|\\b(what|where|when|why|who|how|kya|kaise|kaha|kab|kyun)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern JOKE_PATTERN = Pattern.compile("😂|🤣|lmao|lol|haha|hehe|joke|funny|mazak", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPLIMENT_PATTERN = Pattern.compile("\\b(cute|pretty|handsome|gorgeous|smart|nice|amazing|love your|awesome|tareef)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern INVITATION_PATTERN = Pattern.compile("\\b(coffee|dinner|drinks|meet|hangout|hang out|plans|free this|milte|chalna|movie)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMOTIONAL_SUPPORT_PATTERN = Pattern.compile("\\b(sad|tired|bad day|exhausted|stress|stressed|terrible|crying|hurt|bura|pareshaan)\\b", Pattern.CASE_INSENSITIVE);

    public ConversationAnalysis analyze(ConversationContext context) {
        if (context == null || context.isEmpty()) {
            return ConversationAnalysis.empty("ENGLISH");
        }

        List<ContextMessage> messages = context.messages();
        String language = detectLanguage(messages);
        boolean isDry = detectDryConversation(context);

        ContextMessage lastMsg = context.getLastMessage();
        String lastText = lastMsg != null && lastMsg.content() != null ? lastMsg.content().trim() : "";

        // Conversation gap detection (> 48 hours inactive)
        boolean isLongGap = false;
        if (lastMsg != null && lastMsg.createdAt() != null && messages.size() >= 2) {
            long hoursSinceLastMessage = java.time.Duration.between(lastMsg.createdAt(), java.time.Instant.now()).toHours();
            if (hoursSinceLastMessage >= 48) {
                isLongGap = true;
            }
        }

        // Question detection
        boolean hasUnansweredQuestion = false;
        String unansweredQuestionText = null;
        if (lastMsg != null && !lastMsg.isCurrentUser()) {
            if (lastText.contains("?") || QUESTION_PATTERN.matcher(lastText).find()) {
                hasUnansweredQuestion = true;
                unansweredQuestionText = lastText;
            }
        }

        // Intent detection
        Intent intent = isLongGap && !hasUnansweredQuestion ? Intent.RECONNECTING : detectIntent(lastText, lastMsg != null && lastMsg.isCurrentUser());

        // Topic detection
        String primaryTopic = detectPrimaryTopic(messages);
        List<String> secondaryTopics = detectSecondaryTopics(messages, primaryTopic);

        // Tone detection
        String tone = isLongGap ? "Warm & Reconnecting" : detectTone(lastText, isDry, intent);

        // Momentum detection
        Momentum momentum = isDry ? Momentum.LOW : (isLongGap ? Momentum.LOW : (messages.size() >= 5 ? Momentum.HIGH : Momentum.MEDIUM));

        // Stage detection
        Stage stage = detectStage(messages.size(), isDry, intent, isLongGap);

        return new ConversationAnalysis(
                primaryTopic,
                secondaryTopics,
                intent,
                tone,
                momentum,
                stage,
                language,
                isDry,
                hasUnansweredQuestion,
                unansweredQuestionText
        );
    }

    public String detectLanguage(List<ContextMessage> messages) {
        if (messages == null || messages.isEmpty()) return "ENGLISH";
        int hinglishCount = 0;
        int totalWords = 0;

        for (ContextMessage msg : messages) {
            if (msg.content() == null) continue;
            String[] words = msg.content().split("\\s+");
            for (String w : words) {
                totalWords++;
                if (HINGLISH_PATTERN.matcher(w).find()) {
                    hinglishCount++;
                }
            }
        }

        if (totalWords > 0 && ((double) hinglishCount / totalWords) >= 0.08) {
            return "HINGLISH";
        }
        return "ENGLISH";
    }

    private boolean detectDryConversation(ConversationContext context) {
        ContextMessage lastMsg = context.getLastMessage();
        if (lastMsg == null) return false;

        // Partner spoke last and gave a 1-word or short dry response
        if (!lastMsg.isCurrentUser()) {
            String content = lastMsg.content() != null ? lastMsg.content().trim().toLowerCase(Locale.ROOT) : "";
            if (DRY_WORDS.contains(content) || (content.length() <= 8 && !content.contains("?"))) {
                return true;
            }
        }

        // Check if last 3 messages from partner were <= 12 characters
        int shortPartnerMessages = 0;
        int checked = 0;
        List<ContextMessage> msgs = context.messages();
        for (int i = msgs.size() - 1; i >= 0 && checked < 5; i--) {
            ContextMessage m = msgs.get(i);
            if (!m.isCurrentUser()) {
                checked++;
                String c = m.content() != null ? m.content().trim() : "";
                if (c.length() <= 12) {
                    shortPartnerMessages++;
                }
            }
        }
        return shortPartnerMessages >= 3;
    }

    private static final Pattern FLIRTING_PATTERN = Pattern.compile("\\b(flirt|crush|date me|cutie|handsome|pretty|blush|wink|attractive|marry me)\\b", Pattern.CASE_INSENSITIVE);

    private Intent detectIntent(String lastText, boolean isLastFromCurrentUser) {
        if (lastText.isBlank()) return Intent.STATEMENT;
        if (lastText.contains("?") || QUESTION_PATTERN.matcher(lastText).find()) return Intent.QUESTION;
        if (EMOTIONAL_SUPPORT_PATTERN.matcher(lastText).find()) return Intent.EMOTIONAL_SUPPORT;
        if (INVITATION_PATTERN.matcher(lastText).find()) return Intent.INVITATION;
        if (FLIRTING_PATTERN.matcher(lastText).find()) return Intent.FLIRTING;
        if (JOKE_PATTERN.matcher(lastText).find()) return Intent.JOKE;
        if (COMPLIMENT_PATTERN.matcher(lastText).find()) return Intent.COMPLIMENT;
        return Intent.STATEMENT;
    }

    private String detectPrimaryTopic(List<ContextMessage> messages) {
        String allText = messages.stream()
                .map(m -> m.content() != null ? m.content().toLowerCase(Locale.ROOT) : "")
                .reduce("", (a, b) -> a + " " + b);

        if (allText.contains("football") || allText.contains("cricket") || allText.contains("soccer") || allText.contains("match") || allText.contains("gym") || allText.contains("workout") || allText.contains("fitness") || allText.contains("messi") || allText.contains("ronaldo")) return "Sports & Fitness";
        if (allText.contains("coffee") || allText.contains("tea") || allText.contains("dinner") || allText.contains("food") || allText.contains("restaurant") || allText.contains("pizza")) return "Food & Drinks";
        if (allText.contains("trip") || allText.contains("travel") || allText.contains("flight") || allText.contains("vacation") || allText.contains("mountains") || allText.contains("beach")) return "Travel & Adventures";
        if (allText.contains("study") || allText.contains("studying") || allText.contains("college") || allText.contains("university") || allText.contains("exam") || allText.contains("degree") || allText.contains("major")) return "Studies & Academics";
        if (allText.contains("movie") || allText.contains("series") || allText.contains("netflix") || allText.contains("cinema") || allText.contains("film") || allText.contains("anime") || allText.contains("binge")) return "Movies & Shows";
        if (allText.contains("song") || allText.contains("music") || allText.contains("concert") || allText.contains("band") || allText.contains("album")) return "Music";
        if (allText.contains("work") || allText.contains("office") || allText.contains("boss") || allText.contains("job") || allText.contains("meeting") || allText.contains("client")) return "Work & Career";
        if (allText.contains("weekend") || allText.contains("sunday") || allText.contains("saturday") || allText.contains("plans")) return "Weekend Plans";

        return "Catching up";
    }

    private List<String> detectSecondaryTopics(List<ContextMessage> messages, String primaryTopic) {
        Set<String> topics = new HashSet<>();
        for (ContextMessage msg : messages) {
            String c = msg.content() != null ? msg.content().toLowerCase(Locale.ROOT) : "";
            if (c.contains("work") && !primaryTopic.equals("Work & Career")) topics.add("Work");
            if (c.contains("weekend") && !primaryTopic.equals("Weekend Plans")) topics.add("Weekend");
            if (c.contains("music") && !primaryTopic.equals("Music")) topics.add("Music");
            if (c.contains("travel") && !primaryTopic.equals("Travel & Adventures")) topics.add("Travel");
            if ((c.contains("football") || c.contains("cricket")) && !primaryTopic.equals("Sports & Fitness")) topics.add("Sports");
        }
        return new ArrayList<>(topics);
    }

    private String detectTone(String lastText, boolean isDry, Intent intent) {
        if (isDry) return "Re-energizing";
        if (intent == Intent.EMOTIONAL_SUPPORT) return "Empathetic";
        if (intent == Intent.FLIRTING) return "Playful & Flirty";
        if (intent == Intent.JOKE || intent == Intent.COMPLIMENT) return "Playful";
        if (intent == Intent.INVITATION) return "Warm & Confident";
        if (intent == Intent.QUESTION) return "Direct & Engaging";
        return "Warm & Conversational";
    }

    private Stage detectStage(int messageCount, boolean isDry, Intent intent, boolean isLongGap) {
        if (isLongGap) return Stage.RECONNECTING;
        if (isDry) return Stage.DRY;
        if (messageCount <= 2) return Stage.NEW_MATCH;
        if (messageCount <= 6) return Stage.GETTING_TO_KNOW;
        if (intent == Intent.FLIRTING) return Stage.FLIRTING;
        if (intent == Intent.INVITATION) return Stage.PLANNING;
        if (intent == Intent.EMOTIONAL_SUPPORT) return Stage.DEEP;
        return Stage.CASUAL;
    }
}
