package com.bondcircle;

import com.bondcircle.dto.LoginRequest;
import com.bondcircle.dto.SignupRequest;
import com.bondcircle.entity.User;
import com.bondcircle.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void testSignupSuccess() throws Exception {
        SignupRequest request = new SignupRequest("Test User", "test@example.com", "SecurePass123");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message", is("Account created successfully")))
                .andExpect(jsonPath("$.user.name", is("Test User")))
                .andExpect(jsonPath("$.user.email", is("test@example.com")))
                .andExpect(jsonPath("$.user.id", notNullValue()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User savedUser = userRepository.findByEmail("test@example.com").orElseThrow();
        assertTrue(savedUser.getPasswordHash().startsWith("$2a$") || savedUser.getPasswordHash().startsWith("$2b$"));
    }

    @Test
    void testSignupDuplicateEmail() throws Exception {
        SignupRequest first = new SignupRequest("User One", "duplicate@example.com", "Password123");
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        SignupRequest second = new SignupRequest("User Two", "duplicate@example.com", "AnotherPassword123");
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("An account with this email already exists.")));
    }

    @Test
    void testSignupInvalidEmail() throws Exception {
        SignupRequest request = new SignupRequest("Test User", "invalid-email", "Password123");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void testSignupShortPassword() throws Exception {
        SignupRequest request = new SignupRequest("Test User", "valid@example.com", "short");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Password must have at least 8 characters")));
    }

    @Test
    void testLoginSuccess() throws Exception {
        SignupRequest signup = new SignupRequest("Login User", "login@example.com", "CorrectPassword123");
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest("login@example.com", "CorrectPassword123");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.user.email", is("login@example.com")))
                .andExpect(jsonPath("$.user.name", is("Login User")))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();

        // Test protected /api/auth/me with JWT
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email", is("login@example.com")));
    }

    @Test
    void testLoginWrongPassword() throws Exception {
        SignupRequest signup = new SignupRequest("Login User", "wrongpass@example.com", "CorrectPassword123");
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest("wrongpass@example.com", "IncorrectPassword123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid email or password.")));
    }

    @Test
    void testLoginNonExistentEmail() throws Exception {
        LoginRequest login = new LoginRequest("nonexistent@example.com", "Password123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid email or password.")));
    }

    @Test
    void testProtectedMeEndpointWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isForbidden());
    }
}
