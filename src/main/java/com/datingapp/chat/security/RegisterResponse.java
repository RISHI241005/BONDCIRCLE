package com.datingapp.chat.security;

import java.util.Objects;
import java.util.List;

public class RegisterResponse {

    private Long userId;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private List<String> interests;

    public RegisterResponse() {}

    public RegisterResponse(Long userId, String username, String email, String fullName, String phone, List<String> interests) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.phone = phone;
        this.interests = interests;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public List<String> getInterests() {
        return interests;
    }

    public void setInterests(List<String> interests) {
        this.interests = interests;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegisterResponse that = (RegisterResponse) o;
        return Objects.equals(userId, that.userId) &&
                Objects.equals(username, that.username) &&
                Objects.equals(email, that.email) &&
                Objects.equals(fullName, that.fullName) &&
                Objects.equals(phone, that.phone) &&
                Objects.equals(interests, that.interests);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, username, email, fullName, phone, interests);
    }
}
