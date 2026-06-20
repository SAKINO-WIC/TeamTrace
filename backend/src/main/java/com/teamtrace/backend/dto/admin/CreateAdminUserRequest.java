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
public class CreateAdminUserRequest {

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(teacher|student)$", message = "管理员端仅支持新增教师或学生")
    private String role;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过50")
    private String name;

    @NotBlank(message = "邮箱不能为空")
    @Pattern(regexp = EmailValidation.ALLOWED_EMAIL_REGEXP, flags = Pattern.Flag.CASE_INSENSITIVE, message = EmailValidation.ALLOWED_EMAIL_MESSAGE)
    private String email;

    @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d\\s]).{8,64}$",
            message = "密码必须包含大写字母、小写字母、数字和特殊符号，且不少于8位"
    )
    private String password;

    @Min(value = 0, message = "status必须为0或1")
    @Max(value = 1, message = "status必须为0或1")
    private Integer status = 1;
}
