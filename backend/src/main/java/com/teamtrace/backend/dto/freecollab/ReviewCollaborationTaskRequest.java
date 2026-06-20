package com.teamtrace.backend.dto.freecollab;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewCollaborationTaskRequest {

    @Size(max = 1500, message = "处理说明不能超过1500个字符")
    private String comment;
}
