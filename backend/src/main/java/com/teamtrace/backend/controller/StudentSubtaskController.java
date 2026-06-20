package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.subtask.CreateSubtaskRequest;
import com.teamtrace.backend.dto.subtask.ReviewSubtaskRequest;
import com.teamtrace.backend.dto.subtask.SendBackSubtaskRequest;
import com.teamtrace.backend.dto.subtask.SubmitSubtaskRequest;
import com.teamtrace.backend.dto.subtask.SubtaskProgressResponse;
import com.teamtrace.backend.dto.subtask.SubtaskResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.SubtaskService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/classes")
public class StudentSubtaskController {

    private final JwtTokenProvider jwtTokenProvider;
    private final SubtaskService subtaskService;

    public StudentSubtaskController(JwtTokenProvider jwtTokenProvider, SubtaskService subtaskService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.subtaskService = subtaskService;
    }

    @GetMapping("/{classId}/tasks/{taskId}/groups/{groupId}/subtasks/progress")
    public ApiResponse<SubtaskProgressResponse> progress(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId,
            @PathVariable("groupId") Long groupId) {
        Long studentId = requireStudentUserId(authorization);
        return ApiResponse.success(subtaskService.progressForStudent(studentId, classId, taskId, groupId));
    }

    @GetMapping("/{classId}/tasks/{taskId}/groups/{groupId}/subtasks")
    public ApiResponse<List<SubtaskResponse>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId,
            @PathVariable("groupId") Long groupId) {
        Long studentId = requireStudentUserId(authorization);
        return ApiResponse.success(subtaskService.listForStudent(studentId, classId, taskId, groupId));
    }

    /** 组长拆解子任务 */
    @PostMapping("/{classId}/tasks/{taskId}/groups/{groupId}/subtasks")
    public ApiResponse<SubtaskResponse> createByLeader(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId,
            @PathVariable("groupId") Long groupId,
            @Valid @RequestBody CreateSubtaskRequest request) {
        Long studentId = requireStudentUserId(authorization);
        return ApiResponse.success(subtaskService.createByLeader(studentId, classId, taskId, groupId, request));
    }

    @PostMapping("/{classId}/tasks/{taskId}/groups/{groupId}/subtasks/{subtaskId}/claim")
    public ApiResponse<SubtaskResponse> claim(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId,
            @PathVariable("groupId") Long groupId,
            @PathVariable("subtaskId") Long subtaskId) {
        Long studentId = requireStudentUserId(authorization);
        return ApiResponse.success(subtaskService.claim(studentId, classId, taskId, groupId, subtaskId));
    }

    @PutMapping("/{classId}/tasks/{taskId}/groups/{groupId}/subtasks/{subtaskId}/submit")
    public ApiResponse<SubtaskResponse> submit(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId,
            @PathVariable("groupId") Long groupId,
            @PathVariable("subtaskId") Long subtaskId,
            @Valid @RequestBody SubmitSubtaskRequest request) {
        Long studentId = requireStudentUserId(authorization);
        return ApiResponse.success(subtaskService.submit(studentId, classId, taskId, groupId, subtaskId, request));
    }

    /** 组长审批待审核提交：通过 → 已完成；不通过 → 打回进行中 */
    @PutMapping("/{classId}/tasks/{taskId}/groups/{groupId}/subtasks/{subtaskId}/review")
    public ApiResponse<SubtaskResponse> review(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId,
            @PathVariable("groupId") Long groupId,
            @PathVariable("subtaskId") Long subtaskId,
            @Valid @RequestBody ReviewSubtaskRequest request) {
        Long studentId = requireStudentUserId(authorization);
        return ApiResponse.success(subtaskService.review(studentId, classId, taskId, groupId, subtaskId, request));
    }

    /** 组长将已完成打回（进度回退） */
    @PutMapping("/{classId}/tasks/{taskId}/groups/{groupId}/subtasks/{subtaskId}/send-back")
    public ApiResponse<SubtaskResponse> sendBack(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId,
            @PathVariable("groupId") Long groupId,
            @PathVariable("subtaskId") Long subtaskId,
            @Valid @RequestBody SendBackSubtaskRequest request) {
        Long studentId = requireStudentUserId(authorization);
        return ApiResponse.success(subtaskService.sendBackCompleted(studentId, classId, taskId, groupId, subtaskId, request));
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
