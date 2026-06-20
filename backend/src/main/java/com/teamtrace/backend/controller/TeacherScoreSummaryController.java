package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.score.MemberScoreSummaryResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.ScoreSummaryService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/classes")
public class TeacherScoreSummaryController {

    private final JwtTokenProvider jwtTokenProvider;
    private final ScoreSummaryService scoreSummaryService;

    public TeacherScoreSummaryController(JwtTokenProvider jwtTokenProvider, ScoreSummaryService scoreSummaryService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.scoreSummaryService = scoreSummaryService;
    }

    /** 某任务小组内全部成员的加权分只读汇总（按 studentId 升序） */
    @GetMapping("/{classId}/tasks/{taskId}/groups/{groupId}/score-summaries")
    public ApiResponse<List<MemberScoreSummaryResponse>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId,
            @PathVariable("groupId") Long groupId) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(scoreSummaryService.listForTeacher(teacherId, classId, taskId, groupId));
    }

    /** Batch: score summaries for all semester groups in one call, eliminates N+1. */
    @GetMapping("/{classId}/tasks/{taskId}/score-summaries")
    public ApiResponse<Map<Long, List<MemberScoreSummaryResponse>>> listAllGroups(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(scoreSummaryService.listForTeacherAllGroups(teacherId, classId, taskId));
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
