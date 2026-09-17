package com.datingapp.chat.icebreaker.service;

import com.datingapp.chat.icebreaker.dto.IceBreakerCircle;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Concentric relevance rules inspired by BondCircle's product model. The
 * closest circle uses the active exchange; wider circles gradually move from
 * shared history to interests and finally low-pressure discovery prompts.
 */
@Service
public class ConversationCircleRulesEngine {

    public record CircleRule(
            String code,
            String label,
            String description,
            int baseScore
    ) {
        public IceBreakerCircle toResponse() {
            return new IceBreakerCircle(code, label, description);
        }
    }

    private static final Map<String, CircleRule> RULES = new LinkedHashMap<>();

    static {
        define(new CircleRule(
                "DIRECT_REPLY",
                "Right now",
                "Respond to the latest message and invite the next detail.",
                96));
        define(new CircleRule(
                "CALLBACK",
                "Your history",
                "Return to a topic that appeared in earlier chats.",
                90));
        define(new CircleRule(
                "COMMON_GROUND",
                "Shared interests",
                "Use something both people already enjoy.",
                86));
        define(new CircleRule(
                "DISCOVERY",
                "Their world",
                "Explore one of the other person's interests.",
                78));
        define(new CircleRule(
                "LIGHT_TOUCH",
                "Easy and playful",
                "Use a low-pressure prompt when stronger signals are unavailable.",
                68));
    }

    private static void define(CircleRule rule) {
        RULES.put(rule.code(), rule);
    }

    public CircleRule get(String code) {
        return RULES.get(code);
    }

    public List<CircleRule> all() {
        return List.copyOf(RULES.values());
    }
}
