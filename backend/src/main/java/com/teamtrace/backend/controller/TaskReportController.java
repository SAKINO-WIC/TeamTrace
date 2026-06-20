package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.report.TaskReportResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.TaskReportService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/classes")
public class TaskReportController {

    private final JwtTokenProvider jwtTokenProvider;
    private final TaskReportService taskReportService;

    public TaskReportController(JwtTokenProvider jwtTokenProvider, TaskReportService taskReportService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.taskReportService = taskReportService;
    }

    @GetMapping("/{classId}/tasks/{taskId}/report")
    public ApiResponse<TaskReportResponse> taskReport(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId) {
        Long teacherId = requireTeacher(authorization);
        return ApiResponse.success(taskReportService.generate(teacherId, classId, taskId));
    }

    private Long requireTeacher(String authorization) {
        String token = jwtTokenProvider.resolveBearerToken(authorization);
        if (token == null || !jwtTokenProvider.isValid(token)) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期", HttpStatus.UNAUTHORIZED);
        }
        return jwtTokenProvider.extractUserId(token);
    }
}
