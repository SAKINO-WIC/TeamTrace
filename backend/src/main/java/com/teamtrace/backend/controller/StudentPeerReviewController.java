package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.peerreview.PeerReviewAnonymousItem;
import com.teamtrace.backend.dto.peerreview.SubmitPeerReviewRequest;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.PeerReviewService;
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
@RequestMapping("/api/student/classes")
public class StudentPeerReviewController {

    private final JwtTokenProvider jwtTokenProvider;
    private final PeerReviewService peerReviewService;

    public StudentPeerReviewController(JwtTokenProvider jwtTokenProvider, PeerReviewService peerReviewService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.peerReviewService = peerReviewService;
    }

    /** 提交互评（仅首次可写，不可修改） */
    @PostMapping("/{classId}/tasks/{taskId}/groups/{groupId}/peer-reviews")
    public ApiResponse<PeerReviewAnonymousItem> submit(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId,
            @PathVariable("groupId") Long groupId,
            @Valid @RequestBody SubmitPeerReviewRequest request) {
        Long studentId = requireStudentUserId(authorization);
        return ApiResponse.success(peerReviewService.submitForStudent(studentId, classId, taskId, groupId, request));
    }

    /** 组内互评列表（匿名代号，不含真实用户 ID） */
    @GetMapping("/{classId}/tasks/{taskId}/groups/{groupId}/peer-reviews")
    public ApiResponse<List<PeerReviewAnonymousItem>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("classId") Long classId,
            @PathVariable("taskId") Long taskId,
            @PathVariable("groupId") Long groupId) {
        Long studentId = requireStudentUserId(authorization);
        return ApiResponse.success(peerReviewService.listForStudent(studentId, classId, taskId, groupId));
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
