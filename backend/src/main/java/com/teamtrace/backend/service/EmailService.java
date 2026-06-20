package com.teamtrace.backend.service;

import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.service.email.EmailDeliveryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailDeliveryProvider deliveryProvider;

    public EmailService(EmailDeliveryProvider deliveryProvider) {
        this.deliveryProvider = deliveryProvider;
    }

    public void sendVerificationCode(String toEmail, String code, String purposeLabel) {
        ensureConfigured();

        String body = """
                您正在进行 TeamTrace %s 操作。

                验证码：%s
                有效期 5 分钟，请勿泄露给他人。

                如非本人操作，请忽略此邮件。
                """.formatted(purposeLabel, code).trim();

        deliveryProvider.sendPlainText(toEmail, "TeamTrace 验证码", body);
        log.info("verification email sent via {} to={} purpose={}", deliveryProvider.providerName(), toEmail, purposeLabel);
    }

    public void sendPlainText(String toEmail, String subject, String body, String purposeLabel) {
        ensureConfigured();
        deliveryProvider.sendPlainText(toEmail, subject, body);
        log.info("email sent via {} to={} purpose={}", deliveryProvider.providerName(), toEmail, purposeLabel);
    }

    private void ensureConfigured() {
        if (!deliveryProvider.isConfigured()) {
            throw new BusinessException(
                    "EMAIL_NOT_CONFIGURED",
                    "邮件服务未配置。Render 请设置 BREVO_API_KEY + MAIL_FROM，或 RESEND_API_KEY + MAIL_FROM；本地开发可设置 MAIL_USERNAME / MAIL_PASSWORD",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
