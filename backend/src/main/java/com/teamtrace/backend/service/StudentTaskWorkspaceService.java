package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.group.TaskGroupResponse;
import com.teamtrace.backend.dto.student.StudentClassDetailResponse;
import com.teamtrace.backend.dto.student.StudentTaskWorkspaceResponse;
import com.teamtrace.backend.dto.subtask.SubtaskProgressResponse;
import com.teamtrace.backend.dto.subtask.SubtaskResponse;
import com.teamtrace.backend.dto.teacher.TaskDetailResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentTaskWorkspaceService {

    private final StudentTaskService studentTaskService;
    private final StudentClassService studentClassService;
    private final TeacherTaskGroupService teacherTaskGroupService;
    private final SubtaskService subtaskService;

    public StudentTaskWorkspaceService(
            StudentTaskService studentTaskService,
            StudentClassService studentClassService,
            TeacherTaskGroupService teacherTaskGroupService,
            SubtaskService subtaskService) {
        this.studentTaskService = studentTaskService;
        this.studentClassService = studentClassService;
        this.teacherTaskGroupService = teacherTaskGroupService;
        this.subtaskService = subtaskService;
    }

    @Transactional(readOnly = true)
    public StudentTaskWorkspaceResponse loadWorkspace(Long studentId, Long classId, Long taskId) {
        TaskDetailResponse task = studentTaskService.getClassTask(studentId, classId, taskId);
        StudentClassDetailResponse classContext = studentClassService.getClassDetail(studentId, classId);
        List<TaskGroupResponse> groups = teacherTaskGroupService.listSemesterGroupsForStudent(studentId, classId);

        List<SubtaskResponse> subtasks = null;
        SubtaskProgressResponse progress = null;

        Long groupId = resolveActiveGroupId(classContext, groups, studentId);
        if (groupId != null && canLoadGroupTaskData(classContext)) {
            subtasks = subtaskService.listForStudent(studentId, classId, taskId, groupId);
            progress = subtaskService.progressForStudent(studentId, classId, taskId, groupId);
        }

        return StudentTaskWorkspaceResponse.builder()
                .task(task)
                .classContext(classContext)
                .groups(groups)
                .subtasks(subtasks)
                .progress(progress)
                .build();
    }

    private static boolean canLoadGroupTaskData(StudentClassDetailResponse classContext) {
        String status = classContext.getGroupJoinStatus();
        return status == null || !"待审批".equals(status);
    }

    private static Long resolveActiveGroupId(
            StudentClassDetailResponse classContext, List<TaskGroupResponse> groups, Long studentId) {
        if (classContext.getGroupId() != null) {
            return classContext.getGroupId();
        }
        if (groups == null || studentId == null) {
            return null;
        }
        for (TaskGroupResponse group : groups) {
            if (group == null || group.getGroupId() == null) {
                continue;
            }
            List<Long> members = group.getMemberStudentIds();
            if (members != null && members.stream().anyMatch(id -> studentId.equals(id))) {
                return group.getGroupId();
            }
        }
        return null;
    }
}
