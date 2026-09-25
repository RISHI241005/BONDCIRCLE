package com.bondcircle.service;

import com.bondcircle.exception.EmailDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class BrevoEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailService.class);

    private final String apiKey;
    private final String senderEmail;
    private final String senderName;
    private final String apiUrl;
    private final RestClient restClient;

    @Autowired
    public BrevoEmailService(
            @Value("${brevo.api-key:}") String apiKey,
            @Value("${brevo.sender-email:sagarkewat12121@gmail.com}") String senderEmail,
            @Value("${brevo.sender-name:BondCircle}") String senderName,
            @Value("${brevo.api-url:https://api.brevo.com/v3/smtp/email}") String apiUrl) {
        this(apiKey, senderEmail, senderName, apiUrl, RestClient.create());
    }

    public BrevoEmailService(
            String apiKey,
            String senderEmail,
            String senderName,
            String apiUrl,
            RestClient restClient) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.senderEmail = senderEmail != null && !senderEmail.isBlank() ? senderEmail.trim() : "sagarkewat12121@gmail.com";
        this.senderName = senderName != null && !senderName.isBlank() ? senderName.trim() : "BondCircle";
        this.apiUrl = apiUrl != null && !apiUrl.isBlank() ? apiUrl.trim() : "https://api.brevo.com/v3/smtp/email";
        this.restClient = restClient != null ? restClient : RestClient.create();
    }

    @Override
    public void sendVerificationCode(String toEmail, String code) {
        if (apiKey.isBlank()) {
            log.warn("BREVO_API_KEY is not configured. Verification code could not be sent to {}.", toEmail);
            throw new EmailDeliveryException("Brevo API key is not configured. Please set the BREVO_API_KEY environment variable.");
        }

        String htmlContent = buildHtmlContent(code);

        Map<String, Object> payload = Map.of(
                "sender", Map.of("name", senderName, "email", senderEmail),
                "to", List.of(Map.of("email", toEmail)),
                "subject", "Your BondCircle Verification Code",
                "htmlContent", htmlContent
        );

        try {
            restClient.post()
                    .uri(apiUrl)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        String errorBody = "";
                        try {
                            errorBody = new String(response.getBody().readAllBytes());
                        } catch (Exception ignored) {}
                        log.error("Brevo API returned error status {}: {}", response.getStatusCode(), errorBody);
                        throw new EmailDeliveryException("Brevo email delivery failed with status: " + response.getStatusCode());
                    })
                    .toBodilessEntity();

            log.info("Verification email sent successfully via Brevo to {}", toEmail);
        } catch (EmailDeliveryException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to deliver verification email via Brevo to {}: {}", toEmail, ex.getMessage());
            throw new EmailDeliveryException("Failed to send verification email. Please try again later.", ex);
        }
    }

    @Override
    public void sendPasswordResetCode(String toEmail, String code) {
        if (apiKey.isBlank()) {
            log.warn("BREVO_API_KEY is not configured. Password reset code could not be sent to {}.", toEmail);
            throw new EmailDeliveryException("Brevo API key is not configured. Please set the BREVO_API_KEY environment variable.");
        }

        String htmlContent = buildPasswordResetHtmlContent(code);

        Map<String, Object> payload = Map.of(
                "sender", Map.of("name", senderName, "email", senderEmail),
                "to", List.of(Map.of("email", toEmail)),
                "subject", "Your BondCircle Password Reset Code",
                "htmlContent", htmlContent
        );

        try {
            restClient.post()
                    .uri(apiUrl)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        String errorBody = "";
                        try {
                            errorBody = new String(response.getBody().readAllBytes());
                        } catch (Exception ignored) {}
                        log.error("Brevo API returned error status {}: {}", response.getStatusCode(), errorBody);
                        throw new EmailDeliveryException("Brevo email delivery failed with status: " + response.getStatusCode());
                    })
                    .toBodilessEntity();

            log.info("Password reset email sent successfully via Brevo to {}", toEmail);
        } catch (EmailDeliveryException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to deliver password reset email via Brevo to {}: {}", toEmail, ex.getMessage());
            throw new EmailDeliveryException("Failed to send password reset email. Please try again later.", ex);
        }
    }

    private String buildHtmlContent(String code) {
        return "<!DOCTYPE html>"
                + "<html>"
                + "<head><meta charset=\"utf-8\"><title>BondCircle Verification</title></head>"
                + "<body style=\"margin: 0; padding: 30px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #F8F9FA;\">"
                + "  <div style=\"max-width: 480px; margin: 0 auto; background: #ffffff; border-radius: 18px; padding: 36px; box-shadow: 0 4px 16px rgba(0,0,0,0.06); border: 1px solid #EFEFEF;\">"
                + "    <div style=\"text-align: center; margin-bottom: 24px;\">"
                + "      <h2 style=\"color: #E05375; margin: 0; font-size: 26px; font-weight: 800; letter-spacing: -0.5px;\">BondCircle</h2>"
                + "      <p style=\"color: #6C727F; font-size: 14px; margin-top: 6px;\">Meaningful connections through shared interests</p>"
                + "    </div>"
                + "    <p style=\"font-size: 16px; color: #1E1B2E; margin-bottom: 12px;\">Hello,</p>"
                + "    <p style=\"font-size: 15px; color: #4B5563; line-height: 1.5; margin-bottom: 24px;\">"
                + "      Welcome to BondCircle! Please enter this 6-digit verification code to verify your email address and continue setting up your account:"
                + "    </p>"
                + "    <div style=\"text-align: center; margin: 28px 0;\">"
                + "      <div style=\"display: inline-block; background-color: #FFF0F4; border: 1.5px solid #FFD0DD; border-radius: 14px; padding: 14px 32px;\">"
                + "        <span style=\"font-size: 34px; font-weight: 800; letter-spacing: 8px; color: #E05375;\">" + code + "</span>"
                + "      </div>"
                + "    </div>"
                + "    <p style=\"font-size: 13px; color: #6B7280; line-height: 1.5; text-align: center; margin-top: 24px;\">"
                + "      This code is valid for <strong>10 minutes</strong>. For security, never share this code with anyone."
                + "    </p>"
                + "    <hr style=\"border: none; border-top: 1px solid #F3F4F6; margin: 28px 0 18px 0;\">"
                + "    <p style=\"font-size: 12px; color: #9CA3AF; text-align: center; margin: 0;\">"
                + "      If you did not request this verification code, please ignore this message."
                + "    </p>"
                + "  </div>"
                + "</body>"
                + "</html>";
    }

    private String buildPasswordResetHtmlContent(String code) {
        return "<!DOCTYPE html>"
                + "<html>"
                + "<head><meta charset=\"utf-8\"><title>Reset Your BondCircle Password</title></head>"
                + "<body style=\"margin: 0; padding: 30px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #F8F9FA;\">"
                + "  <div style=\"max-width: 480px; margin: 0 auto; background: #ffffff; border-radius: 18px; padding: 36px; box-shadow: 0 4px 16px rgba(0,0,0,0.06); border: 1px solid #EFEFEF;\">"
                + "    <div style=\"text-align: center; margin-bottom: 24px;\">"
                + "      <h2 style=\"color: #E05375; margin: 0; font-size: 26px; font-weight: 800; letter-spacing: -0.5px;\">BondCircle</h2>"
                + "      <p style=\"color: #6C727F; font-size: 14px; margin-top: 6px;\">Meaningful connections through shared interests</p>"
                + "    </div>"
                + "    <p style=\"font-size: 16px; color: #1E1B2E; margin-bottom: 12px;\">Hello,</p>"
                + "    <p style=\"font-size: 15px; color: #4B5563; line-height: 1.5; margin-bottom: 24px;\">"
                + "      We received a request to reset your password for your BondCircle account. Please enter this 6-digit verification code to proceed with resetting your password:"
                + "    </p>"
                + "    <div style=\"text-align: center; margin: 28px 0;\">"
                + "      <div style=\"display: inline-block; background-color: #FFF0F4; border: 1.5px solid #FFD0DD; border-radius: 14px; padding: 14px 32px;\">"
                + "        <span style=\"font-size: 34px; font-weight: 800; letter-spacing: 8px; color: #E05375;\">" + code + "</span>"
                + "      </div>"
                + "    </div>"
                + "    <p style=\"font-size: 13px; color: #6B7280; line-height: 1.5; text-align: center; margin-top: 24px;\">"
                + "      This code is valid for <strong>10 minutes</strong>. For security, never share this code with anyone."
                + "    </p>"
                + "    <hr style=\"border: none; border-top: 1px solid #F3F4F6; margin: 28px 0 18px 0;\">"
                + "    <p style=\"font-size: 12px; color: #9CA3AF; text-align: center; margin: 0;\">"
                + "      If you did not request a password reset, please ignore this email. Your password will remain unchanged."
                + "    </p>"
                + "  </div>"
                + "</body>"
                + "</html>";
    }
}
