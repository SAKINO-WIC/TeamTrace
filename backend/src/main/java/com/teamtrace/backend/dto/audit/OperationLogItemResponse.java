package com.teamtrace.backend.dto.audit;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/** HTTP 审计行（不落库请求体）；师生查询本人日志共用。 */
@Getter
@Builder
public class OperationLogItemResponse {
    private Long id;
    private Long userId;
    private String role;
    private String action;
    private String targetType;
    private Long targetId;
    private String httpMethod;
    private String path;
    private String queryString;
    private String ip;
    private String userAgent;
    private Integer httpStatus;
    private Integer durationMs;
    private LocalDateTime createdAt;
}
