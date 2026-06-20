package com.teamtrace.backend.dto.subtask;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SubtaskProgressResponse {
    Long groupId;
    long groupCompletedSubtasks;
    long groupClaimedSubtasks;
    BigDecimal groupProgressPercent;
    long groupTotalSubtasks;
    List<MemberSubtaskProgress> members;
}
