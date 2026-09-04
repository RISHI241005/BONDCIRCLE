package com.datingapp.chat.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.datingapp.chat.testutil.AbstractMySQLIntegrationTest;
import com.datingapp.chat.common.exception.BadRequestException;
import com.datingapp.chat.common.response.ApiResponse;
import com.datingapp.chat.security.FindUserResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for User search by phone number API.
 * Uses the MySQL test database via AbstractMySQLIntegrationTest.
 */
@AutoConfigureMockMvc
class UserSearchByPhoneIntegrationTest extends AbstractMySQLIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Test
    @DisplayName("Should find user by phone number with valid authentication")
    void shouldFindUserByPhone() throws Exception {
        String phone = "+1234567890";
        String userToken = createToken(202L);

        mockMvc.perform(get("/api/v1/users/search?phone=" + phone)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(101L))
                .andExpect(jsonPath("$.data.fullName").isString())
                .andExpect(jsonPath("$.data.phone").value(phone))
                .andExpect(jsonPath("$.data.status").value("OFFLINE"))
                .andExpect(jsonPath("$.data.username").isString())
                .andExpect(jsonPath("$.data.profileImageUrl").isString());
    }

    @Test
    @DisplayName("Should return BadRequest when user not found")
    void shouldReturnBadRequestWhenUserNotFound() throws Exception {
        String phone = "+9999999999";
        String userToken = createToken(101L);

        mockMvc.perform(get("/api/v1/users/search?phone=" + phone)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("Should reject self-search by phone number")
    void shouldRejectSelfSearch() throws Exception {
        String phone = "+1234567890";
        String userToken = createToken(101L);

        mockMvc.perform(get("/api/v1/users/search?phone=" + phone)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Cannot search for your own account"));
    }

    @Test
    @DisplayName("Should return BadRequest when phone number is invalid format")
    void shouldReturnBadRequestWhenPhoneInvalidFormat() throws Exception {
        String phone = "invalid";
        String userToken = createToken(101L);

        mockMvc.perform(get("/api/v1/users/search?phone=" + phone)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("Should return BadRequest when phone number is empty")
    void shouldReturnBadRequestWhenPhoneEmpty() throws Exception {
        String phone = "";
        String userToken = createToken(101L);

        mockMvc.perform(get("/api/v1/users/search?phone=" + phone)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("Should reject unauthenticated access")
    void shouldRejectUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/v1/users/search?phone=+1234567890")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    private String createToken(Long userId) {
        return jwtService.generateToken(userId, List.of("ROLE_USER"));
    }
}
