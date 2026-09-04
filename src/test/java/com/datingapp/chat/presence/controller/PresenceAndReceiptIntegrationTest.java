package com.datingapp.chat.presence.controller;

import com.datingapp.chat.conversation.dto.ConversationDetailResponse;
import com.datingapp.chat.conversation.service.ConversationService;
import com.datingapp.chat.message.dto.MessageResponse;
import com.datingapp.chat.message.dto.ReadReceiptRequest;
import com.datingapp.chat.message.dto.SendMessageRequest;
import com.datingapp.chat.message.service.MessageService;
import com.datingapp.chat.security.JwtService;
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

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PresenceAndReceiptIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ObjectMapper objectMapper;

    private String user1Token;
    private String user2Token;
    private String conversationId;

    @BeforeEach
    void setUp() {
        user1Token = "Bearer " + jwtService.generateToken(101L, List.of("ROLE_USER"));
        user2Token = "Bearer " + jwtService.generateToken(202L, List.of("ROLE_USER"));

        ConversationDetailResponse conv = conversationService.createOrGetConversation(101L, 202L);
        conversationId = conv.getConversationId();
    }

    @Test
    @DisplayName("Should send message, verify unread count, acknowledge read receipt via REST and query presence")
    void testReceiptAndPresenceFlow() throws Exception {
        // 1. User 101 sends a message
        SendMessageRequest sendReq = new SendMessageRequest("Hello User 2!", "client-p5-1", null);
        MessageResponse sentMessage = messageService.sendMessage(conversationId, 101L, sendReq);

        // 2. User 202 lists conversations -> unreadCount should be 1
        mockMvc.perform(get("/api/v1/chats")
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].unreadCount").value(1));

        // 3. User 202 sends read receipt via POST /api/v1/chats/{conversationId}/read
        ReadReceiptRequest readReq = new ReadReceiptRequest(sentMessage.getId());
        mockMvc.perform(post("/api/v1/chats/" + conversationId + "/read")
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(readReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 4. User 202 lists conversations again -> unreadCount should now be 0
        mockMvc.perform(get("/api/v1/chats")
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].unreadCount").value(0));

        // 5. Query user presence via GET /api/v1/presence/101
        mockMvc.perform(get("/api/v1/presence/101")
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(101))
                .andExpect(jsonPath("$.data.status").value("OFFLINE"));
    }
}
