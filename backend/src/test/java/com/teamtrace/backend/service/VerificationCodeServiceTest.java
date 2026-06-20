package com.teamtrace.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamtrace.backend.entity.EmailVerificationCode;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.EmailVerificationCodeRepository;
import com.teamtrace.backend.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class VerificationCodeServiceTest {

    private final EmailVerificationCodeRepository codeRepository = mock(EmailVerificationCodeRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final EmailService emailService = mock(EmailService.class);
    private final VerificationCodeService service = new VerificationCodeService(
            codeRepository, userRepository, emailService, 5, 60);

    @Test
    void sendCodeSendsVerificationEmailBeforeReturningSuccess() {
        when(userRepository.existsByEmailAndIsDeleted("new-user@qq.com", 0)).thenReturn(false);
        when(codeRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(
                "new-user@qq.com", VerificationCodeService.PURPOSE_REGISTER))
                .thenReturn(Optional.empty());
        when(codeRepository.save(any(EmailVerificationCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.sendCode(" new-user@qq.com ", VerificationCodeService.PURPOSE_REGISTER);

        ArgumentCaptor<EmailVerificationCode> savedCode = ArgumentCaptor.forClass(EmailVerificationCode.class);
        verify(codeRepository).save(savedCode.capture());
        verify(emailService).sendVerificationCode("new-user@qq.com", savedCode.getValue().getCode(), "注册");
    }

    @Test
    void sendCodePropagatesEmailDeliveryFailure() {
        when(userRepository.existsByEmailAndIsDeleted("new-user@qq.com", 0)).thenReturn(false);
        when(codeRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(
                "new-user@qq.com", VerificationCodeService.PURPOSE_REGISTER))
                .thenReturn(Optional.empty());
        when(codeRepository.save(any(EmailVerificationCode.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new BusinessException("EMAIL_NOT_CONFIGURED", "邮件服务未配置", HttpStatus.SERVICE_UNAVAILABLE))
                .when(emailService)
                .sendVerificationCode(any(), any(), any());

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.sendCode("new-user@qq.com", VerificationCodeService.PURPOSE_REGISTER));

        assertEquals("EMAIL_NOT_CONFIGURED", error.getCode());
    }
}
