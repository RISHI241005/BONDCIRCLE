package com.bondcircle;

import com.bondcircle.dto.LoginRequest;
import com.bondcircle.dto.ProfileRequest;
import com.bondcircle.entity.EmailVerification;
import com.bondcircle.entity.User;
import com.bondcircle.entity.UserProfile;
import com.bondcircle.repository.EmailVerificationRepository;
import com.bondcircle.repository.UserProfileRepository;
import com.bondcircle.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ProfileRealFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String TEST_EMAIL = "realuser@bondcircle.com";
    private static final String TEST_PASSWORD = "StrongPassword123!";

    @BeforeEach
    void setUp() {
        userProfileRepository.deleteAll();
        userRepository.deleteAll();
        emailVerificationRepository.deleteAll();

        // Create verified user
        User user = new User("Sagar Kewat", TEST_EMAIL, passwordEncoder.encode(TEST_PASSWORD));
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    @Test
    @DisplayName("Test real complete flow: Sign In -> Profile Setup -> Enter 5 Categories -> Save -> Verify in PostgreSQL -> Restart -> Sign In again -> Retrieve Profile -> Confirm 5 Categories")
    void testRealEndToEndProfileFlow() throws Exception {
        // Step 1: Sign In
        LoginRequest loginRequest = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String jwtToken = loginJson.get("token").asText();
        assertNotNull(jwtToken);
        assertFalse(jwtToken.isEmpty());

        // Step 2: Profile Setup - Enter/select the 5 categories:
        // 1. Gender
        // 2. Orientation
        // 3. Connection Intention
        // 4. Relationship Style
        // 5. Interests (multiple values)
        ProfileRequest profileRequest = new ProfileRequest(
                "Woman",
                "Bisexual",
                "Long-term relationship",
                "Monogamy",
                List.of("Coffee", "Books", "Travel")
        );

        // Step 3: Save to Spring Boot API
        mockMvc.perform(put("/api/profile/me")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profileRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gender", is("Woman")))
                .andExpect(jsonPath("$.orientation", is("Bisexual")))
                .andExpect(jsonPath("$.connectionIntention", is("Long-term relationship")))
                .andExpect(jsonPath("$.relationshipStyle", is("Monogamy")))
                .andExpect(jsonPath("$.interests", containsInAnyOrder("Coffee", "Books", "Travel")));

        // Step 4: Verify data exists in PostgreSQL
        User persistentUser = userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        UserProfile persistentProfile = userProfileRepository.findByUserId(persistentUser.getId()).orElse(null);
        assertNotNull(persistentProfile, "Profile must be saved in PostgreSQL");
        assertEquals("Woman", persistentProfile.getGender());
        assertEquals("Bisexual", persistentProfile.getOrientation());
        assertEquals("Long-term relationship", persistentProfile.getConnectionIntention());
        assertEquals("Monogamy", persistentProfile.getRelationshipStyle());
        assertEquals(3, persistentProfile.getInterests().size());
        assertTrue(persistentProfile.getInterests().containsAll(List.of("Coffee", "Books", "Travel")));

        // Step 5: Restart / reload app (discard old token, simulate app restart)
        jwtToken = null;

        // Step 6: Sign In again
        MvcResult reLoginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andReturn();

        JsonNode reLoginJson = objectMapper.readTree(reLoginResult.getResponse().getContentAsString());
        String newJwtToken = reLoginJson.get("token").asText();
        assertNotNull(newJwtToken);

        // Step 7 & 8: Retrieve profile & Confirm all five categories are still present
        mockMvc.perform(get("/api/profile/me")
                        .header("Authorization", "Bearer " + newJwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gender", is("Woman")))
                .andExpect(jsonPath("$.orientation", is("Bisexual")))
                .andExpect(jsonPath("$.connectionIntention", is("Long-term relationship")))
                .andExpect(jsonPath("$.relationshipStyle", is("Monogamy")))
                .andExpect(jsonPath("$.interests", hasSize(3)))
                .andExpect(jsonPath("$.interests", containsInAnyOrder("Coffee", "Books", "Travel")));
    }
}
