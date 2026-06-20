package com.teamtrace.backend.listener;

import com.teamtrace.backend.event.VerificationCodeEmailEvent;
import com.teamtrace.backend.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class VerificationCodeEmailListener {

    private static final Logger log = LoggerFactory.getLogger(VerificationCodeEmailListener.class);

    private final EmailService emailService;

    public VerificationCodeEmailListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVerificationCodeEmail(VerificationCodeEmailEvent event) {
        try {
            emailService.sendVerificationCode(event.email(), event.code(), event.purposeLabel());
        } catch (RuntimeException ex) {
            log.error(
                    "verification email failed email={} purpose={}",
                    event.email(),
                    event.purposeLabel(),
                    ex);
        }
    }
}
