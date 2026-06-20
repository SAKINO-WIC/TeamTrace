package com.teamtrace.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_uuid", nullable = false, unique = true)
    private Long userUuid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.student;

    @Column(length = 11, unique = true)
    private String phone;

    @Column(length = 255, unique = true)
    private String email;

    @Column(length = 30)
    private String studentId;

    @Column(length = 512)
    private String avatarUrl;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private Integer status = 1;

    @Column(name = "is_deleted", nullable = false)
    private Integer isDeleted = 0;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "ceremony_no", unique = true)
    private Long ceremonyNo;

    @Column(name = "welcome_email_sent_at")
    private LocalDateTime welcomeEmailSentAt;

    @Column(name = "welcome_email_last_error", length = 500)
    private String welcomeEmailLastError;

    @Column(name = "ceremony_seen_at")
    private LocalDateTime ceremonySeenAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public enum Role {
        admin, teacher, student
    }
}
