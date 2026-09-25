package com.bondcircle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class ProfileRequest {

    @NotBlank(message = "Gender is required")
    private String gender;

    @NotBlank(message = "Orientation is required")
    private String orientation;

    @NotBlank(message = "Connection intention is required")
    private String connectionIntention;

    @NotBlank(message = "Relationship style is required")
    private String relationshipStyle;

    @NotEmpty(message = "At least one interest is required")
    private List<@NotBlank(message = "Interest cannot be blank") String> interests;

    public ProfileRequest() {
    }

    public ProfileRequest(String gender, String orientation, String connectionIntention, String relationshipStyle, List<String> interests) {
        this.gender = gender;
        this.orientation = orientation;
        this.connectionIntention = connectionIntention;
        this.relationshipStyle = relationshipStyle;
        this.interests = interests;
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
}
