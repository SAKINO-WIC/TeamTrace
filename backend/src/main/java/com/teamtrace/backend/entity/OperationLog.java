package com.teamtrace.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** HTTP 层操作审计（不落库请求体，避免密码等敏感信息）。 */
@Getter
@Setter
@Entity
@Table(
        name = "operation_logs",
        indexes = {@Index(name = "idx_operation_logs_created_at", columnList = "created_at")})
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    /**
     * 与存量库表兼容：部分环境在 {@code operation_logs} 上仍保留官方 DDL 的 {@code action} NOT NULL（无默认值）。
     */
    @Column(name = "action", nullable = false, length = 50)
    private String action;

    /**
     * 与存量库表兼容：官方 DDL 的 {@code target_type} NOT NULL。HTTP 审计无业务目标实体时使用占位类别。
     */
    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    /**
     * 与存量库表兼容：官方 DDL 的 {@code target_id} NOT NULL。HTTP 审计无单一业务主键时使用 0。
     */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(length = 32)
    private String role;

    @Column(name = "http_method", nullable = false, length = 16)
    private String httpMethod;

    @Column(nullable = false, length = 512)
    private String path;

    @Column(name = "query_string", length = 512)
    private String queryString;

    @Column(length = 64)
    private String ip;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
