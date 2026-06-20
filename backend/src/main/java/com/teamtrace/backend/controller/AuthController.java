package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.auth.AuthResponse;
import com.teamtrace.backend.dto.auth.LoginRequest;
import com.teamtrace.backend.dto.auth.ResetPasswordRequest;
import com.teamtrace.backend.dto.auth.SendEmailCodeRequest;
import com.teamtrace.backend.dto.auth.StudentRegisterRequest;
import com.teamtrace.backend.dto.auth.TeacherRegisterRequest;
import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.service.AuthService;
import com.teamtrace.backend.service.VerificationCodeService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final VerificationCodeService verificationCodeService;

    public AuthController(AuthService authService, VerificationCodeService verificationCodeService) {
        this.authService = authService;
        this.verificationCodeService = verificationCodeService;
    }

    @PostMapping("/email/send-code")
    public ApiResponse<Map<String, String>> sendEmailCode(@Valid @RequestBody SendEmailCodeRequest request) {
        verificationCodeService.sendCode(request.getEmail(), request.getPurpose());
        return ApiResponse.success(Map.of("message", "验证码已发送"));
    }

    @PostMapping("/student/register")
    public ApiResponse<AuthResponse> registerStudent(@Valid @RequestBody StudentRegisterRequest request) {
        return ApiResponse.success(authService.registerStudent(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/teacher/register")
    public ApiResponse<AuthResponse> registerTeacher(@Valid @RequestBody TeacherRegisterRequest request) {
        return ApiResponse.success(authService.registerTeacher(request));
    }

    @PostMapping("/reset-password")
    public ApiResponse<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.success(Map.of("message", "密码已重置"));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(authService.refreshToken(authorization));
    }

    @PostMapping("/ceremony/seen")
    public ApiResponse<AuthResponse> markCeremonySeen(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(authService.markCeremonySeen(authorization));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, String>> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(authorization);
        return ApiResponse.success(Map.of("message", "退出成功"));
    }
}
