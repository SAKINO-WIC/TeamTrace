package com.teamtrace.backend.dto.group;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentCreateSemesterGroupRequest {

    @NotBlank(message = "小组名称不能为空")
    @Size(max = 100, message = "小组名称过长")
    private String name;

    /** 1 直通 2 审批（与库表 {@code groups.join_mode} 一致） */
    @NotNull(message = "joinMode 不能为空")
    @Min(1)
    @Max(2)
    private Integer joinMode;

    /** 小组邀请码有效期（分钟），默认 1440（24h） */
    @Min(1)
    @Max(10080)
    private Integer inviteCodeExpireMinutes;
}
