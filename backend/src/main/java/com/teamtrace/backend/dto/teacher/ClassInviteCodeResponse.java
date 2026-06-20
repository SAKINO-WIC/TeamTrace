package com.teamtrace.backend.dto.teacher;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ClassInviteCodeResponse {
    private Long classId;
    private String code;
    private Integer status;
    private LocalDateTime expireAt;
}

