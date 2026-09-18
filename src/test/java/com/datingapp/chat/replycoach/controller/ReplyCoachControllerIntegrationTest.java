package com.datingapp.chat.replycoach.controller;

import com.datingapp.chat.conversation.entity.Conversation;
import com.datingapp.chat.conversation.entity.ConversationParticipant;
import com.datingapp.chat.conversation.repository.ConversationParticipantRepository;
import com.datingapp.chat.conversation.repository.ConversationRepository;
import com.datingapp.chat.message.entity.Message;
import com.datingapp.chat.message.entity.MessageStatus;
import com.datingapp.chat.message.entity.MessageType;
import com.datingapp.chat.message.repository.MessageRepository;
import com.datingapp.chat.replycoach.dto.ReplyFeedbackRequest;
import com.datingapp.chat.replycoach.dto.ReplyRegenerateRequest;
import com.datingapp.chat.replycoach.dto.ReplySuggestionRequest;
import com.datingapp.chat.replycoach.entity.FeedbackAction;
import com.datingapp.chat.security.JwtService;
import com.datingapp.chat.security.User;
import com.datingapp.chat.security.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReplyCoachControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationParticipantRepository participantRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.datingapp.chat.block.repository.BlockRepository blockRepository;

    private String userToken;
    private String conversationId;
    private Long user1Id;
    private Long user2Id;

    @BeforeEach
    void setUp() {
        // Setup users
        User u1 = new User("Alice Coach", "999111222", "alicecoach@test.com", "hash");
        u1.setInterests("Tech|Music");
        u1 = userRepository.save(u1);
        user1Id = u1.getId();

        User u2 = new User("Bob Coach", "999111333", "bobcoach@test.com", "hash");
        u2.setInterests("Art|Travel");
        u2 = userRepository.save(u2);
        user2Id = u2.getId();

        userToken = "Bearer " + jwtService.generateToken(user1Id, List.of("ROLE_USER"));

        // Setup conversation
        conversationId = UUID.randomUUID().toString();
        Conversation conv = new Conversation();
        conv.setPublicId(conversationId);
        conv = conversationRepository.save(conv);

        ConversationParticipant p1 = new ConversationParticipant(conv, user1Id);
        ConversationParticipant p2 = new ConversationParticipant(conv, user2Id);
        participantRepository.save(p1);
        participantRepository.save(p2);

        // Add some messages
        Message m1 = new Message();
        m1.setConversation(conv);
        m1.setSenderId(user2Id);
        m1.setContent("Hey Alice! What did you do this weekend?");
        m1.setMessageType(MessageType.TEXT);
        m1.setStatus(MessageStatus.SENT);
        m1.setPublicId(UUID.randomUUID().toString());
        m1.setCreatedAt(Instant.now().minusSeconds(120));
        messageRepository.save(m1);

        Message m2 = new Message();
        m2.setConversation(conv);
        m2.setSenderId(user1Id);
        m2.setContent("I went hiking in the hills! It was refreshing.");
        m2.setMessageType(MessageType.TEXT);
        m2.setStatus(MessageStatus.SENT);
        m2.setPublicId(UUID.randomUUID().toString());
        m2.setCreatedAt(Instant.now().minusSeconds(60));
        messageRepository.save(m2);

        Message m3 = new Message();
        m3.setConversation(conv);
        m3.setSenderId(user2Id);
        m3.setContent("That sounds so fun! Did you take any photos?");
        m3.setMessageType(MessageType.TEXT);
        m3.setStatus(MessageStatus.SENT);
        m3.setPublicId(UUID.randomUUID().toString());
        m3.setCreatedAt(Instant.now().minusSeconds(20));
        messageRepository.save(m3);
    }

    @Test
    @DisplayName("POST /api/ai/reply-suggestions without JWT returns 401")
    void testUnauthenticated() throws Exception {
        ReplySuggestionRequest req = new ReplySuggestionRequest(conversationId, 3);
        mockMvc.perform(post("/api/ai/reply-suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/ai/reply-suggestions with valid request returns 200 and suggestions")
    void testGetReplySuggestionsSuccess() throws Exception {
        ReplySuggestionRequest req = new ReplySuggestionRequest(conversationId, 3);
        mockMvc.perform(post("/api/ai/reply-suggestions")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.generationId").isNotEmpty())
                .andExpect(jsonPath("$.data.conversationId").value(conversationId))
                .andExpect(jsonPath("$.data.suggestions.length()").value(lessThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data.conversationState.topic").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/ai/reply-suggestions/regenerate returns new suggestions avoiding rejected")
    void testRegenerateSuggestionsSuccess() throws Exception {
        ReplyRegenerateRequest req = new ReplyRegenerateRequest(
                conversationId,
                List.of("dummy-suggestion-id"),
                3
        );
        req.setRejectedTexts(List.of("I went hiking in the hills"));

        mockMvc.perform(post("/api/ai/reply-suggestions/regenerate")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.generationId").isNotEmpty())
                .andExpect(jsonPath("$.data.suggestions.length()").value(lessThanOrEqualTo(3)));
    }

    @Test
    @DisplayName("POST /api/ai/reply-suggestions/feedback records interaction")
    void testFeedbackRecordingSuccess() throws Exception {
        ReplyFeedbackRequest req = new ReplyFeedbackRequest(
                "sug-12345",
                conversationId,
                FeedbackAction.USED,
                "Yeah! Captured some scenic mountain views."
        );

        mockMvc.perform(post("/api/ai/reply-suggestions/feedback")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("recorded"))
                .andExpect(jsonPath("$.data.action").value("USED"));
    }

    @Test
    @DisplayName("POST /api/ai/reply-suggestions/feedback supports LIKED action")
    void testLikedFeedbackSuccess() throws Exception {
        ReplyFeedbackRequest req = new ReplyFeedbackRequest(
                "sug-liked-1",
                conversationId,
                FeedbackAction.LIKED,
                "Hiking is my favorite way to recharge!"
        );

        mockMvc.perform(post("/api/ai/reply-suggestions/feedback")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.action").value("LIKED"));
    }

    @Test
    @DisplayName("POST /api/ai/reply-suggestions by non-participant returns 403 Forbidden")
    void testUnauthorizedUserReturnsForbidden() throws Exception {
        User outsider = new User("Outsider Eve", "999111999", "eve@test.com", "hash");
        outsider = userRepository.save(outsider);
        String outsiderToken = "Bearer " + jwtService.generateToken(outsider.getId(), List.of("ROLE_USER"));

        ReplySuggestionRequest req = new ReplySuggestionRequest(conversationId, 3);
        mockMvc.perform(post("/api/ai/reply-suggestions")
                        .header("Authorization", outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/ai/reply-suggestions for blocked conversation returns 403 Forbidden")
    void testBlockedConversationReturnsForbidden() throws Exception {
        com.datingapp.chat.block.entity.Block block = new com.datingapp.chat.block.entity.Block(user1Id, user2Id);
        blockRepository.save(block);

        ReplySuggestionRequest req = new ReplySuggestionRequest(conversationId, 3);
        mockMvc.perform(post("/api/ai/reply-suggestions")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/ai/reply-suggestions with invalid blank conversationId returns 400")
    void testValidationFailure() throws Exception {
        ReplySuggestionRequest req = new ReplySuggestionRequest("", 3);
        mockMvc.perform(post("/api/ai/reply-suggestions")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
