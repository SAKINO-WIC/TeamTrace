package com.teamtrace.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamtrace.backend.service.email.BrevoEmailDeliveryProvider;
import com.teamtrace.backend.service.email.EmailDeliveryProvider;
import com.teamtrace.backend.service.email.ResendEmailDeliveryProvider;
import com.teamtrace.backend.service.email.SmtpEmailDeliveryProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class EmailConfig {

    @Bean
    BrevoEmailDeliveryProvider brevoEmailDeliveryProvider(
            ObjectMapper objectMapper,
            @Value("${BREVO_API_KEY:}") String apiKey,
            @Value("${MAIL_FROM:}") String fromAddress,
            @Value("${MAIL_FROM_NAME:TeamTrace}") String senderName,
            @Value("${teamtrace.email.brevo.api-base-url:https://api.brevo.com}") String apiBaseUrl,
            @Value("${teamtrace.email.brevo.connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${teamtrace.email.brevo.read-timeout-ms:15000}") int readTimeoutMs) {
        return new BrevoEmailDeliveryProvider(
                objectMapper, apiKey, fromAddress, senderName, apiBaseUrl, connectTimeoutMs, readTimeoutMs);
    }

    @Bean
    ResendEmailDeliveryProvider resendEmailDeliveryProvider(
            ObjectMapper objectMapper,
            @Value("${RESEND_API_KEY:}") String apiKey,
            @Value("${MAIL_FROM:}") String fromAddress,
            @Value("${teamtrace.email.resend.api-base-url:https://api.resend.com}") String apiBaseUrl,
            @Value("${teamtrace.email.resend.connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${teamtrace.email.resend.read-timeout-ms:15000}") int readTimeoutMs) {
        return new ResendEmailDeliveryProvider(
                objectMapper, apiKey, fromAddress, apiBaseUrl, connectTimeoutMs, readTimeoutMs);
    }

    @Bean
    SmtpEmailDeliveryProvider smtpEmailDeliveryProvider(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String username,
            @Value("${MAIL_FROM:${spring.mail.username:}}") String fromAddress) {
        return new SmtpEmailDeliveryProvider(mailSender, username, fromAddress);
    }

    @Bean
    @Primary
    EmailDeliveryProvider emailDeliveryProvider(
            @Value("${teamtrace.email.provider:auto}") String providerSetting,
            BrevoEmailDeliveryProvider brevoProvider,
            ResendEmailDeliveryProvider resendProvider,
            SmtpEmailDeliveryProvider smtpProvider) {
        String provider = providerSetting == null ? "auto" : providerSetting.trim().toLowerCase();

        if ("brevo".equals(provider)) {
            return brevoProvider;
        }
        if ("resend".equals(provider)) {
            return resendProvider;
        }
        if ("smtp".equals(provider)) {
            return smtpProvider;
        }

        if (brevoProvider.isConfigured()) {
            return brevoProvider;
        }
        if (resendProvider.isConfigured()) {
            return resendProvider;
        }
        return smtpProvider;
    }
}
