package com.teamtrace.backend.dto.teacher;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseScoreItemResponse {
    private Long studentId;
    private String studentName;
    private BigDecimal totalScore;
    private Integer scoreType; // 1=系统计算 2=教师手动覆盖
    private String calculatedAt;
}
