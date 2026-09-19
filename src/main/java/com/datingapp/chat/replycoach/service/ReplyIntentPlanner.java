package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.model.ConversationEnvironment;
import com.datingapp.chat.replycoach.model.ReplyStrategy;
import com.datingapp.chat.replycoach.model.UserWritingProfile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ReplyIntentPlanner {

    public record PlannedIntent(ReplyStrategy strategy, String rationale) {}

    public record ReplyIntentPlan(List<PlannedIntent> intents) {
        public List<ReplyStrategy> getStrategies() {
            return intents.stream().map(PlannedIntent::strategy).toList();
        }
    }

    public ReplyIntentPlan planIntents(ConversationEnvironment env, UserWritingProfile styleProfile) {
        Set<ReplyStrategy> rejectedStrategies = styleProfile != null && styleProfile.rejectedStrategies() != null
                ? styleProfile.rejectedStrategies()
                : Set.of();

        List<PlannedIntent> planned = new ArrayList<>();
        Set<ReplyStrategy> selected = new LinkedHashSet<>();

        if (env == null) {
            addStrategy(selected, planned, ReplyStrategy.CURIOUS, "Break ice with a personal or interest-based question.", rejectedStrategies);
            addStrategy(selected, planned, ReplyStrategy.PLAYFUL, "Use a lighthearted, playful opener to keep it engaging.", rejectedStrategies);
            addStrategy(selected, planned, ReplyStrategy.THOUGHTFUL, "Provide a warm, relatable starter.", rejectedStrategies);
            return new ReplyIntentPlan(planned);
        }

        if (env.stage() == ConversationEnvironment.Stage.ENDING
                || env.direction() == ConversationEnvironment.Direction.CLOSING) {
            addStrategy(selected, planned, ReplyStrategy.ACKNOWLEDGE, "Acknowledge the close without trying to force more conversation.", rejectedStrategies);
            addStrategy(selected, planned, ReplyStrategy.SUPPORTIVE, "End warmly and leave the door open naturally.", rejectedStrategies);
            addStrategy(selected, planned, ReplyStrategy.PLAYFUL, "Use a light sign-off only if it fits the current tone.", rejectedStrategies);
            ensureThreeDistinct(selected, planned, rejectedStrategies);
            return new ReplyIntentPlan(planned);
        }

        // 1. Direct unanswered question from partner. This must take priority
        // over a low message count/new-match stage.
        if (env.hasUnansweredQuestion()) {
            addStrategy(selected, planned, ReplyStrategy.ANSWER, "Directly answer the question asked by the other person.", rejectedStrategies);
            addStrategy(selected, planned, ReplyStrategy.ASK_FOLLOWUP, "Answer and ask a reciprocal or curious follow-up.", rejectedStrategies);
            addStrategy(selected, planned, ReplyStrategy.PLAYFUL, "Give a cheeky or playful answer to keep chemistry flowing.", rejectedStrategies);
            ensureThreeDistinct(selected, planned, rejectedStrategies);
            return new ReplyIntentPlan(planned);
        }

        // 2. Planning or invitation also takes priority in a new conversation.
        if (env.stage() == ConversationEnvironment.Stage.PLANNING
                || env.responseExpectation() == ConversationEnvironment.ResponseExpectation.ANSWER_REQUIRED) {
            addStrategy(selected, planned, ReplyStrategy.ANSWER, "Respond directly to plans with schedule or enthusiasm.", rejectedStrategies);
            addStrategy(selected, planned, ReplyStrategy.INVITATION, "Co-plan a specific spot or activity.", rejectedStrategies);
            addStrategy(selected, planned, ReplyStrategy.PLAYFUL, "Use light enthusiasm while still answering the invitation.", rejectedStrategies);
            ensureThreeDistinct(selected, planned, rejectedStrategies);
            return new ReplyIntentPlan(planned);
        }

        // 3. Emotional disclosures deserve acknowledgement even early on.
        if (env.temperature() == ConversationEnvironment.Temperature.EMOTIONAL
                || env.stage() == ConversationEnvironment.Stage.SUPPORTIVE) {
            addStrategy(selected, planned, ReplyStrategy.EMPATHIZE, "Validate their feelings with genuine empathy.", rejectedStrategies);
            addStrategy(selected, planned, ReplyStrategy.SUPPORTIVE, "Offer comforting support and room to share.", rejectedStrategies);
            addStrategy(selected, planned, ReplyStrategy.CURIOUS, "Ask what happened without interrogating them.", rejectedStrategies);
            ensureThreeDistinct(selected, planned, rejectedStrategies);
            return new ReplyIntentPlan(planned);
        }

        if (env.stage() == ConversationEnvironment.Stage.NEW_MATCH) {
            addStrategy(selected, planned, ReplyStrategy.CURIOUS, "Break ice with a personal or interest-based question.", rejectedStrategies);
            addStrategy(selected, planned, ReplyStrategy.PLAYFUL, "Use a lighthearted, playful opener to keep it engaging.", rejectedStrategies);
            addStrategy(selected, planned, ReplyStrategy.THOUGHTFUL, "Provide a warm, relatable starter.", rejectedStrategies);
            return new ReplyIntentPlan(planned);
        }

        // 4. Dry conversation revitalization
        if (env.isDry() || env.stage() == ConversationEnvironment.Stage.DRY) {
            addStrategy(selected, planned, ReplyStrategy.RE_OPENER, "Revitalize conversation with an unexpected, fun topic.", rejectedStrategies);
            addStrategy(selected, planned, ReplyStrategy.BANTER, "Playfully poke fun at dry replies or tease them.", rejectedStrategies);
            addStrategy(selected, planned, ReplyStrategy.CURIOUS, "Ask an engaging, easy-to-answer specific question.", rejectedStrategies);
            ensureThreeDistinct(selected, planned, rejectedStrategies);
            return new ReplyIntentPlan(planned);
        }

        // 4. Reconnecting after a long gap
        if (env.stage() == ConversationEnvironment.Stage.RECONNECTING
                || env.direction() == ConversationEnvironment.Direction.RECONNECTING) {
            addStrategy(selected, planned, ReplyStrategy.RECONNECT, "Warmly welcome them back without guilt-tripping.", rejectedStrategies);
            addStrategy(selected, planned, ReplyStrategy.BANTER, "Playful, humorous jab about their disappearance.", rejectedStrategies);
            addStrategy(selected, planned, ReplyStrategy.SUPPORTIVE, "Friendly check-in on how their week has been.", rejectedStrategies);
            ensureThreeDistinct(selected, planned, rejectedStrategies);
            return new ReplyIntentPlan(planned);
        }

        // 6. Flirting & romantic chemistry
        if (env.stage() == ConversationEnvironment.Stage.FLIRTING
                || env.temperature() == ConversationEnvironment.Temperature.FLIRTY) {
            addStrategy(selected, planned, ReplyStrategy.LIGHT_FLIRTING, "Charming flirtatious retort that builds chemistry.", rejectedStrategies);
            addStrategy(selected, planned, ReplyStrategy.TEASE, "Playful teasing that keeps them wanting to reply.", rejectedStrategies);
            addStrategy(selected, planned, ReplyStrategy.PLAYFUL, "Fun, witty banter.", rejectedStrategies);
            ensureThreeDistinct(selected, planned, rejectedStrategies);
            return new ReplyIntentPlan(planned);
        }

        // 7. Topic expansion & general active dialogue
        if (env.direction() == ConversationEnvironment.Direction.EXPANDING_TOPIC) {
            addStrategy(selected, planned, ReplyStrategy.TOPIC_EXPANSION, "Naturally branch the current topic into an interesting story.", rejectedStrategies);
            addStrategy(selected, planned, ReplyStrategy.CURIOUS, "Ask a curious question delving deeper into their experience.", rejectedStrategies);
            addStrategy(selected, planned, ReplyStrategy.PLAYFUL, "Share a playful reaction or humorous take.", rejectedStrategies);
            ensureThreeDistinct(selected, planned, rejectedStrategies);
            return new ReplyIntentPlan(planned);
        }

        // 8. Default balanced dynamic dialogue
        addStrategy(selected, planned, ReplyStrategy.ASK_FOLLOWUP, "Continue the conversation thread with a natural follow-up.", rejectedStrategies);
        addStrategy(selected, planned, ReplyStrategy.PLAYFUL, "Keep the tone lively and fun with playful banter.", rejectedStrategies);
        addStrategy(selected, planned, ReplyStrategy.THOUGHTFUL, "Share a warm, authentic response reflecting your vibe.", rejectedStrategies);
        ensureThreeDistinct(selected, planned, rejectedStrategies);

        return new ReplyIntentPlan(planned);
    }

    private void addStrategy(
            Set<ReplyStrategy> selected,
            List<PlannedIntent> planned,
            ReplyStrategy strategy,
            String rationale,
            Set<ReplyStrategy> rejectedStrategies) {
        if (!selected.contains(strategy) && !rejectedStrategies.contains(strategy)) {
            selected.add(strategy);
            planned.add(new PlannedIntent(strategy, rationale));
        }
    }

    private void ensureThreeDistinct(
            Set<ReplyStrategy> selected,
            List<PlannedIntent> planned,
            Set<ReplyStrategy> rejectedStrategies) {
        ReplyStrategy[] fallbackOrder = new ReplyStrategy[]{
                ReplyStrategy.CURIOUS,
                ReplyStrategy.PLAYFUL,
                ReplyStrategy.SUPPORTIVE,
                ReplyStrategy.BANTER,
                ReplyStrategy.THOUGHTFUL,
                ReplyStrategy.ASK_FOLLOWUP,
                ReplyStrategy.TOPIC_EXPANSION,
                ReplyStrategy.ACKNOWLEDGE
        };

        for (ReplyStrategy strategy : fallbackOrder) {
            if (planned.size() >= 3) break;
            if (!selected.contains(strategy) && !rejectedStrategies.contains(strategy)) {
                selected.add(strategy);
                planned.add(new PlannedIntent(strategy, "Alternative conversational direction."));
            }
        }

        // In the rare case all were rejected, allow any strategy to guarantee 3
        for (ReplyStrategy strategy : ReplyStrategy.values()) {
            if (planned.size() >= 3) break;
            if (!selected.contains(strategy)) {
                selected.add(strategy);
                planned.add(new PlannedIntent(strategy, "Fallback conversational angle."));
            }
        }
    }
}
