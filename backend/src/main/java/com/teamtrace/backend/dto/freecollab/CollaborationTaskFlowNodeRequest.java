package com.teamtrace.backend.dto.freecollab;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CollaborationTaskFlowNodeRequest {

    @Size(max = 160, message = "step title too long")
    private String title;

    @Size(max = 1000, message = "step description too long")
    private String description;

    private Long assigneeId;

    private Boolean claimable;
}
