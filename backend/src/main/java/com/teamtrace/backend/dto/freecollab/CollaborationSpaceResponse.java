package com.teamtrace.backend.dto.freecollab;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CollaborationSpaceResponse {
    Long id;
    String name;
    String description;
    Long creatorId;
    String creatorName;
    String myRole;
    Integer memberCount;
    String activeInviteCode;
    LocalDateTime inviteCodeExpiresAt;
    List<CollaborationSpaceMemberResponse> members;
    LocalDateTime createdAt;
}
