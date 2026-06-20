package com.teamtrace.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamtrace.backend.dto.notification.NotificationPageResponse;
import com.teamtrace.backend.entity.Notification;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.NotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void pageForUserUsesTypeAndIsReadFilters() {
        Notification notification = new Notification();
        notification.setId(1L);
        notification.setUserId(4L);
        notification.setType("subtask_review_result");
        notification.setTitle("title");
        notification.setContent("content");
        notification.setIsRead(1);
        notification.setCreatedAt(LocalDateTime.of(2026, 4, 23, 10, 0));
        when(notificationRepository.searchForUser(
                        eq(4L), eq("subtask_review_result"), eq(1), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification)));
        when(notificationRepository.countByUserIdAndIsRead(4L, 0)).thenReturn(2L);

        NotificationPageResponse response =
                notificationService.pageForUser(4L, 1, 20, "  subtask_review_result ", true);

        assertEquals(1, response.getItems().size());
        assertTrue(response.getItems().get(0).isRead());
        assertEquals(2L, response.getUnreadCount());
    }

    @Test
    void pageForUserPassesNullFiltersWhenBlank() {
        when(notificationRepository.searchForUser(eq(8L), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(notificationRepository.countByUserIdAndIsRead(8L, 0)).thenReturn(0L);

        NotificationPageResponse response = notificationService.pageForUser(8L, 1, 20, "   ", null);

        assertTrue(response.getItems().isEmpty());
        assertFalse(response.getTotalElements() > 0);
        verify(notificationRepository).searchForUser(eq(8L), eq(null), eq(null), any(Pageable.class));
    }

    @Test
    void countUnreadReturnsRepositoryCount() {
        when(notificationRepository.countByUserIdAndIsRead(5L, 0)).thenReturn(3L);

        long unread = notificationService.countUnread(5L);

        assertEquals(3L, unread);
    }

    @Test
    void markReadByTypeUsesTrimmedType() {
        when(notificationRepository.markReadByTypeForUser(4L, "new_appeal")).thenReturn(2);

        long updated = notificationService.markReadByType(4L, "  new_appeal ");

        assertEquals(2L, updated);
        verify(notificationRepository).markReadByTypeForUser(4L, "new_appeal");
    }

    @Test
    void markReadByTypeRejectsBlankType() {
        BusinessException ex = assertThrows(BusinessException.class, () -> notificationService.markReadByType(4L, " "));
        assertEquals("BAD_REQUEST", ex.getCode());
    }

    @Test
    void markReadByRelatedIdAllowsOptionalType() {
        when(notificationRepository.markReadByRelatedIdForUser(4L, 99L, "new_appeal")).thenReturn(2);

        long updated = notificationService.markReadByRelatedId(4L, 99L, " new_appeal ");

        assertEquals(2L, updated);
        verify(notificationRepository).markReadByRelatedIdForUser(4L, 99L, "new_appeal");
    }

    @Test
    void markReadByRelatedIdRejectsInvalidId() {
        BusinessException ex =
                assertThrows(BusinessException.class, () -> notificationService.markReadByRelatedId(4L, 0L, null));
        assertEquals("BAD_REQUEST", ex.getCode());
    }
}
