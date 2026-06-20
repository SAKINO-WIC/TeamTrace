package com.teamtrace.backend.dto.teacher;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TeacherClassListItemResponse {
    private Long classId;
    private String classCode;
    private String name;
    private String semester;
    private Integer status;
    private Integer groupingLocked;
    private Long studentCount;
    private Long groupCount;
    private String activeInviteCode;
    private LocalDateTime inviteExpireAt;
    private LocalDateTime deletedAt;
}
