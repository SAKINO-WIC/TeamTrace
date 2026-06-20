package com.teamtrace.backend.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class BatchCreateTeacherInviteCodesRequest {

    @Min(value = 1, message = "count必须>=1")
    @Max(value = 200, message = "count必须<=200")
    private Integer count;

    /**
     * 过期天数（可选）。为空时由服务端使用默认值（与单个生成一致）。
     */
    private Integer expireDays;
}

