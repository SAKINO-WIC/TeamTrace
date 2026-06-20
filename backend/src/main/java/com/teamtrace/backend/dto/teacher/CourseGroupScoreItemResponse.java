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
public class CourseGroupScoreItemResponse {
    private Long groupId;
    private String groupName;
    private BigDecimal totalScore;
    private Integer scoreType;
    private String calculatedAt;
}
