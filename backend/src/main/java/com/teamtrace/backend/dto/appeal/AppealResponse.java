package com.teamtrace.backend.dto.appeal;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AppealResponse {
    Long id;
    Long studentId;
    Long taskId;
    Long subtaskId;
    String type;
    String reason;
    String attachments;
    Integer status;
    String teacherResponse;
    LocalDateTime createdAt;
    LocalDateTime handledAt;
}
