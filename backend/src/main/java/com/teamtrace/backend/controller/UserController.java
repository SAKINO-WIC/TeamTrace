package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.user.DeleteAccountRequest;
import com.teamtrace.backend.dto.user.UpdateProfileRequest;
import com.teamtrace.backend.dto.user.UserProfileResponse;
import com.teamtrace.backend.entity.User;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.TeacherInviteCodeRepository;
import com.teamtrace.backend.repository.UserRepository;
import com.teamtrace.backend.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.teamtrace.backend.service.UploadService;
import com.teamtrace.backend.service.VerificationCodeService;
import jakarta.validation.Valid;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final UploadService uploadService;
    private final TeacherInviteCodeRepository teacherInviteCodeRepository;
    private final VerificationCodeService verificationCodeService;
    private final PasswordEncoder passwordEncoder;
    private final ConcurrentHashMap<Long, List<Long>> avatarUploadHistory = new ConcurrentHashMap<>();
    private static final int AVATAR_UPLOAD_LIMIT_PER_DAY = 10;
    private static final long DAY_MS = 24 * 60 * 60 * 1000L;

    public UserController(JwtTokenProvider jwtTokenProvider, UserRepository userRepository,
                          UploadService uploadService, VerificationCodeService verificationCodeService,
                          TeacherInviteCodeRepository teacherInviteCodeRepository,
                          PasswordEncoder passwordEncoder) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.uploadService = uploadService;
        this.teacherInviteCodeRepository = teacherInviteCodeRepository;
        this.verificationCodeService = verificationCodeService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/profile")
    public ApiResponse<UserProfileResponse> getProfile(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        User user = getCurrentUser(auth);
        String inviteCode = null;
        if (user.getRole() == User.Role.teacher) {
            inviteCode = teacherInviteCodeRepository.findFirstByUsedByOrderByIdDesc(user.getId())
                    .map(com.teamtrace.backend.entity.TeacherInviteCode::getCode)
                    .orElse(null);
        }
        return ApiResponse.success(
                inviteCode != null ? UserProfileResponse.from(user, inviteCode) : UserProfileResponse.from(user));
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfileResponse> updateProfile(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @Valid @RequestBody UpdateProfileRequest request) {
        User user = getCurrentUser(auth);
        user.setName(request.getName().trim());
        user.setStudentId(request.getStudentId() != null ? request.getStudentId().trim() : null);
        userRepository.save(user);
        String inviteCode = null;
        if (user.getRole() == User.Role.teacher) {
            inviteCode = teacherInviteCodeRepository.findFirstByUsedByOrderByIdDesc(user.getId())
                    .map(com.teamtrace.backend.entity.TeacherInviteCode::getCode)
                    .orElse(null);
        }
        return ApiResponse.success(
                inviteCode != null ? UserProfileResponse.from(user, inviteCode) : UserProfileResponse.from(user));
    }

    @PostMapping("/avatar")
    public ApiResponse<Map<String, String>> uploadAvatar(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam("file") MultipartFile file) {
        User user = getCurrentUser(auth);
        // 仅允许图片格式
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.matches("(?i).*\\.(jpg|jpeg|png|gif|bmp|webp)$")) {
            throw new BusinessException("BAD_REQUEST", "仅支持图片格式 (jpg/png/gif/bmp/webp)", HttpStatus.BAD_REQUEST);
        }
        // 频率限制：每天最多10次
        long now = System.currentTimeMillis();
        List<Long> history = avatarUploadHistory.computeIfAbsent(user.getId(), k -> new ArrayList<>());
        synchronized (history) {
            history.removeIf(t -> now - t > DAY_MS);
            if (history.size() >= AVATAR_UPLOAD_LIMIT_PER_DAY) {
                throw new BusinessException("BAD_REQUEST", "今日头像上传次数已达上限（10次/天），请明天再试", HttpStatus.BAD_REQUEST);
            }
            history.add(now);
        }
        // 删除旧头像文件
        String oldUrl = user.getAvatarUrl();
        if (oldUrl != null && oldUrl.startsWith("/uploads/")) {
            try {
                Path oldFile = Paths.get("uploads", oldUrl.substring("/uploads/".length()));
                Files.deleteIfExists(oldFile);
            } catch (Exception ignored) {
            }
        }
        String url = uploadService.store(file);
        user.setAvatarUrl(url);
        userRepository.save(user);
        return ApiResponse.success(Map.of("url", url));
    }


    @PostMapping("/verify-password")
    public ApiResponse<Map<String, String>> verifyPassword(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, String> body) {
        User user = getCurrentUser(auth);
        String password = body.get("password");
        if (password == null || password.isBlank()) {
            throw new BusinessException("BAD_REQUEST", "密码不能为空", HttpStatus.BAD_REQUEST);
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException("BAD_REQUEST", "当前密码错误", HttpStatus.BAD_REQUEST);
        }
        return ApiResponse.success(Map.of("message", "密码验证通过"));
    }

    @PostMapping("/change-password")
    public ApiResponse<Map<String, String>> changePassword(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, String> body) {
        User user = getCurrentUser(auth);
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new BusinessException("BAD_REQUEST", "旧密码不能为空", HttpStatus.BAD_REQUEST);
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new BusinessException("BAD_REQUEST", "新密码不能为空", HttpStatus.BAD_REQUEST);
        }
        if (!Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d\\s]).{8,64}$").matcher(newPassword).matches()) {
            throw new BusinessException("BAD_REQUEST", "密码必须包含大写字母、小写字母、数字和特殊符号，且不少于8位", HttpStatus.BAD_REQUEST);
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("BAD_REQUEST", "当前密码错误", HttpStatus.BAD_REQUEST);
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return ApiResponse.success(Map.of("message", "密码修改成功"));
    }

    @PostMapping("/delete")
    public ApiResponse<Map<String, String>> deleteAccount(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @Valid @RequestBody DeleteAccountRequest request) {
        User user = getCurrentUser(auth);
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BusinessException("BAD_REQUEST", "未绑定邮箱，无法通过验证码注销", HttpStatus.BAD_REQUEST);
        }
        verificationCodeService.verifyAndConsume(
                user.getEmail(),
                VerificationCodeService.PURPOSE_DELETE_ACCOUNT,
                request.getVerifyCode());
        user.setIsDeleted(1);
        user.setDeletedAt(java.time.LocalDateTime.now());
        userRepository.save(user);
        return ApiResponse.success(Map.of("message", "账号已注销"));
    }

    private User getCurrentUser(String auth) {
        String token = jwtTokenProvider.resolveBearerToken(auth);
        if (token == null || !jwtTokenProvider.isValid(token)) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期", HttpStatus.UNAUTHORIZED);
        }
        Long userId = jwtTokenProvider.extractUserId(token);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "用户不存在", HttpStatus.UNAUTHORIZED));
        if (user.getIsDeleted() != null && user.getIsDeleted() == 1) {
            throw new BusinessException("UNAUTHORIZED", "用户已注销", HttpStatus.UNAUTHORIZED);
        }
        return user;
    }
}
