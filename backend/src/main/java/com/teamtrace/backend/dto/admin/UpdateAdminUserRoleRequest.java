package com.teamtrace.backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateAdminUserRoleRequest {

    @NotBlank(message = "目标角色不能为空")
    @Pattern(regexp = "teacher|student", message = "目标角色只能是teacher或student")
    private String role;

    private String inviteCode;
}
