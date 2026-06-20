package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.subtask.CreateSubtaskRequest;
import com.teamtrace.backend.dto.subtask.SubtaskProgressResponse;
import com.teamtrace.backend.dto.subtask.SubtaskResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.SubtaskService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/classes")
public class TeacherSubtaskController {

    private final JwtTokenProvider jwtTokenProvider;
    private final SubtaskService subtaskService;

    public TeacherSubtaskController(JwtTokenProvider jwtTokenProvider, SubtaskService subtaskService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.subtaskService = subtaskService;
    }

    @GetMapping("/{classId}/tasks/{taskId}/groups/{groupId}/subtasks/progress")
    public ApiResponse<SubtaskProgressResponse> progress(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId,
            @PathVariable("groupId") Long groupId) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(subtaskService.progressForTeacher(teacherId, classId, taskId, groupId));
    }

    @GetMapping("/{classId}/tasks/{taskId}/groups/{groupId}/subtasks")
    public ApiResponse<List<SubtaskResponse>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId,
            @PathVariable("groupId") Long groupId) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(subtaskService.listForTeacher(teacherId, classId, taskId, groupId));
    }

    @PostMapping("/{classId}/tasks/{taskId}/groups/{groupId}/subtasks")
    public ApiResponse<SubtaskResponse> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId,
            @PathVariable("groupId") Long groupId,
            @Valid @RequestBody CreateSubtaskRequest request) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(subtaskService.createByTeacher(teacherId, classId, taskId, groupId, request));
    }

    /** Batch: progress for all semester groups in one call, eliminates N+1. */
    @GetMapping("/{classId}/tasks/{taskId}/groups/progress")
    public ApiResponse<Map<Long, SubtaskProgressResponse>> progressAllGroups(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(subtaskService.progressForTaskAllGroups(teacherId, classId, taskId));
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
