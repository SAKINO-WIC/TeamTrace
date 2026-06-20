package com.teamtrace.backend.dto.teacher;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TeacherClassDetailResponse {
    private Long classId;
    private String classCode;
    private String name;
    private String semester;
    private Integer groupSizeMin;
    private Integer groupSizeMax;
    private Integer groupingLocked;
    private Integer status;
    private String activeInviteCode;
    private LocalDateTime inviteExpireAt;
    private Long studentCount;
}
