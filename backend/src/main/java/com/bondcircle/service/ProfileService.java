package com.bondcircle.service;

import com.bondcircle.dto.ProfileRequest;
import com.bondcircle.dto.ProfileResponse;
import com.bondcircle.entity.User;
import com.bondcircle.entity.UserProfile;
import com.bondcircle.repository.UserProfileRepository;
import com.bondcircle.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProfileService {

    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;

    public ProfileService(UserProfileRepository userProfileRepository, UserRepository userRepository) {
        this.userProfileRepository = userProfileRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ProfileResponse> getProfile(User user) {
        if (user == null || user.getId() == null) {
            return Optional.empty();
        }
        return userProfileRepository.findByUserId(user.getId())
                .map(ProfileResponse::fromEntity);
    }

    @Transactional
    public ProfileResponse saveProfile(User user, ProfileRequest request) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }

        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + user.getId()));

        UserProfile profile = userProfileRepository.findByUserId(managedUser.getId())
                .orElseGet(() -> new UserProfile(managedUser));

        profile.setGender(request.getGender().trim());
        profile.setOrientation(request.getOrientation().trim());
        profile.setConnectionIntention(request.getConnectionIntention().trim());
        profile.setRelationshipStyle(request.getRelationshipStyle().trim());

        List<String> cleanedInterests = request.getInterests().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        if (cleanedInterests.isEmpty()) {
            throw new IllegalArgumentException("At least one non-empty interest is required");
        }

        profile.setInterests(cleanedInterests);

        UserProfile saved = userProfileRepository.save(profile);
        return ProfileResponse.fromEntity(saved);
    }
}
