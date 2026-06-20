package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.appeal.AppealResponse;
import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.student.CreateStudentAppealRequest;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.AppealService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/tasks")
public class StudentAppealController {

    private final JwtTokenProvider jwtTokenProvider;
    private final AppealService appealService;

    public StudentAppealController(JwtTokenProvider jwtTokenProvider, AppealService appealService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.appealService = appealService;
    }

    /** 申诉类型见 {@link com.teamtrace.backend.dto.student.CreateStudentAppealRequest#getType()} */
    @PostMapping("/{taskId}/appeals")
    public ApiResponse<AppealResponse> createAppeal(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("taskId") Long taskId,
            @Valid @RequestBody CreateStudentAppealRequest request) {
        Long studentId = requireStudentUserId(authorization);
        return ApiResponse.success(appealService.createStudentAppeal(studentId, taskId, request));
    }

    @GetMapping("/{taskId}/appeals")
    public ApiResponse<List<AppealResponse>> listMyAppeals(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("taskId") Long taskId) {
        Long studentId = requireStudentUserId(authorization);
        return ApiResponse.success(appealService.listStudentAppealsForTask(studentId, taskId));
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
