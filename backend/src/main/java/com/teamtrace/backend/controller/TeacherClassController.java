package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.teacher.ClassResponse;
import com.teamtrace.backend.dto.teacher.CreateClassRequest;
import com.teamtrace.backend.dto.teacher.GroupingLockRequest;
import com.teamtrace.backend.dto.teacher.TeacherClassDetailResponse;
import com.teamtrace.backend.dto.teacher.TeacherClassListItemResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.TeacherClassService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/classes")
public class TeacherClassController {

    private final JwtTokenProvider jwtTokenProvider;
    private final TeacherClassService teacherClassService;

    public TeacherClassController(JwtTokenProvider jwtTokenProvider, TeacherClassService teacherClassService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.teacherClassService = teacherClassService;
    }

    @PostMapping
    public ApiResponse<ClassResponse> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreateClassRequest request) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(teacherClassService.createClass(teacherId, request));
    }

    @GetMapping
    public ApiResponse<List<TeacherClassListItemResponse>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "status", required = false) String status) {
        Long teacherId = requireTeacherUserId(authorization);
        if ("archived".equals(status)) {
            return ApiResponse.success(teacherClassService.listArchivedClasses(teacherId));
        }
        return ApiResponse.success(teacherClassService.listMyClasses(teacherId));
    }

    @GetMapping("/{classId}")
    public ApiResponse<TeacherClassDetailResponse> detail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(teacherClassService.getClassDetail(teacherId, classId));
    }

    @GetMapping("/{classId}/students")
    public ApiResponse<Map<String, Object>> students(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(teacherClassService.getClassStudents(teacherId, classId, page, size));
    }

    @PutMapping("/{classId}/grouping-lock")
    public ApiResponse<Map<String, Object>> groupingLock(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @Valid @RequestBody GroupingLockRequest request) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(teacherClassService.setGroupingLocked(teacherId, classId, Boolean.TRUE.equals(request.getLocked())));
    }

    @DeleteMapping("/{classId}/students/{studentId}")
    public ApiResponse<Map<String, String>> removeStudent(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("studentId") Long studentId) {
        Long teacherId = requireTeacherUserId(authorization);
        teacherClassService.removeClassStudent(teacherId, classId, studentId);
        return ApiResponse.success(Map.of("message", "移除成功"));
    }

    /** 解散班级（软删除），并软删除该班下未删除任务与小组。 */
    @DeleteMapping("/{classId}")
    public ApiResponse<Map<String, Object>> dissolve(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(teacherClassService.dissolveClass(teacherId, classId));
    }

    /** 恢复已解散班级（30 天内）；恢复后需重新生成班级邀请码（见响应 needNewClassInviteCode）。 */
    @PostMapping("/{classId}/restore")
    public ApiResponse<Map<String, Object>> restore(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(teacherClassService.restoreClass(teacherId, classId));
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
