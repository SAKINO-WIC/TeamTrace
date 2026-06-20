package com.teamtrace.backend.dto.subtask;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SubtaskSubmissionHistoryResponse {
    Long id;
    Long subtaskId;
    Long submitterId;
    Integer versionNo;
    String submissionContent;
    LocalDateTime submittedAt;
    Boolean current;
}
