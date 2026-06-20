package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.admin.monitor.AdminMonitorClassPageResponse;
import com.teamtrace.backend.dto.admin.monitor.AdminMonitorOverviewResponse;
import com.teamtrace.backend.dto.admin.monitor.AdminMonitorTaskPageResponse;
import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.AdminMonitorService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/monitor")
public class AdminMonitorController {

    private final JwtTokenProvider jwtTokenProvider;
    private final AdminMonitorService adminMonitorService;

    public AdminMonitorController(JwtTokenProvider jwtTokenProvider, AdminMonitorService adminMonitorService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.adminMonitorService = adminMonitorService;
    }

    @GetMapping("/overview")
    public ApiResponse<AdminMonitorOverviewResponse> overview(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        requireAdminUserId(authorization);
        return ApiResponse.success(adminMonitorService.overview());
    }

    @GetMapping("/classes")
    public ApiResponse<AdminMonitorClassPageResponse> classes(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status
    ) {
        requireAdminUserId(authorization);
        return ApiResponse.success(adminMonitorService.listClasses(page, size, keyword, status));
    }

    @GetMapping("/tasks")
    public ApiResponse<AdminMonitorTaskPageResponse> tasks(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status
    ) {
        requireAdminUserId(authorization);
        return ApiResponse.success(adminMonitorService.listTasks(page, size, keyword, status));
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

