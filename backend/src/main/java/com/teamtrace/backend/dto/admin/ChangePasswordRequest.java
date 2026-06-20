package com.teamtrace.backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequest {

    @NotBlank(message = "oldPassword不能为空")
    private String oldPassword;

    @NotBlank(message = "newPassword不能为空")
    @Size(min = 6, max = 64, message = "newPassword长度需在6~64之间")
    private String newPassword;
}

