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
public class CreateCollaborationTaskRequest {

    private Long parentTaskId;

    @NotBlank(message = "任务名称不能为空")
    @Size(max = 160, message = "任务名称不能超过160个字符")
    private String title;

    @Size(max = 1500, message = "任务说明不能超过1500个字符")
    private String description;

    @Size(max = 1500, message = "交付要求不能超过1500个字符")
    private String deliverableRequirements;

    private Long assigneeId;

    private Long receiverId;

    private LocalDateTime startAt;

    private LocalDateTime dueAt;

    private List<Long> dependsOnTaskIds;

    @Valid
    @Size(max = 20, message = "flow nodes too many")
    private List<CollaborationTaskFlowNodeRequest> flowNodes;

    @Valid
    @Size(max = 10, message = "任务附件最多10个")
    private List<CollaborationAttachmentRequest> attachments;
}
