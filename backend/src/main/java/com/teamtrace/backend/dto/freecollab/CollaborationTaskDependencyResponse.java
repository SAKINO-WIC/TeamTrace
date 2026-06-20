package com.teamtrace.backend.dto.freecollab;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CollaborationTaskDependencyResponse {
    Long id;
    Long taskId;
    Long dependsOnTaskId;
}
