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
@Table(name = "subtask_submission_histories", indexes = {
        @Index(name = "idx_subtask_submission_histories_subtask_id", columnList = "subtask_id"),
        @Index(name = "idx_subtask_submission_histories_submitter_id", columnList = "submitter_id")
    })
public class SubtaskSubmissionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subtask_id", nullable = false)
    private Long subtaskId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "submitter_id", nullable = false)
    private Long submitterId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "submission_content", nullable = false, columnDefinition = "JSON")
    private String submissionContent;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "is_current", nullable = false)
    private Integer isCurrent;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
