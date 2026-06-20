package com.teamtrace.backend.dto.admin;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserListItemResponse {
    private Long id;
    private String role;
    private String phone;
    private String email;
    private String name;
    private Integer status;
    private Integer isDeleted;
    private Long ceremonyNo;
    private String ceremonyCode;
    private String teacherInviteCode;
    private LocalDateTime welcomeEmailSentAt;
    private String welcomeEmailLastError;
    private LocalDateTime createdAt;
}
