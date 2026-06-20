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
@Table(name = "collaboration_task_submissions", indexes = {
        @Index(name = "idx_collaboration_task_submissions_task", columnList = "task_id"),
        @Index(name = "idx_collaboration_task_submissions_by", columnList = "submitted_by")
})
public class CollaborationTaskSubmission {

    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_RETURNED = "RETURNED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "flow_node_id")
    private Long flowNodeId;

    @Column(name = "submitted_by", nullable = false)
    private Long submittedBy;

    @Column(name = "content", length = 3000)
    private String content;

    @Column(name = "attachments_json", columnDefinition = "TEXT")
    private String attachmentsJson;

    @Column(name = "links_json", columnDefinition = "TEXT")
    private String linksJson;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    @Column(name = "status", nullable = false, length = 30)
    private String status = STATUS_SUBMITTED;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
