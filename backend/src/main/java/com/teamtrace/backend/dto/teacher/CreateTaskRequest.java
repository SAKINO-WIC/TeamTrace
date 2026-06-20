package com.teamtrace.backend.dto.teacher;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateTaskRequest {

    @NotBlank(message = "任务名称不能为空")
    @Size(max = 100, message = "任务名称长度不能超过100")
    private String name;

    private String description;

    /** ISO-8601，含时区，如 2026-04-20T23:59:00+08:00 */
    @NotBlank(message = "截止时间不能为空")
    private String deadline;

    @NotNull(message = "是否开启互评不能为空")
    private Boolean enablePeerReview;

    /** 开启互评时有效，默认 1 */
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

    @Valid
    @Size(max = 10, message = "任务附件最多10个")
    private List<TaskAttachmentRequest> attachments;
}
