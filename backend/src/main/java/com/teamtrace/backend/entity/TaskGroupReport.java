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
        name = "task_group_reports",
        indexes = {
            @Index(name = "idx_task_group_reports_task_id", columnList = "taskId"),
            @Index(name = "idx_task_group_reports_group_id", columnList = "groupId")
        },
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_task_group_reports_task_group", columnNames = {"task_id", "group_id"})
        })
public class TaskGroupReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "submitter_id", nullable = false)
    private Long submitterId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "report_content", nullable = false, columnDefinition = "JSON")
    private String reportContent;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "is_deleted", nullable = false)
    private Integer isDeleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
