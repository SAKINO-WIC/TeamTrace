package com.teamtrace.backend.dto.freecollab;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmitCollaborationTaskRequest {

    private Long flowNodeId;

    @Size(max = 3000, message = "提交说明不能超过3000个字符")
    private String content;

    private String attachmentsJson;

    private String linksJson;
}
