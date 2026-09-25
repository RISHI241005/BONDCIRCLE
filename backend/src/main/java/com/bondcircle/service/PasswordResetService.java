package com.bondcircle.service;

import com.bondcircle.dto.ResetPasswordRequest;
import com.bondcircle.entity.PasswordReset;
import com.bondcircle.entity.User;
import com.bondcircle.exception.InvalidVerificationCodeException;
import com.bondcircle.exception.VerificationCooldownException;
import com.bondcircle.repository.PasswordResetRepository;
import com.bondcircle.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final PasswordResetRepository passwordResetRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    private final int expirationMinutes;
    private final int cooldownSeconds;
    private final int maxAttempts;

    public PasswordResetService(
            PasswordResetRepository passwordResetRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            @Value("${verification.expiration-minutes:10}") int expirationMinutes,
            @Value("${verification.cooldown-seconds:60}") int cooldownSeconds,
            @Value("${verification.max-attempts:5}") int maxAttempts) {
        this.passwordResetRepository = passwordResetRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.expirationMinutes = expirationMinutes;
        this.cooldownSeconds = cooldownSeconds;
        this.maxAttempts = maxAttempts;
    }

    @Transactional
    public void sendResetCode(String rawEmail) {
        String email = rawEmail.trim().toLowerCase();

        // 1. Check if user account exists
        if (!userRepository.existsByEmail(email)) {
            // Anti-enumeration protection: return silently without leaking user existence
            log.info("Password reset requested for non-existent email: {}", email);
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // 2. Resend cooldown protection
        PasswordReset existing = passwordResetRepository.findByEmail(email).orElse(null);
        if (existing != null && existing.getLastSentAt() != null) {
            LocalDateTime cooldownEnd = existing.getLastSentAt().plusSeconds(cooldownSeconds);
            if (cooldownEnd.isAfter(now)) {
                long remainingSeconds = Duration.between(now, cooldownEnd).getSeconds();
                throw new VerificationCooldownException(
                        "Please wait " + Math.max(1, remainingSeconds) + " seconds before requesting another code."
                );
            }
        }

        // 3. Generate secure random 6-digit reset code
        int randomCode = 100000 + secureRandom.nextInt(900000);
        String code = String.valueOf(randomCode);

        // 4. Hash reset code before storing
        String codeHash = passwordEncoder.encode(code);

        // 5. Store / update reset record
        PasswordReset resetRecord;
        if (existing == null) {
            resetRecord = new PasswordReset(email, codeHash, now.plusMinutes(expirationMinutes));
        } else {
            resetRecord = existing;
            resetRecord.setCodeHash(codeHash);
            resetRecord.setExpiresAt(now.plusMinutes(expirationMinutes));
            resetRecord.setAttempts(0);
            resetRecord.setVerified(false);
            resetRecord.setVerifiedAt(null);
            resetRecord.setResetToken(null);
            resetRecord.setResetTokenExpiresAt(null);
            resetRecord.setConsumed(false);
            resetRecord.setLastSentAt(now);
        }
        passwordResetRepository.save(resetRecord);

        // 6. Send code via Brevo transactional email
        emailService.sendPasswordResetCode(email, code);
        log.info("Password reset code sent successfully for email: {}", email);
    }

    @Transactional
    public String verifyResetCode(String rawEmail, String code) {
        String email = rawEmail.trim().toLowerCase();

        PasswordReset resetRecord = passwordResetRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidVerificationCodeException("No verification request found for this email. Please request a new code."));

        LocalDateTime now = LocalDateTime.now();

        // Check expiration
        if (resetRecord.getExpiresAt().isBefore(now)) {
            throw new InvalidVerificationCodeException("Verification code has expired. Please request a new code.");
        }

        // Check attempt limit
        if (resetRecord.getAttempts() >= maxAttempts) {
            throw new InvalidVerificationCodeException("Too many incorrect attempts. Please request a new code.");
        }

        // Validate code
        if (!passwordEncoder.matches(code.trim(), resetRecord.getCodeHash())) {
            int newAttempts = resetRecord.getAttempts() + 1;
            resetRecord.setAttempts(newAttempts);
            passwordResetRepository.save(resetRecord);

            int remaining = maxAttempts - newAttempts;
            if (remaining > 0) {
                throw new InvalidVerificationCodeException("Invalid verification code. " + remaining + " attempts remaining.");
            } else {
                throw new InvalidVerificationCodeException("Too many incorrect attempts. Please request a new code.");
            }
        }

        // Successful verification - generate secure random reset token
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String resetToken = HexFormat.of().formatHex(tokenBytes);

        resetRecord.setVerified(true);
        resetRecord.setVerifiedAt(now);
        resetRecord.setResetToken(resetToken);
        resetRecord.setResetTokenExpiresAt(now.plusMinutes(15));
        resetRecord.setConsumed(false);
        passwordResetRepository.save(resetRecord);

        log.info("Password reset code verified successfully for: {}", email);
        return resetToken;
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        validatePasswordStrength(request.getNewPassword());

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new InvalidVerificationCodeException("Passwords do not match.");
        }

        PasswordReset resetRecord = passwordResetRepository.findByResetToken(request.getResetToken().trim())
                .orElseThrow(() -> new InvalidVerificationCodeException("Invalid or expired password reset token. Please request a new code."));

        LocalDateTime now = LocalDateTime.now();

        if (!resetRecord.isVerified() || resetRecord.isConsumed() ||
                resetRecord.getResetTokenExpiresAt() == null ||
                resetRecord.getResetTokenExpiresAt().isBefore(now)) {
            throw new InvalidVerificationCodeException("Invalid or expired password reset token. Please request a new code.");
        }

        User user = userRepository.findByEmail(resetRecord.getEmail())
                .orElseThrow(() -> new InvalidVerificationCodeException("User account not found."));

        // Update password with BCrypt hash
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Invalidate reset token and mark consumed
        resetRecord.setConsumed(true);
        resetRecord.setResetToken(null);
        resetRecord.setResetTokenExpiresAt(null);
        passwordResetRepository.save(resetRecord);

        log.info("Password successfully reset for user: {}", user.getEmail());
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new InvalidVerificationCodeException("Password must have at least 8 characters.");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new InvalidVerificationCodeException("Password must contain at least 1 uppercase letter (A-Z).");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new InvalidVerificationCodeException("Password must contain at least 1 lowercase letter (a-z).");
        }
        if (!password.matches(".*[0-9].*")) {
            throw new InvalidVerificationCodeException("Password must contain at least 1 number (0-9).");
        }
        if (!password.matches(".*[!@#$%^&*].*")) {
            throw new InvalidVerificationCodeException("Password must contain at least 1 special character (!@#$%^&*).");
        }
    }
}
