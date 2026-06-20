package com.teamtrace.backend.dto.teacher;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveTeacherScoreRequest {

    /** 评分目标类型：student 或 group，默认 student */
    private String targetType;

    @NotNull(message = "studentId不能为空")
    private Long studentId;

    /** 小组评分时使用 */
    private Long groupId;

    @NotNull(message = "score不能为空")
    @DecimalMin(value = "0", message = "分数不能小于0")
    @DecimalMax(value = "100", message = "分数不能大于100")
    private BigDecimal score;
}
