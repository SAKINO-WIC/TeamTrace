package com.teamtrace.backend.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentJoinGroupRequest {

    @NotBlank(message = "邀请码不能为空")
    @Size(max = 20)
    private String inviteCode;
}
