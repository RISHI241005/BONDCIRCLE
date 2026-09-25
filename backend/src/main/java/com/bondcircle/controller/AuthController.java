package com.bondcircle.controller;

import com.bondcircle.dto.*;
import com.bondcircle.entity.User;
import com.bondcircle.service.AuthService;
import com.bondcircle.service.PasswordResetService;
import com.bondcircle.service.VerificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final VerificationService verificationService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService,
                          VerificationService verificationService,
                          PasswordResetService passwordResetService) {
        this.authService = authService;
        this.verificationService = verificationService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/forgot-password/send-code")
    public ResponseEntity<Map<String, String>> forgotPasswordSendCode(@Valid @RequestBody ForgotPasswordSendCodeRequest request) {
        passwordResetService.sendResetCode(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "If an account exists with this email, a verification code has been sent."));
    }

    @PostMapping("/forgot-password/verify-code")
    public ResponseEntity<ForgotPasswordVerifyCodeResponse> forgotPasswordVerifyCode(@Valid @RequestBody ForgotPasswordVerifyCodeRequest request) {
        String resetToken = passwordResetService.verifyResetCode(request.getEmail(), request.getCode());
        return ResponseEntity.ok(new ForgotPasswordVerifyCodeResponse(resetToken, "Code verified successfully."));
    }

    @PostMapping("/forgot-password/reset")
    public ResponseEntity<Map<String, String>> forgotPasswordReset(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully."));
    }

    @PostMapping("/send-verification")
    public ResponseEntity<Map<String, String>> sendVerification(@Valid @RequestBody SendVerificationRequest request) {
        verificationService.sendVerificationCode(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "Verification code sent."));
    }

    @PostMapping("/verify-code")
    public ResponseEntity<Map<String, String>> verifyCode(@Valid @RequestBody VerifyCodeRequest request) {
        verificationService.verifyCode(request.getEmail(), request.getCode());
        return ResponseEntity.ok(Map.of("message", "Email verified successfully"));
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        AuthResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, UserDto>> getCurrentUser(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserDto userDto = authService.getCurrentUser(user.getEmail());
        return ResponseEntity.ok(Map.of("user", userDto));
    }
}
