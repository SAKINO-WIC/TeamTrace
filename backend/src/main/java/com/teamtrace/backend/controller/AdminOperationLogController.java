package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.audit.OperationLogPageResponse;
import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.AdminOperationLogService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/operation-logs")
public class AdminOperationLogController {

    private final JwtTokenProvider jwtTokenProvider;
    private final AdminOperationLogService adminOperationLogService;

    public AdminOperationLogController(
            JwtTokenProvider jwtTokenProvider, AdminOperationLogService adminOperationLogService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.adminOperationLogService = adminOperationLogService;
    }

    @GetMapping
    public ApiResponse<OperationLogPageResponse> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "pathContains", required = false) String pathContains,
            @RequestParam(value = "createdFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(value = "createdTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo) {
        requireAdminUserId(authorization);
        return ApiResponse.success(adminOperationLogService.listLogs(
                page, size, userId, role, pathContains, createdFrom, createdTo));
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportCsv(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "limit", defaultValue = "500") int limit,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "pathContains", required = false) String pathContains,
            @RequestParam(value = "createdFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(value = "createdTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo) {
        requireAdminUserId(authorization);
        byte[] body = adminOperationLogService.exportLogsCsv(
                limit, userId, role, pathContains, createdFrom, createdTo);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"admin-operation-logs.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    private Long requireAdminUserId(String authorization) {
        String token = jwtTokenProvider.resolveBearerToken(authorization);
        if (token == null || !jwtTokenProvider.isValid(token)) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期", HttpStatus.UNAUTHORIZED);
        }
        String role = jwtTokenProvider.extractRole(token);
        if (!"admin".equals(role)) {
            throw new BusinessException("FORBIDDEN", "无权限", HttpStatus.FORBIDDEN);
        }
        return jwtTokenProvider.extractUserId(token);
    }
}

