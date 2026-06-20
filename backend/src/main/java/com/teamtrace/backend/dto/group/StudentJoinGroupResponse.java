package com.teamtrace.backend.dto.group;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StudentJoinGroupResponse {
    Long groupId;
    Long classId;
    /** {@code active} 已入组；{@code pending} 待组长审批 */
    String membershipStatus;
}
