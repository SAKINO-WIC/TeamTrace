package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.EmailVerificationCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {

    Optional<EmailVerificationCode> findFirstByEmailAndPurposeAndUsedAtIsNullAndExpireAtAfterOrderByCreatedAtDesc(
            String email,
            String purpose,
            LocalDateTime now);

    Optional<EmailVerificationCode> findFirstByEmailAndPurposeOrderByCreatedAtDesc(String email, String purpose);
}
