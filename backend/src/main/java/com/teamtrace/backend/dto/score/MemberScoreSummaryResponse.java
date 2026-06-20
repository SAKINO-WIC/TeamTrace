package com.teamtrace.backend.dto.score;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/** 某学生在某任务某小组下的加权分说明（只读汇总）。 */
@Value
@Builder
public class MemberScoreSummaryResponse {
    Long studentId;
    boolean peerReviewApplicable;
    BigDecimal peerReviewWeight;
    BigDecimal teacherScoreWeight;
    Integer peerReviewMaxScore;
    /** 收到的互评分数平均值（原始量纲，0～peerReviewMaxScore） */
    BigDecimal peerAverageReceived;
    /** 互评归一到 0～100 */
    BigDecimal peerAverageOn100;
    BigDecimal teacherScore;
    /** 加权总分（0～100）；互评适用时需互评与教师分齐全，否则为 null */
    BigDecimal weightedTotal100;
}
