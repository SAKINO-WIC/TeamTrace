package com.teamtrace.backend.dto.freecollab;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CollaborationDashboardResponse {
    Integer spaceCount;
    Integer ownedSpaceCount;
    Integer activeProjectCount;
    Integer myActiveTaskCount;
    Integer waitingForMeCount;
    Integer waitingForOthersCount;
    Integer dueSoonCount;
    List<CollaborationTaskResponse> myTasks;
    List<CollaborationTaskResponse> waitingForMe;
    List<CollaborationTaskResponse> waitingForOthers;
    List<CollaborationTaskResponse> dueSoonTasks;
    List<CollaborationActivityLogResponse> recentActivities;
}
