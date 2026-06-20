package com.teamtrace.backend.domain.task;

/**
 * 任务状态枚举值（与持久化字段 tasks.status 保持一致）。
 */
public final class TaskStatusCodes {

    private TaskStatusCodes() {}

    public static final int NOT_STARTED = 0;
    public static final int IN_PROGRESS = 1;
    public static final int CLOSED = 2;

    public static boolean isSupported(Integer status) {
        if (status == null) {
            return false;
        }
        return status == NOT_STARTED || status == IN_PROGRESS || status == CLOSED;
    }
}
