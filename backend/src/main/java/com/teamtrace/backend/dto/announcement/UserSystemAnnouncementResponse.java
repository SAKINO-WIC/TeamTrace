package com.teamtrace.backend.dto.announcement;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserSystemAnnouncementResponse {
    private Long id;
    private String title;
    private String content;
    private Integer priority;
    private Boolean popupEnabled;
    private Boolean forceConfirm;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private Boolean read;
    private Boolean dismissed;
    private Boolean confirmed;
}
