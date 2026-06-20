package com.teamtrace.backend.dto.teacher;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

/** 教师申诉中心工作台单条记录（含班级/任务上下文）。 */
@Value
@Builder
public class TeacherAppealWorkspaceItem {
    Long appealId;
    Long classId;
    String className;
    Long taskId;
    String taskName;
    Long studentId;
    Long subtaskId;
    String type;
    String attachments;
    Integer status;
    String reason;
    LocalDateTime createdAt;
    LocalDateTime handledAt;
    String teacherResponse;
}
