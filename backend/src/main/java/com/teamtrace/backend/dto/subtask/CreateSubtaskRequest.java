package com.teamtrace.backend.dto.subtask;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateSubtaskRequest {

    @NotBlank(message = "子任务名称不能为空")
    @Size(max = 100)
    private String name;

    private String description;
    private String qualityRequirement;

    /** ISO-8601 含时区，与总任务截止时间格式一致 */
    @NotBlank(message = "子任务截止时间不能为空")
    private String deadline;
}
