package com.teamtrace.backend.dto.teacher;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResolveAppealRequest {

    /** 2 驳回 3 成功（与 appeals.status 一致） */
    @NotNull(message = "处理结果不能为空")
    @Min(value = 2, message = "处理结果须为 2（驳回）或 3（成功）")
    @Max(value = 3, message = "处理结果须为 2（驳回）或 3（成功）")
    private Integer outcome;

    /** 教师说明（建议填写） */
    private String teacherResponse;

    /**
     * Deprecated compatibility field.
     * Appeal resolution only records the teacher response and no longer updates {@code teacher_scores}.
     * Senders should adjust grades in the score center instead.
     */
    private BigDecimal adjustedTeacherScore;
}
