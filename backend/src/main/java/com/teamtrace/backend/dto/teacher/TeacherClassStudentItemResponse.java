package com.teamtrace.backend.dto.teacher;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TeacherClassStudentItemResponse {
    private Long studentId;
    private String name;
    private String phone;
    private String email;
    private LocalDateTime joinTime;
}
