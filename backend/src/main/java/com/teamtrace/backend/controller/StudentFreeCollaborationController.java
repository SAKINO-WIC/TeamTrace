package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.freecollab.CollaborationActivityLogResponse;
import com.teamtrace.backend.dto.freecollab.CollaborationDashboardResponse;
import com.teamtrace.backend.dto.freecollab.CollaborationProgressResponse;
import com.teamtrace.backend.dto.freecollab.CollaborationProjectResponse;
import com.teamtrace.backend.dto.freecollab.CollaborationSpaceInviteCodeResponse;
import com.teamtrace.backend.dto.freecollab.CollaborationSpaceResponse;
import com.teamtrace.backend.dto.freecollab.CollaborationTaskResponse;
import com.teamtrace.backend.dto.freecollab.CreateCollaborationProjectRequest;
import com.teamtrace.backend.dto.freecollab.CreateCollaborationProjectWithTasksRequest;
import com.teamtrace.backend.dto.freecollab.CreateCollaborationSpaceRequest;
import com.teamtrace.backend.dto.freecollab.CreateCollaborationTaskRequest;
import com.teamtrace.backend.dto.freecollab.JoinCollaborationSpaceRequest;
import com.teamtrace.backend.dto.freecollab.ReviewCollaborationTaskRequest;
import com.teamtrace.backend.dto.freecollab.SubmitCollaborationTaskRequest;
import com.teamtrace.backend.dto.freecollab.TransferCollaborationOwnerRequest;
import com.teamtrace.backend.dto.freecollab.UpdateCollaborationProjectRequest;
import com.teamtrace.backend.dto.freecollab.UpdateCollaborationTaskRequest;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.StudentFreeCollaborationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/collaboration-spaces")
public class StudentFreeCollaborationController {

    private final JwtTokenProvider jwtTokenProvider;
    private final StudentFreeCollaborationService freeCollaborationService;

    public StudentFreeCollaborationController(
            JwtTokenProvider jwtTokenProvider,
            StudentFreeCollaborationService freeCollaborationService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.freeCollaborationService = freeCollaborationService;
    }

