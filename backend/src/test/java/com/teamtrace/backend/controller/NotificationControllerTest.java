package com.teamtrace.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.notification.NotificationPageResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.NotificationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class NotificationControllerTest {

    @Test
    void pagePassesTypeAndIsReadFilters() {
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        NotificationService notificationService = mock(NotificationService.class);
        when(jwtTokenProvider.resolveBearerToken("Bearer x")).thenReturn("tok");
        when(jwtTokenProvider.isValid("tok")).thenReturn(true);
        when(jwtTokenProvider.extractUserId("tok")).thenReturn(4L);
        NotificationPageResponse expected =
                NotificationPageResponse.builder()
                        .items(List.of())
                        .totalElements(0L)
                        .page(1)
                        .size(20)
                        .unreadCount(0L)
                        .build();
        when(notificationService.pageForUser(4L, 1, 20, "new_appeal", false)).thenReturn(expected);

        NotificationController controller = new NotificationController(jwtTokenProvider, notificationService);
        ApiResponse<NotificationPageResponse> response =
                controller.page("Bearer x", 1, 20, "new_appeal", false);

        assertTrue(response.isSuccess());
        assertEquals(0L, response.getData().getTotalElements());
        verify(notificationService).pageForUser(4L, 1, 20, "new_appeal", false);
    }

    @Test
    void pageThrowsUnauthorizedWhenTokenMissing() {
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        NotificationService notificationService = mock(NotificationService.class);
        when(jwtTokenProvider.resolveBearerToken(null)).thenReturn(null);

        NotificationController controller = new NotificationController(jwtTokenProvider, notificationService);
        BusinessException ex =
                assertThrows(BusinessException.class, () -> controller.page(null, 1, 20, null, null));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void unreadCountReturnsServiceValue() {
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        NotificationService notificationService = mock(NotificationService.class);
        when(jwtTokenProvider.resolveBearerToken("Bearer x")).thenReturn("tok");
        when(jwtTokenProvider.isValid("tok")).thenReturn(true);
        when(jwtTokenProvider.extractUserId("tok")).thenReturn(4L);
        when(notificationService.countUnread(4L)).thenReturn(6L);

        NotificationController controller = new NotificationController(jwtTokenProvider, notificationService);
        ApiResponse<java.util.Map<String, Long>> response = controller.unreadCount("Bearer x");

        assertTrue(response.isSuccess());
        assertEquals(6L, response.getData().get("unreadCount"));
        verify(notificationService).countUnread(4L);
    }

    @Test
    void markReadByTypeReturnsUpdatedCount() {
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        NotificationService notificationService = mock(NotificationService.class);
        when(jwtTokenProvider.resolveBearerToken("Bearer x")).thenReturn("tok");
        when(jwtTokenProvider.isValid("tok")).thenReturn(true);
        when(jwtTokenProvider.extractUserId("tok")).thenReturn(4L);
        when(notificationService.markReadByType(4L, "new_appeal")).thenReturn(3L);

        NotificationController controller = new NotificationController(jwtTokenProvider, notificationService);
        ApiResponse<java.util.Map<String, Long>> response = controller.markReadByType("Bearer x", "new_appeal");

        assertTrue(response.isSuccess());
        assertEquals(3L, response.getData().get("updated"));
        verify(notificationService).markReadByType(4L, "new_appeal");
    }

    @Test
    void markReadByRelatedReturnsUpdatedCount() {
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        NotificationService notificationService = mock(NotificationService.class);
        when(jwtTokenProvider.resolveBearerToken("Bearer x")).thenReturn("tok");
        when(jwtTokenProvider.isValid("tok")).thenReturn(true);
        when(jwtTokenProvider.extractUserId("tok")).thenReturn(4L);
        when(notificationService.markReadByRelatedId(4L, 99L, "new_appeal")).thenReturn(4L);

        NotificationController controller = new NotificationController(jwtTokenProvider, notificationService);
        ApiResponse<java.util.Map<String, Long>> response =
                controller.markReadByRelatedId("Bearer x", 99L, "new_appeal");

        assertTrue(response.isSuccess());
        assertEquals(4L, response.getData().get("updated"));
        verify(notificationService).markReadByRelatedId(4L, 99L, "new_appeal");
    }
}
