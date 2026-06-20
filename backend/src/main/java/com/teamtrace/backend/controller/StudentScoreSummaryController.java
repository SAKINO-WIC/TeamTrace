package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.score.MemberScoreSummaryResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.ScoreSummaryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/classes")
public class StudentScoreSummaryController {

    private final JwtTokenProvider jwtTokenProvider;
    private final ScoreSummaryService scoreSummaryService;

    public StudentScoreSummaryController(JwtTokenProvider jwtTokenProvider, ScoreSummaryService scoreSummaryService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.scoreSummaryService = scoreSummaryService;
    }

    /** 当前学生在某组的加权分只读汇总（互评收到均值 + 教师分 + 加权总分） */
    @GetMapping("/{classId}/tasks/{taskId}/groups/{groupId}/score-summary")
    public ApiResponse<MemberScoreSummaryResponse> mySummary(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId,
            @PathVariable("groupId") Long groupId) {
        Long studentId = requireStudentUserId(authorization);
        return ApiResponse.success(scoreSummaryService.mySummary(studentId, classId, taskId, groupId));
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
