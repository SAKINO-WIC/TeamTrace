package com.teamtrace.backend.dto.subtask;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmitSubtaskRequest {

    /** JSON 字符串，如 {"text":"说明","attachments":[]} */
    @NotBlank(message = "提交内容不能为空")
    private String submissionContent;
}
