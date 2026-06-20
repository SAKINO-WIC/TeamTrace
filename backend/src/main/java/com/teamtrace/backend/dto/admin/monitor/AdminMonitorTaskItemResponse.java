package com.teamtrace.backend.dto.admin.monitor;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminMonitorTaskItemResponse {
    private Long id;
    private String name;
    private String className;
    private Integer status;
    private LocalDateTime deadline;
}

