package com.datingapp.chat.message.controller;

import com.datingapp.chat.block.service.BlockService;
import com.datingapp.chat.conversation.dto.ConversationDetailResponse;
import com.datingapp.chat.conversation.service.ConversationService;
import com.datingapp.chat.message.dto.EditMessageRequest;
import com.datingapp.chat.message.dto.MessageResponse;
import com.datingapp.chat.message.dto.SendMessageRequest;
import com.datingapp.chat.message.service.MessageService;
import com.datingapp.chat.report.dto.CreateReportRequest;
import com.datingapp.chat.report.entity.ReportReason;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MessageLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private BlockService blockService;

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
    @DisplayName("Should edit message, prevent non-owner edit, soft delete, block user and submit report")
    void testFullMessageLifecycle() throws Exception {
        // 1. User 101 sends message
        SendMessageRequest sendReq = new SendMessageRequest("Original message content", "client-ml-1", null);
        MessageResponse sent = messageService.sendMessage(conversationId, 101L, sendReq);
        String messageId = sent.getId();

        // 2. User 101 edits their message
        EditMessageRequest editReq = new EditMessageRequest("Edited message content");
        mockMvc.perform(patch("/api/v1/chats/" + conversationId + "/messages/" + messageId)
                        .header("Authorization", user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("Edited message content"))
                .andExpect(jsonPath("$.data.status").value("EDITED"));

        // 3. User 202 attempts to edit User 101's message -> 403 Forbidden
        mockMvc.perform(patch("/api/v1/chats/" + conversationId + "/messages/" + messageId)
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("NOT_MESSAGE_OWNER"));

        // 4. User 101 soft-deletes message
        mockMvc.perform(delete("/api/v1/chats/" + conversationId + "/messages/" + messageId)
                        .header("Authorization", user1Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("This message was deleted."))
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.status").value("DELETED"));

        // 5. User 101 blocks User 202
        blockService.blockUser(101L, 202L);

        // 6. User 202 attempts to send a new message -> rejected with 403 USER_BLOCKED
        SendMessageRequest blockedSend = new SendMessageRequest("Hello?", "client-ml-2", null);
        mockMvc.perform(post("/api/v1/chats/" + conversationId + "/messages")
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blockedSend)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("USER_BLOCKED"));

        // 7. User 101 submits a moderation report
        CreateReportRequest reportReq = new CreateReportRequest(messageId, ReportReason.HARASSMENT, "Harassing content in conversation");
        mockMvc.perform(post("/api/v1/chats/" + conversationId + "/reports")
                        .header("Authorization", user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reportReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reportId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.reason").value("HARASSMENT"));
    }
}
