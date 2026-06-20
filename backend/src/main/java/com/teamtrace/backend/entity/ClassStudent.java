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
@Table(name = "class_students", indexes = {
        @Index(name = "idx_class_students_class_id", columnList = "classId"),
        @Index(name = "idx_class_students_student_id", columnList = "studentId")
    })
public class ClassStudent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /** 插入走库默认值；重新加入班级时需要可更新 join_time */
    @Column(name = "join_time", insertable = false, updatable = true)
    private LocalDateTime joinTime;

    @Column(name = "is_deleted", nullable = false)
    private Integer isDeleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
