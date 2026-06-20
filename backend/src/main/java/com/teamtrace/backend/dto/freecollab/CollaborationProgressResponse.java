package com.teamtrace.backend.dto.freecollab;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CollaborationProgressResponse {
    CollaborationProjectResponse project;
    Integer taskCount;
    Integer completedTaskCount;
    Integer waitingReceiveCount;
    Integer overdueTaskCount;
    Double completionRate;
    List<CollaborationTaskResponse> tasks;
    List<CollaborationTaskDependencyResponse> dependencies;
}
