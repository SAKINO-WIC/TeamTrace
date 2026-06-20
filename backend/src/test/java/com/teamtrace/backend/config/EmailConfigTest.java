package com.teamtrace.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamtrace.backend.service.email.BrevoEmailDeliveryProvider;
import com.teamtrace.backend.service.email.EmailDeliveryProvider;
import com.teamtrace.backend.service.email.ResendEmailDeliveryProvider;
import com.teamtrace.backend.service.email.SmtpEmailDeliveryProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import static org.mockito.Mockito.mock;

class EmailConfigTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private BrevoEmailDeliveryProvider brevo(String apiKey, String from) {
        return new BrevoEmailDeliveryProvider(
                objectMapper, apiKey, from, "TeamTrace", "https://api.brevo.com", 1000, 1000);
    }

    private ResendEmailDeliveryProvider resend(String apiKey, String from) {
        return new ResendEmailDeliveryProvider(
                objectMapper, apiKey, from, "https://api.resend.com", 1000, 1000);
    }

    @Test
    void autoPrefersBrevoWhenBrevoConfigured() {
        EmailDeliveryProvider selected = new EmailConfig().emailDeliveryProvider(
                "auto",
                brevo("xkeysib-test", "sender@qq.com"),
                resend("re_test", "onboarding@resend.dev"),
                new SmtpEmailDeliveryProvider(mock(JavaMailSender.class), "", ""));

        assertEquals("brevo", selected.providerName());
    }

    @Test
    void autoPrefersResendWhenOnlyResendConfigured() {
        EmailDeliveryProvider selected = new EmailConfig().emailDeliveryProvider(
                "auto",
                brevo("", ""),
                resend("re_test", "onboarding@resend.dev"),
                new SmtpEmailDeliveryProvider(mock(JavaMailSender.class), "", ""));

        assertEquals("resend", selected.providerName());
    }

    @Test
    void autoFallsBackToSmtpWhenHttpProvidersNotConfigured() {
        EmailDeliveryProvider selected = new EmailConfig().emailDeliveryProvider(
                "auto",
                brevo("", ""),
                resend("", ""),
                new SmtpEmailDeliveryProvider(mock(JavaMailSender.class), "sender@qq.com", "sender@qq.com"));

        assertEquals("smtp", selected.providerName());
        assertTrue(selected.isConfigured());
    }

    @Test
    void explicitBrevoIgnoresResend() {
        EmailDeliveryProvider selected = new EmailConfig().emailDeliveryProvider(
                "brevo",
                brevo("xkeysib-test", "sender@qq.com"),
                resend("re_test", "onboarding@resend.dev"),
                new SmtpEmailDeliveryProvider(mock(JavaMailSender.class), "sender@qq.com", "sender@qq.com"));

        assertEquals("brevo", selected.providerName());
    }

    @Test
    void brevoRequiresApiKeyAndFromEmail() {
        assertFalse(brevo("", "sender@qq.com").isConfigured());
        assertFalse(brevo("xkeysib-test", "").isConfigured());
        assertTrue(brevo("xkeysib-test", "2385955502@qq.com").isConfigured());
    }

    @Test
    void brevoParsesMailFromWithDisplayName() {
        BrevoEmailDeliveryProvider.MailFrom parsed =
                BrevoEmailDeliveryProvider.parseMailFrom("TeamTrace <verify@qq.com>", "TeamTrace");

        assertEquals("verify@qq.com", parsed.email());
        assertEquals("TeamTrace", parsed.name());
    }
}
