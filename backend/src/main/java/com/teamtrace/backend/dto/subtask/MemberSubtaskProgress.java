package com.teamtrace.backend.dto.subtask;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MemberSubtaskProgress {
    Long studentId;
    long completedSubtasks;
    long claimedSubtasks;
    BigDecimal progressPercent;
    String studentName;
}
