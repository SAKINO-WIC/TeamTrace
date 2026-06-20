package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.group.CreateTaskGroupRequest;
import com.teamtrace.backend.dto.group.MoveMemberRequest;
import com.teamtrace.backend.dto.group.TaskGroupResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.TeacherTaskGroupService;
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
@RequestMapping("/api/teacher/classes")
public class TeacherClassGroupController {

    private final JwtTokenProvider jwtTokenProvider;
    private final TeacherTaskGroupService teacherTaskGroupService;

    public TeacherClassGroupController(JwtTokenProvider jwtTokenProvider, TeacherTaskGroupService teacherTaskGroupService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.teacherTaskGroupService = teacherTaskGroupService;
    }

    /** 学期固定小组（全任务共用），对应库表 {@code groups.task_id IS NULL} */
    @PostMapping("/{classId}/groups")
    public ApiResponse<TaskGroupResponse> createSemesterGroup(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @Valid @RequestBody CreateTaskGroupRequest request) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(teacherTaskGroupService.createSemesterGroup(teacherId, classId, request));
    }

    @GetMapping("/{classId}/groups")
    public ApiResponse<List<TaskGroupResponse>> listSemesterGroups(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(teacherTaskGroupService.listSemesterGroupsForTeacher(teacherId, classId));
    }

    /** 学期小组间移动成员（同班、task_id 均为 NULL） */
    @PutMapping("/{classId}/groups/{fromGroupId}/members/{studentId}/move")
    public ApiResponse<TaskGroupResponse> moveMemberBetweenSemesterGroups(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("fromGroupId") Long fromGroupId,
            @PathVariable("studentId") Long studentId,
            @Valid @RequestBody MoveMemberRequest request) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(
                teacherTaskGroupService.moveMemberBetweenSemesterGroups(
                        teacherId, classId, fromGroupId, studentId, request));
    }

    /** 将未分组学生直接加入目标小组（无需源小组） */
    @PostMapping("/{classId}/groups/{targetGroupId}/members/{studentId}/add")
    public ApiResponse<TaskGroupResponse> addUngroupedStudentToGroup(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("targetGroupId") Long targetGroupId,
            @PathVariable("studentId") Long studentId) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(
                teacherTaskGroupService.addStudentToSemesterGroup(teacherId, classId, targetGroupId, studentId));
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
