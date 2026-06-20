package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.audit.OperationLogPageResponse;
import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.TeacherOperationLogService;
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
 * 教师查询本人 HTTP 操作审计（{@code operation_logs}，与 {@link com.teamtrace.backend.web.OperationLogFilter} 写入一致）。
 */
@RestController
@RequestMapping("/api/teacher/operation-logs")
public class TeacherOperationLogController {

    private final JwtTokenProvider jwtTokenProvider;
    private final TeacherOperationLogService teacherOperationLogService;

    public TeacherOperationLogController(
            JwtTokenProvider jwtTokenProvider, TeacherOperationLogService teacherOperationLogService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.teacherOperationLogService = teacherOperationLogService;
    }

    /**
     * 分页列表，仅返回当前登录教师 {@code user_id} 匹配的行。可选 {@code classId}：仅保留 {@code path} 含
     * {@code /api/teacher/classes/{classId}/} 的记录，且须为该班 {@code teacher_id}。
     */
    @GetMapping
    public ApiResponse<OperationLogPageResponse> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "classId", required = false) Long classId) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(teacherOperationLogService.listOwnLogs(teacherId, page, size, classId));
    }

    /**
     * 导出本人最近 {@code limit} 条为 CSV（UTF-8）。{@code limit} 默认 500，上限 2000。
     */
    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportCsv(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "limit", defaultValue = "500") int limit,
            @RequestParam(value = "classId", required = false) Long classId) {
        Long teacherId = requireTeacherUserId(authorization);
        byte[] body = teacherOperationLogService.exportOwnLogsCsv(teacherId, limit, classId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"operation-logs.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    private Long requireTeacherUserId(String authorization) {
        String token = jwtTokenProvider.resolveBearerToken(authorization);
        if (token == null || !jwtTokenProvider.isValid(token)) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期", HttpStatus.UNAUTHORIZED);
        }
        String role = jwtTokenProvider.extractRole(token);
        if (!"teacher".equals(role)) {
            throw new BusinessException("FORBIDDEN", "无权限", HttpStatus.FORBIDDEN);
        }
        return jwtTokenProvider.extractUserId(token);
    }
}
