package com.teamtrace.backend.dto.teacher;

import lombok.Builder;
import lombok.Value;

/** 教师端：班级维度仪表盘聚合（只读）。 */
@Value
@Builder
public class ClassDashboardResponse {
    Long classId;
    /** 在班学生数（未软删的 class_students） */
    long activeStudentCount;
    /** 班级下未删除任务数 */
    long taskCount;
    /** 该班级相关任务上、待处理（status=0）的申诉数 */
    long pendingAppealsCount;
}
