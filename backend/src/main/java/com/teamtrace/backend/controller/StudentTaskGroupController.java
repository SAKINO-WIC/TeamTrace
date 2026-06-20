package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.group.StudentCreateSemesterGroupRequest;
import com.teamtrace.backend.dto.group.StudentCreatedGroupResponse;
import com.teamtrace.backend.dto.group.StudentJoinGroupRequest;
import com.teamtrace.backend.dto.group.StudentJoinGroupResponse;
import com.teamtrace.backend.dto.group.TaskGroupResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.StudentSemesterGroupService;
import com.teamtrace.backend.service.TeacherTaskGroupService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/student/classes")
public class StudentTaskGroupController {

    private final JwtTokenProvider jwtTokenProvider;
    private final TeacherTaskGroupService teacherTaskGroupService;
    private final StudentSemesterGroupService studentSemesterGroupService;

    public StudentTaskGroupController(
            JwtTokenProvider jwtTokenProvider,
            TeacherTaskGroupService teacherTaskGroupService,
            StudentSemesterGroupService studentSemesterGroupService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.teacherTaskGroupService = teacherTaskGroupService;
        this.studentSemesterGroupService = studentSemesterGroupService;
    }

    /** 班级学期固定小组列表（与具体任务无关；子任务/互评等仍用路径中的 taskId 区分作业） */
    @GetMapping("/{classId}/groups")
    public ApiResponse<List<TaskGroupResponse>> listSemesterGroups(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId) {
        Long studentId = requireStudentUserId(authorization);
        return ApiResponse.success(teacherTaskGroupService.listSemesterGroupsForStudent(studentId, classId));
    }

    /**
     * 学生自建学期小组（组长为当前用户）。{@code grouping_locked=1} 时拒绝。
     * 返回小组信息与小组邀请码（仅创建时明文返回）。
     */
    @PostMapping("/{classId}/groups")
    public ApiResponse<StudentCreatedGroupResponse> createSemesterGroup(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @Valid @RequestBody StudentCreateSemesterGroupRequest request) {
        Long studentId = requireStudentUserId(authorization);
        return ApiResponse.success(studentSemesterGroupService.createSemesterGroup(studentId, classId, request));
    }

    /** 凭小组邀请码加入（直通立即入组；审批模式为 pending）。须与路径 {@code classId} 一致。 */
    @PostMapping("/{classId}/groups/join")
    public ApiResponse<StudentJoinGroupResponse> joinSemesterGroup(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @Valid @RequestBody StudentJoinGroupRequest request) {
        Long studentId = requireStudentUserId(authorization);
        return ApiResponse.success(
                studentSemesterGroupService.joinByInviteCode(studentId, classId, request.getInviteCode()));
    }

    /** 组长查看待审批的申请用户 ID 列表（仅学生自建组）。 */
    @GetMapping("/{classId}/groups/{groupId}/join-pending")
    public ApiResponse<List<Long>> listJoinPending(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("groupId") Long groupId) {
        Long studentId = requireStudentUserId(authorization);
        return ApiResponse.success(studentSemesterGroupService.listPendingApplicants(studentId, classId, groupId));
    }

    @PutMapping("/{classId}/groups/{groupId}/members/{userId}/approve")
    public ApiResponse<Void> approveJoin(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("groupId") Long groupId,
            @PathVariable("userId") Long userId) {
        Long leaderId = requireStudentUserId(authorization);
        studentSemesterGroupService.approveJoin(leaderId, classId, groupId, userId);
        return ApiResponse.success(null);
    }

    @PutMapping("/{classId}/groups/{groupId}/members/{userId}/reject")
    public ApiResponse<Void> rejectJoin(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("groupId") Long groupId,
            @PathVariable("userId") Long userId) {
        Long leaderId = requireStudentUserId(authorization);
        studentSemesterGroupService.rejectJoin(leaderId, classId, groupId, userId);
        return ApiResponse.success(null);
    }

    /** 组长刷新小组邀请码（旧码失效；仅 {@code is_teacher_created=0} 的组）。 */
    @PostMapping("/{classId}/groups/{groupId}/invite-code/refresh")
    public ApiResponse<StudentCreatedGroupResponse> refreshInviteCode(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("groupId") Long groupId) {
        Long leaderId = requireStudentUserId(authorization);
        return ApiResponse.success(studentSemesterGroupService.refreshInviteCode(leaderId, classId, groupId));
    }

    /** 组长移除活跃组员（不能移除自己）。 */
    @DeleteMapping("/{classId}/groups/{groupId}/members/{userId}")
    public ApiResponse<Void> removeMember(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("groupId") Long groupId,
            @PathVariable("userId") Long userId) {
        Long leaderId = requireStudentUserId(authorization);
        studentSemesterGroupService.removeMemberByLeader(leaderId, classId, groupId, userId);
        return ApiResponse.success(null);
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
