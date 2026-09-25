package com.bondcircle.service;

public interface EmailService {
    void sendVerificationCode(String toEmail, String code);
    void sendPasswordResetCode(String toEmail, String code);
}
