package com.teamtrace.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendEmailCodeRequest {

    @NotBlank(message = "邮箱不能为空")
    @Pattern(regexp = com.teamtrace.backend.util.EmailValidation.ALLOWED_EMAIL_REGEXP, flags = Pattern.Flag.CASE_INSENSITIVE, message = com.teamtrace.backend.util.EmailValidation.ALLOWED_EMAIL_MESSAGE)
    private String email;

    @NotBlank(message = "用途不能为空")
    @Pattern(regexp = "^(register|reset_password|delete_account)$", message = "用途不合法")
    private String purpose;
}
