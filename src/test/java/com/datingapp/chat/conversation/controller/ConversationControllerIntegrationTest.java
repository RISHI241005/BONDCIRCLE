package com.datingapp.chat.conversation.controller;

import com.datingapp.chat.conversation.dto.CreateConversationRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ConversationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private String user1Token;
    private String user2Token;
    private String user3Token;

    @BeforeEach
    void setUp() {
        user1Token = "Bearer " + jwtService.generateToken(101L, List.of("ROLE_USER"));
        user2Token = "Bearer " + jwtService.generateToken(202L, List.of("ROLE_USER"));
        user3Token = "Bearer " + jwtService.generateToken(303L, List.of("ROLE_USER"));
    }

    @Test
    @DisplayName("GET /api/v1/chats without JWT should return 401 Unauthorized")
    void testUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/v1/chats")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Complete Conversation flow: Create, List, Get Details, and Archive")
    void testConversationLifecycle() throws Exception {
        // 1. User 101 creates a conversation with User 202
        CreateConversationRequest request = new CreateConversationRequest(202L);
        MvcResult createResult = mockMvc.perform(post("/api/v1/chats")
                        .header("Authorization", user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.conversationId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        String conversationId = JsonPath.read(responseBody, "$.data.conversationId");

        // 2. User 101 lists their conversations and finds the newly created conversation
        mockMvc.perform(get("/api/v1/chats")
                        .header("Authorization", user1Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].conversationId").value(conversationId))
                .andExpect(jsonPath("$.data[0].otherParticipantId").value(202));

        // 3. User 202 (participant) fetches conversation details
        mockMvc.perform(get("/api/v1/chats/" + conversationId)
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.conversationId").value(conversationId));

        // 4. User 303 (non-participant) attempts to fetch details -> 403 Forbidden
        mockMvc.perform(get("/api/v1/chats/" + conversationId)
                        .header("Authorization", user3Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("CHAT_ACCESS_DENIED"));

        // 5. User 101 archives the conversation
        mockMvc.perform(delete("/api/v1/chats/" + conversationId)
                        .header("Authorization", user1Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
