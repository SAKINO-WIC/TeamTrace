package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.notification.NotificationPageResponse;
import com.teamtrace.backend.dto.notification.NotificationResponse;
import com.teamtrace.backend.entity.Notification;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.NotificationRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final int MAX_TITLE = 100;
    private static final int MAX_CONTENT = 500;

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void notifyUser(Long userId, String type, String title, String content, Long relatedId) {
        if (userId == null || type == null || type.isBlank()) {
            return;
        }
        String t = truncate(title == null ? "" : title.trim(), MAX_TITLE);
        String c = truncate(content == null ? "" : content.trim(), MAX_CONTENT);
        if (t.isEmpty()) {
            t = "通知";
        }
        if (c.isEmpty()) {
            c = " ";
        }
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type.trim());
        n.setTitle(t);
        n.setContent(c);
        n.setRelatedId(relatedId);
        n.setIsRead(0);
        notificationRepository.save(n);
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse pageForUser(Long userId, int page, int size, String type, Boolean isRead) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        String normalizedType = normalizeType(type);
        Integer normalizedIsRead = normalizeIsRead(isRead);
        Page<Notification> pg =
                notificationRepository.searchForUser(
                        userId, normalizedType, normalizedIsRead, PageRequest.of(p - 1, s));
        long unread = notificationRepository.countByUserIdAndIsRead(userId, 0);
        List<NotificationResponse> items =
                pg.getContent().stream().map(this::toResponse).collect(Collectors.toList());
        return NotificationPageResponse.builder()
                .items(items)
                .totalElements(pg.getTotalElements())
                .page(p)
                .size(s)
                .unreadCount(unread)
                .build();
    }

    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification n = notificationRepository
                .findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "通知不存在", HttpStatus.NOT_FOUND));
        n.setIsRead(1);
        return toResponse(notificationRepository.save(n));
    }

    @Transactional
    public long markAllRead(Long userId) {
        return notificationRepository.markAllReadForUser(userId);
    }

    @Transactional
    public long markReadByType(Long userId, String type) {
        String normalizedType = normalizeType(type);
        if (normalizedType == null) {
            throw new BusinessException("BAD_REQUEST", "通知类型不能为空", HttpStatus.BAD_REQUEST);
        }
        return notificationRepository.markReadByTypeForUser(userId, normalizedType);
    }

    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndIsRead(userId, 0);
    }

    @Transactional
    public long markReadByRelatedId(Long userId, Long relatedId, String type) {
        if (relatedId == null || relatedId <= 0) {
            throw new BusinessException("BAD_REQUEST", "relatedId 不合法", HttpStatus.BAD_REQUEST);
        }
        String normalizedType = normalizeType(type);
        return notificationRepository.markReadByRelatedIdForUser(userId, relatedId, normalizedType);
    }

    @Transactional
    public void delete(Long userId, Long notificationId) {
        int deleted = notificationRepository.deleteByIdAndUserId(notificationId, userId);
        if (deleted == 0) {
            throw new BusinessException("NOT_FOUND", "通知不存在", HttpStatus.NOT_FOUND);
        }
    }

    @Transactional
    public long deleteBatch(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("BAD_REQUEST", "通知ID列表不能为空", HttpStatus.BAD_REQUEST);
        }
        return notificationRepository.deleteByIdInAndUserId(ids, userId);
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .content(n.getContent())
                .relatedId(n.getRelatedId())
                .read(n.getIsRead() != null && n.getIsRead() == 1)
                .createdAt(n.getCreatedAt())
                .build();
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

    private static String normalizeType(String type) {
        if (type == null) {
            return null;
        }
        String trimmed = type.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Integer normalizeIsRead(Boolean isRead) {
        if (isRead == null) {
            return null;
        }
        return isRead ? 1 : 0;
    }
}
