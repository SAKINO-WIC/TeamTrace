package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.audit.OperationLogPageResponse;
import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.StudentOperationLogService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生查询本人 HTTP 操作审计（{@code operation_logs}）。
 */
@RestController
@RequestMapping("/api/student/operation-logs")
public class StudentOperationLogController {

    private final JwtTokenProvider jwtTokenProvider;
    private final StudentOperationLogService studentOperationLogService;

    public StudentOperationLogController(
            JwtTokenProvider jwtTokenProvider, StudentOperationLogService studentOperationLogService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.studentOperationLogService = studentOperationLogService;
    }

    /**
     * 分页列表。可选 {@code classId}：仅 {@code path} 含 {@code /api/student/classes/{classId}/} 的记录，且须当前在班。
     */
    @GetMapping
    public ApiResponse<OperationLogPageResponse> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "classId", required = false) Long classId,
            @RequestParam(value = "pathContains", required = false) String pathContains) {
        Long studentId = requireStudentUserId(authorization);
        return ApiResponse.success(
                studentOperationLogService.listOwnLogs(studentId, page, size, classId, pathContains));
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportCsv(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "limit", defaultValue = "500") int limit,
            @RequestParam(value = "classId", required = false) Long classId,
            @RequestParam(value = "pathContains", required = false) String pathContains) {
        Long studentId = requireStudentUserId(authorization);
        byte[] body = studentOperationLogService.exportOwnLogsCsv(studentId, limit, classId, pathContains);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"student-operation-logs.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    private Long requireStudentUserId(String authorization) {
        String token = jwtTokenProvider.resolveBearerToken(authorization);
        if (token == null || !jwtTokenProvider.isValid(token)) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期", HttpStatus.UNAUTHORIZED);
        }
        String role = jwtTokenProvider.extractRole(token);
        if (!"student".equals(role)) {
            throw new BusinessException("FORBIDDEN", "无权限", HttpStatus.FORBIDDEN);
        }
        return jwtTokenProvider.extractUserId(token);
    }
}
