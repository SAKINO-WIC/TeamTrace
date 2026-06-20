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
@Table(name = "teacher_scores", indexes = {
        @Index(name = "idx_teacher_scores_task_id", columnList = "taskId"),
        @Index(name = "idx_teacher_scores_target", columnList = "targetType,targetId")
    })
public class TeacherScore {

    public static final String TARGET_STUDENT = "student";
    public static final String TARGET_GROUP = "group";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "target_type", nullable = false, columnDefinition = "ENUM('student','group')")
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(nullable = false, precision = 5, scale = 1)
    private BigDecimal score;

    @Column(name = "scored_by", nullable = false)
    private Long scoredBy;

    @Column(name = "scored_at", nullable = false)
    private LocalDateTime scoredAt;

    @Column(name = "is_deleted", nullable = false)
    private Integer isDeleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Version
    @Column(nullable = false)
    private Integer version;
}
