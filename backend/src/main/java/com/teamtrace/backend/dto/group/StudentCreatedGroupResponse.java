package com.teamtrace.backend.dto.group;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StudentCreatedGroupResponse {
    TaskGroupResponse group;
    String inviteCode;
    LocalDateTime inviteCodeExpireAt;
}
