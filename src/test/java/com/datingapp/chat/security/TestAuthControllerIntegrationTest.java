package com.datingapp.chat.security;

import com.datingapp.chat.testutil.AbstractMySQLIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TestAuthControllerIntegrationTest extends AbstractMySQLIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should generate valid JWT token via public /test/auth/token endpoint")
    void testGenerateTokenEndpoint() throws Exception {
        TestAuthController.TokenRequest request = new TestAuthController.TokenRequest(101L, List.of("ROLE_USER"));

        mockMvc.perform(post("/test/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("Should retrieve current user profile via GET /api/v1/users/me with valid token")
    void testGetMyProfileEndpoint() throws Exception {
        TestAuthController.TokenRequest request = new TestAuthController.TokenRequest(101L, List.of("ROLE_USER"));
        String tokenJson = mockMvc.perform(post("/test/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(tokenJson).get("token").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(101))
                .andExpect(jsonPath("$.data.fullName").value("Alex Rivera"));
    }
}
