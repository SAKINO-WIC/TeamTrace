package com.teamtrace.backend.service.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamtrace.backend.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

/**
 * Brevo (Sendinblue) HTTP API — works on Render Free (HTTPS 443).
 * Free tier: 300 emails/day. Verify a single sender email (e.g. QQ) without buying a domain.
 */
public class BrevoEmailDeliveryProvider implements EmailDeliveryProvider {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailDeliveryProvider.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String senderEmail;
    private final String senderName;

    public BrevoEmailDeliveryProvider(
            ObjectMapper objectMapper,
            String apiKey,
            String fromAddress,
            String defaultSenderName,
            String apiBaseUrl,
            int connectTimeoutMs,
            int readTimeoutMs) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();

        MailFrom parsed = parseMailFrom(fromAddress, defaultSenderName);
        this.senderEmail = parsed.email();
        this.senderName = parsed.name();

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
        return !apiKey.isBlank() && !senderEmail.isBlank();
    }

    @Override
    public String providerName() {
        return "brevo";
    }

    @Override
    public void sendPlainText(String toEmail, String subject, String body) {
        Map<String, Object> payload = Map.of(
                "sender", Map.of("name", senderName, "email", senderEmail),
                "to", List.of(Map.of("email", toEmail)),
                "subject", subject,
                "textContent", body);

        try {
            restClient.post()
                    .uri("/v3/smtp/email")
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("brevo email sent from={} to={}", senderEmail, toEmail);
        } catch (HttpStatusCodeException ex) {
            log.error("brevo api error to={} status={} body={}", toEmail, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new BusinessException(
                    "EMAIL_SEND_FAILED",
                    "验证码邮件发送失败：" + extractMessage(ex),
                    HttpStatus.SERVICE_UNAVAILABLE);
        } catch (Exception ex) {
            log.error("failed to send brevo email to={}", toEmail, ex);
            throw new BusinessException(
                    "EMAIL_SEND_FAILED",
                    "验证码邮件发送失败，请检查 Brevo 配置或稍后重试",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public static MailFrom parseMailFrom(String fromAddress, String defaultSenderName) {
        String raw = fromAddress == null ? "" : fromAddress.trim();
        String fallbackName = defaultSenderName == null || defaultSenderName.isBlank()
                ? "TeamTrace"
                : defaultSenderName.trim();

        if (raw.isEmpty()) {
            return new MailFrom("", fallbackName);
        }

        int lt = raw.indexOf('<');
        int gt = raw.indexOf('>');
        if (lt >= 0 && gt > lt) {
            String name = raw.substring(0, lt).trim();
            String email = raw.substring(lt + 1, gt).trim();
            if (name.isBlank()) {
                name = fallbackName;
            }
            return new MailFrom(email, name);
        }

        return new MailFrom(raw, fallbackName);
    }

    private String extractMessage(HttpStatusCodeException ex) {
        try {
            String raw = ex.getResponseBodyAsString(StandardCharsets.UTF_8);
            if (raw == null || raw.isBlank()) {
                return "Brevo 请求失败";
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode message = root.get("message");
            if (message != null && message.isTextual()) {
                return message.asText();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "Brevo 请求失败";
    }

    public record MailFrom(String email, String name) {}
}
