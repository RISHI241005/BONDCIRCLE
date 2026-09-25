package com.bondcircle.controller;

import com.bondcircle.dto.ProfileRequest;
import com.bondcircle.dto.ProfileResponse;
import com.bondcircle.entity.User;
import com.bondcircle.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> getProfile(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return profileService.getProfile(user)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @RequestMapping(value = "/me", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ProfileResponse> saveProfile(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ProfileRequest request
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ProfileResponse response = profileService.saveProfile(user, request);
        return ResponseEntity.ok(response);
    }
}
