package com.teamtrace.backend.dto.freecollab;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JoinCollaborationSpaceRequest {

    @NotBlank(message = "邀请码不能为空")
    private String inviteCode;
}
