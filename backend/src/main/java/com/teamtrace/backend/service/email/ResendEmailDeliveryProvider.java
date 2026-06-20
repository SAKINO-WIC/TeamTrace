package com.teamtrace.backend.service.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamtrace.backend.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

public class ResendEmailDeliveryProvider implements EmailDeliveryProvider {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailDeliveryProvider.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String fromAddress;

    public ResendEmailDeliveryProvider(
            ObjectMapper objectMapper,
            @Value("${RESEND_API_KEY:}") String apiKey,
            @Value("${MAIL_FROM:}") String fromAddress,
            @Value("${teamtrace.email.resend.api-base-url:https://api.resend.com}") String apiBaseUrl,
            @Value("${teamtrace.email.resend.connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${teamtrace.email.resend.read-timeout-ms:15000}") int readTimeoutMs) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.fromAddress = fromAddress == null ? "" : fromAddress.trim();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(apiBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank() && !fromAddress.isBlank();
    }

    @Override
    public String providerName() {
        return "resend";
    }

    @Override
    public void sendPlainText(String toEmail, String subject, String body) {
        Map<String, Object> payload = Map.of(
                "from", fromAddress,
                "to", List.of(toEmail),
                "subject", subject,
                "text", body);

        try {
            restClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("resend email sent to={}", toEmail);
        } catch (HttpStatusCodeException ex) {
            log.error("resend api error to={} status={} body={}", toEmail, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new BusinessException(
                    "EMAIL_SEND_FAILED",
                    "验证码邮件发送失败：" + extractResendMessage(ex),
                    HttpStatus.SERVICE_UNAVAILABLE);
        } catch (Exception ex) {
            log.error("failed to send resend email to={}", toEmail, ex);
            throw new BusinessException(
                    "EMAIL_SEND_FAILED",
                    "验证码邮件发送失败，请检查 Resend 配置或稍后重试",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private String extractResendMessage(HttpStatusCodeException ex) {
        try {
            String raw = ex.getResponseBodyAsString(StandardCharsets.UTF_8);
            if (raw == null || raw.isBlank()) {
                return "Resend 请求失败";
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode message = root.get("message");
            if (message != null && message.isTextual()) {
                return message.asText();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "Resend 请求失败";
    }
}
