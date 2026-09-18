package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.model.ConversationAnalysis;
import com.datingapp.chat.replycoach.model.ConversationContext;
import com.datingapp.chat.replycoach.model.ConversationContext.ContextMessage;
import com.datingapp.chat.replycoach.model.UserWritingProfile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReplyCoachPromptBuilder {

    public String buildSystemPrompt(int targetCount, ConversationAnalysis analysis, UserWritingProfile style) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are BondCircle AI Reply Coach — an intelligent, charming, and perceptive conversation copilot sitting right beside the user.\n");
        sb.append("Your role is to analyze the recent two-sided chat between CURRENT_USER and OTHER_USER, and provide exactly ")
                .append(targetCount).append(" natural, ready-to-send reply drafts that CURRENT_USER can choose from.\n\n");

        sb.append("CORE PRINCIPLES:\n");
        sb.append("1. CONVERSATION FLOW: Understand who spoke last and what they asked or shared. The replies must directly continue this thread smoothly.\n");
        if (analysis != null && analysis.hasUnansweredQuestion() && analysis.lastQuestionText() != null) {
            sb.append("2. ANSWER THE QUESTION: The other person explicitly asked: \"")
                    .append(sanitize(analysis.lastQuestionText())).append("\". At least two of your suggested replies MUST directly and realistically answer their question!\n");
        } else {
            sb.append("2. TOPIC CONTINUITY: Stay on the current topic without abrupt random shifts.\n");
        }

        sb.append("3. THREE DISTINCT ANGLES: Provide 3 distinctly different replies (e.g., 1 playful/witty, 1 curious question or follow-up, 1 warm/empathetic response).\n");
        sb.append("4. LENGTH & TONE: Keep replies concise (typically 5 to 25 words). Never sound like an AI, corporate bot, or formal assistant.\n");

        if (style != null && style.promptDirectives() != null && !style.promptDirectives().isBlank()) {
            sb.append("5. USER'S STYLE & PREFERENCES: ").append(style.promptDirectives()).append("\n");
        }

        if (analysis != null && analysis.isDry()) {
            sb.append("6. DRY/LOW-EFFORT CHAT DETECTED: The other user sent low-effort replies (e.g. 'k', 'yeah', 'ok'). Provide engaging re-openers or playful banter to revitalize the conversation naturally!\n");
        }

        String lang = analysis != null ? analysis.language() : "ENGLISH";
        if ("HINGLISH".equalsIgnoreCase(lang)) {
            sb.append("7. LANGUAGE: Natural conversational Hinglish in Roman script (Hindi mixed with English words as used by modern youth, e.g. 'Arre waah, yeh toh badiya hai!', 'Sach me? Phir aage kya hua?'). Strictly NO Devanagari script.\n");
        } else if ("HINDI".equalsIgnoreCase(lang)) {
            sb.append("7. LANGUAGE: Modern conversational Hinglish/Hindi in Roman script.\n");
        } else {
            sb.append("7. LANGUAGE: Natural conversational English.\n");
        }

        sb.append("\nOUTPUT FORMAT: You MUST return a valid JSON object strictly matching this schema:\n");
        sb.append("{\n");
        sb.append("  \"topic\": \"Specific subject being discussed (e.g., Weekend plans, Work stress, Favorite movies)\",\n");
        sb.append("  \"tone\": \"Conversational tone (e.g., Playful, Warm, Curious)\",\n");
        sb.append("  \"engagement\": \"HIGH | BALANCED | DRY | NEW\",\n");
        sb.append("  \"language\": \"ENGLISH | HINGLISH | HINDI\",\n");
        sb.append("  \"dry\": true | false,\n");
        sb.append("  \"suggestions\": [\n");
        sb.append("    {\n");
        sb.append("      \"text\": \"The exact draft reply message for the user to send.\",\n");
        sb.append("      \"strategy\": \"ASK_FOLLOWUP | PLAYFUL | EMPATHIZE | ANSWER | BANTER | CURIOUS | SUPPORTIVE\",\n");
        sb.append("      \"style\": \"Casual | Playful | Warm | Witty\"\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");

        return sb.toString();
    }

    public String buildUserPrompt(
            ConversationContext context,
            ConversationAnalysis analysis,
            List<String> rejectedTexts) {

        StringBuilder sb = new StringBuilder();

        if (context == null || context.isEmpty()) {
            sb.append("CONVERSATION STATUS: Fresh match / No prior messages.\n");
            if (context != null && context.currentUserInterests() != null && !context.currentUserInterests().isBlank()) {
                sb.append("MY INTERESTS/BIO: ").append(sanitize(context.currentUserInterests())).append("\n");
            }
            if (context != null && context.partnerInterests() != null && !context.partnerInterests().isBlank()) {
                sb.append("THEIR INTERESTS/BIO: ").append(sanitize(context.partnerInterests())).append("\n");
            }
            sb.append("TASK: Generate 3 personalized, charismatic conversation openers based on their interests or relatable icebreakers.\n");
        } else {
            sb.append("CONVERSATION CONTEXT (Topic: ")
                    .append(analysis != null ? analysis.primaryTopic() : "General")
                    .append(", Stage: ")
                    .append(analysis != null ? analysis.stage() : "CASUAL")
                    .append("):\n");

            sb.append("=== CHAT HISTORY (Oldest to Newest) ===\n");
            for (ContextMessage msg : context.messages()) {
                String cleanContent = sanitize(msg.content());
                if (msg.isCurrentUser()) {
                    sb.append("CURRENT_USER (YOU): ").append(cleanContent).append("\n");
                } else {
                    sb.append("OTHER_USER: ").append(cleanContent).append("\n");
                }
            }
            sb.append("=== END OF CHAT HISTORY ===\n\n");
        }

        if (rejectedTexts != null && !rejectedTexts.isEmpty()) {
            sb.append("STRICT INSTRUCTION — PREVIOUSLY REJECTED SUGGESTIONS:\n");
            sb.append("The user rejected the following replies. DO NOT repeat or paraphrase these:\n");
            for (String rejected : rejectedTexts) {
                if (rejected != null && !rejected.isBlank()) {
                    sb.append("- \"").append(sanitize(rejected.trim())).append("\"\n");
                }
            }
            sb.append("Provide completely new angles and wording!\n");
        }

        sb.append("\nGenerate the JSON reply suggestions now:");
        return sb.toString();
    }

    private String sanitize(String input) {
        if (input == null) return "";
        return input.replace("\r\n", " ").replace("\n", " ").trim();
    }
}
