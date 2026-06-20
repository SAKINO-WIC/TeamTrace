package com.teamtrace.backend.dto.freecollab;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CollaborationProjectResponse {
    Long id;
    Long spaceId;
    String title;
    String description;
    String status;
    LocalDateTime startAt;
    LocalDateTime dueAt;
    Long createdBy;
    String createdByName;
    Integer taskCount;
    Integer completedTaskCount;
    Integer waitingReceiveCount;
    Integer overdueTaskCount;
    List<CollaborationAttachmentResponse> attachments;
    List<CollaborationTaskResponse> tasks;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
