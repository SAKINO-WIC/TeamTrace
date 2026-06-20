package com.teamtrace.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 对应库表 {@code groups}。{@code task_id = NULL} 表示班级学期固定小组，全学期各任务共用；非空为历史按任务建组数据。
 */
@Getter
@Setter
@Entity
@Table(name = "`groups`", indexes = {
        @Index(name = "idx_groups_class_id", columnList = "classId"),
        @Index(name = "idx_groups_task_id", columnList = "taskId"),
        @Index(name = "idx_groups_leader_id", columnList = "leaderId")
    })
public class TaskGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "leader_id", nullable = false)
    private Long leaderId;

    @Column(name = "invite_code", length = 20)
    private String inviteCode;

    @Column(name = "invite_code_expire")
    private LocalDateTime inviteCodeExpire;

    @Column(name = "invite_code_expire_minutes")
    private Integer inviteCodeExpireMinutes;

    @Column(name = "join_mode", nullable = false)
    private Integer joinMode;

    @Column(name = "is_teacher_created", nullable = false)
    private Integer isTeacherCreated;

    @Column(nullable = false)
    private Integer status;

    @Column(name = "is_deleted", nullable = false)
    private Integer isDeleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
