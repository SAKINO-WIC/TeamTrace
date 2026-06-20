package com.teamtrace.backend.dto.freecollab;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CollaborationActivityLogResponse {
    Long id;
    Long spaceId;
    Long projectId;
    Long taskId;
    Long actorId;
    String actorName;
    String action;
    String summary;
    String detailJson;
    LocalDateTime createdAt;
}
