package com.teamtrace.backend.domain.progress;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 教师反馈：进度以子任务为粒度；同时展示小组与个人维度（见 docs/teacher-feedback-requirements.md）。
 */
public final class ProgressFormulas {

    private ProgressFormulas() {}

    /**
     * 小组进度 = 组内所有成员已完成子任务数之和 / 所有成员认领的子任务数之和。
     *
     * @param completedSubtasksInGroup 组内「已完成」子任务总数
     * @param assignedSubtasksInGroup   组内「已认领」子任务总数（含进行中/待审批等已占用名额）
     * @return 0–100 的百分比，分母为 0 时返回 {@code null}
     */
    public static BigDecimal groupProgressPercent(long completedSubtasksInGroup, long assignedSubtasksInGroup) {
        if (assignedSubtasksInGroup <= 0) {
            return null;
        }
        return BigDecimal.valueOf(completedSubtasksInGroup)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(assignedSubtasksInGroup), 2, RoundingMode.HALF_UP);
    }

    /**
     * 个人进度 = 该成员已完成子任务数 / 该成员认领子任务数之和。
     *
     * @param memberCompleted 该成员已完成子任务数
     * @param memberAssigned  该成员已认领子任务总数
     */
    public static BigDecimal memberProgressPercent(long memberCompleted, long memberAssigned) {
        if (memberAssigned <= 0) {
            return null;
        }
        return BigDecimal.valueOf(memberCompleted)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(memberAssigned), 2, RoundingMode.HALF_UP);
    }
}
