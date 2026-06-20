package com.teamtrace.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank(message = "邮箱不能为空")
    @Pattern(regexp = com.teamtrace.backend.util.EmailValidation.ALLOWED_EMAIL_REGEXP, flags = Pattern.Flag.CASE_INSENSITIVE, message = com.teamtrace.backend.util.EmailValidation.ALLOWED_EMAIL_MESSAGE)
    private String email;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码必须是6位数字")
    private String verifyCode;

    @NotBlank(message = "密码不能为空")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d\\s]).{8,64}$",
            message = "密码必须包含大写字母、小写字母、数字和特殊符号，且不少于8位"
    )
    private String newPassword;
}
