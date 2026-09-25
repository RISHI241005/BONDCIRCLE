package com.bondcircle.dto;

public class ForgotPasswordVerifyCodeResponse {

    private String resetToken;
    private String message;

    public ForgotPasswordVerifyCodeResponse() {
    }

    public ForgotPasswordVerifyCodeResponse(String resetToken, String message) {
        this.resetToken = resetToken;
        this.message = message;
    }

    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
