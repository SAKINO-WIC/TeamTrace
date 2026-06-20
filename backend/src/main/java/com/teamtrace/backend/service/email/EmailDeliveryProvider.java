package com.teamtrace.backend.service.email;

public interface EmailDeliveryProvider {

    boolean isConfigured();

    String providerName();

    void sendPlainText(String toEmail, String subject, String body);
}
