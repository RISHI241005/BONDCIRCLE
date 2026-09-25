package com.bondcircle.service;

import com.bondcircle.entity.EmailVerification;
import com.bondcircle.exception.InvalidVerificationCodeException;
import com.bondcircle.exception.UserAlreadyExistsException;
import com.bondcircle.exception.VerificationCooldownException;
import com.bondcircle.repository.EmailVerificationRepository;
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

@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

    private final EmailVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    private final int expirationMinutes;
    private final int cooldownSeconds;
    private final int maxAttempts;

    public VerificationService(
            EmailVerificationRepository verificationRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            @Value("${verification.expiration-minutes:10}") int expirationMinutes,
            @Value("${verification.cooldown-seconds:60}") int cooldownSeconds,
            @Value("${verification.max-attempts:5}") int maxAttempts) {
        this.verificationRepository = verificationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.expirationMinutes = expirationMinutes;
        this.cooldownSeconds = cooldownSeconds;
        this.maxAttempts = maxAttempts;
    }

    @Transactional
    public void sendVerificationCode(String rawEmail) {
        String email = rawEmail.trim().toLowerCase();

        // 1. Check if user already exists
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("An account with this email already exists.");
        }

        LocalDateTime now = LocalDateTime.now();

        // 2. Resend cooldown protection
        EmailVerification existing = verificationRepository.findByEmail(email).orElse(null);
        if (existing != null && existing.getLastSentAt() != null) {
            LocalDateTime cooldownEnd = existing.getLastSentAt().plusSeconds(cooldownSeconds);
            if (cooldownEnd.isAfter(now)) {
                long remainingSeconds = Duration.between(now, cooldownEnd).getSeconds();
                throw new VerificationCooldownException(
                        "Please wait " + Math.max(1, remainingSeconds) + " seconds before requesting another code."
                );
            }
        }

        // 3. Generate secure random 6-digit verification code
        int randomCode = 100000 + secureRandom.nextInt(900000);
        String code = String.valueOf(randomCode);

        // 4. Hash verification code before storing
        String codeHash = passwordEncoder.encode(code);

        // 5. Store / update verification record
        EmailVerification verification;
        if (existing == null) {
            verification = new EmailVerification(email, codeHash, now.plusMinutes(expirationMinutes));
        } else {
            verification = existing;
            verification.setCodeHash(codeHash);
            verification.setExpiresAt(now.plusMinutes(expirationMinutes));
            verification.setAttempts(0);
            verification.setVerified(false);
            verification.setVerifiedAt(null);
            verification.setLastSentAt(now);
        }
        verificationRepository.save(verification);

        // 6. Send code via Brevo transactional email
        emailService.sendVerificationCode(email, code);
        log.info("Verification process initiated for email: {}", email);
    }

    @Transactional
    public void verifyCode(String rawEmail, String code) {
        String email = rawEmail.trim().toLowerCase();

        EmailVerification verification = verificationRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidVerificationCodeException("No verification request found for this email. Please request a new code."));

        LocalDateTime now = LocalDateTime.now();

        // Check expiration
        if (verification.getExpiresAt().isBefore(now)) {
            throw new InvalidVerificationCodeException("Verification code has expired. Please request a new code.");
        }

        // Check attempt limit
        if (verification.getAttempts() >= maxAttempts) {
            throw new InvalidVerificationCodeException("Too many incorrect attempts. Please request a new code.");
        }

        // Validate code
        if (!passwordEncoder.matches(code.trim(), verification.getCodeHash())) {
            int newAttempts = verification.getAttempts() + 1;
            verification.setAttempts(newAttempts);
            verificationRepository.save(verification);

            int remaining = maxAttempts - newAttempts;
            if (remaining > 0) {
                throw new InvalidVerificationCodeException("Invalid verification code. " + remaining + " attempts remaining.");
            } else {
                throw new InvalidVerificationCodeException("Too many incorrect attempts. Please request a new code.");
            }
        }

        // Successful verification
        verification.setVerified(true);
        verification.setVerifiedAt(now);
        verificationRepository.save(verification);
        log.info("Email verified successfully for: {}", email);
    }

    @Transactional(readOnly = true)
    public boolean isEmailVerified(String rawEmail) {
        String email = rawEmail.trim().toLowerCase();
        return verificationRepository.findByEmail(email)
                .map(v -> v.isVerified() && v.getVerifiedAt() != null &&
                        v.getVerifiedAt().plusMinutes(30).isAfter(LocalDateTime.now()))
                .orElse(false);
    }

    @Transactional
    public void consumeVerification(String rawEmail) {
        String email = rawEmail.trim().toLowerCase();
        verificationRepository.deleteByEmail(email);
    }
}
