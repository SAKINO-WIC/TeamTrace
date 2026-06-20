package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.teacher.ClassInviteCodeResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.TeacherClassInviteCodeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/classes")
public class TeacherClassInviteCodeController {

    private final JwtTokenProvider jwtTokenProvider;
    private final TeacherClassInviteCodeService teacherClassInviteCodeService;

    public TeacherClassInviteCodeController(
            JwtTokenProvider jwtTokenProvider,
            TeacherClassInviteCodeService teacherClassInviteCodeService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.teacherClassInviteCodeService = teacherClassInviteCodeService;
    }

    @PostMapping("/{classId}/invite-codes")
    public ApiResponse<ClassInviteCodeResponse> generate(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId) {
        String token = jwtTokenProvider.resolveBearerToken(authorization);
        if (token == null || !jwtTokenProvider.isValid(token)) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期", HttpStatus.UNAUTHORIZED);
        }
        String role = jwtTokenProvider.extractRole(token);
        if (!"teacher".equals(role)) {
            throw new BusinessException("FORBIDDEN", "无权限", HttpStatus.FORBIDDEN);
        }
        Long teacherId = jwtTokenProvider.extractUserId(token);
        return ApiResponse.success(teacherClassInviteCodeService.generateOrRefresh(teacherId, classId));
    }
}

