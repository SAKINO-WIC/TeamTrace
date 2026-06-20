package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.admin.WelcomeEmailResendResponse;
import com.teamtrace.backend.dto.admin.WelcomeEmailSummaryResponse;
import com.teamtrace.backend.dto.auth.CeremonyInfoResponse;
import com.teamtrace.backend.entity.User;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.UserRepository;
import com.teamtrace.backend.util.AppTime;
import com.teamtrace.backend.util.EmailValidation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CeremonyService {

    public static final int WELCOME_EMAIL_BATCH_LIMIT = 30;
    private static final List<User.Role> CEREMONY_ROLES = List.of(User.Role.teacher, User.Role.student);
    private static final int MAX_ERROR_LENGTH = 500;

    private final UserRepository userRepository;
    private final EmailService emailService;

    public CeremonyService(UserRepository userRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    @Transactional
    public User assignCeremonyNoIfNeeded(User user) {
        if (!isCeremonyRole(user) || user.getCeremonyNo() != null) {
            return user;
        }
        Long max = userRepository.findMaxCeremonyNo(CEREMONY_ROLES);
        user.setCeremonyNo((max == null ? 0L : max) + 1L);
        return userRepository.save(user);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendWelcomeEmailSafely(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !isCeremonyRole(user) || user.getWelcomeEmailSentAt() != null) {
            return;
        }
        assignCeremonyNoIfNeeded(user);
        try {
            emailService.sendPlainText(
                    user.getEmail(),
                    "欢迎加入 TeamTrace 摸鱼终结者计划",
                    buildWelcomeEmailBody(user),
                    "welcome_email");
            user.setWelcomeEmailSentAt(AppTime.now());
            user.setWelcomeEmailLastError(null);
        } catch (RuntimeException ex) {
            user.setWelcomeEmailLastError(truncate(ex.getMessage()));
        }
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public CeremonyInfoResponse buildCeremonyInfo(User user) {
        if (!isCeremonyRole(user) || user.getCeremonyNo() == null) {
            return null;
        }
        boolean isTeacher = user.getRole() == User.Role.teacher;
        String identity = isTeacher ? "教育协作者" : "学习协作者";
        return CeremonyInfoResponse.builder()
                .ceremonyNo(user.getCeremonyNo())
                .ceremonyCode(formatCeremonyCode(user.getCeremonyNo()))
                .title("你是第 " + user.getCeremonyNo() + " 位" + identity)
                .subtitle("欢迎加入 TeamTrace 摸鱼终结者计划。你的每一次协作，都会留下清晰、公平、可被看见的痕迹。")
                .shouldShow(user.getCeremonySeenAt() == null)
                .build();
    }

    @Transactional
    public CeremonyInfoResponse markSeen(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND));
        if (!isCeremonyRole(user)) {
            throw new BusinessException("FORBIDDEN", "当前角色不支持仪式编号", HttpStatus.FORBIDDEN);
        }
        if (user.getCeremonyNo() == null) {
            assignCeremonyNoIfNeeded(user);
        }
        if (user.getCeremonySeenAt() == null) {
            user.setCeremonySeenAt(AppTime.now());
            user = userRepository.save(user);
        }
        return buildCeremonyInfo(user);
    }

    @Transactional
    public int backfillMissingCeremonyNumbers() {
        List<User> users = userRepository.findByRoleInOrderByCreatedAtAscIdAsc(CEREMONY_ROLES);
        long next = 1L;
        int updated = 0;
        for (User user : users) {
            if (user.getCeremonyNo() == null) {
                user.setCeremonyNo(next);
                updated++;
            }
            next = Math.max(next + 1L, (user.getCeremonyNo() == null ? 0L : user.getCeremonyNo()) + 1L);
        }
        userRepository.saveAll(users);
        return updated;
    }

    @Transactional(readOnly = true)
    public WelcomeEmailSummaryResponse welcomeEmailSummary() {
        return WelcomeEmailSummaryResponse.builder()
                .pendingCount(countPendingWelcomeEmails())
                .batchLimit(WELCOME_EMAIL_BATCH_LIMIT)
                .build();
    }

    public WelcomeEmailResendResponse resendPendingWelcomeEmails() {
        List<User> users = userRepository.findByRoleInAndWelcomeEmailSentAtIsNullAndIsDeletedOrderByCreatedAtAscIdAsc(
                CEREMONY_ROLES,
                0,
                PageRequest.of(0, WELCOME_EMAIL_BATCH_LIMIT, Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by("id"))));
        List<WelcomeEmailResendResponse.FailureItem> failures = new ArrayList<>();
        int sent = 0;
        for (User user : users) {
            if (!EmailValidation.isAllowedEmail(user.getEmail())) {
                failures.add(failure(user, "邮箱格式不支持"));
                continue;
            }
            sendWelcomeEmailSafely(user.getId());
            User refreshed = userRepository.findById(user.getId()).orElse(user);
            if (refreshed.getWelcomeEmailSentAt() != null) {
                sent++;
            } else {
                failures.add(failure(refreshed, refreshed.getWelcomeEmailLastError()));
            }
        }
        return WelcomeEmailResendResponse.builder()
                .requestedCount(users.size())
                .sentCount(sent)
                .failedCount(users.size() - sent)
                .remainingCount(countPendingWelcomeEmails())
                .failures(failures)
                .build();
    }

    public WelcomeEmailResendResponse resendSelectedWelcomeEmails(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new BusinessException("BAD_REQUEST", "请选择要补发欢迎邮件的用户", HttpStatus.BAD_REQUEST);
        }
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(userIds));
        if (distinctIds.size() > WELCOME_EMAIL_BATCH_LIMIT) {
            throw new BusinessException(
                    "BAD_REQUEST",
                    "单次最多选择" + WELCOME_EMAIL_BATCH_LIMIT + "位用户",
                    HttpStatus.BAD_REQUEST);
        }

        List<WelcomeEmailResendResponse.FailureItem> failures = new ArrayList<>();
        int sent = 0;
        for (Long userId : distinctIds) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                failures.add(WelcomeEmailResendResponse.FailureItem.builder()
                        .userId(userId)
                        .email(null)
                        .reason("用户不存在")
                        .build());
                continue;
            }
            if (!isCeremonyRole(user) || (user.getIsDeleted() != null && user.getIsDeleted() == 1)) {
                failures.add(failure(user, "当前用户不支持补发欢迎邮件"));
                continue;
            }
            if (user.getWelcomeEmailSentAt() != null) {
                failures.add(failure(user, "欢迎邮件已发送"));
                continue;
            }
            if (!EmailValidation.isAllowedEmail(user.getEmail())) {
                failures.add(failure(user, "邮箱格式不支持或未绑定邮箱"));
                continue;
            }
            sendWelcomeEmailSafely(user.getId());
            User refreshed = userRepository.findById(user.getId()).orElse(user);
            if (refreshed.getWelcomeEmailSentAt() != null) {
                sent++;
            } else {
                failures.add(failure(refreshed, refreshed.getWelcomeEmailLastError()));
            }
        }

        return WelcomeEmailResendResponse.builder()
                .requestedCount(distinctIds.size())
                .sentCount(sent)
                .failedCount(distinctIds.size() - sent)
                .remainingCount(countPendingWelcomeEmails())
                .failures(failures)
                .build();
    }

    private long countPendingWelcomeEmails() {
        return userRepository.countByRoleInAndWelcomeEmailSentAtIsNullAndIsDeleted(CEREMONY_ROLES, 0);
    }

    private WelcomeEmailResendResponse.FailureItem failure(User user, String reason) {
        return WelcomeEmailResendResponse.FailureItem.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .reason((reason == null || reason.isBlank()) ? "发送失败" : reason)
                .build();
    }

    private boolean isCeremonyRole(User user) {
        return user != null && (user.getRole() == User.Role.teacher || user.getRole() == User.Role.student);
    }

    private String buildWelcomeEmailBody(User user) {
        String identity = user.getRole() == User.Role.teacher ? "教育协作者" : "学习协作者";
        return """
                %s，欢迎加入 TeamTrace。

                你是 TeamTrace 摸鱼终结者计划的第 %d 位%s。
                你的专属仪式编号是：%s

                从今天开始，任务拆解、协作痕迹、互评与评分都会更清晰地被记录。
                愿每一次认真投入，都被公平看见。

                TeamTrace
                """.formatted(user.getName(), user.getCeremonyNo(), identity, formatCeremonyCode(user.getCeremonyNo())).trim();
    }

    private String formatCeremonyCode(Long ceremonyNo) {
        return "TT-%06d".formatted(ceremonyNo == null ? 0L : ceremonyNo);
    }

    private String truncate(String raw) {
        if (raw == null || raw.isBlank()) {
            return "发送失败";
        }
        return raw.length() <= MAX_ERROR_LENGTH ? raw : raw.substring(0, MAX_ERROR_LENGTH);
    }
}
