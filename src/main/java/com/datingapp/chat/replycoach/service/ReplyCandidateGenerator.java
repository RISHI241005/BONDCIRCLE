package com.datingapp.chat.replycoach.service;

import com.datingapp.chat.replycoach.dto.ReplySuggestionItem;
import com.datingapp.chat.replycoach.model.ConversationContext;
import com.datingapp.chat.replycoach.model.ConversationEnvironment;
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

        List<ReplySuggestionItem> candidates = new ArrayList<>();
        boolean isHinglish = "HINGLISH".equalsIgnoreCase(env.language()) || "MIXED".equalsIgnoreCase(env.language());
        boolean wantsShort = styleProfile != null && styleProfile.length() == UserWritingProfile.LengthPreference.SHORT;
        boolean wantsEmoji = styleProfile != null && styleProfile.emojiUsage() == UserWritingProfile.EmojiUsage.FREQUENT;
        String emoji = wantsEmoji ? " 😊" : "";
        String laughEmoji = wantsEmoji ? " 😂" : "";

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

        // 3. Unanswered question
        if (env.hasUnansweredQuestion()) {
            String topic = env.primaryTopic();
            if ("Sports & Fitness".equalsIgnoreCase(topic)) {
                if (isHinglish) {
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Haan dekha tha! Kya crazy finish tha match ka!",
                            "Sports", "Excited",
                            ReplyStrategy.ANSWER.name(), "Playful"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Sach bataun toh pura match nahi dekh paya, score kya raha?",
                            "Sports", "Curious",
                            ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Haan! Tum kis team ko support kar rahe the?",
                            "Sports", "Playful",
                            ReplyStrategy.PLAYFUL.name(), "Playful"));
                } else {
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Yes I did! That match was absolute madness!",
                            "Sports", "Excited",
                            ReplyStrategy.ANSWER.name(), "Playful"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "I caught the highlights! Which team were you rooting for?",
                            "Sports", "Curious",
                            ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Yes! Can't believe how that game turned out. Did you enjoy it?",
                            "Sports", "Engaged",
                            ReplyStrategy.THOUGHTFUL.name(), "Warm"));
                }
            } else if ("Studies & Academics".equalsIgnoreCase(topic)) {
                if (isHinglish) {
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            wantsShort ? "Haan bas chal raha hai!" : "Haan bas chal raha hai! Tumhara kitna prep hua?",
                            "Studies", "Curious",
                            ReplyStrategy.ANSWER.name(), "Warm"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Almost done! Par kaafi intense lag raha hai abhi.",
                            "Studies", "Direct",
                            ReplyStrategy.ANSWER.name(), "Thoughtful"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Haha mat pucho! Study break lene ka mann kar raha hai 😂",
                            "Studies", "Playful",
                            ReplyStrategy.BANTER.name(), "Playful"));
                } else {
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            wantsShort ? "Making good progress on it!" : "Making good progress on it! How is yours going?",
                            "Studies", "Curious",
                            ReplyStrategy.ANSWER.name(), "Warm"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Almost done with it, though it's been pretty intense today!",
                            "Studies", "Direct",
                            ReplyStrategy.ANSWER.name(), "Thoughtful"));
                    candidates.add(new ReplySuggestionItem(
                            UUID.randomUUID().toString(),
                            "Haha don't ask! Already dreaming about a study break 😂",
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
                            "Sach bataun toh haan! Tumhara kya opinion hai ispe?",
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
                            "To be completely honest, yes! What's your take on it though?",
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

        // 4. Reconnecting after a gap
        if (env.stage() == ConversationEnvironment.Stage.RECONNECTING) {
            if (isHinglish) {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Hey stranger! Look who's back 😊 Kaise ho?",
                        "Reconnection", "Warm",
                        ReplyStrategy.SUPPORTIVE.name(), "Warm"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Hey! Itne dino baad! Sab theek chal raha hai na?",
                        "Catchup", "Curious",
                        ReplyStrategy.ASK_FOLLOWUP.name(), "Curious"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Look who finally remembered me! 😂 Kya chal raha hai aaj kal?",
                        "Banter", "Playful",
                        ReplyStrategy.BANTER.name(), "Playful"));
            } else {
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Hey stranger! Look who's back 😊 How have you been?",
                        "Reconnection", "Warm",
                        ReplyStrategy.SUPPORTIVE.name(), "Warm"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Hey! Was just thinking about you earlier. How are things with you?",
                        "Catchup", "Thoughtful",
                        ReplyStrategy.THOUGHTFUL.name(), "Warm"));
                candidates.add(new ReplySuggestionItem(
                        UUID.randomUUID().toString(),
                        "Look who's back from the dead! 😂 What have you been up to?",
                        "Banter", "Playful",
                        ReplyStrategy.BANTER.name(), "Playful"));
            }
            return candidates;
        }

        // 5. Emotional support / rough day
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

        // 6. Active dialogue / topic continuation
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
                    "Bilkul sahi kaha tumne, I totally agree with you on this!",
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
                    "That sounds awesome! I completely agree with you on that.",
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
}
