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
@Table(name = "task_attachments", indexes = {
        @Index(name = "idx_task_attachments_task_id", columnList = "taskId"),
        @Index(name = "idx_task_attachments_type", columnList = "type")
    })
public class TaskAttachment {

    public static final String TYPE_FILE = "file";
    public static final String TYPE_LINK = "link";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_deleted", nullable = false)
    private Integer isDeleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
