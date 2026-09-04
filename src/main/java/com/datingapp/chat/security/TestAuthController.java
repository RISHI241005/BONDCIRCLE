package com.datingapp.chat.security;

import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;

import java.util.List;

@RestController
@RequestMapping("/test/auth")
@Profile({"dev", "test"})
public class TestAuthController {

    private final JwtService jwtService;

    public TestAuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/token")
    public TokenResponse generateToken(@RequestBody TokenRequest request) {

        String token = jwtService.generateToken(
                request.userId(),
                request.roles()
        );

        return new TokenResponse(token, "Bearer");
    }

    public record TokenRequest(
            Long userId,
            List<String> roles
    ) {}

    public record TokenResponse(
            String token,
            String tokenType
    ) {}
}
