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
@Table(name = "group_members", indexes = {
        @Index(name = "idx_group_members_group_id", columnList = "groupId"),
        @Index(name = "idx_group_members_user_id", columnList = "userId")
    })
public class GroupMember {

    /** 与库注释一致：0已退出 1正常 2待审批 3已拒绝 */
    public static final int STATUS_LEFT = 0;

    public static final int STATUS_ACTIVE = 1;

    public static final int STATUS_PENDING = 2;

    public static final int STATUS_REJECTED = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "join_time", insertable = false, updatable = false)
    private LocalDateTime joinTime;

    @Column(name = "leave_time")
    private LocalDateTime leaveTime;

    @Column(name = "leave_reason", length = 50)
    private String leaveReason;

    @Column(nullable = false)
    private Integer status;

    @Version
    @Column(nullable = false)
    private Integer version;
}
