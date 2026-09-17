package com.datingapp.chat.icebreaker.service;

import com.datingapp.chat.config.AiAssistantProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class OpenAiConversationAssistant implements LiveConversationAssistant {

    private static final Logger log = LoggerFactory.getLogger(OpenAiConversationAssistant.class);
    private static final List<String> CIRCLES = List.of(
            "DIRECT_REPLY", "CALLBACK", "COMMON_GROUND", "DISCOVERY", "LIGHT_TOUCH");
    private static final List<String> TONES = List.of("CURIOUS", "WARM", "PLAYFUL", "THOUGHTFUL");
    private static final List<String> LANGUAGES = List.of("ENGLISH", "HINGLISH");

    private final AiAssistantProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAiConversationAssistant(AiAssistantProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        Duration timeout = Duration.ofSeconds(Math.max(3, properties.getTimeoutSeconds()));
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(normalizeBaseUrl(properties.getBaseUrl()))
                .requestFactory(requestFactory)
                .build();
    }

    private static String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return "https://api.openai.com/v1";
        }
        String trimmed = url.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    @Override
    public Optional<ReplyBatch> generate(GenerationRequest request) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        boolean isResponsesApi = isResponsesEndpoint();
        try {
            JsonNode response;
            if (isResponsesApi) {
                response = callResponsesEndpoint(request);
            } else {
                response = callChatCompletionsEndpoint(request);
            }
            return parseResponse(response);
        } catch (Exception exception) {
            log.warn("Live conversation generation failed ({}): {}. Falling back to offline engine.",
                    properties.getBaseUrl(), exception.getMessage());
            return Optional.empty();
        }
    }

    boolean isConfigured() {
        return properties.isEnabled()
                && properties.getApiKey() != null
                && !properties.getApiKey().isBlank()
                && properties.getBaseUrl() != null
                && !properties.getBaseUrl().isBlank();
    }

    private boolean isResponsesEndpoint() {
        String baseUrl = properties.getBaseUrl().toLowerCase(Locale.ROOT);
        return baseUrl.contains("ai-gateway.vercel.sh") || baseUrl.endsWith("/responses");
    }

    private JsonNode callChatCompletionsEndpoint(GenerationRequest request) {
        int count = Math.max(1, Math.min(request.count(), properties.getMaxSuggestions()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", instructions(request.mode(), request.language(), request.tone(), count)),
                Map.of("role", "user", "content", conversationContext(request))
        ));
        body.put("response_format", Map.of("type", "json_object"));
        body.put("max_tokens", properties.getMaxOutputTokens());
        body.put("temperature", 0.7);

        String uri = properties.getBaseUrl().endsWith("/chat/completions") ? "" : "/chat/completions";
        return restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + properties.getApiKey().trim())
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode callResponsesEndpoint(GenerationRequest request) {
        int count = Math.max(1, Math.min(request.count(), properties.getMaxSuggestions()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("store", false);
        body.put("max_output_tokens", properties.getMaxOutputTokens());
        body.put("safety_identifier", safetyIdentifier(request.userId()));
        body.put("instructions", instructions(request.mode(), request.language(), request.tone(), count));
        body.put("input", List.of(Map.of(
                "role", "user",
                "content", List.of(Map.of(
                        "type", "input_text",
                        "text", conversationContext(request))))));
        body.put("text", Map.of("format", Map.of(
                "type", "json_schema",
                "name", "bondcircle_conversation_replies",
                "strict", true,
                "schema", responseSchema(count))));

        String uri = properties.getBaseUrl().endsWith("/responses") ? "" : "/responses";
        return restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + properties.getApiKey().trim())
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    private String instructions(String mode, String language, String tone, int count) {
        String modeInstruction = switch (mode) {
            case "UNABLE_TO_TALK" -> """
                The user is currently UNABLE TO TALK, BUSY, IN A MEETING, OR OCCUPIED.
                CRITICAL: Do NOT write generic canned replies like 'I am busy right now'.
                Instead, craft smart, warm, personalized holding replies that directly acknowledge what the other person specifically mentioned or asked, reassure them with your mood, and set an easy expectation for when you can talk.
                """;
            case "AUTOPILOT" -> "Write exactly one ready-to-send reply in the user's voice tailored to their exact mood and situation.";
            case "WRITE_FOR_ME" -> "Write polished ready-to-send drafts tailored directly to the specific talk and mood.";
            default -> "Create distinct, bespoke reply options tailored to the exact conversational context and mood.";
        };

        return """
                You are BondCircle's intelligent conversation analyst and dating/social copilot.
                Deeply analyze the supplied conversation context, messages, and emotional dynamics.
                
                You must output valid JSON with this exact structure:
                {
                  "detectedMood": "Short label of the conversation vibe (e.g., 'Playful & Teasing', 'Exhausted & Venting', 'Flirtatious & Warm', 'Excited', 'Making Plans', 'Late-night & Cozy', 'Reflective & Sincere')",
                  "conversationScenario": "1-2 sharp sentences analyzing what is actually happening beneath the surface, the other person's intent/vibe, and the relationship dynamics.",
                  "shouldReply": "RECOMMENDED" | "OPTIONAL" | "NO_RUSH",
                  "urgency": "HIGH" | "MEDIUM" | "LOW",
                  "decisionReason": "Concise 1-2 sentence assessment of whether and why the user should reply, analyzing the other person's last message and mood.",
                  "replyTiming": "Suggested time frame (e.g. 'Within 1-2 hours', 'Whenever free', 'No rush')",
                  "guidance": "A sharp, strategic tip on how to match or steer the conversation mood.",
                  "replies": [
                    {
                      "text": "The custom-tailored reply message draft",
                      "topic": "Specific subject or context referenced",
                      "reason": "Why this specific reply fits their mood and advances the scenario",
                      "circle": "DIRECT_REPLY" | "CALLBACK" | "COMMON_GROUND" | "DISCOVERY" | "LIGHT_TOUCH",
                      "tone": "CURIOUS" | "WARM" | "PLAYFUL" | "THOUGHTFUL",
                      "language": "ENGLISH" | "HINGLISH"
                    }
                  ]
                }
                
                CRITICAL INTELLIGENCE & ACCURACY RULES:
                1. NO CANNED OR GENERIC TEMPLATES: Under no circumstances generate formulaic prompts (such as 'What part of it has been on your mind most?' or 'I would love to hear more about X').
                2. Bespoke customization: Every single reply MUST directly refer to the specific nouns, verbs, topics, inside jokes, or sentiments they actually mentioned in the chat.
                3. Mood Alignment: The replies must feel alive and human. If they are playful, reply with witty banter. If they had a rough/hectic day, reply with genuine warmth. If making plans, give concrete engaging answers.
                4. UNABLE TO TALK: When user is busy, acknowledge their specific message before stating you are tied up.
                5. Requested language: %s. For AUTO, naturally match the recent chat: use English for English conversations and comfortable Roman-script Hinglish when the chat contains Hindi or Hinglish. If ambiguous, provide a mix.
                6. Requested tone: %s. ALL means vary tones naturally.
                7. Return %d reply option(s).
                8. Never sound robotic. Never mention being an AI.
                """.formatted(modeInstruction, language, tone, count);
    }

    private String conversationContext(GenerationRequest request) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("conversation_state", request.context());
        context.put("my_interests", request.myInterests());
        context.put("their_interests", request.theirInterests());
        context.put("recent_messages", request.history());
        context.put("refresh_variation", request.variation());
        context.put("mode", request.mode());
        context.put("refresh_instruction", "Choose a noticeably different angle for each new variation value.");
        try {
            return objectMapper.writeValueAsString(context);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize conversation context", exception);
        }
    }

    private Map<String, Object> responseSchema(int count) {
        Map<String, Object> replyProperties = new LinkedHashMap<>();
        replyProperties.put("text", Map.of("type", "string", "minLength", 1, "maxLength", 500));
        replyProperties.put("topic", Map.of("type", "string", "minLength", 1, "maxLength", 80));
        replyProperties.put("reason", Map.of("type", "string", "minLength", 1, "maxLength", 180));
        replyProperties.put("circle", Map.of("type", "string", "enum", CIRCLES));
        replyProperties.put("tone", Map.of("type", "string", "enum", TONES));
        replyProperties.put("language", Map.of("type", "string", "enum", LANGUAGES));

        Map<String, Object> replySchema = new LinkedHashMap<>();
        replySchema.put("type", "object");
        replySchema.put("additionalProperties", false);
        replySchema.put("properties", replyProperties);
        replySchema.put("required", List.of("text", "topic", "reason", "circle", "tone", "language"));

        Map<String, Object> rootProperties = new LinkedHashMap<>();
        rootProperties.put("detectedMood", Map.of("type", "string", "minLength", 1, "maxLength", 100));
        rootProperties.put("conversationScenario", Map.of("type", "string", "minLength", 1, "maxLength", 300));
        rootProperties.put("shouldReply", Map.of("type", "string", "enum", List.of("RECOMMENDED", "OPTIONAL", "NO_RUSH")));
        rootProperties.put("urgency", Map.of("type", "string", "enum", List.of("HIGH", "MEDIUM", "LOW")));
        rootProperties.put("decisionReason", Map.of("type", "string", "minLength", 1, "maxLength", 300));
        rootProperties.put("replyTiming", Map.of("type", "string", "minLength", 1, "maxLength", 100));
        rootProperties.put("guidance", Map.of("type", "string", "minLength", 1, "maxLength", 240));
        rootProperties.put("replies", Map.of(
                "type", "array",
                "minItems", 1,
                "maxItems", count,
                "items", replySchema));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", rootProperties);
        schema.put("required", List.of("detectedMood", "conversationScenario", "guidance", "replies"));
        return schema;
    }

    private Optional<ReplyBatch> parseResponse(JsonNode response) {
        if (response == null) {
            return Optional.empty();
        }
        String outputText = "";
        JsonNode choices = response.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            outputText = choices.get(0).path("message").path("content").asText("");
        }
        if (outputText.isBlank()) {
            outputText = response.path("output_text").asText("");
        }
        if (outputText.isBlank()) {
            outputText = findOutputText(response.path("output"));
        }
        if (outputText.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode result = objectMapper.readTree(outputText);
            List<Reply> replies = new ArrayList<>();
            for (JsonNode node : result.path("replies")) {
                String text = node.path("text").asText("").trim();
                if (!text.isBlank()) {
                    replies.add(new Reply(
                            text,
                            node.path("topic").asText("Conversation"),
                            node.path("reason").asText("Custom tailored to conversation mood."),
                            node.path("circle").asText("LIGHT_TOUCH"),
                            node.path("tone").asText("WARM"),
                            node.path("language").asText("ENGLISH")));
                }
            }
            if (replies.isEmpty()) {
                return Optional.empty();
            }

            String guidance = result.path("guidance").asText("Choose a reply that matches your vibe.");
            String shouldReply = result.path("shouldReply").asText("RECOMMENDED");
            String urgency = result.path("urgency").asText("MEDIUM");
            String decisionReason = result.path("decisionReason").asText(guidance);
            String replyTiming = result.path("replyTiming").asText("Whenever you're ready");
            String detectedMood = result.path("detectedMood").asText("Casual & Engaging");
            String conversationScenario = result.path("conversationScenario").asText("Active dialogue between participants.");

            return Optional.of(new ReplyBatch(
                    guidance,
                    List.copyOf(replies),
                    shouldReply,
                    urgency,
                    decisionReason,
                    replyTiming,
                    detectedMood,
                    conversationScenario));
        } catch (Exception exception) {
            log.warn("AI response did not match the expected schema: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private String findOutputText(JsonNode output) {
        for (JsonNode item : output) {
            for (JsonNode content : item.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    String text = content.path("text").asText("");
                    if (!text.isBlank()) {
                        return text;
                    }
                }
            }
        }
        return "";
    }

    private String safetyIdentifier(Long userId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("bondcircle:" + userId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 32);
        } catch (Exception exception) {
            return "bondcircle-user";
        }
    }
}
