package com.teamtrace.backend.dto.report;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmitTaskGroupReportRequest {

    /** JSON 字符串，如 {"text":"总报告说明","link":"https://...","files":[]} */
    @NotBlank(message = "总报告内容不能为空")
    private String reportContent;
}
