package com.teamtrace.backend.dto.teacher;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskAttachmentRequest {

    @Size(max = 20, message = "附件类型长度不能超过20")
    private String type;

    @Size(max = 255, message = "附件名称长度不能超过255")
    private String name;

    @Size(max = 1000, message = "附件地址长度不能超过1000")
    private String url;

    private Long size;
}
