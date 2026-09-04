package com.datingapp.chat.security;

import com.datingapp.chat.conversation.dto.ConversationDetailResponse;
import com.datingapp.chat.conversation.service.ConversationService;
import com.datingapp.chat.message.dto.MessageResponse;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ClientManipulationAndInjectionSecurityTest extends AbstractMySQLIntegrationTest {

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
    private String convIdAB;

    @BeforeEach
    void setUpData() {
        userAToken = UserTestFactory.createBearerToken(jwtService, UserTestFactory.USER_A);
        ConversationDetailResponse conv = conversationService.createOrGetConversation(
                UserTestFactory.USER_A, UserTestFactory.USER_B);
        convIdAB = conv.getConversationId();
    }

    @Test
    @DisplayName("Should ignore client-injected senderId and strictly enforce JWT authenticated sender identity")
    void testSenderIdSpoofingPrevented() throws Exception {
        String jsonPayloadWithSpoofedSender = """
                {
                    "senderId": 9999,
                    "content": "Trying to spoof sender as 9999",
                    "clientMessageId": "spoof-test-1",
                    "type": "TEXT"
                }
                """;

        mockMvc.perform(post("/api/v1/chats/" + convIdAB + "/messages")
                        .header("Authorization", userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayloadWithSpoofedSender))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.senderId").value(UserTestFactory.USER_A));

        // Verify MySQL database record has sender_id = 101, NOT 9999
        Long actualSenderIdInDb = jdbcTemplate.queryForObject(
                "SELECT sender_id FROM messages WHERE client_message_id = 'spoof-test-1'", Long.class);
        assertEquals(UserTestFactory.USER_A, actualSenderIdInDb);
    }

    @Test
    @DisplayName("Should safely persist and retrieve SQL injection strings as literal text in MySQL")
    void testSqlInjectionSafety() throws Exception {
        String sqliContent = "'; DROP TABLE messages; SELECT * FROM users WHERE '1'='1";
        SendMessageRequest req = new SendMessageRequest(sqliContent, "sqli-test-1", null);

        mockMvc.perform(post("/api/v1/chats/" + convIdAB + "/messages")
                        .header("Authorization", userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value(sqliContent));

        // Confirm messages table still exists and row is stored verbatim
        String contentInDb = jdbcTemplate.queryForObject(
                "SELECT content FROM messages WHERE client_message_id = 'sqli-test-1'", String.class);
        assertEquals(sqliContent, contentInDb);
    }

    @Test
    @DisplayName("Should safely handle 4-byte UTF-8 emojis and Unicode in MySQL utf8mb4")
    void testUtf8mb4EmojiSupport() throws Exception {
        String emojiContent = "Hello from Flutter! 💖 🔥 🚀 🎉 💯 🥳";
        SendMessageRequest req = new SendMessageRequest(emojiContent, "emoji-test-1", null);

        mockMvc.perform(post("/api/v1/chats/" + convIdAB + "/messages")
                        .header("Authorization", userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value(emojiContent));

        String contentInDb = jdbcTemplate.queryForObject(
                "SELECT content FROM messages WHERE client_message_id = 'emoji-test-1'", String.class);
        assertEquals(emojiContent, contentInDb);
    }

    @Test
    @DisplayName("Should reject blank message content with 400 Bad Request")
    void testBlankMessageValidation() throws Exception {
        SendMessageRequest req = new SendMessageRequest("    ", "blank-test-1", null);

        mockMvc.perform(post("/api/v1/chats/" + convIdAB + "/messages")
                        .header("Authorization", userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject oversized message exceeding 2000 characters with 400 Bad Request")
    void testOversizedMessageValidation() throws Exception {
        String oversizedContent = "A".repeat(2001);
        SendMessageRequest req = new SendMessageRequest(oversizedContent, "oversized-test-1", null);

        mockMvc.perform(post("/api/v1/chats/" + convIdAB + "/messages")
                        .header("Authorization", userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
