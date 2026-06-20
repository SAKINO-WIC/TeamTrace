package com.teamtrace.backend.dto.teacher;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveCourseScoreRequest {

    @NotNull(message = "studentId不能为空")
    private Long studentId;

    @NotNull(message = "totalScore不能为空")
    @DecimalMin(value = "0", message = "分数不能小于0")
    @DecimalMax(value = "100", message = "分数不能大于100")
    private BigDecimal totalScore;
}
