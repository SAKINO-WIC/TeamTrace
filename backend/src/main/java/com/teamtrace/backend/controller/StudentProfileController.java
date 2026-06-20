package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.student.StudentDeleteAccountRequest;
import com.teamtrace.backend.dto.student.StudentProfileResponse;
import com.teamtrace.backend.dto.teacher.UpdateTeacherProfileRequest;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.StudentProfileService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student")
public class StudentProfileController {

    private final JwtTokenProvider jwtTokenProvider;
    private final StudentProfileService studentProfileService;

    public StudentProfileController(JwtTokenProvider jwtTokenProvider, StudentProfileService studentProfileService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.studentProfileService = studentProfileService;
    }

    @GetMapping("/profile")
    public ApiResponse<StudentProfileResponse> getProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long studentId = requireStudentUserId(authorization);
        return ApiResponse.success(studentProfileService.getProfile(studentId));
    }

    @PutMapping("/profile")
    public ApiResponse<StudentProfileResponse> updateProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody UpdateTeacherProfileRequest request) {
        Long studentId = requireStudentUserId(authorization);
        return ApiResponse.success(studentProfileService.updateProfile(studentId, request));
    }

    /** P1：学生主动注销账号（软删除），请求体须携带当前登录密码。 */
    @DeleteMapping("/account")
    public ApiResponse<Map<String, String>> deleteAccount(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody StudentDeleteAccountRequest request) {
        Long studentId = requireStudentUserId(authorization);
        return ApiResponse.success(studentProfileService.deleteAccount(studentId, request));
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
