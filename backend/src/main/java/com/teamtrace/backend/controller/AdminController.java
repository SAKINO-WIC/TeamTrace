package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.admin.BatchCreateTeacherInviteCodesRequest;
import com.teamtrace.backend.dto.admin.BatchCreateTeacherInviteCodesResponse;
import com.teamtrace.backend.dto.admin.BatchRevokeTeacherInviteCodesRequest;
import com.teamtrace.backend.dto.admin.BatchRevokeTeacherInviteCodesResponse;
import com.teamtrace.backend.dto.admin.ChangePasswordRequest;
import com.teamtrace.backend.dto.admin.AdminEmailSendRequest;
import com.teamtrace.backend.dto.admin.AdminEmailSendResponse;
import com.teamtrace.backend.dto.admin.CreateAdminUserRequest;
import com.teamtrace.backend.dto.admin.RevokeTeacherInviteCodesByQueryRequest;
import com.teamtrace.backend.dto.admin.ResetPasswordRequest;
import com.teamtrace.backend.dto.admin.TeacherInviteCodePageResponse;
import com.teamtrace.backend.dto.admin.UpdateAdminUserRequest;
import com.teamtrace.backend.dto.admin.UpdateAdminUserRoleRequest;
import com.teamtrace.backend.dto.admin.UpdateUserStatusRequest;
import com.teamtrace.backend.dto.admin.UserListItemResponse;
import com.teamtrace.backend.dto.admin.UserPageResponse;
import com.teamtrace.backend.dto.admin.WelcomeEmailResendResponse;
import com.teamtrace.backend.dto.admin.WelcomeEmailSelectedRequest;
import com.teamtrace.backend.dto.admin.WelcomeEmailSummaryResponse;
import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.entity.TeacherInviteCode;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.AdminService;
import com.teamtrace.backend.service.CeremonyService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final JwtTokenProvider jwtTokenProvider;
    private final AdminService adminService;
    private final CeremonyService ceremonyService;

    public AdminController(JwtTokenProvider jwtTokenProvider, AdminService adminService, CeremonyService ceremonyService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.adminService = adminService;
        this.ceremonyService = ceremonyService;
    }

    @PostMapping("/teacher-invite-codes")
    public ApiResponse<Map<String, Object>> createTeacherInviteCode(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "expireDays", required = false) Integer expireDays) {
        requireAdminUserId(authorization);
        TeacherInviteCode row = adminService.createTeacherInviteCode(expireDays);
        Map<String, Object> data = new HashMap<>();
        data.put("id", row.getId());
        data.put("code", row.getCode());
        data.put("status", row.getStatus());
        data.put("expireAt", row.getExpireAt());
        return ApiResponse.success(data);
    }

    @PostMapping("/teacher-invite-codes/batch")
    public ApiResponse<BatchCreateTeacherInviteCodesResponse> batchCreateTeacherInviteCodes(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody BatchCreateTeacherInviteCodesRequest request) {
        requireAdminUserId(authorization);
        return ApiResponse.success(adminService.batchCreateTeacherInviteCodes(request.getCount(), request.getExpireDays()));
    }

    @DeleteMapping("/teacher-invite-codes/{code}")
    public ApiResponse<Map<String, Object>> deleteTeacherInviteCode(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("code") String code) {
        requireAdminUserId(authorization);
        adminService.deleteTeacherInviteCode(code);
        Map<String, Object> data = new HashMap<>();
        data.put("code", code);
        data.put("deleted", true);
        return ApiResponse.success(data);
    }

    @PostMapping("/teacher-invite-codes/{code}/revoke")
    public ApiResponse<Map<String, Object>> revokeTeacherInviteCode(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("code") String code) {
        requireAdminUserId(authorization);
        TeacherInviteCode row = adminService.revokeTeacherInviteCode(code);
        Map<String, Object> data = new HashMap<>();
        data.put("id", row.getId());
        data.put("code", row.getCode());
        data.put("status", row.getStatus());
        return ApiResponse.success(data);
    }

    @PostMapping("/teacher-invite-codes/{code}/resume")
    public ApiResponse<Map<String, Object>> resumeTeacherInviteCode(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("code") String code) {
        requireAdminUserId(authorization);
        TeacherInviteCode row = adminService.resumeTeacherInviteCode(code);
        Map<String, Object> data = new HashMap<>();
        data.put("id", row.getId());
        data.put("code", row.getCode());
        data.put("status", row.getStatus());
        return ApiResponse.success(data);
    }

    @PostMapping("/teacher-invite-codes/revoke-batch")
    public ApiResponse<BatchRevokeTeacherInviteCodesResponse> batchRevokeTeacherInviteCodes(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody BatchRevokeTeacherInviteCodesRequest request) {
        requireAdminUserId(authorization);
        return ApiResponse.success(adminService.batchRevokeTeacherInviteCodes(request.getCodes()));
    }

    @PostMapping("/teacher-invite-codes/revoke-by-query")
    public ApiResponse<BatchRevokeTeacherInviteCodesResponse> revokeTeacherInviteCodesByQuery(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody RevokeTeacherInviteCodesByQueryRequest request) {
        requireAdminUserId(authorization);
        return ApiResponse.success(adminService.revokeTeacherInviteCodesByQuery(
                request.getStatus(),
                request.getExpired(),
                request.getExpireFrom(),
                request.getExpireTo(),
                request.getLimit()
        ));
    }

    @GetMapping("/teacher-invite-codes")
    public ApiResponse<TeacherInviteCodePageResponse> teacherInviteCodes(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "expired", required = false) Boolean expired,
            @RequestParam(value = "expireFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expireFrom,
            @RequestParam(value = "expireTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expireTo) {
        requireAdminUserId(authorization);
        return ApiResponse.success(adminService.pageTeacherInviteCodes(page, size, code, status, expired, expireFrom, expireTo));
    }

    @GetMapping("/users")
    public ApiResponse<UserPageResponse> users(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "isDeleted", required = false) Integer isDeleted) {
        requireAdminUserId(authorization);
        return ApiResponse.success(adminService.pageUsers(page, size, role, phone, email, name, status, isDeleted));
    }

    @PostMapping("/users")
    public ApiResponse<UserListItemResponse> createUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreateAdminUserRequest request) {
        requireAdminUserId(authorization);
        return ApiResponse.success(adminService.createUser(request));
    }

    @PostMapping("/emails/send")
    public ApiResponse<AdminEmailSendResponse> sendEmail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody AdminEmailSendRequest request) {
        requireAdminUserId(authorization);
        return ApiResponse.success(adminService.sendAdminEmail(request));
    }

    @GetMapping("/welcome-emails/summary")
    public ApiResponse<WelcomeEmailSummaryResponse> welcomeEmailSummary(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdminUserId(authorization);
        return ApiResponse.success(ceremonyService.welcomeEmailSummary());
    }

    @PostMapping("/welcome-emails/resend-pending")
    public ApiResponse<WelcomeEmailResendResponse> resendPendingWelcomeEmails(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAdminUserId(authorization);
        return ApiResponse.success(ceremonyService.resendPendingWelcomeEmails());
    }

    @PostMapping("/welcome-emails/resend-selected")
    public ApiResponse<WelcomeEmailResendResponse> resendSelectedWelcomeEmails(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody WelcomeEmailSelectedRequest request) {
        requireAdminUserId(authorization);
        return ApiResponse.success(ceremonyService.resendSelectedWelcomeEmails(request.getUserIds()));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<UserListItemResponse> updateUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateAdminUserRequest request) {
        requireAdminUserId(authorization);
        return ApiResponse.success(adminService.updateUser(id, request));
    }

    @PutMapping("/users/{id}/status")
    public ApiResponse<Map<String, String>> updateStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        requireAdminUserId(authorization);
        adminService.updateUserStatus(id, request);
        return ApiResponse.success(Map.of("message", "状态更新成功"));
    }

    @PutMapping("/users/{id}/role")
    public ApiResponse<UserListItemResponse> updateUserRole(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateAdminUserRoleRequest request) {
        requireAdminUserId(authorization);
        return ApiResponse.success(adminService.updateUserRole(id, request));
    }

    @PostMapping("/users/{id}/reset-password")
    public ApiResponse<Map<String, Object>> resetPassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id,
            @Valid @RequestBody(required = false) ResetPasswordRequest request) {
        requireAdminUserId(authorization);
        boolean autoGenerated = adminService.resetUserPassword(id, request);
        return ApiResponse.success(
                Map.of(
                        "message", "密码已重置",
                        "autoGenerated", autoGenerated));
    }

    @PutMapping("/me/password")
    public ApiResponse<Map<String, String>> changeSelfPassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ChangePasswordRequest request) {
        Long adminUserId = requireAdminUserId(authorization);
        adminService.changeOwnPassword(adminUserId, request);
        return ApiResponse.success(Map.of("message", "密码修改成功"));
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Map<String, Object>> deleteUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        requireAdminUserId(authorization);
        return ApiResponse.success(adminService.deleteUser(id));
    }

    @PostMapping("/users/{id}/restore")
    public ApiResponse<UserListItemResponse> restoreUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id) {
        requireAdminUserId(authorization);
        return ApiResponse.success(adminService.restoreUser(id));
    }

    private Long requireAdminUserId(String authorization) {
        String token = jwtTokenProvider.resolveBearerToken(authorization);
        if (token == null || !jwtTokenProvider.isValid(token)) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期", HttpStatus.UNAUTHORIZED);
        }
        String role = jwtTokenProvider.extractRole(token);
        if (!"admin".equals(role)) {
            throw new BusinessException("FORBIDDEN", "无权限", HttpStatus.FORBIDDEN);
        }
        return jwtTokenProvider.extractUserId(token);
    }
}
