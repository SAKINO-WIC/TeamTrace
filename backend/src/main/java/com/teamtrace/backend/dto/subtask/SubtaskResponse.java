package com.teamtrace.backend.dto.subtask;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SubtaskResponse {
    Long subtaskId;
    Long subtaskUuid;
    Long taskId;
    Long groupId;
    String name;
    String description;
    String qualityRequirement;
    Long assigneeId;
    LocalDateTime deadline;
    Integer status;
    LocalDateTime submittedAt;
    String submissionContent;
    List<SubtaskSubmissionHistoryResponse> submissionHistories;
    Long reviewerId;
    String reviewComment;
    LocalDateTime reviewedAt;
}
