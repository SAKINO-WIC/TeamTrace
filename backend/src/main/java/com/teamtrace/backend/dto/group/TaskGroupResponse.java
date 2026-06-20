package com.teamtrace.backend.dto.group;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TaskGroupResponse {
    Long groupId;
    Long classId;
    Long taskId;
    String name;
    Long leaderId;
    List<Long> memberStudentIds;
    Map<Long, String> memberNames;
    String inviteCode;
    java.time.LocalDateTime inviteCodeExpire;
    String leaderName;
}
