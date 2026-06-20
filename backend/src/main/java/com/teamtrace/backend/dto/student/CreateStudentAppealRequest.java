package com.teamtrace.backend.dto.student;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateStudentAppealRequest {

    @NotBlank(message = "申诉理由不能为空")
    private String reason;

    /**
     * {@code teacher_score} 教师评分、{@code peer_review} 互评异常、{@code task_review} 子任务/进度误判。省略时按
     * {@code teacher_score} 处理（兼容旧客户端）。
     */
    private String type;

    /** {@code type=task_review} 时必填，须为本任务下、学生所在组内的子任务。 */
    private Long subtaskId;

    /** 可选，JSON 数组字符串，如 ["https://..."] */
    private String attachments;
}
