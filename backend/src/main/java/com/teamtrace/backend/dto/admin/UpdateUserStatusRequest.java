package com.teamtrace.backend.dto.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserStatusRequest {

    /** 1 启用；0 禁用 */
    @NotNull(message = "status不能为空")
    @Min(value = 0, message = "status必须为0或1")
    @Max(value = 1, message = "status必须为0或1")
    private Integer status;
}

