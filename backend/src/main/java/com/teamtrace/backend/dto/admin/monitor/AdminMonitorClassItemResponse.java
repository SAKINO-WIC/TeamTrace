package com.teamtrace.backend.dto.admin.monitor;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminMonitorClassItemResponse {
    private Long id;
    private String name;
    private String teacherName;
    private Integer status;
    private long memberCount;
}

