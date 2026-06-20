package com.teamtrace.backend.dto.report;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TaskGroupReportHistoryResponse {
    Long id;
    Long reportId;
    Long submitterId;
    Integer versionNo;
    String reportContent;
    LocalDateTime submittedAt;
    Boolean current;
}
