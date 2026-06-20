package com.teamtrace.backend.dto.teacher;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GroupingLockRequest {

    /** true=锁定分组，false=解锁 */
    @NotNull(message = "locked 不能为空")
    private Boolean locked;
}
