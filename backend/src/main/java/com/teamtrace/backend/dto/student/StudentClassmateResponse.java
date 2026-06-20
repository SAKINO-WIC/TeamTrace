package com.teamtrace.backend.dto.student;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StudentClassmateResponse {
    private Long studentId;
    private String name;
    private String groupName;
    private Boolean isLeader;
}
