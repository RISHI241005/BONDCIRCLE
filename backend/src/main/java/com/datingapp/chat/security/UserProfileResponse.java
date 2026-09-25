package com.datingapp.chat.security;

import java.util.List;

public record UserProfileResponse(
        Long userId,
        String publicId,
        String email,
        String fullName,
        String phone,
        List<String> interests
) {
}
