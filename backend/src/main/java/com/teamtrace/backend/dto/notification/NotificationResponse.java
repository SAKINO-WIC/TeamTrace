package com.teamtrace.backend.dto.notification;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class NotificationResponse {
    Long id;
    String type;
    String title;
    String content;
    Long relatedId;
    boolean read;
    LocalDateTime createdAt;
}
