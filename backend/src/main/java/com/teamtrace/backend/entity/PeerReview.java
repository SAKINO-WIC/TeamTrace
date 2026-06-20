package com.teamtrace.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "peer_reviews", indexes = {
        @Index(name = "idx_peer_reviews_task_id", columnList = "taskId"),
        @Index(name = "idx_peer_reviews_group_id", columnList = "groupId"),
        @Index(name = "idx_peer_reviews_reviewer_id", columnList = "reviewerId"),
        @Index(name = "idx_peer_reviews_reviewee_id", columnList = "revieweeId")
    })
public class PeerReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Column(name = "reviewee_id", nullable = false)
    private Long revieweeId;

    @Column(nullable = false, precision = 5, scale = 1)
    private BigDecimal score;

    @Column(length = 500)
    private String comment;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "is_deleted", nullable = false)
    private Integer isDeleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Version
    @Column(nullable = false)
    private Integer version;
}
