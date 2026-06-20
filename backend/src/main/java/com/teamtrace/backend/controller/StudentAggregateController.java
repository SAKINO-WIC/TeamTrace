package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.appeal.AppealResponse;
import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.teacher.TaskSummaryResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.AppealService;
import com.teamtrace.backend.service.StudentTaskService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student")
public class StudentAggregateController {

    private final JwtTokenProvider jwtTokenProvider;
    private final StudentTaskService studentTaskService;
    private final AppealService appealService;

    public StudentAggregateController(
            JwtTokenProvider jwtTokenProvider,
            StudentTaskService studentTaskService,
            AppealService appealService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.studentTaskService = studentTaskService;
        this.appealService = appealService;
    }

    @GetMapping("/tasks")
    public ApiResponse<List<TaskSummaryResponse>> listTasks(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "keyword", required = false) String keyword) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(studentTaskService.listAllTasks(userId, status, keyword));
    }

    @GetMapping("/appeals")
    public ApiResponse<List<AppealResponse>> listAppeals(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(appealService.listStudentAppeals(userId));
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
