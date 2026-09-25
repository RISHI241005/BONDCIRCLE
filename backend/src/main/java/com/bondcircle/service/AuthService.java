package com.bondcircle.service;

import com.bondcircle.dto.*;
import com.bondcircle.entity.User;
import com.bondcircle.exception.EmailNotVerifiedException;
import com.bondcircle.exception.InvalidCredentialsException;
import com.bondcircle.exception.UserAlreadyExistsException;
import com.bondcircle.repository.UserRepository;
import com.bondcircle.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final VerificationService verificationService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       VerificationService verificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.verificationService = verificationService;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new UserAlreadyExistsException("An account with this email already exists.");
        }

        if (!verificationService.isEmailVerified(normalizedEmail)) {
            throw new EmailNotVerifiedException("Email address has not been verified. Please verify your email first.");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());
        User user = new User(request.getName().trim(), normalizedEmail, passwordHash);
        user.setEmailVerified(true);
        User savedUser = userRepository.save(user);

        verificationService.consumeVerification(normalizedEmail);

        UserDto userDto = new UserDto(savedUser.getId(), savedUser.getName(), savedUser.getEmail());
        return AuthResponse.signupSuccess(userDto);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password.");
        }

        String token = jwtService.generateToken(user.getEmail(), user.getId());
        UserDto userDto = new UserDto(user.getId(), user.getName(), user.getEmail());
        return AuthResponse.loginSuccess(token, userDto);
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));
        return new UserDto(user.getId(), user.getName(), user.getEmail());
    }
}
