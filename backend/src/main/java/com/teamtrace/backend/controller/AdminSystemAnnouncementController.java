package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.admin.SystemAnnouncementCreateRequest;
import com.teamtrace.backend.dto.admin.SystemAnnouncementPageResponse;
import com.teamtrace.backend.dto.admin.SystemAnnouncementResponse;
import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.SystemAnnouncementService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system-announcements")
public class AdminSystemAnnouncementController {
    private final JwtTokenProvider jwtTokenProvider;
    private final SystemAnnouncementService systemAnnouncementService;

    public AdminSystemAnnouncementController(
            JwtTokenProvider jwtTokenProvider,
            SystemAnnouncementService systemAnnouncementService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.systemAnnouncementService = systemAnnouncementService;
    }

    @GetMapping
    public ApiResponse<SystemAnnouncementPageResponse> page(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "targetScope", required = false) String targetScope,
            @RequestParam(value = "keyword", required = false) String keyword) {
        requireAdminUserId(authorization);
        return ApiResponse.success(systemAnnouncementService.page(page, size, status, targetScope, keyword));
    }

    @PostMapping
    public ApiResponse<SystemAnnouncementResponse> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody SystemAnnouncementCreateRequest request) {
        Long adminUserId = requireAdminUserId(authorization);
        return ApiResponse.success(systemAnnouncementService.create(adminUserId, request));
    }

    @PutMapping("/{id}/withdraw")
    public ApiResponse<SystemAnnouncementResponse> withdraw(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        requireAdminUserId(authorization);
        return ApiResponse.success(systemAnnouncementService.withdraw(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Long>> delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        requireAdminUserId(authorization);
        systemAnnouncementService.delete(id);
        return ApiResponse.success(Map.of("deleted", 1L));
    }

    private Long requireAdminUserId(String authorization) {
        String token = jwtTokenProvider.resolveBearerToken(authorization);
        if (token == null || !jwtTokenProvider.isValid(token)) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期", HttpStatus.UNAUTHORIZED);
        }
        String role = jwtTokenProvider.extractRole(token);
        if (!"admin".equals(role)) {
            throw new BusinessException("FORBIDDEN", "无权访问管理员接口", HttpStatus.FORBIDDEN);
        }
        return jwtTokenProvider.extractUserId(token);
    }
}
