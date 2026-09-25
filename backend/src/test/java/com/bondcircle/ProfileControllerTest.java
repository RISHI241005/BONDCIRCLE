package com.bondcircle;

import com.bondcircle.dto.ProfileRequest;
import com.bondcircle.entity.User;
import com.bondcircle.entity.UserProfile;
import com.bondcircle.repository.UserProfileRepository;
import com.bondcircle.repository.UserRepository;
import com.bondcircle.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User testUser;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        userProfileRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User("Sagar Kewat", "sagar@example.com", passwordEncoder.encode("Password123!"));
        testUser.setEmailVerified(true);
        testUser = userRepository.save(testUser);

        jwtToken = jwtService.generateToken(testUser.getEmail(), testUser.getId());
    }

    @Test
    void testGetProfileUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/profile/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetProfileNotFoundInitially() throws Exception {
        mockMvc.perform(get("/api/profile/me")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void testSaveProfileSuccess() throws Exception {
        ProfileRequest request = new ProfileRequest(
                "Woman",
                "Bisexual",
                "Long-term relationship",
                "Monogamy",
                List.of("Coffee", "Books", "Travel")
        );

        mockMvc.perform(put("/api/profile/me")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gender", is("Woman")))
                .andExpect(jsonPath("$.orientation", is("Bisexual")))
                .andExpect(jsonPath("$.connectionIntention", is("Long-term relationship")))
                .andExpect(jsonPath("$.relationshipStyle", is("Monogamy")))
                .andExpect(jsonPath("$.interests", hasSize(3)))
                .andExpect(jsonPath("$.interests", containsInAnyOrder("Coffee", "Books", "Travel")));

        // Verify in PostgreSQL database
        UserProfile saved = userProfileRepository.findByUserId(testUser.getId()).orElse(null);
        assertNotNull(saved);
        assertEquals("Woman", saved.getGender());
        assertEquals("Bisexual", saved.getOrientation());
        assertEquals("Long-term relationship", saved.getConnectionIntention());
        assertEquals("Monogamy", saved.getRelationshipStyle());
        assertEquals(3, saved.getInterests().size());
        assertTrue(saved.getInterests().containsAll(List.of("Coffee", "Books", "Travel")));
    }

    @Test
    void testRetrieveSavedProfileAfterAppReload() throws Exception {
        // Step 1: Save profile
        ProfileRequest request = new ProfileRequest(
                "Man",
                "Straight",
                "Life partner",
                "Monogamy",
                List.of("Fitness", "Startups", "Food")
        );

        mockMvc.perform(put("/api/profile/me")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Step 2: Simulate restart / re-login with a newly generated token for the same user
        String newToken = jwtService.generateToken(testUser.getEmail(), testUser.getId());

        mockMvc.perform(get("/api/profile/me")
                        .header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gender", is("Man")))
                .andExpect(jsonPath("$.orientation", is("Straight")))
                .andExpect(jsonPath("$.connectionIntention", is("Life partner")))
                .andExpect(jsonPath("$.relationshipStyle", is("Monogamy")))
                .andExpect(jsonPath("$.interests", containsInAnyOrder("Fitness", "Startups", "Food")));
    }

    @Test
    void testUpdateExistingProfileDoesNotCreateDuplicate() throws Exception {
        // Save initial
        ProfileRequest initialRequest = new ProfileRequest(
                "Man",
                "Straight",
                "Short-term, open to long",
                "Still figuring it out",
                List.of("Movies", "Music")
        );
        mockMvc.perform(put("/api/profile/me")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialRequest)))
                .andExpect(status().isOk());

        assertEquals(1, userProfileRepository.count());

        // Update profile
        ProfileRequest updateRequest = new ProfileRequest(
                "Nonbinary",
                "Queer",
                "Long-term relationship",
                "Non-monogamy",
                List.of("Books", "Music", "Photography")
        );
        mockMvc.perform(put("/api/profile/me")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gender", is("Nonbinary")))
                .andExpect(jsonPath("$.orientation", is("Queer")))
                .andExpect(jsonPath("$.connectionIntention", is("Long-term relationship")))
                .andExpect(jsonPath("$.relationshipStyle", is("Non-monogamy")))
                .andExpect(jsonPath("$.interests", containsInAnyOrder("Books", "Music", "Photography")));

        assertEquals(1, userProfileRepository.count());
    }

    @Test
    void testSaveProfileValidationFailures() throws Exception {
        // Empty interests
        ProfileRequest emptyInterests = new ProfileRequest(
                "Man", "Straight", "Life partner", "Monogamy", List.of()
        );
        mockMvc.perform(put("/api/profile/me")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyInterests)))
                .andExpect(status().isBadRequest());

        // Blank gender
        ProfileRequest blankGender = new ProfileRequest(
                "", "Straight", "Life partner", "Monogamy", List.of("Books")
        );
        mockMvc.perform(put("/api/profile/me")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankGender)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testDataIsolationBetweenUsers() throws Exception {
        User otherUser = new User("Other User", "other@example.com", passwordEncoder.encode("Password123!"));
        otherUser.setEmailVerified(true);
        otherUser = userRepository.save(otherUser);
        String otherToken = jwtService.generateToken(otherUser.getEmail(), otherUser.getId());

        // User A saves profile
        ProfileRequest userARequest = new ProfileRequest(
                "Man", "Gay", "Life partner", "Monogamy", List.of("Fitness", "Coffee")
        );
        mockMvc.perform(put("/api/profile/me")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userARequest)))
                .andExpect(status().isOk());

        // User B saves profile
        ProfileRequest userBRequest = new ProfileRequest(
                "Woman", "Lesbian", "Figuring out my goals", "Non-monogamy", List.of("Books", "Movies")
        );
        mockMvc.perform(put("/api/profile/me")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userBRequest)))
                .andExpect(status().isOk());

        // User A profile check
        mockMvc.perform(get("/api/profile/me")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gender", is("Man")))
                .andExpect(jsonPath("$.orientation", is("Gay")));

        // User B profile check
        mockMvc.perform(get("/api/profile/me")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gender", is("Woman")))
                .andExpect(jsonPath("$.orientation", is("Lesbian")));
    }
}
