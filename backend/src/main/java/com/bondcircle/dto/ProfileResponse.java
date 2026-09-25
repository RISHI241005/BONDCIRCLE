package com.bondcircle.dto;

import com.bondcircle.entity.UserProfile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ProfileResponse {

    private Long id;
    private String gender;
    private String orientation;
    private String connectionIntention;
    private String relationshipStyle;
    private List<String> interests;
    private LocalDateTime updatedAt;

    public ProfileResponse() {
    }

    public ProfileResponse(Long id, String gender, String orientation, String connectionIntention, String relationshipStyle, List<String> interests, LocalDateTime updatedAt) {
        this.id = id;
        this.gender = gender;
        this.orientation = orientation;
        this.connectionIntention = connectionIntention;
        this.relationshipStyle = relationshipStyle;
        this.interests = interests != null ? new ArrayList<>(interests) : new ArrayList<>();
        this.updatedAt = updatedAt;
    }

    public static ProfileResponse fromEntity(UserProfile profile) {
        if (profile == null) {
            return null;
        }
        return new ProfileResponse(
                profile.getId(),
                profile.getGender(),
                profile.getOrientation(),
                profile.getConnectionIntention(),
                profile.getRelationshipStyle(),
                profile.getInterests(),
                profile.getUpdatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getOrientation() {
        return orientation;
    }

    public void setOrientation(String orientation) {
        this.orientation = orientation;
    }

    public String getConnectionIntention() {
        return connectionIntention;
    }

    public void setConnectionIntention(String connectionIntention) {
        this.connectionIntention = connectionIntention;
    }

    public String getRelationshipStyle() {
        return relationshipStyle;
    }

    public void setRelationshipStyle(String relationshipStyle) {
        this.relationshipStyle = relationshipStyle;
    }

    public List<String> getInterests() {
        return interests;
    }

    public void setInterests(List<String> interests) {
        this.interests = interests;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
