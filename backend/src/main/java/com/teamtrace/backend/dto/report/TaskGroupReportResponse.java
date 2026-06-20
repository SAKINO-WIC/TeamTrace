package com.teamtrace.backend.dto.report;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TaskGroupReportResponse {
    Long reportId;
    Long taskId;
    Long groupId;
    Long submitterId;
    Integer versionNo;
    String reportContent;
    LocalDateTime submittedAt;
    List<TaskGroupReportHistoryResponse> histories;
}
