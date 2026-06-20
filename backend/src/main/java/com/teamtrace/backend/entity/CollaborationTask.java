package com.teamtrace.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "collaboration_tasks", indexes = {
        @Index(name = "idx_collaboration_tasks_space_project", columnList = "space_id,project_id"),
        @Index(name = "idx_collaboration_tasks_assignee", columnList = "assignee_id"),
        @Index(name = "idx_collaboration_tasks_receiver", columnList = "receiver_id")
})
public class CollaborationTask {

    public static final String CLAIM_MODE_OPEN = "OPEN";
    public static final String CLAIM_MODE_ASSIGNED = "ASSIGNED";

    public static final String STATUS_UNCLAIMED = "UNCLAIMED";
    public static final String STATUS_CLAIMED = "CLAIMED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_WAITING_RECEIVE = "WAITING_RECEIVE";
    public static final String STATUS_RETURNED = "RETURNED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(name = "parent_task_id")
    private Long parentTaskId;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "description", length = 1500)
    private String description;

    @Column(name = "deliverable_requirements", length = 1500)
    private String deliverableRequirements;

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(name = "receiver_id")
    private Long receiverId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "claim_mode", nullable = false, length = 20)
    private String claimMode = CLAIM_MODE_OPEN;

    @Column(name = "status", nullable = false, length = 30)
    private String status = STATUS_UNCLAIMED;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
