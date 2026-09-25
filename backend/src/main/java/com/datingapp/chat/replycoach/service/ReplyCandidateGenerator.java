package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.dto.ReplySuggestionItem;
import com.datingapp.chat.replycoach.model.ConversationContext;
import com.datingapp.chat.replycoach.model.ConversationEnvironment;
import com.datingapp.chat.replycoach.model.LatestMessageAnalysis;
import com.datingapp.chat.replycoach.model.ReplyStrategy;
import com.datingapp.chat.replycoach.model.UserWritingProfile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ReplyCandidateGenerator {

    public List<ReplySuggestionItem> generateCandidates(
            ConversationContext context,
            ConversationEnvironment env,
            UserWritingProfile styleProfile,
            List<String> rejectedTexts,
            int limit) {
        return generateCandidates(context, env, styleProfile, rejectedTexts, limit, null, List.of(), 1);
    }

    public List<ReplySuggestionItem> generateCandidates(
            ConversationContext context,
            ConversationEnvironment env,
            UserWritingProfile styleProfile,
            List<String> rejectedTexts,
            int limit,
            LatestMessageAnalysis latest,
            List<ReplyStrategy> plannedStrategies,
            int generationNumber) {

        List<ReplySuggestionItem> candidates = new ArrayList<>();
        boolean isHinglish = "HINGLISH".equalsIgnoreCase(env.language()) || "MIXED".equalsIgnoreCase(env.language());
        boolean wantsShort = styleProfile != null && styleProfile.length() == UserWritingProfile.LengthPreference.SHORT;
        boolean wantsEmoji = styleProfile != null && styleProfile.emojiUsage() == UserWritingProfile.EmojiUsage.FREQUENT;
        String emoji = wantsEmoji ? " 😊" : "";
        String laughEmoji = wantsEmoji ? " 😂" : "";

        if (generationNumber > 1 && env != null
                && env.stage() != ConversationEnvironment.Stage.ENDING
                && env.direction() != ConversationEnvironment.Direction.CLOSING) {
            addRefreshCandidates(candidates, env, latest, isHinglish, generationNumber);
        }

        // 0. Long-term memory callback (if available)
        if (context != null && context.longTermMemories() != null && !context.longTermMemories().isEmpty()) {
            for (String memory : context.longTermMemories()) {
                if (memory.toLowerCase().contains("event") || memory.toLowerCase().contains("plan")) {
                    String cleanMem = memory.replaceAll("(?i)partner event/plan:\\s*\"?", "").replaceAll("\"$", "").trim();
                    if (isHinglish) {
                        candidates.add(new ReplySuggestionItem(
                                UUID.randomUUID().toString(),
                                "Waise wo " + cleanMem + " kaisa raha?" + laughEmoji,
                                "Catchup", "Curious",
                                ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                    } else {
                        candidates.add(new ReplySuggestionItem(
                                UUID.randomUUID().toString(),
                                "By the way, how did that " + cleanMem + " go?" + emoji,
                                "Catchup", "Curious",
                                ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                    }
                    break;
                }
            }
        }

        // 1. Fresh match / Icebreakers
        if (context == null || context.isEmpty()) {
            String partnerInterests = context != null ? context.partnerInterests() : null;
            if (partnerInterests != null && !partnerInterests.isBlank()) {
                String interest = partnerInterests.split("\\|")[0].trim();
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Hey! Saw you're into " + interest + " — what's your favorite thing about it?",
                        "Interests", "Curious",
                        ReplyStrategy.CURIOUS.name(), "Curious"));
            } else {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Hey! How's your week treating you so far?" + emoji,
                        "Greeting", "Warm",
                        ReplyStrategy.SUPPORTIVE.name(), "Warm"));
            }
            candidates.add(new ReplySuggestionItem(
                    UUID.randomUUID().toString(),
                    "Coffee or tea person? Need to know before we talk further! 👀",
                    "Icebreaker", "Playful",
                    ReplyStrategy.PLAYFUL.name(), "Playful"));
            candidates.add(new ReplySuggestionItem(
                    UUID.randomUUID().toString(),
                    "Random question: what's one place you've always wanted to travel to?",
                    "Travel", "Curious",
                    ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
            candidates.add(new ReplySuggestionItem(
                    UUID.randomUUID().toString(),
                    "Hey there! What's been the highlight of your day today?",
                    "Day", "Friendly",
                    ReplyStrategy.THOUGHTFUL.name(), "Friendly"));
            return candidates;
        }

        // 2. Dry conversation recovery
        if (env.isDry() || env.stage() == ConversationEnvironment.Stage.DRY) {
            if (isHinglish) {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        wantsShort ? "Arre itna serious 'haan'? 😂" : "Arre itna serious 'haan'? 😂 Sab theek na?",
                        "Banter", "Playful",
                        ReplyStrategy.BANTER.name(), "Playful"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Waise aaj din bhar kya kiya? Kuch interesting?",
                        "Curiosity", "Curious",
                        ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Lagta hai kaafi thake huye ho aaj! Kya chal raha hai?",
                        "Warmth", "Warm",
                        ReplyStrategy.EMPATHIZE.name(), "Warm"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Haha details se overwhelm mat karo mujhe! Batao sach me kya hua?",
                        "Humor", "Playful",
                        ReplyStrategy.PLAYFUL.name(), "Witty"));
            } else {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        wantsShort ? "That's a very concise reply 😂" : "Okay that's a very concise reply 😂 What's actually going on today?",
                        "Humor", "Playful",
                        ReplyStrategy.BANTER.name(), "Playful"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Haha don't overwhelm me with all the details! What are you up to?",
                        "Banter", "Playful",
                        ReplyStrategy.PLAYFUL.name(), "Witty"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Random question to shake things up — what made you smile today?",
                        "Curiosity", "Curious",
                        ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Sounds like a long day! Doing anything fun tonight to unwind?",
                        "Empathy", "Warm",
                        ReplyStrategy.SUPPORTIVE.name(), "Warm"));
            }
            return candidates;
        }

        // 3. Natural closing: do not force another question when the other
        // person is clearly ending the exchange.
        if (env.stage() == ConversationEnvironment.Stage.ENDING) {
            if (isHinglish) {
                candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                        "Theek hai, rest karo 😊 baad mein baat karte hain.", "Closing", "Warm",
                        ReplyStrategy.ACKNOWLEDGE.name(), "Warm"));
                candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                        "Goodnight! Kal catch up karte hain 😊", "Closing", "Supportive",
                        ReplyStrategy.SUPPORTIVE.name(), "Casual"));
                candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                        "Chalo phir, sleep well 😄", "Closing", "Playful",
                        ReplyStrategy.PLAYFUL.name(), "Playful"));
            } else {
                candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                        "Alright, rest well 😊 talk later.", "Closing", "Warm",
                        ReplyStrategy.ACKNOWLEDGE.name(), "Warm"));
                candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                        "Goodnight! We can catch up later 😊", "Closing", "Supportive",
                        ReplyStrategy.SUPPORTIVE.name(), "Casual"));
                candidates.add(new ReplySuggestionItem(UUID.randomUUID().toString(),
                        "Sleep well 😄", "Closing", "Playful",
                        ReplyStrategy.PLAYFUL.name(), "Playful"));
            }
            return candidates;
        }

        // 4. Planning/invitation. Avoid inventing availability: ask for the
        // concrete time/place so the user can decide and edit before sending.
        if (env.stage() == ConversationEnvironment.Stage.PLANNING) {
            if (isHinglish) {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Kal kis time ka soch rahe ho? Main schedule check kar loon.",
                        "Plans", "Direct", ReplyStrategy.ANSWER.name(), "Casual"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Haan plan bana sakte hain 👀 place kya socha hai?",
                        "Plans", "Curious", ReplyStrategy.INVITATION.name(), "Curious"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Okayy, time aur place bhejo — phir scene lock karte hain 😂",
                        "Plans", "Playful", ReplyStrategy.PLAYFUL.name(), "Playful"));
            } else {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "What time were you thinking? I'll check my schedule.",
                        "Plans", "Direct", ReplyStrategy.ANSWER.name(), "Casual"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "We could make a plan 👀 where were you thinking?",
                        "Plans", "Curious", ReplyStrategy.INVITATION.name(), "Curious"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Send me the time and place — let's see if we can lock it in 😂",
                        "Plans", "Playful", ReplyStrategy.PLAYFUL.name(), "Playful"));
            }
            return candidates;
        }

        // 5. Unanswered question
        if (env.hasUnansweredQuestion()) {
            String topic = env.primaryTopic();
            if ("Sports & Fitness".equalsIgnoreCase(topic)) {
                if (isHinglish) {
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Match ka kaunsa moment sabse crazy tha? 👀",
                            "Sports", "Excited",
                            ReplyStrategy.ANSWER.name(), "Playful"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Score batao first — phir full match analysis karte hain 😂",
                            "Sports", "Curious",
                            ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Tum kis team ko support kar rahe the?",
                            "Sports", "Playful",
                            ReplyStrategy.PLAYFUL.name(), "Playful"));
                } else {
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Which moment from the match are we talking about? 👀",
                            "Sports", "Excited",
                            ReplyStrategy.ANSWER.name(), "Playful"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Which team were you rooting for? Give me the match recap 😂",
                            "Sports", "Curious",
                            ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Okay, give me your honest match review — worth the hype?",
                            "Sports", "Engaged",
                            ReplyStrategy.THOUGHTFUL.name(), "Warm"));
                }
            } else if ("Studies & Academics".equalsIgnoreCase(topic)) {
                if (isHinglish) {
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            wantsShort ? "Tumhara kitna prep hua?" : "Pehle tum batao — tumhara kitna prep hua? 😅",
                            "Studies", "Curious",
                            ReplyStrategy.ANSWER.name(), "Warm"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Iska honest answer thoda detailed hai 😅 tumhara kaisa chal raha hai?",
                            "Studies", "Direct",
                            ReplyStrategy.ANSWER.name(), "Thoughtful"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Ye question sunte hi study break yaad aa gaya 😂",
                            "Studies", "Playful",
                            ReplyStrategy.BANTER.name(), "Playful"));
                } else {
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            wantsShort ? "How is yours going?" : "I'll give you the full update — how is yours going?",
                            "Studies", "Curious",
                            ReplyStrategy.ANSWER.name(), "Warm"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "That needs an honest update 😅 which part are you asking about?",
                            "Studies", "Direct",
                            ReplyStrategy.ANSWER.name(), "Thoughtful"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "That question alone makes a study break sound good 😂",
                            "Studies", "Playful",
                            ReplyStrategy.BANTER.name(), "Playful"));
                }
            } else {
                if (isHinglish) {
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Haha accha sawal hai! Honestly, situation pe depend karta hai.",
                            "Direct", "Playful",
                            ReplyStrategy.ANSWER.name(), "Playful"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Pehle tumhara opinion sunna hai ispe 👀",
                            "Curious", "Curious",
                            ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Maine is baare me kabhi socha nahi tha, par ab sochna padega 😂",
                            "Thoughtful", "Humorous",
                            ReplyStrategy.THOUGHTFUL.name(), "Playful"));
                } else {
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Haha great question! Honestly, it depends on the day 😂",
                            "Direct", "Playful",
                            ReplyStrategy.ANSWER.name(), "Playful"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "I want your take first 👀 then I'll give you mine.",
                            "Curious", "Curious",
                            ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "I hadn't thought about that before, but now I'm intrigued!",
                            "Thoughtful", "Thoughtful",
                            ReplyStrategy.THOUGHTFUL.name(), "Thoughtful"));
                }
            }
            return candidates;
        }

        // 6. Reconnecting after a gap
        if (env.stage() == ConversationEnvironment.Stage.RECONNECTING) {
            if (isHinglish) {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Heyy 😊 Kaise ho? Kya chal raha hai aaj kal?",
                        "Reconnection", "Warm",
                        ReplyStrategy.SUPPORTIVE.name(), "Warm"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Hey! Sab theek chal raha hai na?",
                        "Catchup", "Curious",
                        ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Heyy 😂 Kya chal raha hai aaj kal?",
                        "Banter", "Playful",
                        ReplyStrategy.BANTER.name(), "Playful"));
            } else {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Heyy 😊 How have you been?",
                        "Reconnection", "Warm",
                        ReplyStrategy.SUPPORTIVE.name(), "Warm"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Hey! How have things been with you lately?",
                        "Catchup", "Thoughtful",
                        ReplyStrategy.THOUGHTFUL.name(), "Warm"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Hey you 😂 what have you been up to lately?",
                        "Banter", "Playful",
                        ReplyStrategy.BANTER.name(), "Playful"));
            }
            return candidates;
        }

        // 7. Emotional support / rough day
        if (env.temperature() == ConversationEnvironment.Temperature.EMOTIONAL
                || env.stage() == ConversationEnvironment.Stage.SUPPORTIVE) {
            if (isHinglish) {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        wantsShort ? "Arre, that sounds rough 😭 Sab theek na?" : "Arre, I'm so sorry! Kaafi rough lag raha hai. Sab theek na?",
                        "Empathy", "Empathetic",
                        ReplyStrategy.EMPATHIZE.name(), "Warm"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Aisa kya hua yaar? If you want to vent, I'm right here to listen.",
                        "Support", "Supportive",
                        ReplyStrategy.SUPPORTIVE.name(), "Thoughtful"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Take a deep breath! Aaj aaram karo thoda, kal better hoga ❤️",
                        "Comfort", "Warm",
                        ReplyStrategy.SUPPORTIVE.name(), "Warm"));
            } else {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        wantsShort ? "That sounds really rough, you holding up okay?" : "I'm so sorry, that sounds really rough. Are you holding up okay?",
                        "Empathy", "Empathetic",
                        ReplyStrategy.EMPATHIZE.name(), "Warm"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Oh no, what happened? If you need to vent, I'm here to listen.",
                        "Support", "Supportive",
                        ReplyStrategy.SUPPORTIVE.name(), "Thoughtful"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Sending you good vibes! Hopefully you can relax and unwind tonight.",
                        "Comfort", "Warm",
                        ReplyStrategy.SUPPORTIVE.name(), "Warm"));
            }
            return candidates;
        }

        // 8. Active dialogue / topic continuation
        String topic = env.primaryTopic();
        if ("Sports & Fitness".equalsIgnoreCase(topic)) {
            if (isHinglish) {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Match dekha tha kya? Kaafi crazy finish tha!",
                        "Sports", "Curious",
                        ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Haha totally agree! Tum kis team ko support karte ho?",
                        "Sports", "Playful",
                        ReplyStrategy.PLAYFUL.name(), "Playful"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Game on! Next match saath me dekhna padega 👀",
                        "Sports", "Warm",
                        ReplyStrategy.BANTER.name(), "Warm"));
            } else {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Did you watch the match? That finish was absolute madness!",
                        "Sports", "Curious",
                        ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Haha 100%! Which team do you root for the most?",
                        "Sports", "Playful",
                        ReplyStrategy.PLAYFUL.name(), "Playful"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Game on! We definitely have to debate this over coffee 👀",
                        "Sports", "Warm",
                        ReplyStrategy.BANTER.name(), "Warm"));
            }
        } else if ("Studies & Academics".equalsIgnoreCase(topic)) {
            candidates.add(new ReplySuggestionItem(
                    UUID.randomUUID().toString(),
                    "Haha that sounds like quite a journey! What are you studying?",
                    "Studies", "Curious",
                    ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
            candidates.add(new ReplySuggestionItem(
                    UUID.randomUUID().toString(),
                    "College can be an absolute whirlwind! How are classes going?",
                    "Studies", "Supportive",
                    ReplyStrategy.SUPPORTIVE.name(), "Warm"));
            candidates.add(new ReplySuggestionItem(
                    UUID.randomUUID().toString(),
                    "Respect! That definitely takes a lot of dedication 👏",
                    "Studies", "Thoughtful",
                    ReplyStrategy.THOUGHTFUL.name(), "Thoughtful"));
        } else if (isHinglish) {
            candidates.add(new ReplySuggestionItem(
                    UUID.randomUUID().toString(),
                    "Arre waah, yeh toh kaafi cool hai! Aur batao?",
                    "Engage", "Warm",
                    ReplyStrategy.SUPPORTIVE.name(), "Warm"));
            candidates.add(new ReplySuggestionItem(
                    UUID.randomUUID().toString(),
                    "Haha seriously? Mujhe bilkul expected nahi tha yeh!",
                    "Surprise", "Playful",
                    ReplyStrategy.PLAYFUL.name(), "Playful"));
            candidates.add(new ReplySuggestionItem(
                    UUID.randomUUID().toString(),
                    "Interesting take 👀 tumhe aisa kyun laga?",
                    "Agreement", "Thoughtful",
                    ReplyStrategy.THOUGHTFUL.name(), "Thoughtful"));
        } else {
            candidates.add(new ReplySuggestionItem(
                    UUID.randomUUID().toString(),
                    "Haha no way! What happened after that?",
                    "Story", "Curious",
                    ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
            candidates.add(new ReplySuggestionItem(
                    UUID.randomUUID().toString(),
                    "Okay, that's an interesting take — what made you see it that way?",
                    "Supportive", "Warm",
                    ReplyStrategy.SUPPORTIVE.name(), "Warm"));
            candidates.add(new ReplySuggestionItem(
                    UUID.randomUUID().toString(),
                    "Okay now you definitely have my full attention 👀",
                    "Teasing", "Playful",
                    ReplyStrategy.BANTER.name(), "Playful"));
        }

        return candidates;
    }

    private void addRefreshCandidates(
            List<ReplySuggestionItem> candidates,
            ConversationEnvironment env,
            LatestMessageAnalysis latest,
            boolean hinglish,
            int generationNumber) {
        String latestText = latest != null && latest.text() != null
                ? latest.text().toLowerCase(java.util.Locale.ROOT) : "";
        boolean studies = "Studies & Academics".equalsIgnoreCase(env.primaryTopic())
                || latestText.matches(".*\\b(college|class|assignment|exam|presentation|project)\\b.*");
        boolean emotional = latest != null && latest.emotionalSignal();
        boolean planning = env.stage() == ConversationEnvironment.Stage.PLANNING
                || latest != null && latest.intent() == com.datingapp.chat.replycoach.model.MessageIntelligence.Intent.INVITATION;

        if (studies) {
            if (hinglish && generationNumber % 2 == 0) {
                add(candidates, "College really said free time cancel today 😂", "Studies", "Playful", ReplyStrategy.BANTER);
                add(candidates, "Ab proper break banta hai — recovery plan kya hai?", "Studies", "Warm", ReplyStrategy.TOPIC_EXPANSION);
                add(candidates, "Kis subject ne sabse zyada torture kiya? 😭", "Studies", "Curious", ReplyStrategy.CURIOUS);
            } else if (hinglish) {
                add(candidates, "Honestly main assignment number two ke baad disappear ho jata 😂", "Studies", "Playful", ReplyStrategy.PERSONAL);
                add(candidates, "Kal bhi same scene hai ya finally thodi peace?", "Studies", "Thoughtful", ReplyStrategy.TOPIC_EXPANSION);
                add(candidates, "At least surviving that deserves a reward 😭", "Studies", "Supportive", ReplyStrategy.SUPPORTIVE);
            } else if (generationNumber % 2 == 0) {
                add(candidates, "College really decided free time was cancelled today 😂", "Studies", "Playful", ReplyStrategy.BANTER);
                add(candidates, "You deserve a proper break after that—what's the recovery plan?", "Studies", "Warm", ReplyStrategy.TOPIC_EXPANSION);
                add(candidates, "Which subject caused the most damage? 😭", "Studies", "Curious", ReplyStrategy.CURIOUS);
            } else {
                add(candidates, "Honestly, assignment number two would've finished me 😂", "Studies", "Playful", ReplyStrategy.PERSONAL);
                add(candidates, "Is tomorrow more of the same or do you finally get some peace?", "Studies", "Thoughtful", ReplyStrategy.TOPIC_EXPANSION);
                add(candidates, "At least surviving that deserves a reward 😭", "Studies", "Supportive", ReplyStrategy.SUPPORTIVE);
            }
            return;
        }

        if (planning) {
            if (hinglish) {
                add(candidates, "Place tum choose karoge ya options bheju? 👀", "Plans", "Curious", ReplyStrategy.INVITATION);
                add(candidates, "Pehle time lock karte hain, phir baaki scene easy hai 😂", "Plans", "Playful", ReplyStrategy.PLAN);
                add(candidates, "Plan interesting lag raha hai—details bhejo 😄", "Plans", "Warm", ReplyStrategy.ACKNOWLEDGE);
            } else {
                add(candidates, "Are you choosing the place, or should I send options? 👀", "Plans", "Curious", ReplyStrategy.INVITATION);
                add(candidates, "Let's lock the time first, then the rest is easy 😂", "Plans", "Playful", ReplyStrategy.PLAN);
                add(candidates, "That plan sounds promising—send me the details 😄", "Plans", "Warm", ReplyStrategy.ACKNOWLEDGE);
            }
            return;
        }

        if (emotional) {
            if (hinglish) {
                add(candidates, "Yaar that sounds rough—thoda breathe karne ka time mila?", "Support", "Warm", ReplyStrategy.THOUGHTFUL);
                add(candidates, "Aaj ka villain kaun tha phir? 😭", "Support", "Playful", ReplyStrategy.BANTER);
                add(candidates, "Vent karna ho toh I'm listening, no pressure.", "Support", "Supportive", ReplyStrategy.SUPPORTIVE);
            } else {
                add(candidates, "That sounds rough—did you get any time to breathe?", "Support", "Warm", ReplyStrategy.THOUGHTFUL);
                add(candidates, "So who was today's villain? 😭", "Support", "Playful", ReplyStrategy.BANTER);
                add(candidates, "If you want to vent, I'm listening—no pressure.", "Support", "Supportive", ReplyStrategy.SUPPORTIVE);
            }
            return;
        }

        if (hinglish) {
            add(candidates, "Okay wait, iska unexpected part kya tha? 👀", env.primaryTopic(), "Curious", ReplyStrategy.STORY_CONTINUATION);
            add(candidates, "Ye story clearly abhi khatam nahi hui 😂", env.primaryTopic(), "Playful", ReplyStrategy.BANTER);
            add(candidates, "Waise iske baad tumhara mood better hua ya aur chaos?", env.primaryTopic(), "Thoughtful", ReplyStrategy.TOPIC_EXPANSION);
        } else {
            add(candidates, "Okay wait, what was the most unexpected part? 👀", env.primaryTopic(), "Curious", ReplyStrategy.STORY_CONTINUATION);
            add(candidates, "This story clearly isn't over yet 😂", env.primaryTopic(), "Playful", ReplyStrategy.BANTER);
            add(candidates, "Did things calm down after that, or was there more chaos?", env.primaryTopic(), "Thoughtful", ReplyStrategy.TOPIC_EXPANSION);
        }
    }

    private void add(
            List<ReplySuggestionItem> candidates,
            String text,
            String topic,
            String tone,
            ReplyStrategy strategy) {
        candidates.add(new ReplySuggestionItem(
                UUID.randomUUID().toString(), text, topic, tone, strategy.name(), tone));
    }
}
