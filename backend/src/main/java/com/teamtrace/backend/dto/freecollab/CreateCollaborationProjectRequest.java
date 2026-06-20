package com.teamtrace.backend.dto.freecollab;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCollaborationProjectRequest {

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 120, message = "项目名称不能超过120个字符")
    private String title;

    @Size(max = 1000, message = "项目说明不能超过1000个字符")
    private String description;

    private LocalDateTime startAt;

    private LocalDateTime dueAt;

    @Valid
    @Size(max = 10, message = "项目附件最多10个")
    private List<CollaborationAttachmentRequest> attachments;
}
