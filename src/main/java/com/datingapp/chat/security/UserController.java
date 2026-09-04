package com.datingapp.chat.security;

import com.datingapp.chat.common.exception.BadRequestException;
import com.datingapp.chat.common.response.ApiResponse;
import com.datingapp.chat.common.response.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = userService.register(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Registration successful. You can sign in now."));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request.getPhone(), request.getPassword());
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful. Welcome back."));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<FindUserResponse>> searchByPhone(@RequestParam("phone") String phone, HttpServletRequest request) {
        Long currentUserId = getCurrentUserId(request);
        FindUserResponse response = userService.findByPhone(phone, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response, "User found"));
    }

    private Long getCurrentUserId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return ((UserPrincipal) authentication.getPrincipal()).getUserId();
        }
        return null;
    }
}
