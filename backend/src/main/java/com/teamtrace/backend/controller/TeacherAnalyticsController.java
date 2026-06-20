package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.teacher.TeacherAppealWorkspaceItem;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.AppealService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 教师跨班级聚合接口（申诉中心等）。 */
@RestController
@RequestMapping("/api/teacher/analytics")
public class TeacherAnalyticsController {

    private final JwtTokenProvider jwtTokenProvider;
    private final AppealService appealService;

    public TeacherAnalyticsController(JwtTokenProvider jwtTokenProvider, AppealService appealService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.appealService = appealService;
    }

    /** 教师申诉中心工作台：一次返回全部申诉（含班级/任务名称）。 */
    @GetMapping("/appeals-workspace")
    public ApiResponse<List<TeacherAppealWorkspaceItem>> appealsWorkspace(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long teacherId = requireTeacherUserId(authorization);
        return ApiResponse.success(appealService.listTeacherAppealsWorkspace(teacherId));
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
