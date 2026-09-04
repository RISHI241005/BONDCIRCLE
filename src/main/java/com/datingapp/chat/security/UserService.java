package com.datingapp.chat.security;

import com.datingapp.chat.common.exception.BadRequestException;
import com.datingapp.chat.common.exception.ConflictException;
import com.datingapp.chat.common.exception.UnauthorizedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // Validate password confirmation
        if (request.getPassword() == null || !request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Password and confirmation do not match");
        }

        // Validate email is not duplicate
        String email = request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        String phone = normalizePhone(request.getPhone());

        if (userRepository.findByEmailIgnoreCase(email) != null) {
            throw new ConflictException("Email is already registered");
        }

        // Validate phone is not duplicate
        if (userRepository.findByPhoneIgnoreCase(phone) != null) {
            throw new ConflictException("Phone number is already registered");
        }

        // Encode password
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // Create and save user
        User user = new User(
                request.getFullName().trim(),
                phone,
                email,
                encodedPassword
        );

        userRepository.save(user);

        // Build response (excluding password hash)
        return new RegisterResponse(
                user.getId(),
                String.valueOf(user.getId()),
                user.getEmail(),
                user.getFullName(),
                user.getPhone()
        );
    }

    @Transactional
    public LoginResponse login(String phone, String password) {
        // Look up user by phone
        User user = userRepository.findByPhoneIgnoreCase(normalizePhone(phone));
        if (user == null) {
            throw new UnauthorizedException("Invalid phone or password", com.datingapp.chat.common.exception.ErrorCode.UNAUTHORIZED);
        }

        // Verify password
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid phone or password", com.datingapp.chat.common.exception.ErrorCode.UNAUTHORIZED);
        }

        // Generate JWT token
        String token = jwtService.generateToken(user.getId(), List.of("ROLE_USER"));

        return new LoginResponse(
                user.getId(),
                String.valueOf(user.getId()),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                token
        );
    }

    /**
     * Find a user by phone number for discovery.
     * currentUserId is the ID of the authenticated user making the search.
     * Returns safe user info without exposing sensitive data.
     * Throws BadRequestException if user not found or phone number is invalid.
     * Self-search protection: if currentUserId is provided and matches, throws exception.
     */
    public FindUserResponse findByPhone(String phone, Long currentUserId) {
        // Validate phone number format on backend
        if (phone == null || phone.trim().isEmpty()) {
            throw new BadRequestException("Phone number is required");
        }
        String trimmedPhone = phone.trim();
        if (!trimmedPhone.matches("^[+]?[0-9\\s-]{10,20}$")) {
            throw new BadRequestException("Invalid phone number format");
        }

        // Look up user by phone
        User user = userRepository.findByPhoneIgnoreCase(normalizePhone(trimmedPhone));

        if (user == null) {
            throw new BadRequestException("User not found");
        }

        // Do not allow user to search their own phone number
        if (currentUserId != null && user.getId().equals(currentUserId)) {
            throw new BadRequestException("Cannot search for your own account");
        }

        // Return safe user info (excluding password hash)
        return new FindUserResponse(
                user.getId(),
                String.valueOf(user.getId()),
                user.getFullName(),
                user.getPhone(),
                "OFFLINE", // Default status; WebSocket presence would update this
                ""          // Reserved for a future profile image URL
        );
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            throw new BadRequestException("Phone number is required");
        }

        String trimmed = phone.trim();
        if (!trimmed.matches("^[+]?[0-9\\s-]{10,20}$")) {
            throw new BadRequestException("Invalid phone number format");
        }

        boolean international = trimmed.startsWith("+");
        String digits = trimmed.replaceAll("\\D", "");
        if (digits.length() < 10 || digits.length() > 15) {
            throw new BadRequestException("Phone number must contain 10 to 15 digits");
        }
        return international ? "+" + digits : digits;
    }
}
