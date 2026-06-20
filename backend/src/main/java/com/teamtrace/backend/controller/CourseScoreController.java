package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.teacher.CourseGroupScoreItemResponse;
import com.teamtrace.backend.dto.teacher.CourseScoreItemResponse;
import com.teamtrace.backend.dto.teacher.SaveCourseGroupScoreRequest;
import com.teamtrace.backend.dto.teacher.SaveCourseScoreRequest;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.CourseScoreService;
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
@RequestMapping("/api/teacher/classes/{classId}/course-scores")
public class CourseScoreController {

    private final JwtTokenProvider jwtTokenProvider;
    private final CourseScoreService courseScoreService;

    public CourseScoreController(JwtTokenProvider jwtTokenProvider, CourseScoreService courseScoreService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.courseScoreService = courseScoreService;
    }

    @GetMapping
    public ApiResponse<List<CourseScoreItemResponse>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long classId) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(courseScoreService.listCourseScores(teacherId, classId));
    }

    @PostMapping
    public ApiResponse<CourseScoreItemResponse> save(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long classId,
            @Valid @RequestBody SaveCourseScoreRequest request) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(courseScoreService.saveCourseScore(teacherId, classId, request));
    }

    @GetMapping("/groups")
    public ApiResponse<List<CourseGroupScoreItemResponse>> listGroups(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long classId) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(courseScoreService.listCourseGroupScores(teacherId, classId));
    }

    @PostMapping("/groups")
    public ApiResponse<CourseGroupScoreItemResponse> saveGroup(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long classId,
            @Valid @RequestBody SaveCourseGroupScoreRequest request) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(courseScoreService.saveCourseGroupScore(teacherId, classId, request));
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
