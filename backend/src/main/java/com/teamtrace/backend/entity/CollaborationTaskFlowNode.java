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
@Table(name = "collaboration_task_flow_nodes", indexes = {
        @Index(name = "idx_collaboration_task_flow_nodes_task", columnList = "task_id,step_order"),
        @Index(name = "idx_collaboration_task_flow_nodes_assignee", columnList = "assignee_id"),
        @Index(name = "idx_collaboration_task_flow_nodes_status", columnList = "status")
})
public class CollaborationTaskFlowNode {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_UNCLAIMED = "UNCLAIMED";
    public static final String STATUS_CLAIMED = "CLAIMED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_RETURNED = "RETURNED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(name = "claimable", nullable = false)
    private Boolean claimable = false;

    @Column(name = "status", nullable = false, length = 30)
    private String status = STATUS_PENDING;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

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
