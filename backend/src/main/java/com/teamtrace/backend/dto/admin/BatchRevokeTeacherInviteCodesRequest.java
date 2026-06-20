package com.teamtrace.backend.dto.admin;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class BatchRevokeTeacherInviteCodesRequest {
    @NotEmpty(message = "codes不能为空")
    private List<String> codes;
}

