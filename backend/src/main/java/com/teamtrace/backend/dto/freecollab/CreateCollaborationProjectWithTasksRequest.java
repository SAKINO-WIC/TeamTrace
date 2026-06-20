package com.teamtrace.backend.dto.freecollab;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCollaborationProjectWithTasksRequest {

    @Valid
    @NotNull(message = "项目内容不能为空")
    private ProjectPayload project;

    @Valid
    @Size(min = 1, max = 80, message = "任务数量必须在1到80个之间")
    private List<TaskPayload> tasks;

    @Getter
    @Setter
    public static class ProjectPayload {

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

    @Getter
    @Setter
    public static class TaskPayload {

        @NotBlank(message = "任务本地编号不能为空")
        @Size(max = 64, message = "任务本地编号不能超过64个字符")
        private String localId;

        @Size(max = 64, message = "父级任务本地编号不能超过64个字符")
        private String parentLocalId;

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

        @Size(max = 80, message = "前置任务数量不能超过80个")
        private List<@Size(max = 64, message = "前置任务本地编号不能超过64个字符") String> dependsOnLocalIds;

        @Valid
        @Size(max = 20, message = "flow nodes too many")
        private List<CollaborationTaskFlowNodeRequest> flowNodes;

        @Valid
        @Size(max = 10, message = "任务附件最多10个")
        private List<CollaborationAttachmentRequest> attachments;
    }
}
