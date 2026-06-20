package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.student.JoinClassRequest;
import com.teamtrace.backend.dto.student.StudentClassDetailResponse;
import com.teamtrace.backend.dto.student.StudentClassListItemResponse;
import com.teamtrace.backend.dto.student.StudentClassmateResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.StudentClassService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/classes")
public class StudentClassController {

    private final JwtTokenProvider jwtTokenProvider;
    private final StudentClassService studentClassService;

    public StudentClassController(JwtTokenProvider jwtTokenProvider, StudentClassService studentClassService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.studentClassService = studentClassService;
    }

    @GetMapping
    public ApiResponse<List<StudentClassListItemResponse>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(studentClassService.listClasses(userId));
    }

    @PostMapping("/join")
    public ApiResponse<Map<String, Long>> join(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody JoinClassRequest request) {
        Long userId = requireStudentUserId(authorization);
        Long classId = studentClassService.joinClass(userId, request.getInviteCode());
        return ApiResponse.success(Map.of("classId", classId));
    }

    @GetMapping("/{classId}")
    public ApiResponse<StudentClassDetailResponse> getDetail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(studentClassService.getClassDetail(userId, classId));
    }

    @GetMapping("/{classId}/classmates")
    public ApiResponse<List<StudentClassmateResponse>> listClassmates(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId) {
        Long userId = requireStudentUserId(authorization);
        return ApiResponse.success(studentClassService.listClassmates(userId, classId));
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

