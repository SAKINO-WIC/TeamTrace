package com.teamtrace.backend.dto.teacher;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ClassResponse {
    private Long classId;
    private String classCode;
    private String name;
    private String semester;
    private Integer groupSizeMin;
    private Integer groupSizeMax;
    private Integer groupingLocked;
    private Integer status;
}
