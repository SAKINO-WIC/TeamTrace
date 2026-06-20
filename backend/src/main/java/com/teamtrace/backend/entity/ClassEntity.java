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
@Table(name = "classes", indexes = {
        @Index(name = "idx_classes_teacher_id", columnList = "teacherId")
    })
public class ClassEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_code", nullable = false, length = 20)
    private String classCode;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "semester", nullable = false, length = 20)
    private String semester;

    @Column(name = "group_size_min")
    private Integer groupSizeMin;

    @Column(name = "group_size_max")
    private Integer groupSizeMax;

    @Column(name = "grouping_locked", nullable = false)
    private Integer groupingLocked;

    @Column(name = "status", nullable = false)
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
