package com.teamtrace.backend.dto.freecollab;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCollaborationSpaceRequest {

    @NotBlank(message = "协作空间名称不能为空")
    @Size(max = 100, message = "协作空间名称不能超过100个字符")
    private String name;

    @Size(max = 500, message = "协作空间说明不能超过500个字符")
    private String description;
}
