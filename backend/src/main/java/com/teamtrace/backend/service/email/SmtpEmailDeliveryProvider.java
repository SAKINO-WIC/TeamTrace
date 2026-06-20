package com.teamtrace.backend.service.email;

import com.teamtrace.backend.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class SmtpEmailDeliveryProvider implements EmailDeliveryProvider {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailDeliveryProvider.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean configured;

    public SmtpEmailDeliveryProvider(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String username,
            @Value("${MAIL_FROM:${spring.mail.username:}}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress == null || fromAddress.isBlank() ? username : fromAddress.trim();
        this.configured = this.fromAddress != null
                && !this.fromAddress.isBlank()
                && username != null
                && !username.isBlank();
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public String providerName() {
        return "smtp";
    }

    @Override
    public void sendPlainText(String toEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
            log.info("smtp email sent to={}", toEmail);
        } catch (Exception ex) {
            log.error("failed to send smtp email to={}", toEmail, ex);
            throw new BusinessException(
                    "EMAIL_SEND_FAILED",
                    "验证码邮件发送失败，请检查 SMTP 配置或稍后重试",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
