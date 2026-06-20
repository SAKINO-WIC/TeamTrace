package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.auth.AuthResponse;
import com.teamtrace.backend.dto.auth.LoginRequest;
import com.teamtrace.backend.dto.auth.ResetPasswordRequest;
import com.teamtrace.backend.dto.auth.StudentRegisterRequest;
import com.teamtrace.backend.dto.auth.TeacherRegisterRequest;
import com.teamtrace.backend.entity.TeacherInviteCode;
import com.teamtrace.backend.entity.User;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.TokenBlacklistService;
import com.teamtrace.backend.repository.TeacherInviteCodeRepository;
import com.teamtrace.backend.repository.UserRepository;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.util.AppTime;
import com.teamtrace.backend.util.EmailValidation;
import com.teamtrace.backend.util.SnowflakeIdGenerator;
import java.util.Date;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final TeacherInviteCodeRepository teacherInviteCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final SnowflakeIdGenerator idGenerator;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final VerificationCodeService verificationCodeService;
    private final CeremonyService ceremonyService;

    public AuthService(
            UserRepository userRepository,
            TeacherInviteCodeRepository teacherInviteCodeRepository,
            PasswordEncoder passwordEncoder,
            SnowflakeIdGenerator idGenerator,
            JwtTokenProvider jwtTokenProvider,
            TokenBlacklistService tokenBlacklistService,
            VerificationCodeService verificationCodeService,
            CeremonyService ceremonyService
    ) {
        this.userRepository = userRepository;
        this.teacherInviteCodeRepository = teacherInviteCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.idGenerator = idGenerator;
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenBlacklistService = tokenBlacklistService;
        this.verificationCodeService = verificationCodeService;
        this.ceremonyService = ceremonyService;
    }

    @Transactional
    public AuthResponse registerStudent(StudentRegisterRequest request) {
        String email = EmailValidation.normalize(request.getEmail());
        if (userRepository.existsByEmailAndIsDeleted(email, 0)) {
            throw new BusinessException("BUSINESS_CONFLICT", "该邮箱已注册", HttpStatus.CONFLICT);
        }

        verificationCodeService.verifyAndConsume(
                email,
                VerificationCodeService.PURPOSE_REGISTER,
                request.getVerifyCode());

        User saved = userRepository.saveAndFlush(createUserWithEmail(
                User.Role.student,
                email,
                request.getPassword(),
                request.getName()));
        saved = ceremonyService.assignCeremonyNoIfNeeded(saved);
        scheduleWelcomeEmailAfterCommit(saved.getId());
        return buildAuthResponse(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = findActiveUserByLoginId(request.getEmail())
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "邮箱或密码错误", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("UNAUTHORIZED", "邮箱或密码错误", HttpStatus.UNAUTHORIZED);
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("FORBIDDEN", "账户已被禁用", HttpStatus.FORBIDDEN);
        }

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse registerTeacher(TeacherRegisterRequest request) {
        String email = EmailValidation.normalize(request.getEmail());
        if (userRepository.existsByEmailAndIsDeleted(email, 0)) {
            throw new BusinessException("BUSINESS_CONFLICT", "该邮箱已注册", HttpStatus.CONFLICT);
        }

        verificationCodeService.verifyAndConsume(
                email,
                VerificationCodeService.PURPOSE_REGISTER,
                request.getVerifyCode());

        TeacherInviteCode inviteCode = teacherInviteCodeRepository.findByCode(request.getInviteCode())
                .orElseThrow(() -> new BusinessException("BAD_REQUEST", "教师邀请码无效", HttpStatus.BAD_REQUEST));

        if (inviteCode.getStatus() == null || inviteCode.getStatus() != 0) {
            throw new BusinessException("BAD_REQUEST", "教师邀请码已使用", HttpStatus.BAD_REQUEST);
        }
        if (inviteCode.getExpireAt() == null || inviteCode.getExpireAt().isBefore(AppTime.now())) {
            throw new BusinessException("BAD_REQUEST", "教师邀请码已过期", HttpStatus.BAD_REQUEST);
        }

        User saved = userRepository.saveAndFlush(createUserWithEmail(
                User.Role.teacher,
                email,
                request.getPassword(),
                request.getName()));
        saved = ceremonyService.assignCeremonyNoIfNeeded(saved);
        scheduleWelcomeEmailAfterCommit(saved.getId());

        inviteCode.setStatus(1);
        inviteCode.setUsedBy(saved.getId());
        inviteCode.setUsedAt(AppTime.now());
        teacherInviteCodeRepository.save(inviteCode);

        return buildAuthResponse(saved);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email = EmailValidation.normalize(request.getEmail());
        verificationCodeService.verifyAndConsume(
                email,
                VerificationCodeService.PURPOSE_RESET_PASSWORD,
                request.getVerifyCode());

        User user = userRepository.findByEmailAndIsDeleted(email, 0)
                .orElseThrow(() -> new BusinessException("BAD_REQUEST", "该邮箱尚未注册", HttpStatus.BAD_REQUEST));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse refreshToken(String authorizationHeader) {
        String token = jwtTokenProvider.resolveBearerToken(authorizationHeader);
        if (token == null) {
            throw new BusinessException("UNAUTHORIZED", "缺少Authorization头", HttpStatus.UNAUTHORIZED);
        }
        if (!jwtTokenProvider.isValid(token)) {
            throw new BusinessException("UNAUTHORIZED", "token无效或已过期", HttpStatus.UNAUTHORIZED);
        }

        Long userId = jwtTokenProvider.extractUserId(token);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "用户不存在", HttpStatus.UNAUTHORIZED));

        if (user.getIsDeleted() != null && user.getIsDeleted() == 1) {
            throw new BusinessException("UNAUTHORIZED", "用户已注销", HttpStatus.UNAUTHORIZED);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("FORBIDDEN", "账户已被禁用", HttpStatus.FORBIDDEN);
        }

        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public void logout(String authorizationHeader) {
        String token = jwtTokenProvider.resolveBearerToken(authorizationHeader);
        if (token == null) {
            throw new BusinessException("UNAUTHORIZED", "缺少Authorization头", HttpStatus.UNAUTHORIZED);
        }
        if (!jwtTokenProvider.isValid(token)) {
            throw new BusinessException("UNAUTHORIZED", "token无效或已过期", HttpStatus.UNAUTHORIZED);
        }

        Date expiration = jwtTokenProvider.extractExpiration(token);
        tokenBlacklistService.revoke(token, expiration);
    }

    @Transactional
    public AuthResponse markCeremonySeen(String authorizationHeader) {
        String token = jwtTokenProvider.resolveBearerToken(authorizationHeader);
        if (token == null || !jwtTokenProvider.isValid(token)) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期", HttpStatus.UNAUTHORIZED);
        }
        Long userId = jwtTokenProvider.extractUserId(token);
        ceremonyService.markSeen(userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "用户不存在", HttpStatus.UNAUTHORIZED));
        return buildAuthResponse(user);
    }

    private Optional<User> findActiveUserByLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            return Optional.empty();
        }
        String trimmed = loginId.trim();
        if (EmailValidation.isAllowedEmail(trimmed)) {
            return userRepository.findByEmailAndIsDeleted(EmailValidation.normalize(trimmed), 0);
        }
        if (EmailValidation.isLegacyPhoneLogin(trimmed)) {
            Optional<User> phoneUser = userRepository.findByPhoneAndIsDeleted(trimmed, 0);
            if (phoneUser.isPresent()) {
                User.Role role = phoneUser.get().getRole();
                Long uid = phoneUser.get().getId();
                if (role == User.Role.admin || uid == 32L || uid == 33L) {
                    return phoneUser;
                }
            }
            throw new BusinessException("BAD_REQUEST", "请使用邮箱登录", HttpStatus.BAD_REQUEST);
        }
        throw new BusinessException("BAD_REQUEST", EmailValidation.ALLOWED_EMAIL_MESSAGE, HttpStatus.BAD_REQUEST);
    }

    private User createUserWithEmail(User.Role role, String email, String rawPassword, String name) {
        User user = new User();
        user.setUserUuid(idGenerator.nextId());
        user.setRole(role);
        user.setEmail(email);
        user.setPhone(null);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setName(name.trim());
        user.setStatus(1);
        user.setIsDeleted(0);
        return user;
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtTokenProvider.generateToken(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().name());
        return AuthResponse.builder()
                .token(token)
                .role(user.getRole().name())
                .user(AuthResponse.UserProfile.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .studentId(user.getStudentId())
                        .avatarUrl(user.getAvatarUrl())
                        .phone(user.getPhone())
                        .build())
                .ceremony(ceremonyService.buildCeremonyInfo(user))
                .build();
    }

    private void scheduleWelcomeEmailAfterCommit(Long userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            ceremonyService.sendWelcomeEmailSafely(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ceremonyService.sendWelcomeEmailSafely(userId);
            }
        });
    }
}
