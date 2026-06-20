package com.teamtrace.backend.dto.subtask;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewSubtaskRequest {

    /** true 通过（进入已完成）；false 打回重做（回到进行中） */
    @NotNull(message = "须指定是否通过")
    private Boolean approved;

    private String reviewComment;
}
