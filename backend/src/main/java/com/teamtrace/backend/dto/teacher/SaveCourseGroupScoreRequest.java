package com.teamtrace.backend.dto.teacher;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveCourseGroupScoreRequest {

    @NotNull(message = "groupId cannot be null")
    private Long groupId;

    @NotNull(message = "totalScore cannot be null")
    @DecimalMin(value = "0", message = "score cannot be less than 0")
    @DecimalMax(value = "100", message = "score cannot be greater than 100")
    private BigDecimal totalScore;
}
