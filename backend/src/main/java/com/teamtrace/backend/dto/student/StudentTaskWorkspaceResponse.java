package com.teamtrace.backend.dto.student;

import com.teamtrace.backend.dto.group.TaskGroupResponse;
import com.teamtrace.backend.dto.subtask.SubtaskProgressResponse;
import com.teamtrace.backend.dto.subtask.SubtaskResponse;
import com.teamtrace.backend.dto.teacher.TaskDetailResponse;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** 学生任务详情页一次返回：任务、班级小组上下文、小组列表、子任务与进度（后两者仅在已入组且非待审批时填充）。 */
@Getter
@Builder
public class StudentTaskWorkspaceResponse {

    private TaskDetailResponse task;
    private StudentClassDetailResponse classContext;
    private List<TaskGroupResponse> groups;
    private List<SubtaskResponse> subtasks;
    private SubtaskProgressResponse progress;
}
