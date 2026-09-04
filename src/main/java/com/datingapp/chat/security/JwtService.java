package com.datingapp.chat.security;

import com.datingapp.chat.common.exception.ErrorCode;
import com.datingapp.chat.common.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * Service responsible for validating incoming JWT tokens and extracting user claims.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;
    private final String issuer;
    private final long expirationMs;

    public JwtService(
            @Value("${chat.jwt.secret:test-only-secret-key-change-before-production-0123456789abcdef}") String secret,
            @Value("${chat.jwt.issuer:dating-app-auth-service}") String issuer,
            @Value("${chat.jwt.expiration-ms:86400000}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.expirationMs = expirationMs;
    }

    /**
     * Generates a signed JWT token (used for integration testing and verification).
     */
    public String generateToken(Long userId, List<String> roles) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("roles", roles != null ? roles : List.of("ROLE_USER"))
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates and extracts claims from a raw JWT string.
     */
    public Claims extractClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(this.issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            log.warn("JWT token has expired: {}", ex.getMessage());
            throw new UnauthorizedException("JWT token has expired", ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Invalid JWT token: {}", ex.getMessage());
            throw new UnauthorizedException("Invalid JWT token", ErrorCode.TOKEN_INVALID);
        }
    }

    /**
     * Extracts external User ID from token subject.
     */
    public Long extractUserId(String token) {
        Claims claims = extractClaims(token);
        try {
            return Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("Invalid user identifier in token subject", ErrorCode.TOKEN_INVALID);
        }
    }

    /**
     * Extracts roles from claims.
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Claims claims = extractClaims(token);
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof List<?>) {
            return (List<String>) rolesObj;
        }
        return List.of("ROLE_USER");
    }

    /**
     * Validates if token is valid without throwing exceptions.
     */
    public boolean validateToken(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
