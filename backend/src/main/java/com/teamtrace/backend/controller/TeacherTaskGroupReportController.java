package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.report.TaskGroupReportResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.TaskGroupReportService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/classes")
public class TeacherTaskGroupReportController {

    private final JwtTokenProvider jwtTokenProvider;
    private final TaskGroupReportService taskGroupReportService;

    public TeacherTaskGroupReportController(
            JwtTokenProvider jwtTokenProvider,
            TaskGroupReportService taskGroupReportService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.taskGroupReportService = taskGroupReportService;
    }

    @GetMapping("/{classId}/tasks/{taskId}/groups/{groupId}/group-report")
    public ApiResponse<TaskGroupReportResponse> get(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId,
            @PathVariable("groupId") Long groupId) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(taskGroupReportService.getForTeacher(teacherId, classId, taskId, groupId));
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
