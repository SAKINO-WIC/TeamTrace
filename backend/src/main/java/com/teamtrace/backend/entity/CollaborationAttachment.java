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
@Table(name = "collaboration_attachments", indexes = {
        @Index(name = "idx_collaboration_attachments_target", columnList = "target_type,target_id"),
        @Index(name = "idx_collaboration_attachments_space", columnList = "space_id"),
        @Index(name = "idx_collaboration_attachments_project", columnList = "project_id"),
        @Index(name = "idx_collaboration_attachments_task", columnList = "task_id")
})
public class CollaborationAttachment {

    public static final String TARGET_PROJECT = "PROJECT";
    public static final String TARGET_TASK = "TASK";
    public static final String TYPE_FILE = "file";
    public static final String TYPE_LINK = "link";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

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
    private Integer isDeleted = 0;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
