package com.teamtrace.backend.dto.student;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentDeleteAccountRequest {

    @NotBlank(message = "请输入登录密码")
    private String password;
}
