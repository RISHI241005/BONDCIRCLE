package com.datingapp.chat.security;

import com.datingapp.chat.testutil.AbstractMySQLIntegrationTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class JwtAuthenticationSecurityTest extends AbstractMySQLIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    @DisplayName("Should reject request with 401 UNAUTHORIZED when Authorization header is missing")
    void testMissingJwtAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/chats")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Should reject request with 401 UNAUTHORIZED when token has expired")
    void testExpiredJwtAuthentication() throws Exception {
        SecretKey secretKey = Keys.hmacShaKeyFor(
                "test-only-secret-key-change-before-production-0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject("101")
                .issuer("dating-app-auth-service")
                .claim("roles", List.of("ROLE_USER"))
                .issuedAt(new Date(System.currentTimeMillis() - 10000))
                .expiration(new Date(System.currentTimeMillis() - 5000))
                .signWith(secretKey)
                .compact();

        mockMvc.perform(get("/api/v1/chats")
                        .header("Authorization", "Bearer " + expiredToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Should reject request with 401 UNAUTHORIZED when token has invalid signature")
    void testInvalidSignatureJwt() throws Exception {
        SecretKey rogueKey = Keys.hmacShaKeyFor(
                "DifferentSecretKeyForSigningTamperedJwtTokenPayload123456".getBytes(StandardCharsets.UTF_8));
        String tamperedToken = Jwts.builder()
                .subject("101")
                .issuer("dating-app-auth-service")
                .claim("roles", List.of("ROLE_USER"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(rogueKey)
                .compact();

        mockMvc.perform(get("/api/v1/chats")
                        .header("Authorization", "Bearer " + tamperedToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Should reject request with 401 UNAUTHORIZED when issuer is untrusted")
    void testWrongIssuerJwt() throws Exception {
        SecretKey secretKey = Keys.hmacShaKeyFor(
                "test-only-secret-key-change-before-production-0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        String wrongIssuerToken = Jwts.builder()
                .subject("101")
                .issuer("untrusted-fake-auth-service")
                .claim("roles", List.of("ROLE_USER"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(secretKey)
                .compact();

        mockMvc.perform(get("/api/v1/chats")
                        .header("Authorization", "Bearer " + wrongIssuerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }
}
