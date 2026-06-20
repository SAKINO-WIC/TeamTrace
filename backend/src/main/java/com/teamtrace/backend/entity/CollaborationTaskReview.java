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
@Table(name = "collaboration_task_reviews", indexes = {
        @Index(name = "idx_collaboration_task_reviews_task", columnList = "task_id"),
        @Index(name = "idx_collaboration_task_reviews_submission", columnList = "submission_id")
})
public class CollaborationTaskReview {

    public static final String RESULT_ACCEPTED = "ACCEPTED";
    public static final String RESULT_RETURNED = "RETURNED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Column(name = "result", nullable = false, length = 30)
    private String result;

    @Column(name = "comment", length = 1500)
    private String comment;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
