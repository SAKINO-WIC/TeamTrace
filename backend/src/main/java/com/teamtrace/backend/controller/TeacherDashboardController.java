package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.teacher.ClassDashboardResponse;
import com.teamtrace.backend.dto.teacher.TaskDashboardResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.DashboardService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/classes")
public class TeacherDashboardController {

    private final JwtTokenProvider jwtTokenProvider;
    private final DashboardService dashboardService;

    public TeacherDashboardController(JwtTokenProvider jwtTokenProvider, DashboardService dashboardService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.dashboardService = dashboardService;
    }

    /** 班级仪表盘：在班人数、任务数、待处理申诉数 */
    @GetMapping("/{classId}/dashboard")
    public ApiResponse<ClassDashboardResponse> classDashboard(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(dashboardService.classDashboard(teacherId, classId));
    }

    /** 任务仪表盘：小组/成员、子任务状态分布、互评条数、教师评分条数、待申诉 */
    @GetMapping("/{classId}/tasks/{taskId}/dashboard")
    public ApiResponse<TaskDashboardResponse> taskDashboard(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(dashboardService.taskDashboard(teacherId, classId, taskId));
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
