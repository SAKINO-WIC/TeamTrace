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
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_user_id", columnList = "userId"),
        @Index(name = "idx_notifications_is_read", columnList = "isRead")
    })
public class Notification {

    public static final String TYPE_NEW_APPEAL = "new_appeal";
    public static final String TYPE_APPEAL_RESOLVED = "appeal_resolved";

    /** 组员已提交子任务，待组长审批 */
    public static final String TYPE_SUBTASK_PENDING_REVIEW = "subtask_pending_review";
    /** 组长审批通过或未通过 */
    public static final String TYPE_SUBTASK_REVIEW_RESULT = "subtask_review_result";
    /** 组长将已完成子任务打回 */
    public static final String TYPE_SUBTASK_SENT_BACK = "subtask_sent_back";

    /** 定时任务：子任务截止前即将到期（仅认领后进行中/待审） */
    public static final String TYPE_SUBTASK_DEADLINE_SOON = "subtask_deadline_soon";

    /** 自由协作：任务认领、指定、开始等任务流转 */
    public static final String TYPE_COLLABORATION_TASK = "collaboration_task";
    /** 自由协作：提交结果后的接收/打回处理 */
    public static final String TYPE_COLLABORATION_REVIEW = "collaboration_review";
    /** 自由协作：空间邀请、成员加入等空间消息 */
    public static final String TYPE_COLLABORATION_SPACE = "collaboration_space";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "related_id")
    private Long relatedId;

    @Column(name = "is_read", nullable = false)
    private Integer isRead;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
