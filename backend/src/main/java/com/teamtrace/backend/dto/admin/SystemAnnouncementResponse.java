package com.teamtrace.backend.dto.admin;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SystemAnnouncementResponse {
    private Long id;
    private String title;
    private String content;
    private String targetScope;
    private Integer priority;
    private Boolean popupEnabled;
    private Boolean forceConfirm;
    private Integer status;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long recipientCount;
    private Long readCount;
    private Long confirmedCount;
}
