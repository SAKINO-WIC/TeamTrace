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

@Getter
@Setter
@Entity
@Table(name = "task_group_report_histories", indexes = {
        @Index(name = "idx_task_group_report_histories_report_id", columnList = "reportId"),
        @Index(name = "idx_task_group_report_histories_task_group", columnList = "taskId,groupId")
    })
public class TaskGroupReportHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

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

    @Column(name = "is_current", nullable = false)
    private Integer isCurrent;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
