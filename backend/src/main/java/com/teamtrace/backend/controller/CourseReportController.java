package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.report.CourseReportResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.CourseReportService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/classes")
public class CourseReportController {

    private final JwtTokenProvider jwtTokenProvider;
    private final CourseReportService courseReportService;

    public CourseReportController(JwtTokenProvider jwtTokenProvider, CourseReportService courseReportService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.courseReportService = courseReportService;
    }

    @GetMapping("/{classId}/course-report")
    public ApiResponse<CourseReportResponse> courseReport(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId) {
        Long teacherId = requireTeacher(authorization);
        return ApiResponse.success(courseReportService.generate(teacherId, classId));
    }

    private Long requireTeacher(String authorization) {
        String token = jwtTokenProvider.resolveBearerToken(authorization);
        if (token == null || !jwtTokenProvider.isValid(token)) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期", HttpStatus.UNAUTHORIZED);
        }
        return jwtTokenProvider.extractUserId(token);
    }
}
