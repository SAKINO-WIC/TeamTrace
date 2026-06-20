package com.teamtrace.backend.dto.admin;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BatchRevokeTeacherInviteCodesResponse {
    private List<String> succeeded;
    private List<TeacherInviteCodeBatchFailureItem> failed;
}

