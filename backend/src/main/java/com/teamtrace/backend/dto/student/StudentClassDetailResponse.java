package com.teamtrace.backend.dto.student;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StudentClassDetailResponse {

    private Long classId;
    private String classCode;
    private String name;
    private String semester;
    private Integer groupingLocked;
    private String studentStatus;
    private Long teacherId;
    private String teacherName;
    private Long studentCount;
    private Long groupId;
    private String groupName;
    private String groupJoinStatus;
}
