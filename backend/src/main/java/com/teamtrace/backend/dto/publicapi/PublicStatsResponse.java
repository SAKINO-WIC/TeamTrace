package com.teamtrace.backend.dto.publicapi;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PublicStatsResponse {
    private long totalUsers;
    private long studentCount;
    private long teacherCount;
    private long classCount;
    private long taskCount;
    private long groupCount;
    private long subtaskCount;
    private long appealCount;
    private long daysSinceFirstDevLog;
    private String generatedAt;
}
