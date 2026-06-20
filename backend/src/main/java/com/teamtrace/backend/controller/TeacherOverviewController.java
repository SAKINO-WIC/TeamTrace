package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.AppealService;
import com.teamtrace.backend.service.DashboardService;
import com.teamtrace.backend.service.TeacherClassService;
import com.teamtrace.backend.service.TeacherTaskGroupService;
import com.teamtrace.backend.service.TeacherTaskService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher")
public class TeacherOverviewController {

    private final JwtTokenProvider jwt;
    private final TeacherClassService classSvc;
    private final TeacherTaskService taskSvc;
    private final TeacherTaskGroupService groupSvc;
    private final DashboardService dashSvc;
    private final AppealService appealSvc;

    public TeacherOverviewController(
            JwtTokenProvider jwt,
            TeacherClassService classSvc,
            TeacherTaskService taskSvc,
            TeacherTaskGroupService groupSvc,
            DashboardService dashSvc,
            AppealService appealSvc) {
        this.jwt = jwt;
        this.classSvc = classSvc;
        this.taskSvc = taskSvc;
        this.groupSvc = groupSvc;
        this.dashSvc = dashSvc;
        this.appealSvc = appealSvc;
    }

    @GetMapping("/classes/{classId}/overview")
    public ApiResponse<Map<String, Object>> classOverview(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable("classId") Long classId) {
        Long tid = requireTeacher(auth);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("detail", classSvc.getClassDetail(tid, classId));
        r.put("tasks", taskSvc.listTasks(tid, classId));
        r.put("groups", groupSvc.listSemesterGroupsForTeacher(tid, classId));
        return ApiResponse.success(r);
    }

    @GetMapping("/classes/{classId}/tasks/{taskId}/overview")
    public ApiResponse<Map<String, Object>> taskOverview(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId) {
        Long tid = requireTeacher(auth);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("task", taskSvc.getTask(tid, classId, taskId));
        r.put("groups", groupSvc.listSemesterGroupsForTeacher(tid, classId));
        r.put("dashboard", dashSvc.taskDashboard(tid, classId, taskId));
        r.put("appeals", appealSvc.listTeacherAppealsForTask(tid, classId, taskId));
        r.put("classTasks", taskSvc.listTasks(tid, classId));
        r.put("classDetail", classSvc.getClassDetail(tid, classId));
        return ApiResponse.success(r);
    }

    private Long requireTeacher(String auth) {
        String token = jwt.resolveBearerToken(auth);
        if (token == null || !jwt.isValid(token))
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期", HttpStatus.UNAUTHORIZED);
        if (!"teacher".equals(jwt.extractRole(token)))
            throw new BusinessException("FORBIDDEN", "无权限", HttpStatus.FORBIDDEN);
        return jwt.extractUserId(token);
    }
}
