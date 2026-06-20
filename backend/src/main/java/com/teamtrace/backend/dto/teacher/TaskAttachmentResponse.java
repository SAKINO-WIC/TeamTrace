package com.teamtrace.backend.dto.teacher;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TaskAttachmentResponse {

    private Long attachmentId;
    private String type;
    private String name;
    private String url;
    private Long size;
    private Integer sortOrder;
}
