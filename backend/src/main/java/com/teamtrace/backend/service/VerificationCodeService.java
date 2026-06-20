package com.teamtrace.backend.service;

import com.teamtrace.backend.entity.EmailVerificationCode;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.EmailVerificationCodeRepository;
import com.teamtrace.backend.repository.UserRepository;
import com.teamtrace.backend.util.AppTime;
import com.teamtrace.backend.util.EmailValidation;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationCodeService {

    public static final String PURPOSE_REGISTER = "register";
    public static final String PURPOSE_RESET_PASSWORD = "reset_password";
    public static final String PURPOSE_DELETE_ACCOUNT = "delete_account";

    private static final Set<String> VALID_PURPOSES = Set.of(
            PURPOSE_REGISTER, PURPOSE_RESET_PASSWORD, PURPOSE_DELETE_ACCOUNT);

    private final EmailVerificationCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();
    private final long expireMinutes;
    private final long resendSeconds;

    public VerificationCodeService(
            EmailVerificationCodeRepository codeRepository,
            UserRepository userRepository,
            EmailService emailService,
            @Value("${teamtrace.email.verification.expire-minutes:5}") long expireMinutes,
            @Value("${teamtrace.email.verification.resend-seconds:60}") long resendSeconds) {
        this.codeRepository = codeRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.expireMinutes = expireMinutes;
        this.resendSeconds = resendSeconds;
    }

    @Transactional
    public void sendCode(String rawEmail, String purpose) {
        String email = EmailValidation.normalize(rawEmail);
        validatePurpose(purpose);
        if (!EmailValidation.isAllowedEmail(email)) {
            throw new BusinessException("BAD_REQUEST", EmailValidation.ALLOWED_EMAIL_MESSAGE, HttpStatus.BAD_REQUEST);
        }

        if (PURPOSE_REGISTER.equals(purpose) && userRepository.existsByEmailAndIsDeleted(email, 0)) {
            throw new BusinessException("BUSINESS_CONFLICT", "该邮箱已注册", HttpStatus.CONFLICT);
        }
        if ((PURPOSE_RESET_PASSWORD.equals(purpose) || PURPOSE_DELETE_ACCOUNT.equals(purpose))
                && !userRepository.existsByEmailAndIsDeleted(email, 0)) {
            throw new BusinessException("BAD_REQUEST", "该邮箱尚未注册", HttpStatus.BAD_REQUEST);
        }

        codeRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(email, purpose).ifPresent(latest -> {
            if (latest.getCreatedAt() != null
                    && latest.getCreatedAt().isAfter(AppTime.now().minusSeconds(resendSeconds))) {
                throw new BusinessException("TOO_MANY_REQUESTS", "发送过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS);
            }
        });

        String code = String.format("%06d", random.nextInt(1_000_000));
        EmailVerificationCode entity = new EmailVerificationCode();
        entity.setEmail(email);
        entity.setCode(code);
        entity.setPurpose(purpose);
        entity.setExpireAt(AppTime.now().plusMinutes(expireMinutes));
        entity.setCreatedAt(AppTime.now());
        codeRepository.save(entity);

        String purposeLabel = getPurposeLabel(purpose);
        emailService.sendVerificationCode(email, code, purposeLabel);
    }

    @Transactional
    public void verifyAndConsume(String rawEmail, String purpose, String rawCode) {
        String email = EmailValidation.normalize(rawEmail);
        validatePurpose(purpose);
        String code = rawCode == null ? "" : rawCode.trim();
        if (!code.matches("^\\d{6}$")) {
            throw new BusinessException("BAD_REQUEST", "验证码格式不正确", HttpStatus.BAD_REQUEST);
        }

        EmailVerificationCode latest = codeRepository
                .findFirstByEmailAndPurposeAndUsedAtIsNullAndExpireAtAfterOrderByCreatedAtDesc(
                        email, purpose, AppTime.now())
                .orElseThrow(() -> new BusinessException("BAD_REQUEST", "验证码无效或已过期", HttpStatus.BAD_REQUEST));

        if (!latest.getCode().equals(code)) {
            throw new BusinessException("BAD_REQUEST", "验证码错误", HttpStatus.BAD_REQUEST);
        }

        latest.setUsedAt(AppTime.now());
        codeRepository.save(latest);
    }

    private void validatePurpose(String purpose) {
        if (!VALID_PURPOSES.contains(purpose)) {
            throw new BusinessException("BAD_REQUEST", "不支持的验证码用途", HttpStatus.BAD_REQUEST);
        }
    }

    private String getPurposeLabel(String purpose) {
        switch (purpose) {
            case PURPOSE_REGISTER:
                return "注册";
            case PURPOSE_RESET_PASSWORD:
                return "重置密码";
            case PURPOSE_DELETE_ACCOUNT:
                return "注销账号";
            default:
                return purpose;
        }
    }
}
