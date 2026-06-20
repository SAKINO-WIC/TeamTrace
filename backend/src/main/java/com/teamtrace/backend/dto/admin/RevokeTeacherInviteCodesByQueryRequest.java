package com.teamtrace.backend.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class RevokeTeacherInviteCodesByQueryRequest {
    private Integer status;
    private Boolean expired;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime expireFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime expireTo;

    @Min(value = 1, message = "limit必须>=1")
    @Max(value = 200, message = "limit必须<=200")
    private Integer limit = 200;
}

