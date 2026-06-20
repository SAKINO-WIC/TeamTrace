package com.teamtrace.backend.dto.notification;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class NotificationPageResponse {
    List<NotificationResponse> items;
    long totalElements;
    int page;
    int size;
    long unreadCount;
}
