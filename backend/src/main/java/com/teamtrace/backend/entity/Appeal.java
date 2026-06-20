package com.teamtrace.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "appeals", indexes = {
        @Index(name = "idx_appeals_student_id", columnList = "studentId"),
        @Index(name = "idx_appeals_task_id", columnList = "taskId"),
        @Index(name = "idx_appeals_status", columnList = "status")
    })
public class Appeal {

    public static final String TYPE_TEACHER_SCORE = "teacher_score";

    public static final String TYPE_PEER_REVIEW = "peer_review";

    public static final String TYPE_TASK_REVIEW = "task_review";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "subtask_id")
    private Long subtaskId;

    /**
     * 与库 ENUM 一致：{@value #TYPE_TASK_REVIEW}、{@value #TYPE_PEER_REVIEW}、{@value #TYPE_TEACHER_SCORE}。
     */
    @Column(nullable = false, columnDefinition = "ENUM('task_review','peer_review','teacher_score')")
    private String type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "JSON")
    private String attachments;

    /** 0 待处理 1 已处理 2 驳回 3 成功 */
    @Column(nullable = false)
    private Integer status;

    @Column(name = "teacher_response", columnDefinition = "TEXT")
    private String teacherResponse;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "is_deleted", nullable = false)
    private Integer isDeleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Version
    @Column(nullable = false)
    private Integer version;
}
