package com.teamtrace.backend.dto.teacher;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新任务：仅传需要修改的字段；全空则服务端拒绝。
 */
@Getter
@Setter
public class UpdateTaskRequest {

    @Size(max = 100, message = "任务名称长度不能超过100")
    private String name;

    private String description;

    /** ISO-8601，含时区 */
    private String deadline;

    /**
     * 互评阶段结束时间（ISO-8601，含时区）。仅当任务已开启互评时可传；须晚于任务截止时间、晚于当前时间，
     * 且须严格晚于当前已生效的互评截止时间（仅允许延后，用于 P1「延长 / 重新开放互评」）。
     */
    private String peerReviewDeadline;

    private Boolean enablePeerReview;

    @Min(value = 1, message = "互评时长至少1小时")
    @Max(value = 8760, message = "互评时长过大")
    private Integer peerReviewOffsetHours;

    @Min(value = 1, message = "互评满分至少为1")
    @Max(value = 1000, message = "互评满分过大")
    private Integer peerReviewMaxScore;

    @DecimalMin(value = "0", message = "互评权重不能小于0")
    @DecimalMax(value = "1", message = "互评权重不能大于1")
    private BigDecimal peerReviewWeight;

    @DecimalMin(value = "0", message = "教师评分权重不能小于0")
    @DecimalMax(value = "1", message = "教师评分权重不能大于1")
    private BigDecimal teacherScoreWeight;

    @jakarta.validation.Valid
    @Size(max = 10, message = "任务附件最多10个")
    private List<TaskAttachmentRequest> attachments;
}
