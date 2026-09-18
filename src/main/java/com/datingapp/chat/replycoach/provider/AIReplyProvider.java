package com.datingapp.chat.replycoach.provider;

import com.datingapp.chat.config.AiAssistantProperties;
import com.datingapp.chat.replycoach.dto.ConversationStateDto;
import com.datingapp.chat.replycoach.dto.ReplySuggestionItem;
import com.datingapp.chat.replycoach.model.ConversationAnalysis;
import com.datingapp.chat.replycoach.model.ConversationContext;
import com.datingapp.chat.replycoach.model.UserWritingProfile;
import com.datingapp.chat.replycoach.service.ReplyCoachPromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class AIReplyProvider {

    private static final Logger log = LoggerFactory.getLogger(AIReplyProvider.class);

    private final AiAssistantProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient defaultRestClient;
    private final ReplyCoachPromptBuilder promptBuilder;

    @Autowired
    public AIReplyProvider(
            AiAssistantProperties properties,
            ObjectMapper objectMapper,
            ReplyCoachPromptBuilder promptBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.promptBuilder = promptBuilder != null ? promptBuilder : new ReplyCoachPromptBuilder();
        Duration timeout = Duration.ofSeconds(Math.max(3, properties.getTimeoutSeconds()));
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.defaultRestClient = RestClient.builder()
                .baseUrl(normalizeBaseUrl(properties.getBaseUrl()))
                .requestFactory(requestFactory)
                .build();
    }

    public AIReplyProvider(AiAssistantProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, new ReplyCoachPromptBuilder());
    }

    private static String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return "https://api.groq.com/openai/v1";
        }
        String trimmed = url.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public boolean isConfigured() {
        return properties.isEnabled()
                && properties.getApiKey() != null
                && !properties.getApiKey().isBlank();
    }

    /**
     * Advanced generation method using rich domain models and structured prompt builder.
     */
    public Optional<GenerationResult> generateSuggestions(
            ConversationContext context,
            ConversationAnalysis analysis,
            UserWritingProfile styleProfile,
            List<String> rejectedTexts,
            int limit) {

        if (!isConfigured()) {
            log.info("AI Provider is not enabled or API key is not configured.");
            return Optional.empty();
        }

        int targetCount = Math.max(1, Math.min(limit, 3));
        String apiKey = properties.getApiKey().trim();
        String baseUrl = normalizeBaseUrl(properties.getBaseUrl());
        String model = properties.getModel() != null && !properties.getModel().isBlank()
                ? properties.getModel().trim()
                : "openai/gpt-oss-20b";

        String systemPrompt = promptBuilder.buildSystemPrompt(targetCount, analysis, styleProfile);
        String userPrompt = promptBuilder.buildUserPrompt(context, analysis, rejectedTexts);

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            ));
            body.put("response_format", Map.of("type", "json_object"));
            body.put("max_tokens", 800);
            body.put("temperature", 0.7);

            String uri = baseUrl.endsWith("/chat/completions") ? "" : "/chat/completions";
            JsonNode response = defaultRestClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            return parseAiResponse(response, targetCount, analysis);
        } catch (Exception ex) {
            log.warn("AI Reply Coach generation failed via {}: {}. Falling back to internal engine.",
                    baseUrl, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Backward-compatible generation method for dialogue string inputs.
     */
    public Optional<GenerationResult> generateSuggestions(
            List<String> formattedDialogue,
            String detectedLanguage,
            boolean isDry,
            List<String> rejectedTexts,
            String userStyleHints,
            String userInterests,
            String partnerInterests,
            int limit) {

        if (!isConfigured()) {
            log.info("AI Provider is not enabled or API key is not configured.");
            return Optional.empty();
        }

        int targetCount = Math.max(1, Math.min(limit, 3));
        String apiKey = properties.getApiKey().trim();
        String baseUrl = normalizeBaseUrl(properties.getBaseUrl());
        String model = properties.getModel() != null && !properties.getModel().isBlank()
                ? properties.getModel().trim()
                : "openai/gpt-oss-20b";

        String systemPrompt = buildSystemPrompt(targetCount, detectedLanguage, isDry, userStyleHints);
        String userPrompt = buildUserPrompt(formattedDialogue, rejectedTexts, userInterests, partnerInterests, isDry);

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            ));
            body.put("response_format", Map.of("type", "json_object"));
            body.put("max_tokens", 800);
            body.put("temperature", 0.7);

            String uri = baseUrl.endsWith("/chat/completions") ? "" : "/chat/completions";
            JsonNode response = defaultRestClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            return parseAiResponse(response, targetCount, null);
        } catch (Exception ex) {
            log.warn("AI Reply Coach generation failed via {}: {}. Falling back to internal engine.",
                    baseUrl, ex.getMessage());
            return Optional.empty();
        }
    }

    private String buildSystemPrompt(int targetCount, String detectedLanguage, boolean isDry, String userStyleHints) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are BondCircle AI Reply Coach — an intelligent, charming, and perceptive conversation copilot sitting right beside the user.\n");
        sb.append("Your role is to analyze the recent two-sided chat between CURRENT_USER and OTHER_USER, and provide exactly ")
                .append(targetCount).append(" natural, ready-to-send reply drafts that CURRENT_USER can choose from.\n\n");

        sb.append("CRITICAL GUIDELINES:\n");
        sb.append("1. CONVERSATION FLOW: Understand who spoke last and what they asked or shared. The replies must directly continue this thread smoothly.\n");
        sb.append("2. VARIETY & ANGLES: Provide distinct angles (e.g., one witty/playful, one curious question, one warm/thoughtful response). Do not repeat the same thought.\n");
        sb.append("3. LENGTH & STYLE: Keep replies concise (typically 5 to 25 words). Never sound like an AI or corporate bot.\n");

        if (userStyleHints != null && !userStyleHints.isBlank()) {
            sb.append("4. USER'S PERSONAL STYLE: ").append(userStyleHints).append("\n");
        }

        if (isDry) {
            sb.append("5. DRY/STUCK CHAT DETECTED: The other user sent low-effort replies (e.g. 'k', 'yeah', 'cool'). Provide engaging re-openers or playful banter to breathe life back into the conversation!\n");
        }

        if ("HINGLISH".equalsIgnoreCase(detectedLanguage)) {
            sb.append("6. LANGUAGE: Speak in natural, urban Roman-script Hinglish (Hindi mixed with English words as used by modern youth, e.g. 'Arre waah, yeh toh badiya hai!', 'Sach me? Phir aage kya hua?'). Strictly NO Devanagari script.\n");
        } else if ("HINDI".equalsIgnoreCase(detectedLanguage)) {
            sb.append("6. LANGUAGE: Modern conversational Hinglish/Hindi in Roman script.\n");
        } else {
            sb.append("6. LANGUAGE: Natural conversational English.\n");
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
        sb.append("      \"topic\": \"Short sub-topic\",\n");
        sb.append("      \"tone\": \"Playful | Curious | Warm\",\n");
        sb.append("      \"strategy\": \"ASK_FOLLOWUP | PLAYFUL | EMPATHIZE | ANSWER | BANTER | CURIOUS | SUPPORTIVE\",\n");
        sb.append("      \"style\": \"Casual | Playful | Warm\"\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String buildUserPrompt(
            List<String> formattedDialogue,
            List<String> rejectedTexts,
            String userInterests,
            String partnerInterests,
            boolean isDry) {

        StringBuilder sb = new StringBuilder();
        if (formattedDialogue.isEmpty()) {
            sb.append("CONVERSATION STATUS: Fresh match / No prior messages.\n");
            if (userInterests != null && !userInterests.isBlank()) {
                sb.append("MY INTERESTS/BIO: ").append(userInterests).append("\n");
            }
            if (partnerInterests != null && !partnerInterests.isBlank()) {
                sb.append("THEIR INTERESTS/BIO: ").append(partnerInterests).append("\n");
            }
            sb.append("TASK: Generate 3 personalized, charismatic conversation openers based on their interests or relatable icebreakers.\n");
        } else {
            sb.append("RECENT CHAT HISTORY (Chronological):\n");
            for (String msg : formattedDialogue) {
                sb.append(msg).append("\n");
            }
        }

        if (rejectedTexts != null && !rejectedTexts.isEmpty()) {
            sb.append("\nREJECTED SUGGESTIONS (DO NOT repeat these or similar phrasing):\n");
            for (String rejected : rejectedTexts) {
                sb.append("- \"").append(rejected).append("\"\n");
            }
        }

        sb.append("\nGenerate the JSON reply suggestions now:");
        return sb.toString();
    }

    private Optional<GenerationResult> parseAiResponse(JsonNode response, int targetCount, ConversationAnalysis analysis) {
        if (response == null) {
            return Optional.empty();
        }
        String outputText = "";
        JsonNode choices = response.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            outputText = choices.get(0).path("message").path("content").asText("");
        }
        if (outputText.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(outputText);
            JsonNode suggestionsNode = root.path("suggestions");
            if (!suggestionsNode.isArray() || suggestionsNode.isEmpty()) {
                return Optional.empty();
            }

            List<ReplySuggestionItem> items = new ArrayList<>();
            for (JsonNode item : suggestionsNode) {
                String text = item.path("text").asText("").trim();
                if (!text.isBlank() && text.length() <= 500) {
                    boolean exists = items.stream().anyMatch(i -> i.getText().equalsIgnoreCase(text));
                    if (!exists) {
                        String topic = item.hasNonNull("topic") ? item.path("topic").asText("General") : "General";
                        String tone = item.hasNonNull("tone") ? item.path("tone").asText("Conversational") : "Conversational";
                        String strategy = item.hasNonNull("strategy") ? item.path("strategy").asText("CONVERSATIONAL") : "CONVERSATIONAL";
                        String style = item.hasNonNull("style") ? item.path("style").asText(tone) : tone;

                        items.add(new ReplySuggestionItem(
                                UUID.randomUUID().toString(),
                                text,
                                topic,
                                tone,
                                strategy,
                                style
                        ));
                    }
                }
                if (items.size() >= targetCount) {
                    break;
                }
            }

            if (items.isEmpty()) {
                return Optional.empty();
            }

            String defaultStage = analysis != null ? analysis.stage().name() : "CASUAL";
            boolean defaultHasQuestion = analysis != null && analysis.hasUnansweredQuestion();
            String defaultQuestionText = analysis != null ? analysis.lastQuestionText() : null;

            String stage = root.hasNonNull("stage") ? root.path("stage").asText(defaultStage) : defaultStage;
            boolean hasQuestion = root.hasNonNull("hasUnansweredQuestion") ? root.path("hasUnansweredQuestion").asBoolean(defaultHasQuestion) : defaultHasQuestion;
            String questionText = root.hasNonNull("unansweredQuestionText") ? root.path("unansweredQuestionText").asText(defaultQuestionText) : defaultQuestionText;

            ConversationStateDto state = new ConversationStateDto(
                    root.path("topic").asText(analysis != null ? analysis.primaryTopic() : "Chat"),
                    root.path("tone").asText(analysis != null ? analysis.tone() : "Friendly"),
                    root.path("engagement").asText(analysis != null && analysis.isDry() ? "DRY" : "BALANCED"),
                    root.path("language").asText(analysis != null ? analysis.language() : "ENGLISH"),
                    root.path("dry").asBoolean(analysis != null && analysis.isDry()),
                    stage,
                    hasQuestion,
                    questionText
            );

            return Optional.of(new GenerationResult(items, state));
        } catch (Exception ex) {
            log.warn("Failed to parse AI JSON response: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public record GenerationResult(List<ReplySuggestionItem> suggestions, ConversationStateDto state) {
    }
}
