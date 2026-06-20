package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.appeal.AppealResponse;
import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.teacher.ResolveAppealRequest;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.AppealService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/classes")
public class TeacherAppealController {

    private final JwtTokenProvider jwtTokenProvider;
    private final AppealService appealService;

    public TeacherAppealController(JwtTokenProvider jwtTokenProvider, AppealService appealService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.appealService = appealService;
    }

    @GetMapping("/{classId}/tasks/{taskId}/appeals")
    public ApiResponse<List<AppealResponse>> listAppeals(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(appealService.listTeacherAppealsForTask(teacherId, classId, taskId));
    }

    @PutMapping("/{classId}/tasks/{taskId}/appeals/{appealId}")
    public ApiResponse<AppealResponse> resolveAppeal(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId,
            @PathVariable("appealId") Long appealId,
            @Valid @RequestBody ResolveAppealRequest request) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(appealService.resolveAppeal(teacherId, classId, taskId, appealId, request));
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
