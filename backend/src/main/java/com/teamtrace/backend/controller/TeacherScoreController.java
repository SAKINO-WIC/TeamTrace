package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.teacher.SaveTeacherScoreRequest;
import com.teamtrace.backend.dto.teacher.TeacherScoreResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.TeacherScoreService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/classes/{classId}/tasks/{taskId}/scores")
public class TeacherScoreController {

    private final JwtTokenProvider jwtTokenProvider;
    private final TeacherScoreService teacherScoreService;

    public TeacherScoreController(JwtTokenProvider jwtTokenProvider, TeacherScoreService teacherScoreService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.teacherScoreService = teacherScoreService;
    }

    @PostMapping
    public ApiResponse<TeacherScoreResponse> save(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long classId,
            @PathVariable Long taskId,
            @Valid @RequestBody SaveTeacherScoreRequest request) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(teacherScoreService.saveScore(teacherId, classId, taskId, request));
    }

    @GetMapping
    public ApiResponse<List<TeacherScoreResponse>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long classId,
            @PathVariable Long taskId) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(teacherScoreService.listScores(teacherId, classId, taskId));
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
