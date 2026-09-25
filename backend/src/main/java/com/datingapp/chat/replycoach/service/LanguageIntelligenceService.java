package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.model.ConversationContext.ContextMessage;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class LanguageIntelligenceService {

    private static final Pattern HINGLISH_WORD_PATTERN = Pattern.compile(
            "\\b(kya|hai|hain|nahi|nahin|yaar|chal|chalo|accha|acha|haan|han|bhai|theek|thik|kaise|kaisa|kaha|kahan|kuch|hoga|hogi|raha|rahi|rahe|meri|mera|mere|tere|tera|teri|apna|apni|sab|matlab|shuru|suno|sun|aaj|kal|parso|waise|badiya|badhiya|mast|fasa|bata|batao|scene|sahi|toh|arre|are|chalega|chalenge|milte|kitne|baje|dost|pagal|dekh|dekha|bolo|ab|fir|phir|kyun|kyu|bolo|kar|karo|karega|karunga|karungi|lag|raha|gaya|gayi|diya|de|le|lo|jaan|sach|sachi|bilkul|thoda|thodi|zyada|bohot|bahut|kisi|usse|isse|hona|chahiye)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SLANG_PATTERN = Pattern.compile(
            "\\b(bro|bhai|yaar|scene|chill|mast|sahi|boss|tbh|ngl|fr|idk|smh|btw|imo)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public record LanguageProfile(
            String dominantLanguage,
            String currentUserLanguage,
            String partnerLanguage,
            double hinglishRatio,
            boolean hasLanguageSwitch,
            Set<String> detectedSlang
    ) {}

    public LanguageProfile analyzeLanguage(List<ContextMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return new LanguageProfile("ENGLISH", "ENGLISH", "ENGLISH", 0.0, false, Collections.emptySet());
        }

        int totalWords = 0;
        int hinglishWords = 0;
        int userHinglishWords = 0;
        int userTotalWords = 0;
        int partnerHinglishWords = 0;
        int partnerTotalWords = 0;
        Set<String> slangSet = new HashSet<>();

        String lastLang = null;
        boolean languageSwitched = false;

        for (ContextMessage msg : messages) {
            if (msg == null || msg.content() == null) continue;
            String text = msg.content().trim();
            if (text.isEmpty()) continue;

            String[] tokens = text.split("\\s+");
            int msgHinglish = 0;

            for (String token : tokens) {
                String clean = token.replaceAll("[^a-zA-Z]", "").toLowerCase(Locale.ROOT);
                if (clean.isEmpty()) continue;

                totalWords++;
                if (msg.isCurrentUser()) {
                    userTotalWords++;
                } else {
                    partnerTotalWords++;
                }

                if (HINGLISH_WORD_PATTERN.matcher(clean).matches()) {
                    hinglishWords++;
                    msgHinglish++;
                    if (msg.isCurrentUser()) {
                        userHinglishWords++;
                    } else {
                        partnerHinglishWords++;
                    }
                }

                if (SLANG_PATTERN.matcher(clean).matches()) {
                    slangSet.add(clean);
                }
            }

            String currentMsgLang = (tokens.length > 0 && ((double) msgHinglish / tokens.length) >= 0.15)
                    ? "HINGLISH" : "ENGLISH";

            if (lastLang != null && !lastLang.equals(currentMsgLang)) {
                languageSwitched = true;
            }
            lastLang = currentMsgLang;
        }

        double overallRatio = totalWords > 0 ? (double) hinglishWords / totalWords : 0.0;
        double userRatio = userTotalWords > 0 ? (double) userHinglishWords / userTotalWords : 0.0;
        double partnerRatio = partnerTotalWords > 0 ? (double) partnerHinglishWords / partnerTotalWords : 0.0;

        String dominant = classifyLanguage(overallRatio);
        String userLang = classifyLanguage(userRatio);
        String partnerLang = classifyLanguage(partnerRatio);

        return new LanguageProfile(dominant, userLang, partnerLang, overallRatio, languageSwitched, slangSet);
    }

    private String classifyLanguage(double hinglishRatio) {
        if (hinglishRatio >= 0.20) {
            return "HINGLISH";
        } else if (hinglishRatio >= 0.06) {
            return "MIXED";
        }
        return "ENGLISH";
    }

    public boolean isHinglishOrMixed(String language) {
        return "HINGLISH".equalsIgnoreCase(language) || "MIXED".equalsIgnoreCase(language);
    }
}
