package com.teamtrace.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "subtasks", indexes = {
        @Index(name = "idx_subtasks_task_id", columnList = "taskId"),
        @Index(name = "idx_subtasks_group_id", columnList = "groupId"),
        @Index(name = "idx_subtasks_assignee_id", columnList = "assigneeId"),
        @Index(name = "idx_subtasks_status", columnList = "status")
    })
public class Subtask {

    /** 1 待认领 2 进行中 3 待审批 4 已完成 */
    public static final int STATUS_PENDING_CLAIM = 1;

    public static final int STATUS_IN_PROGRESS = 2;
    public static final int STATUS_PENDING_REVIEW = 3;
    public static final int STATUS_DONE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subtask_uuid", nullable = false, unique = true)
    private Long subtaskUuid;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "quality_requirement", columnDefinition = "TEXT")
    private String qualityRequirement;

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(nullable = false)
    private LocalDateTime deadline;

    @Column(name = "reminder_time")
    private LocalDateTime reminderTime;

    @Column(nullable = false)
    private Integer status;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "submission_content", columnDefinition = "JSON")
    private String submissionContent;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "is_overdue", nullable = false)
    private Integer isOverdue;

    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(name = "is_deleted", nullable = false)
    private Integer isDeleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
