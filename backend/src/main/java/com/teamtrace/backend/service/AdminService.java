package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.admin.BatchCreateTeacherInviteCodesResponse;
import com.teamtrace.backend.dto.admin.BatchRevokeTeacherInviteCodesResponse;
import com.teamtrace.backend.dto.admin.ChangePasswordRequest;
import com.teamtrace.backend.dto.admin.AdminEmailSendRequest;
import com.teamtrace.backend.dto.admin.AdminEmailSendResponse;
import com.teamtrace.backend.dto.admin.CreateAdminUserRequest;
import com.teamtrace.backend.dto.admin.ResetPasswordRequest;
import com.teamtrace.backend.dto.admin.TeacherInviteCodeBatchFailureItem;
import com.teamtrace.backend.dto.admin.TeacherInviteCodeListItemResponse;
import com.teamtrace.backend.dto.admin.TeacherInviteCodePageResponse;
import com.teamtrace.backend.dto.admin.UpdateAdminUserRequest;
import com.teamtrace.backend.dto.admin.UpdateAdminUserRoleRequest;
import com.teamtrace.backend.dto.admin.UpdateUserStatusRequest;
import com.teamtrace.backend.dto.admin.UserListItemResponse;
import com.teamtrace.backend.dto.admin.UserPageResponse;
import com.teamtrace.backend.entity.TeacherInviteCode;
import com.teamtrace.backend.entity.User;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.TeacherInviteCodeRepository;
import com.teamtrace.backend.repository.UserRepository;
import com.teamtrace.backend.util.AppTime;
import com.teamtrace.backend.util.EmailValidation;
import com.teamtrace.backend.util.SnowflakeIdGenerator;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 10;

    private static final int TEACHER_INVITE_UNUSED = 0;
    private static final int TEACHER_INVITE_USED = 1;
    private static final int TEACHER_INVITE_REVOKED = 2;
    private static final int TEACHER_INVITE_EXPIRE_DAYS_DEFAULT = 30;
    private static final int TEACHER_INVITE_BATCH_MAX = 200;
    private static final int ADMIN_EMAIL_MAX_RECIPIENTS = 50;
    private static final int ADMIN_EMAIL_MAX_FAILURES = 20;

    private final UserRepository userRepository;
    private final TeacherInviteCodeRepository teacherInviteCodeRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final SnowflakeIdGenerator idGenerator;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String defaultResetPassword;

    public AdminService(
            UserRepository userRepository,
            TeacherInviteCodeRepository teacherInviteCodeRepository,
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            SnowflakeIdGenerator idGenerator,
            @Value("${ADMIN_RESET_DEFAULT_PASSWORD:}") String defaultResetPassword) {
        this.userRepository = userRepository;
        this.teacherInviteCodeRepository = teacherInviteCodeRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.idGenerator = idGenerator;
        this.defaultResetPassword = defaultResetPassword == null ? "" : defaultResetPassword.trim();
    }

    @Transactional
    public TeacherInviteCode createTeacherInviteCode(Integer expireDays) {
        int days = expireDays == null ? TEACHER_INVITE_EXPIRE_DAYS_DEFAULT : Math.max(1, Math.min(expireDays, 365));
        LocalDateTime expireAt = AppTime.now().plusDays(days);

        int maxAttempts = 8;
        for (int i = 0; i < maxAttempts; i++) {
            TeacherInviteCode row = new TeacherInviteCode();
            row.setCode(generateCode(12));
            row.setStatus(TEACHER_INVITE_UNUSED);
            row.setExpireAt(expireAt);
            try {
                return teacherInviteCodeRepository.save(row);
            } catch (DataIntegrityViolationException ex) {
                // unique conflict, retry
            }
        }
        throw new BusinessException("INTERNAL_ERROR", "生成教师邀请码失败，请重试", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Transactional
    public BatchCreateTeacherInviteCodesResponse batchCreateTeacherInviteCodes(Integer count, Integer expireDays) {
        int c = count == null ? 0 : count;
        if (c <= 0 || c > TEACHER_INVITE_BATCH_MAX) {
            throw new BusinessException("BAD_REQUEST", "count参数无效", HttpStatus.BAD_REQUEST);
        }

        List<TeacherInviteCodeListItemResponse> ok = new ArrayList<>();
        List<TeacherInviteCodeBatchFailureItem> failed = new ArrayList<>();
        for (int i = 0; i < c; i++) {
            try {
                TeacherInviteCode row = createTeacherInviteCode(expireDays);
                ok.add(TeacherInviteCodeListItemResponse.builder()
                        .id(row.getId())
                        .code(row.getCode())
                        .status(row.getStatus())
                        .expireAt(row.getExpireAt())
                        .usedBy(row.getUsedBy())
                        .usedAt(row.getUsedAt())
                        .build());
            } catch (BusinessException ex) {
                failed.add(TeacherInviteCodeBatchFailureItem.builder()
                        .code(null)
                        .errorCode(ex.getCode())
                        .message(ex.getMessage())
                        .build());
            } catch (RuntimeException ex) {
                failed.add(TeacherInviteCodeBatchFailureItem.builder()
                        .code(null)
                        .errorCode("INTERNAL_ERROR")
                        .message("生成失败")
                        .build());
            }
        }

        return BatchCreateTeacherInviteCodesResponse.builder()
                .succeeded(ok)
                .failed(failed)
                .build();
    }

    @Transactional
    public TeacherInviteCode revokeTeacherInviteCode(String code) {
        TeacherInviteCode row = requireTeacherInviteCode(code);
        if (row.getStatus() == TEACHER_INVITE_REVOKED) {
            return row;
        }
        row.setStatus(TEACHER_INVITE_REVOKED);
        return teacherInviteCodeRepository.save(row);
    }

    @Transactional
    public TeacherInviteCode resumeTeacherInviteCode(String code) {
        TeacherInviteCode row = requireTeacherInviteCode(code);
        if (row.getStatus() == TEACHER_INVITE_UNUSED) {
            return row;
        }
        row.setStatus(TEACHER_INVITE_UNUSED);
        row.setUsedBy(null);
        row.setUsedAt(null);
        return teacherInviteCodeRepository.save(row);
    }

    @Transactional
    public void deleteTeacherInviteCode(String code) {
        TeacherInviteCode row = requireTeacherInviteCode(code);
        teacherInviteCodeRepository.delete(row);
    }

    @Transactional
    public BatchRevokeTeacherInviteCodesResponse batchRevokeTeacherInviteCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            throw new BusinessException("BAD_REQUEST", "codes参数无效", HttpStatus.BAD_REQUEST);
        }
        if (codes.size() > 1000) {
            throw new BusinessException("BAD_REQUEST", "codes数量过多", HttpStatus.BAD_REQUEST);
        }

        Set<String> uniq = new LinkedHashSet<>();
        for (String c : codes) {
            if (c == null) {
                continue;
            }
            String t = c.trim();
            if (!t.isBlank()) {
                uniq.add(t);
            }
        }
        if (uniq.isEmpty()) {
            throw new BusinessException("BAD_REQUEST", "codes参数无效", HttpStatus.BAD_REQUEST);
        }

        List<String> ok = new ArrayList<>();
        List<TeacherInviteCodeBatchFailureItem> failed = new ArrayList<>();
        for (String code : uniq) {
            try {
                revokeTeacherInviteCode(code);
                ok.add(code);
            } catch (BusinessException ex) {
                failed.add(TeacherInviteCodeBatchFailureItem.builder()
                        .code(code)
                        .errorCode(ex.getCode())
                        .message(ex.getMessage())
                        .build());
            } catch (RuntimeException ex) {
                failed.add(TeacherInviteCodeBatchFailureItem.builder()
                        .code(code)
                        .errorCode("INTERNAL_ERROR")
                        .message("撤销失败")
                        .build());
            }
        }

        return BatchRevokeTeacherInviteCodesResponse.builder()
                .succeeded(ok)
                .failed(failed)
                .build();
    }

    @Transactional
    public BatchRevokeTeacherInviteCodesResponse revokeTeacherInviteCodesByQuery(
            Integer status,
            Boolean expired,
            LocalDateTime expireFrom,
            LocalDateTime expireTo,
            Integer limit
    ) {
        Integer st = status;
        if (st != null && st != TEACHER_INVITE_UNUSED && st != TEACHER_INVITE_USED && st != TEACHER_INVITE_REVOKED) {
            throw new BusinessException("BAD_REQUEST", "status参数无效", HttpStatus.BAD_REQUEST);
        }
        if (expireFrom != null && expireTo != null && expireFrom.isAfter(expireTo)) {
            throw new BusinessException("BAD_REQUEST", "expireFrom/expireTo范围无效", HttpStatus.BAD_REQUEST);
        }
        int lim = (limit == null ? TEACHER_INVITE_BATCH_MAX : limit);
        if (lim <= 0 || lim > TEACHER_INVITE_BATCH_MAX) {
            throw new BusinessException("BAD_REQUEST", "limit参数无效", HttpStatus.BAD_REQUEST);
        }

        LocalDateTime now = AppTime.now();
        PageRequest pageable = PageRequest.of(0, lim, Sort.by(Sort.Direction.DESC, "id"));
        List<TeacherInviteCode> rows = teacherInviteCodeRepository
                .search(null, st, expired, now, expireFrom, expireTo, pageable)
                .getContent();

        if (rows.isEmpty()) {
            return BatchRevokeTeacherInviteCodesResponse.builder()
                    .succeeded(List.of())
                    .failed(List.of())
                    .build();
        }

        List<String> ok = new ArrayList<>();
        List<TeacherInviteCodeBatchFailureItem> failed = new ArrayList<>();
        for (TeacherInviteCode row : rows) {
            String code = row.getCode();
            if (code == null || code.isBlank()) {
                continue;
            }
            try {
                revokeTeacherInviteCode(code);
                ok.add(code);
            } catch (BusinessException ex) {
                failed.add(TeacherInviteCodeBatchFailureItem.builder()
                        .code(code)
                        .errorCode(ex.getCode())
                        .message(ex.getMessage())
                        .build());
            } catch (RuntimeException ex) {
                failed.add(TeacherInviteCodeBatchFailureItem.builder()
                        .code(code)
                        .errorCode("INTERNAL_ERROR")
                        .message("撤销失败")
                        .build());
            }
        }

        return BatchRevokeTeacherInviteCodesResponse.builder()
                .succeeded(ok)
                .failed(failed)
                .build();
    }

    @Transactional(readOnly = true)
    public TeacherInviteCodePageResponse pageTeacherInviteCodes(
            int page,
            int size,
            String code,
            Integer status,
            Boolean expired,
            LocalDateTime expireFrom,
            LocalDateTime expireTo
    ) {
        int p = Math.max(1, page);
        int s = Math.min(MAX_PAGE_SIZE, Math.max(1, size <= 0 ? DEFAULT_PAGE_SIZE : size));
        PageRequest pageable = PageRequest.of(p - 1, s, Sort.by(Sort.Direction.DESC, "id"));

        String c = (code == null || code.isBlank()) ? null : code.trim();
        Integer st = status;
        if (st != null && st != TEACHER_INVITE_UNUSED && st != TEACHER_INVITE_USED && st != TEACHER_INVITE_REVOKED) {
            throw new BusinessException("BAD_REQUEST", "status参数无效", HttpStatus.BAD_REQUEST);
        }
        if (expireFrom != null && expireTo != null && expireFrom.isAfter(expireTo)) {
            throw new BusinessException("BAD_REQUEST", "expireFrom/expireTo范围无效", HttpStatus.BAD_REQUEST);
        }

        LocalDateTime now = AppTime.now();
        Page<TeacherInviteCode> pg = teacherInviteCodeRepository.search(c, st, expired, now, expireFrom, expireTo, pageable);

        Set<Long> usedByIds = pg.getContent().stream()
                .map(TeacherInviteCode::getUsedBy)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, User> usedByUsers = usedByIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(usedByIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<TeacherInviteCodeListItemResponse> list = pg.getContent().stream()
                .map(row -> TeacherInviteCodeListItemResponse.builder()
                        .id(row.getId())
                        .code(row.getCode())
                        .status(row.getStatus())
                        .expireAt(row.getExpireAt())
                        .usedBy(row.getUsedBy())
                        .usedAt(row.getUsedAt())
                        .usedByUser(toUsedByUserItem(
                                row.getUsedBy() == null ? null : usedByUsers.get(row.getUsedBy())))
                        .build())
                .toList();

        return TeacherInviteCodePageResponse.builder()
                .list(list)
                .page(p)
                .size(s)
                .total(pg.getTotalElements())
                .pages(pg.getTotalPages())
                .hasNext(pg.hasNext())
                .build();
    }

    private TeacherInviteCodeListItemResponse.UsedByUserItem toUsedByUserItem(User user) {
        if (user == null) {
            return null;
        }
        return TeacherInviteCodeListItemResponse.UsedByUserItem.builder()
                .id(user.getId())
                .name(user.getName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .build();
    }

    @Transactional(readOnly = true)
    public UserPageResponse pageUsers(int page, int size, String role, String phone, String email, String name, Integer status, Integer isDeleted) {
        int p = Math.max(1, page);
        int s = Math.min(MAX_PAGE_SIZE, Math.max(1, size <= 0 ? DEFAULT_PAGE_SIZE : size));
        PageRequest pageable = PageRequest.of(p - 1, s, Sort.by(Sort.Direction.DESC, "id"));

        User.Role roleFilter = null;
        if (role != null && !role.isBlank()) {
            try {
                roleFilter = User.Role.valueOf(role.trim());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException("BAD_REQUEST", "role参数无效", HttpStatus.BAD_REQUEST);
            }
        }
        if (status != null && status != 0 && status != 1) {
            throw new BusinessException("BAD_REQUEST", "status参数无效", HttpStatus.BAD_REQUEST);
        }
        if (isDeleted != null && isDeleted != 0 && isDeleted != 1) {
            throw new BusinessException("BAD_REQUEST", "isDeleted参数无效", HttpStatus.BAD_REQUEST);
        }

        String phoneFilter = (phone == null || phone.isBlank()) ? null : phone.trim();
        String emailFilter = (email == null || email.isBlank()) ? null : email.trim();
        String nameFilter = (name == null || name.isBlank()) ? null : name.trim();
        Integer deletedFilter = isDeleted == null ? 0 : isDeleted;

        Page<User> pg = userRepository.searchUsers(
                roleFilter,
                status,
                deletedFilter,
                phoneFilter,
                emailFilter,
                nameFilter,
                pageable);

        List<UserListItemResponse> list = pg.getContent().stream()
                .map(this::toUserListItem)
                .toList();

        return UserPageResponse.builder()
                .list(list)
                .page(p)
                .size(s)
                .total(pg.getTotalElements())
                .pages(pg.getTotalPages())
                .hasNext(pg.hasNext())
                .build();
    }

    public AdminEmailSendResponse sendAdminEmail(AdminEmailSendRequest request) {
        String scope = normalizeEmailScope(request.getRecipientScope());
        String subject = request.getSubject().trim();
        String body = request.getBody().trim();
        List<String> recipients = resolveAdminEmailRecipients(scope, request.getManualEmails());

        if (recipients.isEmpty()) {
            throw new BusinessException("BAD_REQUEST", "没有可发送的收件人", HttpStatus.BAD_REQUEST);
        }
        if (recipients.size() > ADMIN_EMAIL_MAX_RECIPIENTS) {
            throw new BusinessException(
                    "BAD_REQUEST",
                    "单次最多发送" + ADMIN_EMAIL_MAX_RECIPIENTS + "封，请缩小收件范围",
                    HttpStatus.BAD_REQUEST);
        }

        String finalBody = buildAdminEmailBody(body);
        List<AdminEmailSendResponse.FailureItem> failures = new ArrayList<>();
        int sentCount = 0;
        for (String email : recipients) {
            try {
                emailService.sendPlainText(email, subject, finalBody, "admin_email");
                sentCount++;
            } catch (RuntimeException ex) {
                if (failures.size() < ADMIN_EMAIL_MAX_FAILURES) {
                    failures.add(AdminEmailSendResponse.FailureItem.builder()
                            .email(email)
                            .reason("发送失败")
                            .build());
                }
            }
        }

        return AdminEmailSendResponse.builder()
                .recipientScope(scope)
                .subject(subject)
                .requestedCount(recipients.size())
                .sentCount(sentCount)
                .failedCount(recipients.size() - sentCount)
                .skippedCount(0)
                .failures(failures)
                .build();
    }

    @Transactional
    public void updateUserStatus(Long userId, UpdateUserStatusRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND));
        if (user.getIsDeleted() != null && user.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND);
        }
        if (user.getRole() == User.Role.admin) {
            throw new BusinessException("FORBIDDEN", "不允许禁用管理员账户", HttpStatus.FORBIDDEN);
        }
        user.setStatus(request.getStatus());
        userRepository.save(user);
    }

    @Transactional
    public UserListItemResponse createUser(CreateAdminUserRequest request) {
        User.Role role = parseManagedRole(request.getRole());
        String email = EmailValidation.normalize(request.getEmail());
        String phone = normalizePhone(request.getPhone());
        ensureEmailAvailable(email, null);
        ensurePhoneAvailable(phone, null);

        User user = new User();
        user.setUserUuid(idGenerator.nextId());
        user.setRole(role);
        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        user.setIsDeleted(0);
        user.setDeletedAt(null);
        return toUserListItem(userRepository.save(user));
    }

    @Transactional
    public UserListItemResponse updateUser(Long userId, UpdateAdminUserRequest request) {
        User user = requireManagedUser(userId, false);
        String email = EmailValidation.normalize(request.getEmail());
        String phone = normalizePhone(request.getPhone());
        ensureEmailAvailable(email, user.getId());
        ensurePhoneAvailable(phone, user.getId());

        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setPhone(phone);
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        return toUserListItem(userRepository.save(user));
    }

    @Transactional
    public UserListItemResponse updateUserRole(Long userId, UpdateAdminUserRoleRequest request) {
        User user = requireManagedUser(userId, false);
        User.Role targetRole = parseManagedRole(request.getRole());
        User.Role currentRole = user.getRole();
        if (currentRole == targetRole) {
            return toUserListItem(user);
        }

        if (currentRole == User.Role.student && targetRole == User.Role.teacher) {
            TeacherInviteCode inviteCode = consumeTeacherInviteCodeForAdminRoleChange(request.getInviteCode(), user.getId());
            user.setRole(User.Role.teacher);
            userRepository.save(user);
            teacherInviteCodeRepository.save(inviteCode);
            return toUserListItem(user);
        }

        if (currentRole == User.Role.teacher && targetRole == User.Role.student) {
            deleteTeacherInviteCodesBoundToUser(user.getId());
            user.setRole(User.Role.student);
            return toUserListItem(userRepository.save(user));
        }

        throw new BusinessException("BAD_REQUEST", "不支持的角色变更", HttpStatus.BAD_REQUEST);
    }

    @Transactional
    public boolean resetUserPassword(Long userId, ResetPasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND));
        if (user.getIsDeleted() != null && user.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND);
        }
        boolean autoGenerated =
                request == null || request.getNewPassword() == null || request.getNewPassword().isBlank();
        String pwd = autoGenerated ? getDefaultResetPassword() : request.getNewPassword().trim();
        user.setPassword(passwordEncoder.encode(pwd));
        userRepository.save(user);
        return autoGenerated;
    }

    @Transactional
    public void changeOwnPassword(Long adminUserId, ChangePasswordRequest request) {
        User user = userRepository.findById(adminUserId)
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "用户不存在", HttpStatus.UNAUTHORIZED));
        if (user.getIsDeleted() != null && user.getIsDeleted() == 1) {
            throw new BusinessException("UNAUTHORIZED", "用户不存在", HttpStatus.UNAUTHORIZED);
        }
        if (user.getRole() != User.Role.admin) {
            throw new BusinessException("FORBIDDEN", "无权限", HttpStatus.FORBIDDEN);
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("BAD_REQUEST", "旧密码不正确", HttpStatus.BAD_REQUEST);
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public Map<String, Object> deleteUser(Long userId) {
        User user = requireManagedUser(userId, false);
        user.setIsDeleted(1);
        user.setDeletedAt(AppTime.now());
        user.setStatus(0);
        userRepository.save(user);

        List<TeacherInviteCode> removedInvites = deleteTeacherInviteCodesBoundToUser(userId);

        Map<String, Object> data = new HashMap<>();
        data.put("message", "账户已删除");
        data.put("role", user.getRole() == null ? null : user.getRole().name());
        data.put("removedInviteCodes", removedInvites.stream().map(TeacherInviteCode::getCode).toList());
        return data;
    }

    @Transactional
    public UserListItemResponse restoreUser(Long userId) {
        User user = requireManagedUser(userId, true);
        if (user.getIsDeleted() == null || user.getIsDeleted() == 0) {
            throw new BusinessException("BAD_REQUEST", "账户未删除，无需恢复", HttpStatus.BAD_REQUEST);
        }
        user.setIsDeleted(0);
        user.setDeletedAt(null);
        user.setStatus(0);
        return toUserListItem(userRepository.save(user));
    }

    private TeacherInviteCode consumeTeacherInviteCodeForAdminRoleChange(String code, Long userId) {
        String normalized = code == null ? "" : code.trim();
        if (normalized.isBlank()) {
            throw new BusinessException("BAD_REQUEST", "学生改为教师时必须填写教师邀请码", HttpStatus.BAD_REQUEST);
        }
        TeacherInviteCode inviteCode = requireTeacherInviteCode(normalized);
        if (inviteCode.getStatus() == null || inviteCode.getStatus() != TEACHER_INVITE_UNUSED) {
            throw new BusinessException("BAD_REQUEST", "教师邀请码不可用", HttpStatus.BAD_REQUEST);
        }
        if (inviteCode.getExpireAt() == null || inviteCode.getExpireAt().isBefore(AppTime.now())) {
            throw new BusinessException("BAD_REQUEST", "教师邀请码已过期", HttpStatus.BAD_REQUEST);
        }
        inviteCode.setStatus(TEACHER_INVITE_USED);
        inviteCode.setUsedBy(userId);
        inviteCode.setUsedAt(AppTime.now());
        return inviteCode;
    }

    private List<TeacherInviteCode> deleteTeacherInviteCodesBoundToUser(Long userId) {
        List<TeacherInviteCode> rows = teacherInviteCodeRepository.findByUsedByOrderByIdDesc(userId);
        if (!rows.isEmpty()) {
            teacherInviteCodeRepository.deleteAll(rows);
        }
        return rows;
    }

    private String normalizeEmailScope(String scope) {
        if (scope == null || scope.isBlank()) {
            throw new BusinessException("BAD_REQUEST", "收件范围不能为空", HttpStatus.BAD_REQUEST);
        }
        String normalized = scope.trim().toLowerCase();
        if (!Set.of("all", "teacher", "student", "manual").contains(normalized)) {
            throw new BusinessException("BAD_REQUEST", "收件范围无效", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private List<String> resolveAdminEmailRecipients(String scope, List<String> manualEmails) {
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        if ("manual".equals(scope)) {
            if (manualEmails == null || manualEmails.isEmpty()) {
                throw new BusinessException("BAD_REQUEST", "请至少填写一个收件邮箱", HttpStatus.BAD_REQUEST);
            }
            for (String item : manualEmails) {
                String email = EmailValidation.normalize(item);
                if (email.isBlank()) {
                    continue;
                }
                if (!EmailValidation.isAllowedEmail(email)) {
                    throw new BusinessException("BAD_REQUEST", "邮箱格式或域名不支持：" + item, HttpStatus.BAD_REQUEST);
                }
                emails.add(email);
            }
            return new ArrayList<>(emails);
        }

        if ("teacher".equals(scope)) {
            collectActiveUserEmails(emails, User.Role.teacher);
        } else if ("student".equals(scope)) {
            collectActiveUserEmails(emails, User.Role.student);
        } else {
            collectActiveUserEmails(emails, User.Role.teacher);
            collectActiveUserEmails(emails, User.Role.student);
        }
        return new ArrayList<>(emails);
    }

    private void collectActiveUserEmails(LinkedHashSet<String> emails, User.Role role) {
        Page<User> page = userRepository.searchUsers(
                role,
                1,
                0,
                null,
                null,
                null,
                PageRequest.of(0, ADMIN_EMAIL_MAX_RECIPIENTS + 1, Sort.by(Sort.Direction.ASC, "id")));
        for (User user : page.getContent()) {
            if (user.getRole() != User.Role.teacher && user.getRole() != User.Role.student) {
                continue;
            }
            String email = EmailValidation.normalize(user.getEmail());
            if (EmailValidation.isAllowedEmail(email)) {
                emails.add(email);
            }
        }
    }

    private String buildAdminEmailBody(String body) {
        return (body + "\n\n--\nTeamTrace 管理员通知").trim();
    }

    private UserListItemResponse toUserListItem(User user) {
        TeacherInviteCode teacherInviteCode = user.getRole() == User.Role.teacher
                ? teacherInviteCodeRepository.findFirstByUsedByOrderByIdDesc(user.getId()).orElse(null)
                : null;
        return UserListItemResponse.builder()
                .id(user.getId())
                .role(user.getRole() == null ? null : user.getRole().name())
                .phone(user.getPhone())
                .email(user.getEmail())
                .name(user.getName())
                .status(user.getStatus())
                .isDeleted(user.getIsDeleted())
                .ceremonyNo(user.getCeremonyNo())
                .ceremonyCode(formatCeremonyCode(user.getCeremonyNo()))
                .teacherInviteCode(teacherInviteCode == null ? null : teacherInviteCode.getCode())
                .welcomeEmailSentAt(user.getWelcomeEmailSentAt())
                .welcomeEmailLastError(user.getWelcomeEmailLastError())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private String formatCeremonyCode(Long ceremonyNo) {
        if (ceremonyNo == null || ceremonyNo <= 0) {
            return null;
        }
        return String.format("TT-%06d", ceremonyNo);
    }

    private User requireManagedUser(Long userId, boolean allowDeleted) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND));
        boolean isDeleted = user.getIsDeleted() != null && user.getIsDeleted() == 1;
        if (!allowDeleted && isDeleted) {
            throw new BusinessException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND);
        }
        if (user.getRole() == User.Role.admin) {
            throw new BusinessException("FORBIDDEN", "管理员账户不能在此操作", HttpStatus.FORBIDDEN);
        }
        if (user.getRole() != User.Role.teacher && user.getRole() != User.Role.student) {
            throw new BusinessException("FORBIDDEN", "当前角色不支持管理", HttpStatus.FORBIDDEN);
        }
        return user;
    }

    private User.Role parseManagedRole(String role) {
        User.Role parsed;
        try {
            parsed = User.Role.valueOf(role.trim());
        } catch (RuntimeException ex) {
            throw new BusinessException("BAD_REQUEST", "角色参数无效", HttpStatus.BAD_REQUEST);
        }
        if (parsed != User.Role.teacher && parsed != User.Role.student) {
            throw new BusinessException("FORBIDDEN", "管理员端仅支持新增教师或学生", HttpStatus.FORBIDDEN);
        }
        return parsed;
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.trim();
    }

    private void ensureEmailAvailable(String email, Long currentUserId) {
        userRepository.findByEmail(email)
                .filter(user -> currentUserId == null || !user.getId().equals(currentUserId))
                .ifPresent(user -> {
                    String message = user.getIsDeleted() != null && user.getIsDeleted() == 1
                            ? "该邮箱已被历史账户占用，请恢复原账户或更换邮箱"
                            : "该邮箱已注册";
                    throw new BusinessException("BUSINESS_CONFLICT", message, HttpStatus.CONFLICT);
                });
    }

    private void ensurePhoneAvailable(String phone, Long currentUserId) {
        if (phone == null) {
            return;
        }
        userRepository.findByPhone(phone)
                .filter(user -> currentUserId == null || !user.getId().equals(currentUserId))
                .ifPresent(user -> {
                    String message = user.getIsDeleted() != null && user.getIsDeleted() == 1
                            ? "该手机号已被历史账户占用，请恢复原账户或更换手机号"
                            : "该手机号已被使用";
                    throw new BusinessException("BUSINESS_CONFLICT", message, HttpStatus.CONFLICT);
                });
    }

    private TeacherInviteCode requireTeacherInviteCode(String code) {
        return teacherInviteCodeRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "邀请码不存在", HttpStatus.NOT_FOUND));
    }

    private String generateCode(int length) {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(secureRandom.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private String getDefaultResetPassword() {
        if (defaultResetPassword.isBlank()) {
            throw new BusinessException("CONFIG_ERROR", "默认重置密码未配置", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return defaultResetPassword;
    }
}
