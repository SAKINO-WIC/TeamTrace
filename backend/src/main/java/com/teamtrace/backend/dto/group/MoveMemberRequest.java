package com.teamtrace.backend.dto.group;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MoveMemberRequest {

    @NotNull(message = "目标小组不能为空")
    private Long targetGroupId;
}
