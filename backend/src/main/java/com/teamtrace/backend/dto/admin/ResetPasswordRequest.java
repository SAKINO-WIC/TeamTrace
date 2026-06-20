package com.teamtrace.backend.dto.admin;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {

    /**
     * 可选：指定新密码；为空则由后端生成临时密码。
     * 仅用于开发/教学环境。
     */
    @Size(min = 6, max = 64, message = "newPassword长度需在6~64之间")
    private String newPassword;
}

