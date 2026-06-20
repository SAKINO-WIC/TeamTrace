package com.teamtrace.backend.dto.admin.monitor;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminMonitorTaskPageResponse {
    private List<AdminMonitorTaskItemResponse> list;
    private int page;
    private int size;
    private long total;
    private long pages;
    private boolean hasNext;
}

