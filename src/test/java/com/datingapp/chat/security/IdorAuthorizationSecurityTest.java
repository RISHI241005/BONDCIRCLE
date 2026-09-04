package com.datingapp.chat.security;

import com.datingapp.chat.conversation.dto.ConversationDetailResponse;
import com.datingapp.chat.conversation.service.ConversationService;
import com.datingapp.chat.message.dto.EditMessageRequest;
import com.datingapp.chat.message.dto.MessageResponse;
import com.datingapp.chat.message.dto.ReadReceiptRequest;
import com.datingapp.chat.message.dto.SendMessageRequest;
import com.datingapp.chat.message.service.MessageService;
import com.datingapp.chat.testutil.AbstractMySQLIntegrationTest;
import com.datingapp.chat.testutil.UserTestFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class IdorAuthorizationSecurityTest extends AbstractMySQLIntegrationTest {

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

    private String userAToken;
    private String userCToken;
    private String convIdAB;
    private String messageIdA;

    @BeforeEach
    void setUpData() {
        userAToken = UserTestFactory.createBearerToken(jwtService, UserTestFactory.USER_A);
        userCToken = UserTestFactory.createBearerToken(jwtService, UserTestFactory.USER_C);

        ConversationDetailResponse conv = conversationService.createOrGetConversation(
                UserTestFactory.USER_A, UserTestFactory.USER_B);
        convIdAB = conv.getConversationId();

        MessageResponse msg = messageService.sendMessage(
                convIdAB, UserTestFactory.USER_A, new SendMessageRequest("Hello B from A", "idor-1", null));
        messageIdA = msg.getId();
    }

    @Test
    @DisplayName("Should reject unauthorized User C accessing Conversation AB details with 403")
    void testIdorGetConversationDetails() throws Exception {
        mockMvc.perform(get("/api/v1/chats/" + convIdAB)
                        .header("Authorization", userCToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CHAT_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Should reject unauthorized User C reading Conversation AB message history with 403")
    void testIdorGetMessageHistory() throws Exception {
        mockMvc.perform(get("/api/v1/chats/" + convIdAB + "/messages")
                        .header("Authorization", userCToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CHAT_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Should reject unauthorized User C sending message to Conversation AB with 403")
    void testIdorSendMessage() throws Exception {
        SendMessageRequest req = new SendMessageRequest("Rogue message from C", "idor-c-1", null);

        mockMvc.perform(post("/api/v1/chats/" + convIdAB + "/messages")
                        .header("Authorization", userCToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CHAT_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Should reject unauthorized User C editing User A's message with 403")
    void testIdorEditMessage() throws Exception {
        EditMessageRequest req = new EditMessageRequest("Tampered message text");

        mockMvc.perform(patch("/api/v1/chats/" + convIdAB + "/messages/" + messageIdA)
                        .header("Authorization", userCToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should reject unauthorized User C deleting User A's message with 403")
    void testIdorDeleteMessage() throws Exception {
        mockMvc.perform(delete("/api/v1/chats/" + convIdAB + "/messages/" + messageIdA)
                        .header("Authorization", userCToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should reject unauthorized User C sending read receipt for Conversation AB with 403")
    void testIdorReadReceipt() throws Exception {
        ReadReceiptRequest req = new ReadReceiptRequest(messageIdA);

        mockMvc.perform(post("/api/v1/chats/" + convIdAB + "/read")
                        .header("Authorization", userCToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CHAT_ACCESS_DENIED"));
    }
}
