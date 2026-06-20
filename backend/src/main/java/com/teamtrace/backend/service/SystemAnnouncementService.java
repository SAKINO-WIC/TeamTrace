package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.admin.SystemAnnouncementCreateRequest;
import com.teamtrace.backend.dto.admin.SystemAnnouncementPageResponse;
import com.teamtrace.backend.dto.admin.SystemAnnouncementResponse;
import com.teamtrace.backend.dto.announcement.UserSystemAnnouncementResponse;
import com.teamtrace.backend.entity.SystemAnnouncement;
import com.teamtrace.backend.entity.SystemAnnouncementRecipient;
import com.teamtrace.backend.entity.User;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.SystemAnnouncementRecipientRepository;
import com.teamtrace.backend.repository.SystemAnnouncementRepository;
import com.teamtrace.backend.repository.UserRepository;
import com.teamtrace.backend.util.AppTime;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemAnnouncementService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> TARGET_SCOPES = Set.of("all", "teacher", "student", "selected");

    private final SystemAnnouncementRepository announcementRepository;
    private final SystemAnnouncementRecipientRepository recipientRepository;
    private final UserRepository userRepository;

    public SystemAnnouncementService(
            SystemAnnouncementRepository announcementRepository,
            SystemAnnouncementRecipientRepository recipientRepository,
            UserRepository userRepository) {
        this.announcementRepository = announcementRepository;
        this.recipientRepository = recipientRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public SystemAnnouncementResponse create(Long adminUserId, SystemAnnouncementCreateRequest request) {
        String scope = normalizeScope(request.getTargetScope());
        List<User> recipients = resolveRecipients(scope, request.getTargetUserIds());
        if (recipients.isEmpty()) {
            throw new BusinessException("BAD_REQUEST", "接收人不能为空", HttpStatus.BAD_REQUEST);
        }

        SystemAnnouncement announcement = new SystemAnnouncement();
        announcement.setTitle(request.getTitle().trim());
        announcement.setContent(request.getContent().trim());
        announcement.setTargetScope(scope);
        announcement.setPriority(normalizePriority(request.getPriority()));
        announcement.setPopupEnabled(Boolean.FALSE.equals(request.getPopupEnabled()) ? 0 : 1);
        announcement.setForceConfirm(Boolean.TRUE.equals(request.getForceConfirm()) ? 1 : 0);
        announcement.setStatus(SystemAnnouncement.STATUS_ACTIVE);
        announcement.setStartsAt(request.getStartsAt());
        announcement.setExpiresAt(request.getExpiresAt());
        announcement.setCreatedBy(adminUserId);
        validateTimeWindow(announcement.getStartsAt(), announcement.getExpiresAt());

        SystemAnnouncement saved = announcementRepository.save(announcement);
        List<SystemAnnouncementRecipient> recipientRows = recipients.stream()
                .map(user -> {
                    SystemAnnouncementRecipient recipient = new SystemAnnouncementRecipient();
                    recipient.setAnnouncementId(saved.getId());
                    recipient.setUserId(user.getId());
                    return recipient;
                })
                .toList();
        recipientRepository.saveAll(recipientRows);
        return toAdminResponse(saved);
    }

    @Transactional(readOnly = true)
    public SystemAnnouncementPageResponse page(Integer page, Integer size, Integer status, String targetScope, String keyword) {
        int normalizedPage = Math.max(page == null ? 1 : page, 1);
        int normalizedSize = Math.min(Math.max(size == null ? 10 : size, 1), MAX_PAGE_SIZE);
        String scope = targetScope == null || targetScope.isBlank() ? null : normalizeScope(targetScope);
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Page<SystemAnnouncement> result = announcementRepository.search(
                status,
                scope,
                kw,
                PageRequest.of(normalizedPage - 1, normalizedSize));
        List<SystemAnnouncementResponse> items = result.getContent().stream()
                .map(this::toAdminResponse)
                .toList();
        return SystemAnnouncementPageResponse.builder()
                .items(items)
                .totalElements(result.getTotalElements())
                .page(normalizedPage)
                .size(normalizedSize)
                .build();
    }

    @Transactional
    public SystemAnnouncementResponse withdraw(Long id) {
        SystemAnnouncement announcement = requireAnnouncement(id);
        announcement.setStatus(SystemAnnouncement.STATUS_WITHDRAWN);
        return toAdminResponse(announcementRepository.save(announcement));
    }

    @Transactional
    public void delete(Long id) {
        requireAnnouncement(id);
        recipientRepository.deleteByAnnouncementId(id);
        announcementRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<UserSystemAnnouncementResponse> pendingForUser(Long userId) {
        LocalDateTime now = AppTime.now();
        List<SystemAnnouncementRecipient> receipts = recipientRepository.findByUserIdOrderByIdDesc(userId);
        if (receipts.isEmpty()) {
            return List.of();
        }

        Map<Long, SystemAnnouncementRecipient> receiptByAnnouncement = new HashMap<>();
        for (SystemAnnouncementRecipient receipt : receipts) {
            receiptByAnnouncement.put(receipt.getAnnouncementId(), receipt);
        }

        return announcementRepository.findAllById(receiptByAnnouncement.keySet()).stream()
                .filter(announcement -> announcement.getStatus() != null
                        && announcement.getStatus() == SystemAnnouncement.STATUS_ACTIVE)
                .filter(announcement -> isWithinWindow(announcement, now))
                .filter(announcement -> shouldShowToUser(announcement, receiptByAnnouncement.get(announcement.getId())))
                .sorted((left, right) -> Long.compare(right.getId(), left.getId()))
                .map(announcement -> toUserResponse(announcement, receiptByAnnouncement.get(announcement.getId())))
                .toList();
    }

    @Transactional
    public UserSystemAnnouncementResponse acknowledge(Long userId, Long announcementId, String action) {
        SystemAnnouncement announcement = requireAnnouncement(announcementId);
        SystemAnnouncementRecipient receipt = recipientRepository.findByAnnouncementIdAndUserId(announcementId, userId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "公告不存在", HttpStatus.NOT_FOUND));
        LocalDateTime now = AppTime.now();
        if (receipt.getReadAt() == null) {
            receipt.setReadAt(now);
        }
        if ("confirm".equalsIgnoreCase(action)) {
            receipt.setConfirmedAt(now);
            receipt.setDismissedAt(now);
        } else {
            receipt.setDismissedAt(now);
        }
        return toUserResponse(announcement, recipientRepository.save(receipt));
    }

    private List<User> resolveRecipients(String scope, List<Long> selectedIds) {
        if ("all".equals(scope)) {
            return userRepository.findByIsDeleted(0).stream()
                    .filter(user -> user.getRole() == User.Role.teacher || user.getRole() == User.Role.student)
                    .toList();
        }
        if ("teacher".equals(scope) || "student".equals(scope)) {
            return userRepository.findByRoleAndIsDeleted(User.Role.valueOf(scope), 0);
        }
        if (selectedIds == null || selectedIds.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new HashSet<>(selectedIds);
        return userRepository.findAllById(ids).stream()
                .filter(user -> user.getIsDeleted() != null && user.getIsDeleted() == 0)
                .filter(user -> user.getRole() == User.Role.teacher || user.getRole() == User.Role.student)
                .toList();
    }

    private SystemAnnouncement requireAnnouncement(Long id) {
        return announcementRepository.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "系统公告不存在", HttpStatus.NOT_FOUND));
    }

    private static String normalizeScope(String scope) {
        String normalized = scope == null ? "" : scope.trim().toLowerCase();
        if (!TARGET_SCOPES.contains(normalized)) {
            throw new BusinessException("BAD_REQUEST", "公告接收范围无效", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private static int normalizePriority(Integer priority) {
        int normalized = priority == null ? 1 : priority;
        return normalized <= 1 ? 1 : 2;
    }

    private static void validateTimeWindow(LocalDateTime startsAt, LocalDateTime expiresAt) {
        if (startsAt != null && expiresAt != null && !expiresAt.isAfter(startsAt)) {
            throw new BusinessException("BAD_REQUEST", "结束时间必须晚于开始时间", HttpStatus.BAD_REQUEST);
        }
    }

    private static boolean isWithinWindow(SystemAnnouncement announcement, LocalDateTime now) {
        boolean afterStart = announcement.getStartsAt() == null || !announcement.getStartsAt().isAfter(now);
        boolean beforeEnd = announcement.getExpiresAt() == null || announcement.getExpiresAt().isAfter(now);
        return afterStart && beforeEnd;
    }

    private static boolean shouldShowToUser(SystemAnnouncement announcement, SystemAnnouncementRecipient receipt) {
        if (receipt == null) {
            return false;
        }
        if (announcement.getForceConfirm() != null && announcement.getForceConfirm() == 1) {
            return receipt.getConfirmedAt() == null;
        }
        if (announcement.getPopupEnabled() != null && announcement.getPopupEnabled() == 1) {
            return receipt.getDismissedAt() == null;
        }
        return receipt.getReadAt() == null;
    }

    private SystemAnnouncementResponse toAdminResponse(SystemAnnouncement announcement) {
        Long id = announcement.getId();
        return SystemAnnouncementResponse.builder()
                .id(id)
                .title(announcement.getTitle())
                .content(announcement.getContent())
                .targetScope(announcement.getTargetScope())
                .priority(announcement.getPriority())
                .popupEnabled(announcement.getPopupEnabled() != null && announcement.getPopupEnabled() == 1)
                .forceConfirm(announcement.getForceConfirm() != null && announcement.getForceConfirm() == 1)
                .status(announcement.getStatus())
                .startsAt(announcement.getStartsAt())
                .expiresAt(announcement.getExpiresAt())
                .createdBy(announcement.getCreatedBy())
                .createdAt(announcement.getCreatedAt())
                .updatedAt(announcement.getUpdatedAt())
                .recipientCount(id == null ? 0L : recipientRepository.countByAnnouncementId(id))
                .readCount(id == null ? 0L : recipientRepository.countByAnnouncementIdAndReadAtIsNotNull(id))
                .confirmedCount(id == null ? 0L : recipientRepository.countByAnnouncementIdAndConfirmedAtIsNotNull(id))
                .build();
    }

    private static UserSystemAnnouncementResponse toUserResponse(SystemAnnouncement announcement, SystemAnnouncementRecipient receipt) {
        return UserSystemAnnouncementResponse.builder()
                .id(announcement.getId())
                .title(announcement.getTitle())
                .content(announcement.getContent())
                .priority(announcement.getPriority())
                .popupEnabled(announcement.getPopupEnabled() != null && announcement.getPopupEnabled() == 1)
                .forceConfirm(announcement.getForceConfirm() != null && announcement.getForceConfirm() == 1)
                .startsAt(announcement.getStartsAt())
                .expiresAt(announcement.getExpiresAt())
                .createdAt(announcement.getCreatedAt())
                .read(receipt.getReadAt() != null)
                .dismissed(receipt.getDismissedAt() != null)
                .confirmed(receipt.getConfirmedAt() != null)
                .build();
    }
}
