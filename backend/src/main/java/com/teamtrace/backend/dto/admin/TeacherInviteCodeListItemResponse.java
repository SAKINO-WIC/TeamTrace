package com.teamtrace.backend.dto.admin;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TeacherInviteCodeListItemResponse {
    @Data
    @Builder
    public static class UsedByUserItem {
        private Long id;
        private String name;
        private String phone;
        private String email;
    }

    private Long id;
    private String code;
    private Integer status;
    private LocalDateTime expireAt;
    private Long usedBy;
    private LocalDateTime usedAt;
    private UsedByUserItem usedByUser;
}

