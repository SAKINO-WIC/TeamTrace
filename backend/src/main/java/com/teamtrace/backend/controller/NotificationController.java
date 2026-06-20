package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.notification.NotificationPageResponse;
import com.teamtrace.backend.dto.notification.NotificationResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.NotificationService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 站内通知：学生与教师共用，按 JWT 用户维度隔离。 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final JwtTokenProvider jwtTokenProvider;
    private final NotificationService notificationService;

    public NotificationController(JwtTokenProvider jwtTokenProvider, NotificationService notificationService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.notificationService = notificationService;
    }

    /** 显式 path，避免部分 Spring 版本下「仅类级路径 + 无值 @GetMapping」未注册到 DispatcherServlet。 */
    @GetMapping(path = {"", "/"})
    public ApiResponse<NotificationPageResponse> page(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "isRead", required = false) Boolean isRead) {
        Long userId = requireLoggedInUserId(authorization);
        return ApiResponse.success(notificationService.pageForUser(userId, page, size, type, isRead));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markRead(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        Long userId = requireLoggedInUserId(authorization);
        return ApiResponse.success(notificationService.markRead(userId, id));
    }

    @PutMapping("/read-all")
    public ApiResponse<Map<String, Long>> markAllRead(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = requireLoggedInUserId(authorization);
        long n = notificationService.markAllRead(userId);
        return ApiResponse.success(Map.of("updated", n));
    }

    @PutMapping("/read-by-type")
    public ApiResponse<Map<String, Long>> markReadByType(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("type") String type) {
        Long userId = requireLoggedInUserId(authorization);
        long n = notificationService.markReadByType(userId, type);
        return ApiResponse.success(Map.of("updated", n));
    }

    @PutMapping("/read-by-related")
    public ApiResponse<Map<String, Long>> markReadByRelatedId(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("relatedId") Long relatedId,
            @RequestParam(value = "type", required = false) String type) {
        Long userId = requireLoggedInUserId(authorization);
        long n = notificationService.markReadByRelatedId(userId, relatedId, type);
        return ApiResponse.success(Map.of("updated", n));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = requireLoggedInUserId(authorization);
        return ApiResponse.success(Map.of("unreadCount", notificationService.countUnread(userId)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Long>> delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        Long userId = requireLoggedInUserId(authorization);
        notificationService.delete(userId, id);
        return ApiResponse.success(Map.of("deleted", 1L));
    }

    @DeleteMapping("/batch")
    public ApiResponse<Map<String, Long>> deleteBatch(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody List<Long> ids) {
        Long userId = requireLoggedInUserId(authorization);
        long n = notificationService.deleteBatch(userId, ids);
        return ApiResponse.success(Map.of("deleted", n));
    }

    private Long requireLoggedInUserId(String authorization) {
        String token = jwtTokenProvider.resolveBearerToken(authorization);
        if (token == null || !jwtTokenProvider.isValid(token)) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期", HttpStatus.UNAUTHORIZED);
        }
        return jwtTokenProvider.extractUserId(token);
    }
}
