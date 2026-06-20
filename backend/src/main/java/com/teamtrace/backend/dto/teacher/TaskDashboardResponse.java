package com.teamtrace.backend.dto.teacher;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

/** 教师端：单任务维度仪表盘聚合（只读）。 */
@Value
@Builder
public class TaskDashboardResponse {
    Long taskId;
    Long classId;
    String taskName;
    LocalDateTime deadline;
    /** 任务业务状态（与 Task.status 一致） */
    Integer taskStatus;
    boolean enablePeerReview;

    long groupCount;
    /**
     * 该任务下各小组有效成员条数。业务上同一班级内一名学生只在一个小组，故单任务下与 {@link
     * #activeDistinctStudentsInGroups} 通常一致；若历史数据或异常出现重复，可对比二者。
     */
    long activeMembersInGroups;
    /** 该任务下已入组学生人数（按 userId 去重） */
    long activeDistinctStudentsInGroups;

    long subtasksTotal;
    long subtasksPendingClaim;
    long subtasksInProgress;
    long subtasksPendingReview;
    long subtasksCompleted;

    long peerReviewsTotal;
    /** 已录入的学生维度教师评分条数 */
    long teacherScoresStudentCount;
    long pendingAppealsCount;
}
