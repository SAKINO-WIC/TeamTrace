package com.teamtrace.backend.dto.admin;

import com.teamtrace.backend.util.EmailValidation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateAdminUserRequest {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过50")
    private String name;

    @NotBlank(message = "邮箱不能为空")
    @Pattern(regexp = EmailValidation.ALLOWED_EMAIL_REGEXP, flags = Pattern.Flag.CASE_INSENSITIVE, message = EmailValidation.ALLOWED_EMAIL_MESSAGE)
    private String email;

    @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    @Min(value = 0, message = "status必须为0或1")
    @Max(value = 1, message = "status必须为0或1")
    private Integer status = 1;
}
