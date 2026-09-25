package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.model.ConversationAnalysis;
import com.datingapp.chat.replycoach.model.ConversationContext;
import com.datingapp.chat.replycoach.model.ConversationContext.ContextMessage;
import com.datingapp.chat.replycoach.model.ConversationEnvironment;
import com.datingapp.chat.replycoach.model.LatestMessageAnalysis;
import com.datingapp.chat.replycoach.model.PartnerCommunicationProfile;
import com.datingapp.chat.replycoach.model.UserWritingProfile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReplyCoachPromptBuilder {

    public String buildSystemPrompt(
            int targetCount,
            ConversationEnvironment env,
            UserWritingProfile style,
            List<ReplyIntentPlanner.PlannedIntent> plannedIntents) {
        return buildSystemPrompt(targetCount, env, style, plannedIntents, null, null, null);
    }

    public String buildSystemPrompt(
            int targetCount,
            ConversationEnvironment env,
            UserWritingProfile style,
            List<ReplyIntentPlanner.PlannedIntent> plannedIntents,
            LatestMessageAnalysis latest,
            PartnerCommunicationProfile partnerStyle,
            SuggestionGenerationSession.Snapshot session) {

        StringBuilder sb = new StringBuilder();
        sb.append("You are BondCircle AI Reply Coach — an intelligent, empathetic, and perceptive conversation copilot sitting right beside the user.\n");
        sb.append("Your role is to analyze the recent two-sided chat between CURRENT_USER and OTHER_USER, and provide exactly ")
                .append(targetCount).append(" natural, ready-to-send reply drafts that CURRENT_USER can choose from.\n\n");

        sb.append("CORE PRINCIPLES:\n");
        sb.append("1. LATEST MESSAGE FIRST: The latest incoming OTHER_USER message is the strongest signal. Every reply must directly make sense beneath that exact message; older context only resolves references and prevents repetition.\n");

        if (latest != null && latest.text() != null && !latest.text().isBlank()) {
            sb.append("LATEST MESSAGE INTERPRETATION: intent=").append(latest.intent())
                    .append(", topic=").append(latest.topic())
                    .append(", emotion=").append(latest.emotion())
                    .append(", tone=").append(latest.tone())
                    .append(", opportunity=").append(latest.conversationOpportunity())
                    .append(", expectedResponse=").append(latest.expectedResponseType()).append(".\n");
        }

        if (env != null && env.hasUnansweredQuestion() && env.unansweredQuestionText() != null) {
            sb.append("2. ANSWER THE QUESTION: The other person explicitly asked: \"")
                    .append(sanitize(env.unansweredQuestionText())).append("\". At least two of your suggested replies MUST directly and realistically answer their question!\n");
        } else {
            sb.append("2. TOPIC CONTINUITY: Stay on the current topic without abrupt random shifts.\n");
        }

        sb.append("3. THREE DISTINCT STRATEGIC PATHS:\n");
        if (plannedIntents != null && !plannedIntents.isEmpty()) {
            sb.append("You MUST produce 1 distinct reply for each of the following planned strategies:\n");
            for (ReplyIntentPlanner.PlannedIntent pi : plannedIntents) {
                sb.append("   - Strategy [").append(pi.strategy().name()).append("]: ").append(pi.rationale()).append("\n");
            }
        } else {
            sb.append("Provide 3 distinctly different replies (e.g., 1 playful/witty, 1 curious question or follow-up, 1 warm/empathetic response).\n");
        }

        sb.append("4. LENGTH & TONE: Keep replies concise (typically 5 to 25 words). Never sound like an AI, corporate bot, or formal assistant.\n");

        if (style != null && style.promptDirectives() != null && !style.promptDirectives().isBlank()) {
            sb.append("5. USER'S STYLE & PREFERENCES: ").append(style.promptDirectives()).append("\n");
        }

        if (style != null && style.negativeDirectives() != null && !style.negativeDirectives().isBlank()) {
            sb.append("6. NEGATIVE SIGNALS (USER HISTORICALLY DISLIKES): ").append(style.negativeDirectives()).append("\n");
        }

        if (partnerStyle != null && partnerStyle.promptDirectives() != null) {
            sb.append("PARTNER COMMUNICATION STYLE: ").append(partnerStyle.promptDirectives()).append("\n");
        }

        if (session != null && session.generationNumber() > 1) {
            sb.append("REFRESH GENERATION ").append(session.generationNumber())
                    .append(": Previously attempted strategies were ")
                    .append(session.previousStrategies())
                    .append(". Use the newly planned strategies and produce different ideas, intent, structure, and meaning—not paraphrases.\n");
        }

        if (env != null && env.isDry()) {
            sb.append("7. DRY/LOW-EFFORT CHAT DETECTED: The other user sent low-effort replies (e.g. 'k', 'yeah', 'ok'). Provide engaging re-openers or playful banter to revitalize the conversation naturally! Strictly avoid generic questions like 'How are you?' or 'Tell me more'.\n");
        }

        String lang = env != null ? env.language() : "ENGLISH";
        if ("HINGLISH".equalsIgnoreCase(lang) || "MIXED".equalsIgnoreCase(lang)) {
            sb.append("8. LANGUAGE: Natural conversational urban Hinglish in Roman script (Hindi mixed with English words as used by modern youth, e.g. 'Arre waah, yeh toh badiya hai!', 'Sach me? Phir aage kya hua?'). Strictly NO Devanagari script. Avoid unnatural machine-translated Hindi.\n");
        } else {
            sb.append("8. LANGUAGE: Natural conversational English.\n");
        }

        sb.append("9. ANTI-HALLUCINATION RULE: Never invent facts about what the user did, where they went, what they watched, or what they think if not explicitly stated in context. Suggested replies must remain completely truthful.\n");
        sb.append("10. SAFETY: Never generate harassment, threats, manipulation, guilt-tripping, or emotional blackmail.\n");

        sb.append("\nOUTPUT FORMAT: You MUST return a valid JSON object strictly matching this schema:\n");
        sb.append("{\n");
        sb.append("  \"conversationEnvironment\": {\n");
        sb.append("    \"topic\": \"Specific subject being discussed\",\n");
        sb.append("    \"stage\": \"").append(env != null ? env.stage().name() : "CASUAL").append("\",\n");
        sb.append("    \"momentum\": \"HIGH | STABLE | LOW\",\n");
        sb.append("    \"tone\": \"Conversational tone\",\n");
        sb.append("    \"direction\": \"CONTINUING_TOPIC | EXPANDING_TOPIC | CHANGING_TOPIC | RECONNECTING\",\n");
        sb.append("    \"responseExpectation\": \"ANSWER_REQUIRED | FOLLOW_UP_NEEDED | EMOTIONAL_RESPONSE | OPEN_TOPIC\",\n");
        sb.append("    \"language\": \"").append(lang).append("\",\n");
        sb.append("    \"engagement\": \"MUTUAL | BALANCED | DRY | NEW\"\n");
        sb.append("  },\n");
        sb.append("  \"suggestions\": [\n");
        sb.append("    {\n");
        sb.append("      \"text\": \"The exact draft reply message for the user to send.\",\n");
        sb.append("      \"strategy\": \"ANSWER | ASK_FOLLOWUP | CURIOUS | PLAYFUL | BANTER | EMPATHIZE | SUPPORTIVE | THOUGHTFUL | LIGHT_FLIRTING | RE_OPENER | TOPIC_EXPANSION | TOPIC_SHIFT | TEASE | INVITATION | STORY_CONTINUATION | PERSONAL | PLAN\",\n");
        sb.append("      \"style\": \"Casual | Playful | Warm | Witty\",\n");
        sb.append("      \"topic\": \"Short topic\",\n");
        sb.append("      \"tone\": \"Playful | Warm | Curious\"\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");

        return sb.toString();
    }

    public String buildSystemPrompt(int targetCount, ConversationAnalysis analysis, UserWritingProfile style) {
        ConversationEnvironment env = analysis != null ? new ConversationEnvironment(
                ConversationEnvironment.Stage.valueOf(analysis.stage().name()),
                ConversationEnvironment.Momentum.valueOf(analysis.momentum().name()),
                ConversationEnvironment.RelationshipSignal.BUILDING,
                ConversationEnvironment.LastSpeaker.OTHER_USER,
                analysis.hasUnansweredQuestion() ? ConversationEnvironment.ResponseExpectation.ANSWER_REQUIRED : ConversationEnvironment.ResponseExpectation.FOLLOW_UP_NEEDED,
                ConversationEnvironment.Temperature.WARM,
                ConversationEnvironment.Direction.EXPANDING_TOPIC,
                analysis.hasUnansweredQuestion() ? ConversationEnvironment.QuestionState.QUESTION_ASKED : ConversationEnvironment.QuestionState.NO_QUESTION,
                List.of(ConversationEnvironment.EngagementSignal.BALANCED_INITIATIVE),
                ConversationEnvironment.Depth.PERSONAL,
                analysis.primaryTopic(),
                analysis.secondaryTopics(),
                List.of("General"),
                analysis.language(),
                analysis.isDry(),
                analysis.hasUnansweredQuestion(),
                analysis.lastQuestionText(),
                0,
                false,
                false
        ) : null;

        return buildSystemPrompt(targetCount, env, style, null);
    }

    public String buildUserPrompt(
            ConversationContext context,
            ConversationEnvironment env,
            List<String> rejectedTexts) {
        return buildUserPrompt(context, env, rejectedTexts, null, null, null);
    }

    public String buildUserPrompt(
            ConversationContext context,
            ConversationEnvironment env,
            List<String> rejectedTexts,
            LatestMessageAnalysis latest,
            PartnerCommunicationProfile partnerStyle,
            SuggestionGenerationSession.Snapshot session) {

        StringBuilder sb = new StringBuilder();

        if (latest != null && latest.text() != null && !latest.text().isBlank()) {
            sb.append("=== PRIMARY REPLY TARGET: LATEST INCOMING MESSAGE ===\n");
            sb.append("OTHER_USER: ").append(sanitize(latest.text())).append("\n");
            sb.append("Intent: ").append(latest.intent())
                    .append(" | Topic: ").append(latest.topic())
                    .append(" | Emotion: ").append(latest.emotion())
                    .append(" | Tone: ").append(latest.tone())
                    .append(" | Language: ").append(latest.language()).append("\n");
            sb.append("Question: ").append(latest.question())
                    .append(" | Implicit question: ").append(latest.implicitQuestion())
                    .append(" | Request: ").append(latest.request())
                    .append(" | Opportunity: ").append(latest.conversationOpportunity()).append("\n");
            sb.append("Every suggestion must respond naturally to this message.\n");
            sb.append("=====================================================\n\n");
        }

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
                    .append(env != null ? env.primaryTopic() : "General")
                    .append(", Stage: ")
                    .append(env != null ? env.stage().name() : "CASUAL")
                    .append("):\n");

            // Include long-term memory anchors if present
            if (context.longTermMemories() != null && !context.longTermMemories().isEmpty()) {
                sb.append("=== IMPORTANT MEMORY FROM EARLIER CONVERSATION ===\n");
                for (String memory : context.longTermMemories()) {
                    sb.append("- ").append(memory).append("\n");
                }
                sb.append("==================================================\n\n");
            }

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

        if (session != null && session.generationNumber() > 1) {
            sb.append("\nREFRESH SESSION:\n");
            sb.append("Generation number: ").append(session.generationNumber()).append("\n");
            sb.append("Already attempted strategies: ").append(session.previousStrategies()).append("\n");
            sb.append("Previously shown suggestions are included above. Do not repeat their ideas, intent, structure, question type, or meaning.\n");
        }

        sb.append("\nGenerate the JSON reply suggestions now:");
        return sb.toString();
    }

    public String buildUserPrompt(
            ConversationContext context,
            ConversationAnalysis analysis,
            List<String> rejectedTexts) {
        ConversationEnvironment env = analysis != null ? new ConversationEnvironment(
                ConversationEnvironment.Stage.valueOf(analysis.stage().name()),
                ConversationEnvironment.Momentum.valueOf(analysis.momentum().name()),
                ConversationEnvironment.RelationshipSignal.BUILDING,
                ConversationEnvironment.LastSpeaker.OTHER_USER,
                analysis.hasUnansweredQuestion() ? ConversationEnvironment.ResponseExpectation.ANSWER_REQUIRED : ConversationEnvironment.ResponseExpectation.FOLLOW_UP_NEEDED,
                ConversationEnvironment.Temperature.WARM,
                ConversationEnvironment.Direction.EXPANDING_TOPIC,
                analysis.hasUnansweredQuestion() ? ConversationEnvironment.QuestionState.QUESTION_ASKED : ConversationEnvironment.QuestionState.NO_QUESTION,
                List.of(ConversationEnvironment.EngagementSignal.BALANCED_INITIATIVE),
                ConversationEnvironment.Depth.PERSONAL,
                analysis.primaryTopic(),
                analysis.secondaryTopics(),
                List.of("General"),
                analysis.language(),
                analysis.isDry(),
                analysis.hasUnansweredQuestion(),
                analysis.lastQuestionText(),
                0,
                false,
                false
        ) : null;

        return buildUserPrompt(context, env, rejectedTexts);
    }

    private String sanitize(String input) {
        if (input == null) return "";
        return input.replace("\r\n", " ").replace("\n", " ").trim();
    }
}
