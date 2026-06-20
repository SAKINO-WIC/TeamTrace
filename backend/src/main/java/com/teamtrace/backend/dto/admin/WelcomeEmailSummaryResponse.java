package com.teamtrace.backend.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WelcomeEmailSummaryResponse {
    private long pendingCount;
    private int batchLimit;
}
