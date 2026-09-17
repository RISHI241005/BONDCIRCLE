package com.datingapp.chat.icebreaker.service.impl;

import com.datingapp.chat.common.exception.ErrorCode;
import com.datingapp.chat.common.exception.ForbiddenException;
import com.datingapp.chat.common.exception.ResourceNotFoundException;
import com.datingapp.chat.conversation.entity.Conversation;
import com.datingapp.chat.conversation.repository.ConversationParticipantRepository;
import com.datingapp.chat.conversation.repository.ConversationRepository;
import com.datingapp.chat.icebreaker.dto.IceBreakerResponse;
import com.datingapp.chat.icebreaker.dto.IceBreakerSuggestion;
import com.datingapp.chat.icebreaker.service.IceBreakerService;
import com.datingapp.chat.message.entity.Message;
import com.datingapp.chat.message.repository.MessageRepository;
import com.datingapp.chat.security.User;
import com.datingapp.chat.security.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class InterestBasedIceBreakerService implements IceBreakerService {

    private static final int MESSAGE_LOOKBACK = 12;

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public InterestBasedIceBreakerService(
            ConversationRepository conversationRepository,
            ConversationParticipantRepository participantRepository,
            MessageRepository messageRepository,
            UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public IceBreakerResponse getSuggestions(String conversationId, Long userId) {
        Conversation conversation = conversationRepository.findByPublicId(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conversation not found: " + conversationId, ErrorCode.CONVERSATION_NOT_FOUND));

        if (!participantRepository.existsByConversation_PublicIdAndUserId(conversationId, userId)) {
            throw new ForbiddenException("You are not a participant in this conversation", ErrorCode.CHAT_ACCESS_DENIED);
        }

        Long otherUserId = participantRepository.findOtherParticipantUserIds(conversationId, userId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Other participant not found", ErrorCode.PARTICIPANT_NOT_FOUND));

        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found", ErrorCode.RESOURCE_NOT_FOUND));
        User otherUser = userRepository.findById(otherUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Other user not found", ErrorCode.RESOURCE_NOT_FOUND));

        List<Message> recentMessages = messageRepository.findRecentMessages(conversation.getId(), MESSAGE_LOOKBACK)
                .stream()
                .filter(message -> !message.isDeleted())
                .toList();
        String context = detectContext(recentMessages);
        List<String> shared = sharedInterests(currentUser.getInterestList(), otherUser.getInterestList());
        List<IceBreakerSuggestion> suggestions = buildSuggestions(
                context,
                currentUser.getInterestList(),
                otherUser.getInterestList(),
                shared,
                recentMessages);

        return new IceBreakerResponse(
                conversationId,
                context,
                guidanceFor(context),
                shared,
                suggestions);
    }

    private String detectContext(List<Message> messages) {
        if (messages.isEmpty()) {
            return "NEW_CONVERSATION";
        }
        long shortReplies = messages.stream()
                .limit(4)
                .filter(message -> message.getContent() != null && message.getContent().trim().length() <= 24)
                .count();
        if (messages.size() >= 3 && shortReplies >= 3) {
            return "SHORT_REPLIES";
        }
        Instant lastMessageAt = messages.getFirst().getCreatedAt();
        if (lastMessageAt != null && lastMessageAt.isBefore(Instant.now().minus(6, ChronoUnit.HOURS))) {
            return "QUIET_CONVERSATION";
        }
        return "KEEP_IT_GOING";
    }

    private String guidanceFor(String context) {
        return switch (context) {
            case "NEW_CONVERSATION" -> "Start with an easy, specific question that invites a story.";
            case "SHORT_REPLIES" -> "Try a light either-or question instead of another broad question.";
            case "QUIET_CONVERSATION" -> "Restart gently without calling out the gap in replies.";
            default -> "Build on a real interest and leave room for more than a yes or no.";
        };
    }

    private List<String> sharedInterests(List<String> mine, List<String> theirs) {
        Map<String, String> mineByKey = new LinkedHashMap<>();
        mine.forEach(value -> mineByKey.put(value.toLowerCase(Locale.ROOT), value));
        return theirs.stream()
                .filter(value -> mineByKey.containsKey(value.toLowerCase(Locale.ROOT)))
                .map(value -> mineByKey.get(value.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private List<IceBreakerSuggestion> buildSuggestions(
            String context,
            List<String> mine,
            List<String> theirs,
            List<String> shared,
            List<Message> messages) {
        List<IceBreakerSuggestion> result = new ArrayList<>();
        List<String> availableTheirs = prioritizeUnused(theirs, messages);
        if (!shared.isEmpty()) {
            List<String> differentTopics = availableTheirs.stream()
                    .filter(interest -> shared.stream().noneMatch(value -> value.equalsIgnoreCase(interest)))
                    .toList();
            if (!differentTopics.isEmpty()) {
                availableTheirs = differentTopics;
            }
        }

        if (!shared.isEmpty()) {
            String interest = shared.getFirst();
            result.add(new IceBreakerSuggestion(
                    "We both like " + interest + " — what’s your favorite memory connected to it?",
                    interest,
                    "A shared interest makes the question feel natural and personal."));
        }

        if (!availableTheirs.isEmpty()) {
            String interest = availableTheirs.getFirst();
            result.add(new IceBreakerSuggestion(
                    "I saw you’re into " + interest + " — what got you interested in it?",
                    interest,
                    "It invites them to tell a story about something they already enjoy."));
            result.add(new IceBreakerSuggestion(
                    "Quick choice: a relaxed " + interest + " day or an adventurous one?",
                    interest,
                    context.equals("SHORT_REPLIES")
                            ? "An either-or question is easy to answer when replies are getting short."
                            : "A playful choice is low-pressure and gives you an easy follow-up."));
        } else if (!mine.isEmpty()) {
            String interest = mine.getFirst();
            result.add(new IceBreakerSuggestion(
                    "I’ve been really into " + interest + " lately. What have you been enjoying recently?",
                    interest,
                    "Sharing your own interest first makes the question feel balanced."));
        }

        addFallbacks(result, context);
        return result.stream().limit(3).toList();
    }

    private List<String> prioritizeUnused(List<String> interests, List<Message> messages) {
        String recentText = messages.stream()
                .map(Message::getContent)
                .filter(content -> content != null)
                .reduce("", (left, right) -> left + " " + right)
                .toLowerCase(Locale.ROOT);
        return interests.stream()
                .sorted((left, right) -> Boolean.compare(
                        recentText.contains(left.toLowerCase(Locale.ROOT)),
                        recentText.contains(right.toLowerCase(Locale.ROOT))))
                .toList();
    }

    private void addFallbacks(List<IceBreakerSuggestion> result, String context) {
        if (context.equals("QUIET_CONVERSATION")) {
            result.add(new IceBreakerSuggestion(
                    "What’s something you’re looking forward to this week?",
                    "This week",
                    "It restarts the chat naturally without focusing on the silence."));
        }
        result.add(new IceBreakerSuggestion(
                "What’s been the best part of your day so far?",
                "Today",
                "It is warm, specific, and easy to answer with more than one word."));
        result.add(new IceBreakerSuggestion(
                "Would you rather plan a cozy evening or do something spontaneous?",
                "Just for fun",
                "A simple choice keeps the conversation playful and creates follow-up questions."));
        result.add(new IceBreakerSuggestion(
                "What’s something you could talk about for hours?",
                "Favorites",
                "It helps uncover an interest even when the profile has none yet."));
    }
}
