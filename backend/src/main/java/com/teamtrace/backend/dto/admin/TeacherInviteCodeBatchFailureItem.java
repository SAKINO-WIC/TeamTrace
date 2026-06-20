package com.teamtrace.backend.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TeacherInviteCodeBatchFailureItem {
    private String code;
    private String errorCode;
    private String message;
}

