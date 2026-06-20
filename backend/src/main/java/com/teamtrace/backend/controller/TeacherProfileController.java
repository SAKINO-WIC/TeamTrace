package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.admin.ChangePasswordRequest;
import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.teacher.TeacherProfileResponse;
import com.teamtrace.backend.dto.teacher.UpdateTeacherProfileRequest;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.TeacherProfileService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher")
public class TeacherProfileController {

    private final JwtTokenProvider jwtTokenProvider;
    private final TeacherProfileService teacherProfileService;

    public TeacherProfileController(JwtTokenProvider jwtTokenProvider, TeacherProfileService teacherProfileService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.teacherProfileService = teacherProfileService;
    }

    @GetMapping("/profile")
    public ApiResponse<TeacherProfileResponse> getProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(teacherProfileService.getProfile(teacherId));
    }

    @PutMapping("/profile")
    public ApiResponse<TeacherProfileResponse> updateProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody UpdateTeacherProfileRequest request) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(teacherProfileService.updateProfile(teacherId, request));
    }

    @PutMapping("/me/password")
    public ApiResponse<Map<String, String>> changePassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ChangePasswordRequest request) {
        Long teacherId = requireTeacherUserId(authorization);
        teacherProfileService.changePassword(teacherId, request);
        return ApiResponse.success(Map.of("message", "密码修改成功"));
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
