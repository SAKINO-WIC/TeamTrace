package com.teamtrace.backend.dto.freecollab;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CollaborationSpaceMemberResponse {
    Long studentId;
    String name;
    String avatarUrl;
    String role;
    LocalDateTime joinedAt;
}
