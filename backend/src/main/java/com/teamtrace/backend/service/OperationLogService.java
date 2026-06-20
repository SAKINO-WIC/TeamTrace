package com.teamtrace.backend.service;

import com.teamtrace.backend.entity.OperationLog;
import com.teamtrace.backend.repository.OperationLogRepository;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * HTTP 审计落库。从 Servlet Filter 调用时，显式 {@link TransactionTemplate} 比仅靠 @Transactional 更可靠（避免代理/调用链导致未提交）。
 */
public class OperationLogService {

    private static final Logger log = LoggerFactory.getLogger(OperationLogService.class);

    private final OperationLogRepository operationLogRepository;
    private final TransactionTemplate newTxTemplate;

    public OperationLogService(
            OperationLogRepository operationLogRepository,
            @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
        this.operationLogRepository = operationLogRepository;
        this.newTxTemplate = new TransactionTemplate(transactionManager);
        this.newTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(OperationLog row) {
        try {
            newTxTemplate.executeWithoutResult(
                    status -> operationLogRepository.save(Objects.requireNonNull(row)));
        } catch (RuntimeException ex) {
            log.error("operation log persist failed path={}", row.getPath(), ex);
        }
    }
}
