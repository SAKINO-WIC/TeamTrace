package com.teamtrace.backend.dto.teacher;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherProfileResponse {
    private Long userId;
    private String name;
    private String phone;
    private String email;
    private String role;
    private String createdAt;
}
