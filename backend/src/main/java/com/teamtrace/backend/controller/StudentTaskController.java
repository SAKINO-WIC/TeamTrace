package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.student.StudentTaskWorkspaceResponse;
import com.teamtrace.backend.dto.teacher.TaskDetailResponse;
import com.teamtrace.backend.dto.teacher.TaskSummaryResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.StudentTaskService;
import com.teamtrace.backend.service.StudentTaskWorkspaceService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/classes")
public class StudentTaskController {

    private final JwtTokenProvider jwtTokenProvider;
    private final StudentTaskService studentTaskService;
    private final StudentTaskWorkspaceService studentTaskWorkspaceService;

    public StudentTaskController(
            JwtTokenProvider jwtTokenProvider,
            StudentTaskService studentTaskService,
            StudentTaskWorkspaceService studentTaskWorkspaceService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.studentTaskService = studentTaskService;
        this.studentTaskWorkspaceService = studentTaskWorkspaceService;
    }

    @GetMapping("/{classId}/tasks")
    public ApiResponse<List<TaskSummaryResponse>> listTasks(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "keyword", required = false) String keyword) {
        Long studentId = requireStudentUserId(authorization);
        return ApiResponse.success(studentTaskService.listClassTasks(studentId, classId, status, keyword));
    }

    @GetMapping("/{classId}/tasks/{taskId}")
    public ApiResponse<TaskDetailResponse> getTask(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId) {
        Long studentId = requireStudentUserId(authorization);
        return ApiResponse.success(studentTaskService.getClassTask(studentId, classId, taskId));
    }

    /** 任务详情工作台：合并任务/班级/小组/子任务/进度，减少前端多次跨区往返。 */
    @GetMapping("/{classId}/tasks/{taskId}/workspace")
    public ApiResponse<StudentTaskWorkspaceResponse> getTaskWorkspace(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId) {
        Long studentId = requireStudentUserId(authorization);
        return ApiResponse.success(studentTaskWorkspaceService.loadWorkspace(studentId, classId, taskId));
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
