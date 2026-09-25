package com.bondcircle;

import com.bondcircle.dto.*;
import com.bondcircle.entity.EmailVerification;
import com.bondcircle.entity.PasswordReset;
import com.bondcircle.entity.User;
import com.bondcircle.repository.EmailVerificationRepository;
import com.bondcircle.repository.PasswordResetRepository;
import com.bondcircle.repository.UserRepository;
import com.bondcircle.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
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

    @Autowired
    private EmailVerificationRepository verificationRepository;

    @Autowired
    private PasswordResetRepository passwordResetRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        verificationRepository.deleteAll();
        passwordResetRepository.deleteAll();
        doNothing().when(emailService).sendVerificationCode(anyString(), anyString());
        doNothing().when(emailService).sendPasswordResetCode(anyString(), anyString());
    }

    private void createPreVerifiedEmail(String email) {
        String codeHash = passwordEncoder.encode("123456");
        EmailVerification verification = new EmailVerification(email.toLowerCase(), codeHash, LocalDateTime.now().plusMinutes(10));
        verification.setVerified(true);
        verification.setVerifiedAt(LocalDateTime.now());
        verificationRepository.save(verification);
    }

    @Test
    void testSendVerificationSuccess() throws Exception {
        SendVerificationRequest request = new SendVerificationRequest("test@example.com");

        mockMvc.perform(post("/api/auth/send-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Verification code sent.")));

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationCode(eq("test@example.com"), codeCaptor.capture());

        String capturedCode = codeCaptor.getValue();
        assertEquals(6, capturedCode.length());
        assertTrue(capturedCode.matches("^[0-9]{6}$"));
    }

    @Test
    void testSendVerificationDuplicateEmail() throws Exception {
        User existingUser = new User("Existing User", "existing@example.com", passwordEncoder.encode("Password123"));
        userRepository.save(existingUser);

        SendVerificationRequest request = new SendVerificationRequest("existing@example.com");

        mockMvc.perform(post("/api/auth/send-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("An account with this email already exists.")));
    }

    @Test
    void testSendVerificationCooldown() throws Exception {
        SendVerificationRequest request = new SendVerificationRequest("cooldown@example.com");

        // First send succeeds
        mockMvc.perform(post("/api/auth/send-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Second send immediately should hit cooldown
        mockMvc.perform(post("/api/auth/send-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", containsString("Please wait")));
    }

    @Test
    void testVerifyCodeWrongCode() throws Exception {
        // First request verification code
        mockMvc.perform(post("/api/auth/send-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendVerificationRequest("verifytest@example.com"))))
                .andExpect(status().isOk());

        VerifyCodeRequest wrongCode = new VerifyCodeRequest("verifytest@example.com", "000000");

        mockMvc.perform(post("/api/auth/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid verification code")));
    }

    @Test
    void testVerifyCodeSuccess() throws Exception {
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);

        mockMvc.perform(post("/api/auth/send-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendVerificationRequest("verifytest@example.com"))))
                .andExpect(status().isOk());

        verify(emailService).sendVerificationCode(eq("verifytest@example.com"), codeCaptor.capture());
        String correctCode = codeCaptor.getValue();

        VerifyCodeRequest validCode = new VerifyCodeRequest("verifytest@example.com", correctCode);

        mockMvc.perform(post("/api/auth/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Email verified successfully")));
    }

    @Test
    void testSignupWithoutVerificationFails() throws Exception {
        SignupRequest request = new SignupRequest("Unverified User", "unverified@example.com", "SecurePass123");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Email address has not been verified. Please verify your email first.")));
    }

    @Test
    void testSignupSuccessAfterVerification() throws Exception {
        createPreVerifiedEmail("test@example.com");

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
        assertTrue(savedUser.isEmailVerified());

        // Verification record should be consumed
        assertTrue(verificationRepository.findByEmail("test@example.com").isEmpty());
    }

    @Test
    void testSignupDuplicateEmail() throws Exception {
        createPreVerifiedEmail("duplicate@example.com");

        SignupRequest first = new SignupRequest("User One", "duplicate@example.com", "Password123");
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        createPreVerifiedEmail("duplicate@example.com");
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
    void testNormalLoginSuccessWithoutOtp() throws Exception {
        createPreVerifiedEmail("login@example.com");

        SignupRequest signup = new SignupRequest("Login User", "login@example.com", "CorrectPassword123");
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());

        // Normal Login: NO OTP, NO Verification step required
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

        // Protected endpoint works with JWT
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email", is("login@example.com")));
    }

    @Test
    void testLoginWrongPassword() throws Exception {
        createPreVerifiedEmail("wrongpass@example.com");

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

    @Test
    void testForgotPasswordSendCodeSuccessForExistingUser() throws Exception {
        User user = new User("Reset User", "user@example.com", passwordEncoder.encode("OldPassword123"));
        userRepository.save(user);

        ForgotPasswordSendCodeRequest request = new ForgotPasswordSendCodeRequest("user@example.com");

        mockMvc.perform(post("/api/auth/forgot-password/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("If an account exists")));

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetCode(eq("user@example.com"), codeCaptor.capture());

        String code = codeCaptor.getValue();
        assertEquals(6, code.length());
        assertTrue(code.matches("^[0-9]{6}$"));
    }

    @Test
    void testForgotPasswordSendCodeNonExistentAccountAntiEnumeration() throws Exception {
        ForgotPasswordSendCodeRequest request = new ForgotPasswordSendCodeRequest("nonexistent@example.com");

        mockMvc.perform(post("/api/auth/forgot-password/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("If an account exists")));

        verify(emailService, never()).sendPasswordResetCode(eq("nonexistent@example.com"), anyString());
    }

    @Test
    void testForgotPasswordSendCodeCooldown() throws Exception {
        User user = new User("Cooldown User", "cooldown@example.com", passwordEncoder.encode("OldPassword123"));
        userRepository.save(user);

        ForgotPasswordSendCodeRequest request = new ForgotPasswordSendCodeRequest("cooldown@example.com");

        mockMvc.perform(post("/api/auth/forgot-password/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Immediate retry should trigger 429
        mockMvc.perform(post("/api/auth/forgot-password/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", containsString("Please wait")));
    }

    @Test
    void testForgotPasswordVerifyWrongCode() throws Exception {
        User user = new User("Verify User", "verify@example.com", passwordEncoder.encode("OldPassword123"));
        userRepository.save(user);

        mockMvc.perform(post("/api/auth/forgot-password/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordSendCodeRequest("verify@example.com"))))
                .andExpect(status().isOk());

        ForgotPasswordVerifyCodeRequest wrongCode = new ForgotPasswordVerifyCodeRequest("verify@example.com", "000000");

        mockMvc.perform(post("/api/auth/forgot-password/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid verification code. 4 attempts remaining.")));
    }

    @Test
    void testForgotPasswordVerifyExpiredCode() throws Exception {
        User user = new User("Expired User", "expired@example.com", passwordEncoder.encode("OldPassword123"));
        userRepository.save(user);

        PasswordReset expiredRecord = new PasswordReset("expired@example.com", passwordEncoder.encode("123456"), LocalDateTime.now().minusMinutes(1));
        passwordResetRepository.save(expiredRecord);

        ForgotPasswordVerifyCodeRequest request = new ForgotPasswordVerifyCodeRequest("expired@example.com", "123456");

        mockMvc.perform(post("/api/auth/forgot-password/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Verification code has expired")));
    }

    @Test
    void testForgotPasswordVerifyTooManyAttempts() throws Exception {
        User user = new User("Locked User", "locked@example.com", passwordEncoder.encode("OldPassword123"));
        userRepository.save(user);

        PasswordReset lockedRecord = new PasswordReset("locked@example.com", passwordEncoder.encode("123456"), LocalDateTime.now().plusMinutes(10));
        lockedRecord.setAttempts(5);
        passwordResetRepository.save(lockedRecord);

        ForgotPasswordVerifyCodeRequest request = new ForgotPasswordVerifyCodeRequest("locked@example.com", "123456");

        mockMvc.perform(post("/api/auth/forgot-password/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Too many incorrect attempts")));
    }

    @Test
    void testForgotPasswordCompleteFlowSuccess() throws Exception {
        User user = new User("Flow User", "flow@example.com", passwordEncoder.encode("OldPassword123"));
        userRepository.save(user);

        // 1. Send Code
        mockMvc.perform(post("/api/auth/forgot-password/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordSendCodeRequest("flow@example.com"))))
                .andExpect(status().isOk());

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetCode(eq("flow@example.com"), codeCaptor.capture());
        String code = codeCaptor.getValue();

        // 2. Verify Code -> Returns resetToken
        MvcResult verifyResult = mockMvc.perform(post("/api/auth/forgot-password/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordVerifyCodeRequest("flow@example.com", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetToken", notNullValue()))
                .andExpect(jsonPath("$.message", containsString("Code verified successfully")))
                .andReturn();

        String resetToken = objectMapper.readTree(verifyResult.getResponse().getContentAsString()).get("resetToken").asText();

        // 3. Reset Password - mismatch check
        ResetPasswordRequest mismatchReq = new ResetPasswordRequest(resetToken, "NewPassword123!", "DifferentPassword123!");
        mockMvc.perform(post("/api/auth/forgot-password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mismatchReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Passwords do not match.")));

        // 4. Reset Password - success
        ResetPasswordRequest resetReq = new ResetPasswordRequest(resetToken, "NewPassword123!", "NewPassword123!");
        mockMvc.perform(post("/api/auth/forgot-password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Password reset successfully.")));

        // 5. Old password no longer works
        LoginRequest oldLogin = new LoginRequest("flow@example.com", "OldPassword123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oldLogin)))
                .andExpect(status().isUnauthorized());

        // 6. New password works and logs in successfully
        LoginRequest newLogin = new LoginRequest("flow@example.com", "NewPassword123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.user.email", is("flow@example.com")));

        // 7. Reset token cannot be reused
        mockMvc.perform(post("/api/auth/forgot-password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid or expired password reset token")));
    }

    @Test
    void testResetPasswordWithoutVerificationFails() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("fake-token-12345", "NewPassword123!", "NewPassword123!");
        mockMvc.perform(post("/api/auth/forgot-password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid or expired password reset token")));
    }

    @Test
    void testResetPasswordComplexityViolationsRejected() throws Exception {
        User user = new User("Complexity User", "complex@example.com", passwordEncoder.encode("OldPassword123"));
        userRepository.save(user);

        PasswordReset record = new PasswordReset("complex@example.com", passwordEncoder.encode("123456"), LocalDateTime.now().plusMinutes(10));
        record.setVerified(true);
        record.setResetToken("valid-token-complex");
        record.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(15));
        passwordResetRepository.save(record);

        // Missing special character
        mockMvc.perform(post("/api/auth/forgot-password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest("valid-token-complex", "NewPassword123", "NewPassword123"))))
                .andExpect(status().isBadRequest());

        // Missing uppercase
        mockMvc.perform(post("/api/auth/forgot-password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest("valid-token-complex", "newpassword123!", "newpassword123!"))))
                .andExpect(status().isBadRequest());

        // Missing lowercase
        mockMvc.perform(post("/api/auth/forgot-password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest("valid-token-complex", "NEWPASSWORD123!", "NEWPASSWORD123!"))))
                .andExpect(status().isBadRequest());

        // Missing number
        mockMvc.perform(post("/api/auth/forgot-password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest("valid-token-complex", "NewPassword!!!!", "NewPassword!!!!"))))
                .andExpect(status().isBadRequest());

        // Too short (< 8 chars)
        mockMvc.perform(post("/api/auth/forgot-password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest("valid-token-complex", "Aa1!", "Aa1!"))))
                .andExpect(status().isBadRequest());
    }
}
