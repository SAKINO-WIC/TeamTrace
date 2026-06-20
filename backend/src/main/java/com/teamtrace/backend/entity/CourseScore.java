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

@Entity
@Table(name = "course_scores", indexes = {
        @Index(name = "idx_course_scores_class_id", columnList = "classId"),
        @Index(name = "idx_course_scores_student_id", columnList = "studentId")
    })
@Getter
@Setter
public class CourseScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "total_score", precision = 5, scale = 1)
    private BigDecimal totalScore;

    @Column(name = "weight_config", columnDefinition = "JSON")
    private String weightConfig;

    @Column(name = "score_type", nullable = false)
    private Integer scoreType = 1; // 1=系统计算 2=教师手动覆盖

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @Column(name = "is_deleted")
    private Integer isDeleted = 0;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Version
    @Column(name = "version")
    private Integer version = 0;
}
