package com.teamtrace.backend.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String role;
    private UserProfile user;
    private CeremonyInfoResponse ceremony;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class UserProfile {
        private Long id;
        private String name;
        private String email;
        private String studentId;
        private String avatarUrl;
        /** @deprecated 旧账号兼容，新用户为空 */
        private String phone;
    }
}
