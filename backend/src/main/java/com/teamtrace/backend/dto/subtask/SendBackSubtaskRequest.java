package com.teamtrace.backend.dto.subtask;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** 组长将「已完成」子任务打回，进度回退为进行中（需求：打回后需重做） */
@Getter
@Setter
public class SendBackSubtaskRequest {

    @NotBlank(message = "打回说明不能为空")
    private String reviewComment;
}
