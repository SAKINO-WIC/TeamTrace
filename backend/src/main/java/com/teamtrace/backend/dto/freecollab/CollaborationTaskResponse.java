package com.teamtrace.backend.dto.freecollab;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CollaborationTaskResponse {
    Long id;
    Long projectId;
    Long spaceId;
    Long parentTaskId;
    String title;
    String description;
    String deliverableRequirements;
    Long assigneeId;
    String assigneeName;
    String assigneeAvatarUrl;
    Long receiverId;
    String receiverName;
    Long createdBy;
    String createdByName;
    String claimMode;
    String status;
    LocalDateTime startAt;
    LocalDateTime dueAt;
    LocalDateTime submittedAt;
    LocalDateTime acceptedAt;
    LocalDateTime completedAt;
    Boolean overdue;
    List<CollaborationAttachmentResponse> attachments;
    List<CollaborationTaskFlowNodeResponse> flowNodes;
    CollaborationTaskFlowNodeResponse currentFlowNode;
    CollaborationTaskSubmissionResponse latestSubmission;
    List<CollaborationTaskSubmissionResponse> submissions;
    List<Long> dependsOnTaskIds;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
