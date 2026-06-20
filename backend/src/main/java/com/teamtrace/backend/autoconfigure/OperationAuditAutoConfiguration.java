package com.teamtrace.backend.autoconfigure;

import com.teamtrace.backend.repository.OperationLogRepository;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.OperationLogService;
import com.teamtrace.backend.web.OperationLogFilter;
import jakarta.servlet.DispatcherType;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 在 JPA / Repository 就绪之后再注册审计；用 {@link FilterRegistrationBean} 显式挂到 Servlet，避免仅靠
 * {@code @Bean} {@link jakarta.servlet.Filter} 在部分 Boot 版本下未参与请求链。
 *
 * <p>条件用 {@link OperationLogRepository} 而非 {@code EntityManagerFactory}：后者在部分自动配置顺序下
 * {@code @ConditionalOnBean} 可能过早判定为不存在，导致整段审计未注册。
 */
@AutoConfiguration(
        after = {HibernateJpaAutoConfiguration.class, DataJpaRepositoriesAutoConfiguration.class})
@ConditionalOnProperty(name = "teamtrace.audit.http.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(OperationLogRepository.class)
public class OperationAuditAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OperationAuditAutoConfiguration.class);

    @Bean
    public OperationLogService operationLogService(
            OperationLogRepository operationLogRepository,
            @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
        return new OperationLogService(operationLogRepository, transactionManager);
    }

    @Bean
    public ApplicationRunner operationLogSchemaRunner(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                String nullable = jdbcTemplate.query(
                        "SELECT IS_NULLABLE FROM information_schema.COLUMNS "
                                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation_logs' AND COLUMN_NAME = 'user_id'",
                        rs -> rs.next() ? rs.getString(1) : null);
                if ("NO".equalsIgnoreCase(nullable)) {
                    String usersIdType = jdbcTemplate.query(
                            "SELECT COLUMN_TYPE FROM information_schema.COLUMNS "
                                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'id'",
                            rs -> rs.next() ? rs.getString(1) : null);
                    if (usersIdType == null || usersIdType.isBlank()) {
                        log.warn("TeamTrace HTTP audit: skip nullability align, users.id column type not found");
                        return;
                    }
                    String fkName = jdbcTemplate.query(
                            "SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE "
                                    + "WHERE TABLE_SCHEMA = DATABASE() "
                                    + "AND TABLE_NAME = 'operation_logs' "
                                    + "AND COLUMN_NAME = 'user_id' "
                                    + "AND REFERENCED_TABLE_NAME = 'users' "
                                    + "AND REFERENCED_COLUMN_NAME = 'id' "
                                    + "LIMIT 1",
                            rs -> rs.next() ? rs.getString(1) : null);
                    if (fkName != null && !fkName.isBlank()) {
                        jdbcTemplate.execute("ALTER TABLE operation_logs DROP FOREIGN KEY `" + fkName + "`");
                    }
                    jdbcTemplate.execute(
                            "ALTER TABLE operation_logs MODIFY COLUMN user_id " + usersIdType + " NULL");
                    if (fkName != null && !fkName.isBlank()) {
                        jdbcTemplate.execute(
                                "ALTER TABLE operation_logs ADD CONSTRAINT `" + fkName
                                        + "` FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE");
                    }
                    log.info("TeamTrace HTTP audit: aligned operation_logs.user_id to NULLABLE");
                }
            } catch (RuntimeException ex) {
                log.warn("TeamTrace HTTP audit: failed to align operation_logs.user_id nullability", ex);
            }
        };
    }

    @Bean
    public FilterRegistrationBean<OperationLogFilter> operationLogFilterRegistration(
            JwtTokenProvider jwtTokenProvider, OperationLogService operationLogService) {
        log.info("TeamTrace HTTP audit: OperationLogFilter registered (table operation_logs)");
        FilterRegistrationBean<OperationLogFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new OperationLogFilter(jwtTokenProvider, operationLogService));
        reg.addUrlPatterns("/*");
        reg.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR));
        reg.setOrder(Ordered.LOWEST_PRECEDENCE);
        return reg;
    }
}
