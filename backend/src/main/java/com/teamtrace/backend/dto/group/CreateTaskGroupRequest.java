package com.teamtrace.backend.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateTaskGroupRequest {

    @NotBlank(message = "小组名称不能为空")
    @Size(max = 100, message = "小组名称过长")
    private String name;

    @NotNull(message = "组长不能为空")
    private Long leaderId;

    /** 组员用户 ID（须含组长；均在班级内） */
    @NotEmpty(message = "至少一名成员")
    private List<Long> memberStudentIds;
}
