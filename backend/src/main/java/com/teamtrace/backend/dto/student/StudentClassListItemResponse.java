package com.teamtrace.backend.dto.student;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StudentClassListItemResponse {

    private Long classId;
    private String classCode;
    private String name;
    private String semester;
    private Integer groupingLocked;
    private String studentStatus;
    private Long groupId;
    private String groupName;
    private String groupJoinStatus;
    private Long teacherId;
    private String teacherName;
}
