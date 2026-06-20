package com.teamtrace.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "collaboration_space_members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_collaboration_space_member",
                        columnNames = {"space_id", "student_id"})
        },
        indexes = {
                @Index(name = "idx_collaboration_space_members_student_id", columnList = "studentId"),
                @Index(name = "idx_collaboration_space_members_space_id", columnList = "spaceId")
        })
public class CollaborationSpaceMember {

    public static final String ROLE_OWNER = "OWNER";
    public static final String ROLE_MEMBER = "MEMBER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "role", nullable = false, length = 20)
    private String role = ROLE_MEMBER;

    @Column(name = "is_deleted", nullable = false)
    private Integer isDeleted = 0;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;
}
