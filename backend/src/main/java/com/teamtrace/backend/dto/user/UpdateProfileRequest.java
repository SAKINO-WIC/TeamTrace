package com.teamtrace.backend.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名最长50字")
    private String name;

    @Size(max = 30, message = "学号最长30位")
    private String studentId;
}
