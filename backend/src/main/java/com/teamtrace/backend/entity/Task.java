package com.teamtrace.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "tasks", indexes = {
        @Index(name = "idx_tasks_class_id", columnList = "classId"),
        @Index(name = "idx_tasks_teacher_id", columnList = "teacherId")
    })
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_uuid", nullable = false, unique = true)
    private Long taskUuid;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private LocalDateTime deadline;

    @Column(name = "enable_peer_review", nullable = false)
    private Integer enablePeerReview;

    @Column(name = "peer_review_deadline")
    private LocalDateTime peerReviewDeadline;

    @Column(name = "peer_review_offset_hours")
    private Integer peerReviewOffsetHours;

    @Column(name = "peer_review_max_score")
    private Integer peerReviewMaxScore;

    @Column(name = "peer_review_weight", nullable = false, precision = 3, scale = 2)
    private BigDecimal peerReviewWeight;

    @Column(name = "teacher_score_weight", nullable = false, precision = 3, scale = 2)
    private BigDecimal teacherScoreWeight;

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
