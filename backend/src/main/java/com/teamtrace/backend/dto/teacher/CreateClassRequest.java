package com.teamtrace.backend.dto.teacher;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateClassRequest {

    @NotBlank(message = "班级名称不能为空")
    @Size(max = 100, message = "班级名称长度不能超过100")
    private String name;

    @NotBlank(message = "学期不能为空")
    @Size(max = 20, message = "学期长度不能超过20")
    private String semester;

    @Min(value = 1, message = "小组最小人数不能小于1")
    @Max(value = 10, message = "小组最小人数不能大于10")
    private Integer groupSizeMin;

    @Min(value = 1, message = "小组最大人数不能小于1")
    @Max(value = 10, message = "小组最大人数不能大于10")
    private Integer groupSizeMax;
}
