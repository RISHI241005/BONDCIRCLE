package com.bondcircle.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "gender", nullable = false)
    private String gender;

    @Column(name = "orientation", nullable = false)
    private String orientation;

    @Column(name = "connection_intention", nullable = false)
    private String connectionIntention;

    @Column(name = "relationship_style", nullable = false)
    private String relationshipStyle;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_profile_interests",
            joinColumns = @JoinColumn(name = "profile_id")
    )
    @Column(name = "interest", nullable = false)
    private List<String> interests = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public UserProfile() {
    }

    public UserProfile(User user) {
        this.user = user;
    }

    public UserProfile(User user, String gender, String orientation, String connectionIntention, String relationshipStyle, List<String> interests) {
        this.user = user;
        this.gender = gender;
        this.orientation = orientation;
        this.connectionIntention = connectionIntention;
        this.relationshipStyle = relationshipStyle;
        this.interests = interests != null ? new ArrayList<>(interests) : new ArrayList<>();
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
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
        this.interests = interests != null ? new ArrayList<>(interests) : new ArrayList<>();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