    @GetMapping
    public ApiResponse<List<CollaborationSpaceResponse>> listSpaces(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.listSpaces(userId));
    }

    @PostMapping
    public ApiResponse<CollaborationSpaceResponse> createSpace(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreateCollaborationSpaceRequest request) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.createSpace(userId, request));
    }

    @GetMapping("/dashboard")
    public ApiResponse<CollaborationDashboardResponse> getDashboard(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.getDashboard(userId));
    }

    @GetMapping("/{spaceId}")
    public ApiResponse<CollaborationSpaceResponse> getSpace(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("spaceId") Long spaceId) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.getSpace(userId, spaceId));
    }

    @PostMapping("/{spaceId}/invite-codes")
    public ApiResponse<CollaborationSpaceInviteCodeResponse> generateInviteCode(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("spaceId") Long spaceId) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.generateInviteCode(userId, spaceId));
    }

    @PostMapping("/{spaceId}/leave")
    public ApiResponse<CollaborationSpaceResponse> leaveSpace(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("spaceId") Long spaceId) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.leaveSpace(userId, spaceId));
    }

    @DeleteMapping("/{spaceId}/members/{studentId}")
    public ApiResponse<CollaborationSpaceResponse> removeMember(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("spaceId") Long spaceId,
            @PathVariable("studentId") Long studentId) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.removeMember(userId, spaceId, studentId));
    }

    @PostMapping("/{spaceId}/transfer-owner")
    public ApiResponse<CollaborationSpaceResponse> transferOwner(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("spaceId") Long spaceId,
            @Valid @RequestBody TransferCollaborationOwnerRequest request) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.transferOwner(userId, spaceId, request.getNewOwnerId()));
    }

    @PostMapping("/join")
    public ApiResponse<CollaborationSpaceResponse> joinSpace(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody JoinCollaborationSpaceRequest request) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.joinSpace(userId, request.getInviteCode()));
    }

    @GetMapping("/{spaceId}/projects")
    public ApiResponse<List<CollaborationProjectResponse>> listProjects(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("spaceId") Long spaceId) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.listProjects(userId, spaceId));
    }

    @PostMapping("/{spaceId}/projects")
    public ApiResponse<CollaborationProjectResponse> createProject(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("spaceId") Long spaceId,
            @Valid @RequestBody CreateCollaborationProjectRequest request) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.createProject(userId, spaceId, request));
    }

    @PostMapping("/{spaceId}/projects/with-tasks")
    public ApiResponse<CollaborationProjectResponse> createProjectWithTasks(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("spaceId") Long spaceId,
            @Valid @RequestBody CreateCollaborationProjectWithTasksRequest request) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.createProjectWithTasks(userId, spaceId, request));
    }

    @GetMapping("/{spaceId}/projects/{projectId}")
    public ApiResponse<CollaborationProjectResponse> getProject(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("spaceId") Long spaceId,
            @PathVariable("projectId") Long projectId) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.getProject(userId, spaceId, projectId));
    }

    @PatchMapping("/{spaceId}/projects/{projectId}")
    public ApiResponse<CollaborationProjectResponse> updateProject(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("spaceId") Long spaceId,
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody UpdateCollaborationProjectRequest request) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.updateProject(userId, spaceId, projectId, request));
    }

    @PostMapping("/{spaceId}/projects/{projectId}/archive")
    public ApiResponse<CollaborationProjectResponse> archiveProject(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("spaceId") Long spaceId,
            @PathVariable("projectId") Long projectId) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.archiveProject(userId, spaceId, projectId));
    }

    @GetMapping("/{spaceId}/projects/{projectId}/tasks")
    public ApiResponse<List<CollaborationTaskResponse>> listTasks(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("spaceId") Long spaceId,
            @PathVariable("projectId") Long projectId) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.listTasks(userId, spaceId, projectId));
    }

    @PostMapping("/{spaceId}/projects/{projectId}/tasks")
    public ApiResponse<CollaborationTaskResponse> createTask(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("spaceId") Long spaceId,
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody CreateCollaborationTaskRequest request) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.createTask(userId, spaceId, projectId, request));
    }

    @PatchMapping("/{spaceId}/projects/{projectId}/tasks/{taskId}")
    public ApiResponse<CollaborationTaskResponse> updateTask(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("spaceId") Long spaceId,
            @PathVariable("projectId") Long projectId,
            @PathVariable("taskId") Long taskId,
            @Valid @RequestBody UpdateCollaborationTaskRequest request) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.updateTask(userId, spaceId, projectId, taskId, request));
    }

    @PostMapping("/{spaceId}/projects/{projectId}/tasks/{taskId}/archive")
    public ApiResponse<CollaborationTaskResponse> archiveTask(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("spaceId") Long spaceId,
            @PathVariable("projectId") Long projectId,
            @PathVariable("taskId") Long taskId) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.archiveTask(userId, spaceId, projectId, taskId));
    }

    @PostMapping("/{spaceId}/projects/{projectId}/tasks/{taskId}/claim")
    public ApiResponse<CollaborationTaskResponse> claimTask(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("spaceId") Long spaceId,
            @PathVariable("projectId") Long projectId,
            @PathVariable("taskId") Long taskId) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.claimTask(userId, spaceId, projectId, taskId));
    }

    @PostMapping("/{spaceId}/projects/{projectId}/tasks/{taskId}/flow-nodes/{nodeId}/claim")
    public ApiResponse<CollaborationTaskResponse> claimFlowNode(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("spaceId") Long spaceId,
            @PathVariable("projectId") Long projectId,
            @PathVariable("taskId") Long taskId,
            @PathVariable("nodeId") Long nodeId) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.claimFlowNode(userId, spaceId, projectId, taskId, nodeId));
    }

    @PostMapping("/{spaceId}/projects/{projectId}/tasks/{taskId}/submit")
    public ApiResponse<CollaborationTaskResponse> submitTask(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("spaceId") Long spaceId,
            @PathVariable("projectId") Long projectId,
            @PathVariable("taskId") Long taskId,
            @Valid @RequestBody SubmitCollaborationTaskRequest request) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.submitTask(userId, spaceId, projectId, taskId, request));
    }

    @PostMapping("/{spaceId}/projects/{projectId}/tasks/{taskId}/accept")
    public ApiResponse<CollaborationTaskResponse> acceptTask(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("spaceId") Long spaceId,
            @PathVariable("projectId") Long projectId,
            @PathVariable("taskId") Long taskId,
            @Valid @RequestBody ReviewCollaborationTaskRequest request) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.acceptTask(userId, spaceId, projectId, taskId, request));
    }

    @PostMapping("/{spaceId}/projects/{projectId}/tasks/{taskId}/return")
    public ApiResponse<CollaborationTaskResponse> returnTask(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("spaceId") Long spaceId,
            @PathVariable("projectId") Long projectId,
            @PathVariable("taskId") Long taskId,
            @Valid @RequestBody ReviewCollaborationTaskRequest request) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.returnTask(userId, spaceId, projectId, taskId, request));
    }

    @GetMapping("/{spaceId}/projects/{projectId}/progress")
    public ApiResponse<CollaborationProgressResponse> getProgress(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("spaceId") Long spaceId,
            @PathVariable("projectId") Long projectId) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.getProgress(userId, spaceId, projectId));
    }

    @GetMapping("/{spaceId}/activity-logs")
    public ApiResponse<List<CollaborationActivityLogResponse>> listActivityLogs(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("spaceId") Long spaceId,
            @RequestParam(value = "projectId", required = false) Long projectId) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(freeCollaborationService.listActivityLogs(userId, spaceId, projectId));
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
