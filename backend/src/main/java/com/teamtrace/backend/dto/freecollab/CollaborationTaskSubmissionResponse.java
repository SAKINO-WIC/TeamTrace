package com.teamtrace.backend.dto.freecollab;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CollaborationTaskSubmissionResponse {
    Long id;
    Long taskId;
    Long flowNodeId;
    Long submittedBy;
    String submittedByName;
    String content;
    String attachmentsJson;
    String linksJson;
    Integer versionNo;
    String status;
    LocalDateTime createdAt;
}
