package com.teamtrace.backend.dto.teacher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TaskSummaryResponse {

    private Long taskId;
    private Long taskUuid;
    private Long classId;
    private String name;
    private LocalDateTime deadline;
    private Boolean enablePeerReview;
    private LocalDateTime peerReviewDeadline;
    private Integer peerReviewOffsetHours;
    private Integer peerReviewMaxScore;
    private BigDecimal peerReviewWeight;
    private BigDecimal teacherScoreWeight;
    private Integer status;
}
