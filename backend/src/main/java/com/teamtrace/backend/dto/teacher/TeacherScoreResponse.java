package com.teamtrace.backend.dto.teacher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
public class TeacherScoreResponse {
    private Long studentId;
    private Long groupId;
    private String studentName;
    private BigDecimal score;
    private Long scoredBy;
    private LocalDateTime scoredAt;
}
