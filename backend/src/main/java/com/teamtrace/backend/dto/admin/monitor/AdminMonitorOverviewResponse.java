package com.teamtrace.backend.dto.admin.monitor;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminMonitorOverviewResponse {
    private long classCount;
    private long taskCount;
    private long groupCount;
    private long activeClassCount;
}

