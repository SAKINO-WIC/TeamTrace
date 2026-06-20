package com.teamtrace.backend.dto.freecollab;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CollaborationTaskFlowNodeResponse {
    Long id;
    Long taskId;
    Integer stepOrder;
    String title;
    String description;
    Long assigneeId;
    String assigneeName;
    String assigneeAvatarUrl;
    Boolean claimable;
    String status;
    Boolean current;
    LocalDateTime startedAt;
    LocalDateTime submittedAt;
    LocalDateTime completedAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
