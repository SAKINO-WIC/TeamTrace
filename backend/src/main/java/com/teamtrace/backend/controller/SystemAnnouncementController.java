package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.announcement.UserSystemAnnouncementResponse;
import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.SystemAnnouncementService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system-announcements")
public class SystemAnnouncementController {
    private final JwtTokenProvider jwtTokenProvider;
    private final SystemAnnouncementService systemAnnouncementService;

    public SystemAnnouncementController(
            JwtTokenProvider jwtTokenProvider,
            SystemAnnouncementService systemAnnouncementService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.systemAnnouncementService = systemAnnouncementService;
    }

    @GetMapping("/pending")
    public ApiResponse<List<UserSystemAnnouncementResponse>> pending(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = requireLoggedInUserId(authorization);
        return ApiResponse.success(systemAnnouncementService.pendingForUser(userId));
    }

    @PutMapping("/{id}/ack")
    public ApiResponse<UserSystemAnnouncementResponse> acknowledge(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id,
            @RequestParam(value = "action", defaultValue = "dismiss") String action) {
        Long userId = requireLoggedInUserId(authorization);
        return ApiResponse.success(systemAnnouncementService.acknowledge(userId, id, action));
    }

    private Long requireLoggedInUserId(String authorization) {
        String token = jwtTokenProvider.resolveBearerToken(authorization);
        if (token == null || !jwtTokenProvider.isValid(token)) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期", HttpStatus.UNAUTHORIZED);
        }
        return jwtTokenProvider.extractUserId(token);
    }
}
