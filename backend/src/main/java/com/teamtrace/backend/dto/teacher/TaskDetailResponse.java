package com.teamtrace.backend.dto.teacher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TaskDetailResponse {

    private Long taskId;
    private Long taskUuid;
    private Long classId;
    private String name;
    private String description;
    private LocalDateTime deadline;
    private Boolean enablePeerReview;
    private LocalDateTime peerReviewDeadline;
    private Integer peerReviewOffsetHours;
    private Integer peerReviewMaxScore;
    private BigDecimal peerReviewWeight;
    private BigDecimal teacherScoreWeight;
    private Integer status;
    private Boolean isOverdue;
    private Boolean canPeerReviewNow;
    private String peerReviewPhase;
    private Boolean canSubmitAppeal;
    private List<TaskAttachmentResponse> attachments;
}
