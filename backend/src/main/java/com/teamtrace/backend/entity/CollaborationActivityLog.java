package com.teamtrace.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "collaboration_activity_logs", indexes = {
        @Index(name = "idx_collaboration_logs_space_time", columnList = "space_id,created_at"),
        @Index(name = "idx_collaboration_logs_project", columnList = "project_id"),
        @Index(name = "idx_collaboration_logs_task", columnList = "task_id")
})
public class CollaborationActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    @Column(name = "detail_json", columnDefinition = "TEXT")
    private String detailJson;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
