package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.teacher.CreateTaskRequest;
import com.teamtrace.backend.dto.teacher.TaskDetailResponse;
import com.teamtrace.backend.dto.teacher.TaskSummaryResponse;
import com.teamtrace.backend.dto.teacher.UpdateTaskRequest;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.TeacherTaskService;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/classes")
public class TeacherTaskController {

    private final JwtTokenProvider jwtTokenProvider;
    private final TeacherTaskService teacherTaskService;

    public TeacherTaskController(JwtTokenProvider jwtTokenProvider, TeacherTaskService teacherTaskService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.teacherTaskService = teacherTaskService;
    }

    @PostMapping("/{classId}/tasks")
    public ApiResponse<TaskSummaryResponse> createTask(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @Valid @RequestBody CreateTaskRequest request) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(teacherTaskService.createTask(teacherId, classId, request));
    }

    @GetMapping("/{classId}/tasks")
    public ApiResponse<List<TaskSummaryResponse>> listTasks(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(teacherTaskService.listTasks(teacherId, classId));
    }

    @GetMapping("/{classId}/tasks/{taskId}")
    public ApiResponse<TaskDetailResponse> getTask(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(teacherTaskService.getTask(teacherId, classId, taskId));
    }

    @PutMapping("/{classId}/tasks/{taskId}")
    public ApiResponse<TaskDetailResponse> updateTask(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId,
            @Valid @RequestBody UpdateTaskRequest request) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(teacherTaskService.updateTask(teacherId, classId, taskId, request));
    }

    @DeleteMapping("/{classId}/tasks/{taskId}")
    public ApiResponse<Map<String, String>> deleteTask(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId) {
        Long teacherId = requireTeacherUserId(authorization);
        teacherTaskService.softDeleteTask(teacherId, classId, taskId);
        return ApiResponse.success(Map.of("message", "任务已删除"));
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
