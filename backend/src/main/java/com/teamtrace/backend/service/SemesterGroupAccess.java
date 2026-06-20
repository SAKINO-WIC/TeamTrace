package com.teamtrace.backend.service;

import com.teamtrace.backend.entity.TaskGroup;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.TaskGroupRepository;
import com.teamtrace.backend.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 班级学期固定小组（{@code groups.task_id IS NULL}）与历史「按任务建组」行并存时的访问校验。
 * 小组须属路径中的 {@code classId}（与别班无关）；若 {@code task_id} 非空则仅匹配该任务（旧数据）；若为 NULL
 * 则该班任意任务下的子任务/互评均可引用。
 */
@Service
public class SemesterGroupAccess {

    private final TaskRepository taskRepository;
    private final TaskGroupRepository taskGroupRepository;

    public SemesterGroupAccess(TaskRepository taskRepository, TaskGroupRepository taskGroupRepository) {
        this.taskRepository = taskRepository;
        this.taskGroupRepository = taskGroupRepository;
    }

    public TaskGroup requireGroupForTask(Long classId, Long taskId, Long groupId) {
        taskRepository
                .findByIdAndClassIdAndIsDeleted(taskId, classId, 0)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND));
        TaskGroup g = taskGroupRepository
                .findByIdAndClassIdAndIsDeleted(groupId, classId, 0)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "小组不存在", HttpStatus.NOT_FOUND));
        if (g.getTaskId() != null && !g.getTaskId().equals(taskId)) {
            throw new BusinessException("NOT_FOUND", "小组不存在", HttpStatus.NOT_FOUND);
        }
        return g;
    }
}
