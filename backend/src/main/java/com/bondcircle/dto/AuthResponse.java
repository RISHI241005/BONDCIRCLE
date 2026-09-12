package com.bondcircle.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private String message;
    private String token;
    private UserDto user;

    public AuthResponse() {
    }

    public AuthResponse(String message, UserDto user) {
        this.message = message;
        this.user = user;
    }

    public AuthResponse(String token, UserDto user, String message) {
        this.token = token;
        this.user = user;
        this.message = message;
    }

    public static AuthResponse signupSuccess(UserDto user) {
        return new AuthResponse("Account created successfully", user);
    }

    public static AuthResponse loginSuccess(String token, UserDto user) {
        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setUser(user);
        return response;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UserDto getUser() {
        return user;
    }

    public void setUser(UserDto user) {
        this.user = user;
    }
}
