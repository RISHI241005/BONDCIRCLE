package com.bondcircle;

import com.bondcircle.exception.EmailDeliveryException;
import com.bondcircle.service.BrevoEmailService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class BrevoEmailServiceTest {

    @Test
    void testSendVerificationCodeSuccess() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String apiUrl = "https://api.brevo.com/v3/smtp/email";
        String apiKey = "xkeysib-testkey12345";
        BrevoEmailService brevoService = new BrevoEmailService(
                apiKey,
                "noreply@bondcircle.com",
                "BondCircle",
                apiUrl,
                builder.build()
        );

        server.expect(requestTo(apiUrl))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.to[0].email").value("user@gmail.com"))
                .andExpect(jsonPath("$.sender.name").value("BondCircle"))
                .andExpect(jsonPath("$.sender.email").value("noreply@bondcircle.com"))
                .andExpect(jsonPath("$.subject").value("Your BondCircle Verification Code"))
                .andExpect(jsonPath("$.htmlContent").value(org.hamcrest.Matchers.containsString("654321")))
                .andRespond(withSuccess("{\"messageId\":\"<12345@smtp-relay.mailin.fr>\"}", MediaType.APPLICATION_JSON));

        brevoService.sendVerificationCode("user@gmail.com", "654321");

        server.verify();
    }

    @Test
    void testSendVerificationCodeWithoutApiKeyThrows() {
        BrevoEmailService brevoService = new BrevoEmailService(
                "",
                "noreply@bondcircle.com",
                "BondCircle",
                "https://api.brevo.com/v3/smtp/email"
        );

        EmailDeliveryException ex = assertThrows(EmailDeliveryException.class, () ->
                brevoService.sendVerificationCode("user@gmail.com", "123456")
        );

        assertTrue(ex.getMessage().contains("Brevo API key is not configured"));
    }

    @Test
    void testSendVerificationCodeBrevoError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String apiUrl = "https://api.brevo.com/v3/smtp/email";
        String apiKey = "xkeysib-invalid";
        BrevoEmailService brevoService = new BrevoEmailService(
                apiKey,
                "noreply@bondcircle.com",
                "BondCircle",
                apiUrl,
                builder.build()
        );

        server.expect(requestTo(apiUrl))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"unauthorized\",\"message\":\"Key not found\"}"));

        assertThrows(EmailDeliveryException.class, () ->
                brevoService.sendVerificationCode("user@gmail.com", "123456")
        );

        server.verify();
    }

    @Test
    void testSendPasswordResetCodeSuccess() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String apiUrl = "https://api.brevo.com/v3/smtp/email";
        String apiKey = "xkeysib-testkey12345";
        BrevoEmailService brevoService = new BrevoEmailService(
                apiKey,
                "noreply@bondcircle.com",
                "BondCircle",
                apiUrl,
                builder.build()
        );

        server.expect(requestTo(apiUrl))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.to[0].email").value("user@gmail.com"))
                .andExpect(jsonPath("$.sender.name").value("BondCircle"))
                .andExpect(jsonPath("$.sender.email").value("noreply@bondcircle.com"))
                .andExpect(jsonPath("$.subject").value("Your BondCircle Password Reset Code"))
                .andExpect(jsonPath("$.htmlContent").value(org.hamcrest.Matchers.containsString("789012")))
                .andExpect(jsonPath("$.htmlContent").value(org.hamcrest.Matchers.containsString("reset your password")))
                .andRespond(withSuccess("{\"messageId\":\"<reset-12345@smtp-relay.mailin.fr>\"}", MediaType.APPLICATION_JSON));

        brevoService.sendPasswordResetCode("user@gmail.com", "789012");

        server.verify();
    }
}
