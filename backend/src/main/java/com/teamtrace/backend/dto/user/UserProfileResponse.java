package com.teamtrace.backend.dto.user;

import com.teamtrace.backend.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class UserProfileResponse {
    private Long id;
    private String name;
    private String email;
    private String studentId;
    private String avatarUrl;
    private String role;
    private String inviteCode;

    public static UserProfileResponse from(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .studentId(user.getStudentId())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole().name())
                .build();
    }

    public static UserProfileResponse from(User user, String inviteCode) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .studentId(user.getStudentId())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole().name())
                .inviteCode(inviteCode)
                .build();
    }
}
