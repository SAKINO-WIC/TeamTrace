package com.teamtrace.backend.dto.freecollab;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CollaborationSpaceInviteCodeResponse {
    Long spaceId;
    String code;
    LocalDateTime expiresAt;
}
