package com.datingapp.chat.message.controller;

import com.datingapp.chat.conversation.dto.ConversationDetailResponse;
import com.datingapp.chat.conversation.service.ConversationService;
import com.datingapp.chat.message.dto.SendMessageRequest;
import com.datingapp.chat.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MessageControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ObjectMapper objectMapper;

    private String user1Token;
    private String user2Token;
    private String user3Token;
    private String conversationId;

    @BeforeEach
    void setUp() {
        user1Token = "Bearer " + jwtService.generateToken(101L, List.of("ROLE_USER"));
        user2Token = "Bearer " + jwtService.generateToken(202L, List.of("ROLE_USER"));
        user3Token = "Bearer " + jwtService.generateToken(303L, List.of("ROLE_USER"));

        ConversationDetailResponse conv = conversationService.createOrGetConversation(101L, 202L);
        conversationId = conv.getConversationId();
    }

    @Test
    @DisplayName("Should send message, enforce idempotency, and paginate history with cursor")
    void testMessageFlow() throws Exception {
        // 1. User 101 sends first message
        SendMessageRequest req1 = new SendMessageRequest("First message from User 1", "client-id-1", null);
        MvcResult res1 = mockMvc.perform(post("/api/v1/chats/" + conversationId + "/messages")
                        .header("Authorization", user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("First message from User 1"))
                .andExpect(jsonPath("$.data.senderId").value(101))
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andReturn();

        String msg1Id = JsonPath.read(res1.getResponse().getContentAsString(), "$.data.id");

        // 2. Idempotency test: Retry same message send with client-id-1
        mockMvc.perform(post("/api/v1/chats/" + conversationId + "/messages")
                        .header("Authorization", user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(msg1Id));

        // 3. User 202 replies to message 1
        SendMessageRequest req2 = new SendMessageRequest("Reply from User 2", "client-id-2", msg1Id);
        mockMvc.perform(post("/api/v1/chats/" + conversationId + "/messages")
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.replyTo.id").value(msg1Id))
                .andExpect(jsonPath("$.data.replyTo.content").value("First message from User 1"));

        // 4. User 101 sends third message
        SendMessageRequest req3 = new SendMessageRequest("Third message from User 1", "client-id-3", null);
        mockMvc.perform(post("/api/v1/chats/" + conversationId + "/messages")
                        .header("Authorization", user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req3)))
                .andExpect(status().isCreated());

        // 5. Query page with limit=2 (should receive newest 2 messages and nextCursor)
        MvcResult pageResult = mockMvc.perform(get("/api/v1/chats/" + conversationId + "/messages?limit=2")
                        .header("Authorization", user1Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.nextCursor").isNotEmpty())
                .andReturn();

        String nextCursor = JsonPath.read(pageResult.getResponse().getContentAsString(), "$.data.nextCursor");

        // 6. Query next page using cursor
        mockMvc.perform(get("/api/v1/chats/" + conversationId + "/messages?limit=2&cursor=" + nextCursor)
                        .header("Authorization", user1Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.messages.length()").value(1))
                .andExpect(jsonPath("$.data.hasMore").value(false));

        // 7. Non-participant (User 303) cannot read history
        mockMvc.perform(get("/api/v1/chats/" + conversationId + "/messages")
                        .header("Authorization", user3Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("CHAT_ACCESS_DENIED"));
    }
}
