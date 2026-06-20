package com.teamtrace.backend.dto.freecollab;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransferCollaborationOwnerRequest {
    @NotNull(message = "新发起人不能为空")
    private Long newOwnerId;
}
