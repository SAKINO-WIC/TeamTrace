package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.teacher.TaskDetailResponse;
import com.teamtrace.backend.dto.teacher.TaskSummaryResponse;
import com.teamtrace.backend.domain.task.StudentTaskDetailViewResolver;
import com.teamtrace.backend.domain.task.TaskStatusCodes;
import com.teamtrace.backend.entity.ClassEntity;
import com.teamtrace.backend.entity.ClassStudent;
import com.teamtrace.backend.entity.Task;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.AppealRepository;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.ClassStudentRepository;
import com.teamtrace.backend.repository.TaskRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentTaskService {

    private final ClassRepository classRepository;
    private final TaskRepository taskRepository;
    private final AppealRepository appealRepository;
    private final ClassMembershipService classMembershipService;
    private final ClassStudentRepository classStudentRepository;
    private final TaskSummaryMapper taskSummaryMapper;
    private final TaskAttachmentService taskAttachmentService;
    private final StudentTaskDetailViewResolver studentTaskDetailViewResolver;

    public StudentTaskService(
            ClassRepository classRepository,
            TaskRepository taskRepository,
            AppealRepository appealRepository,
            ClassMembershipService classMembershipService,
            ClassStudentRepository classStudentRepository,
            TaskSummaryMapper taskSummaryMapper,
            TaskAttachmentService taskAttachmentService,
            StudentTaskDetailViewResolver studentTaskDetailViewResolver) {
        this.classRepository = classRepository;
        this.taskRepository = taskRepository;
        this.appealRepository = appealRepository;
        this.classMembershipService = classMembershipService;
        this.classStudentRepository = classStudentRepository;
        this.taskSummaryMapper = taskSummaryMapper;
        this.taskAttachmentService = taskAttachmentService;
        this.studentTaskDetailViewResolver = studentTaskDetailViewResolver;
    }

    @Transactional(readOnly = true)
    public List<TaskSummaryResponse> listAllTasks(Long studentId, Integer status, String keyword) {
        List<Long> classIds = classStudentRepository.findByStudentIdAndIsDeletedOrderByIdDesc(studentId, 0).stream()
                .map(ClassStudent::getClassId)
                .distinct()
                .toList();
        if (classIds.isEmpty()) {
            return List.of();
        }

        Integer normalizedStatus = normalizeStatus(status);
        String normalizedKeyword = normalizeKeyword(keyword);
        return taskRepository.searchStudentTasksInClasses(classIds, 0, normalizedStatus, normalizedKeyword).stream()
                .map(taskSummaryMapper::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskSummaryResponse> listClassTasks(
            Long studentId, Long classId, Integer status, String keyword) {
        requireStudentViewableClass(studentId, classId);
        Integer normalizedStatus = normalizeStatus(status);
        String normalizedKeyword = normalizeKeyword(keyword);
        return taskRepository.searchStudentTasks(classId, 0, normalizedStatus, normalizedKeyword).stream()
                .map(taskSummaryMapper::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskDetailResponse getClassTask(Long studentId, Long classId, Long taskId) {
        requireStudentViewableClass(studentId, classId);
        Task task = taskRepository
                .findByIdAndClassIdAndIsDeleted(taskId, classId, 0)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND));
        boolean hasPendingAppeal = appealRepository.existsByTaskIdAndStudentIdAndStatusAndIsDeleted(taskId, studentId, 0, 0);
        return studentTaskDetailViewResolver.resolve(
                taskSummaryMapper.toDetail(task, taskAttachmentService.listResponses(task.getId())),
                !hasPendingAppeal);
    }

    private void requireStudentViewableClass(Long studentId, Long classId) {
        ClassEntity clazz = classRepository
                .findById(classId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND));
        if (clazz.getIsDeleted() != null && clazz.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND);
        }
        if (clazz.getStatus() != null && clazz.getStatus() == 0) {
            throw new BusinessException("BAD_REQUEST", "班级已结束", HttpStatus.BAD_REQUEST);
        }
        classMembershipService.requireActiveStudentInClass(studentId, classId);
    }

    private static Integer normalizeStatus(Integer status) {
        if (status == null) {
            return null;
        }
        if (!TaskStatusCodes.isSupported(status)) {
            throw new BusinessException("BAD_REQUEST", "status 仅支持 0(未开始)/1(进行中)/2(已截止)", HttpStatus.BAD_REQUEST);
        }
        return status;
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
