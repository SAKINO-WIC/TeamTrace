package com.teamtrace.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "system_announcements", indexes = {
        @Index(name = "idx_system_announcements_status", columnList = "status"),
        @Index(name = "idx_system_announcements_created_at", columnList = "created_at")
})
public class SystemAnnouncement {
    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_WITHDRAWN = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "target_scope", nullable = false, length = 20)
    private String targetScope;

    @Column(nullable = false)
    private Integer priority = 1;

    @Column(name = "popup_enabled", nullable = false)
    private Integer popupEnabled = 1;

    @Column(name = "force_confirm", nullable = false)
    private Integer forceConfirm = 0;

    @Column(nullable = false)
    private Integer status = STATUS_ACTIVE;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
