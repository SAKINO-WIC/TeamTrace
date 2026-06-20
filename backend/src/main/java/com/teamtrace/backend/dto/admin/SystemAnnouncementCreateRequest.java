package com.teamtrace.backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SystemAnnouncementCreateRequest {
    @NotBlank(message = "公告标题不能为空")
    @Size(max = 120, message = "公告标题不能超过120个字符")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    @Size(max = 5000, message = "公告内容不能超过5000个字符")
    private String content;

    @NotBlank(message = "接收范围不能为空")
    private String targetScope;

    private List<Long> targetUserIds;
    private Integer priority;
    private Boolean popupEnabled;
    private Boolean forceConfirm;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;
}
