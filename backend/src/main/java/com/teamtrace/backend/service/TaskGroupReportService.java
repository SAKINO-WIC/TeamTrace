package com.teamtrace.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamtrace.backend.dto.report.SubmitTaskGroupReportRequest;
import com.teamtrace.backend.dto.report.TaskGroupReportHistoryResponse;
import com.teamtrace.backend.dto.report.TaskGroupReportResponse;
import com.teamtrace.backend.entity.ClassEntity;
import com.teamtrace.backend.entity.GroupMember;
import com.teamtrace.backend.entity.Task;
import com.teamtrace.backend.entity.TaskGroup;
import com.teamtrace.backend.entity.TaskGroupReport;
import com.teamtrace.backend.entity.TaskGroupReportHistory;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.GroupMemberRepository;
import com.teamtrace.backend.repository.TaskGroupReportHistoryRepository;
import com.teamtrace.backend.repository.TaskGroupReportRepository;
import com.teamtrace.backend.repository.TaskRepository;
import com.teamtrace.backend.util.AppTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskGroupReportService {

    private final ClassRepository classRepository;
    private final TaskRepository taskRepository;
    private final SemesterGroupAccess semesterGroupAccess;
    private final GroupMemberRepository groupMemberRepository;
    private final ClassMembershipService classMembershipService;
    private final TaskGroupReportRepository taskGroupReportRepository;
    private final TaskGroupReportHistoryRepository taskGroupReportHistoryRepository;
    private final ObjectMapper objectMapper;

    public TaskGroupReportService(
            ClassRepository classRepository,
            TaskRepository taskRepository,
            SemesterGroupAccess semesterGroupAccess,
            GroupMemberRepository groupMemberRepository,
            ClassMembershipService classMembershipService,
            TaskGroupReportRepository taskGroupReportRepository,
            TaskGroupReportHistoryRepository taskGroupReportHistoryRepository,
            ObjectMapper objectMapper) {
        this.classRepository = classRepository;
        this.taskRepository = taskRepository;
        this.semesterGroupAccess = semesterGroupAccess;
        this.groupMemberRepository = groupMemberRepository;
        this.classMembershipService = classMembershipService;
        this.taskGroupReportRepository = taskGroupReportRepository;
        this.taskGroupReportHistoryRepository = taskGroupReportHistoryRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public TaskGroupReportResponse getForStudent(Long studentId, Long classId, Long taskId, Long groupId) {
        requireStudentClassView(classId, studentId);
        TaskGroup group = semesterGroupAccess.requireGroupForTask(classId, taskId, groupId);
        requireStudentInGroup(studentId, group.getId());
        return taskGroupReportRepository
                .findByTaskIdAndGroupIdAndIsDeleted(taskId, group.getId(), 0)
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public TaskGroupReportResponse getForTeacher(Long teacherId, Long classId, Long taskId, Long groupId) {
        requireTeacherClass(teacherId, classId);
        TaskGroup group = semesterGroupAccess.requireGroupForTask(classId, taskId, groupId);
        return taskGroupReportRepository
                .findByTaskIdAndGroupIdAndIsDeleted(taskId, group.getId(), 0)
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional
    public TaskGroupReportResponse submitByLeader(
            Long studentId,
            Long classId,
            Long taskId,
            Long groupId,
            SubmitTaskGroupReportRequest request) {
        requireStudentClassView(classId, studentId);
        TaskGroup group = semesterGroupAccess.requireGroupForTask(classId, taskId, groupId);
        requireLeader(studentId, group);
        Task task = requireOpenTask(classId, taskId);

        String content = validateReportContent(request.getReportContent());
        LocalDateTime now = AppTime.now();
        TaskGroupReport report = taskGroupReportRepository
                .findByTaskIdAndGroupIdAndIsDeleted(task.getId(), group.getId(), 0)
                .orElse(null);

        if (report == null) {
            TaskGroupReport created = new TaskGroupReport();
            created.setTaskId(task.getId());
            created.setGroupId(group.getId());
            created.setSubmitterId(studentId);
            created.setVersionNo(1);
            created.setReportContent(content);
            created.setSubmittedAt(now);
            created.setIsDeleted(0);
            TaskGroupReport saved = taskGroupReportRepository.save(created);
            taskGroupReportHistoryRepository.save(buildHistory(saved, studentId, 1, content, now, 1));
            return toResponse(saved);
        }

        int nextVersion = report.getVersionNo() == null ? 1 : report.getVersionNo() + 1;
        taskGroupReportHistoryRepository.findByReportIdOrderByVersionNoDesc(report.getId())
                .forEach(history -> history.setIsCurrent(0));
        report.setSubmitterId(studentId);
        report.setVersionNo(nextVersion);
        report.setReportContent(content);
        report.setSubmittedAt(now);
        TaskGroupReport saved = taskGroupReportRepository.save(report);
        taskGroupReportHistoryRepository.save(buildHistory(saved, studentId, nextVersion, content, now, 1));
        return toResponse(saved);
    }

    private Task requireOpenTask(Long classId, Long taskId) {
        Task task = taskRepository
                .findByIdAndClassIdAndIsDeleted(taskId, classId, 0)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND));
        LocalDateTime now = AppTime.now();
        if (task.getDeadline() == null || !now.isBefore(task.getDeadline())) {
            throw new BusinessException("BAD_REQUEST", "总任务已超过截止时间，不能提交或修改小组总报告", HttpStatus.BAD_REQUEST);
        }
        return task;
    }

    private String validateReportContent(String raw) {
        String content = raw == null ? "" : raw.trim();
        if (content.isBlank()) {
            throw new BusinessException("BAD_REQUEST", "小组总报告内容不能为空", HttpStatus.BAD_REQUEST);
        }
        try {
            objectMapper.readTree(content);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("BAD_REQUEST", "小组总报告内容格式无效", HttpStatus.BAD_REQUEST);
        }
        return content;
    }

    private void requireStudentInGroup(Long studentId, Long groupId) {
        if (!groupMemberRepository.existsByGroupIdAndUserIdAndStatus(groupId, studentId, GroupMember.STATUS_ACTIVE)) {
            throw new BusinessException("FORBIDDEN", "你不在该小组中", HttpStatus.FORBIDDEN);
        }
    }

    private void requireLeader(Long studentId, TaskGroup group) {
        if (!studentId.equals(group.getLeaderId())) {
            throw new BusinessException("FORBIDDEN", "仅组长可提交小组总报告", HttpStatus.FORBIDDEN);
        }
    }

    private void requireStudentClassView(Long classId, Long studentId) {
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

    private ClassEntity requireTeacherClass(Long teacherId, Long classId) {
        ClassEntity clazz = classRepository
                .findById(classId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND));
        if (clazz.getIsDeleted() != null && clazz.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND);
        }
        if (!teacherId.equals(clazz.getTeacherId())) {
            throw new BusinessException("FORBIDDEN", "无权限", HttpStatus.FORBIDDEN);
        }
        return clazz;
    }

    private TaskGroupReportHistory buildHistory(
            TaskGroupReport report,
            Long submitterId,
            Integer versionNo,
            String content,
            LocalDateTime submittedAt,
            Integer isCurrent) {
        TaskGroupReportHistory history = new TaskGroupReportHistory();
        history.setReportId(report.getId());
        history.setTaskId(report.getTaskId());
        history.setGroupId(report.getGroupId());
        history.setSubmitterId(submitterId);
        history.setVersionNo(versionNo);
        history.setReportContent(content);
        history.setSubmittedAt(submittedAt);
        history.setIsCurrent(isCurrent);
        return history;
    }

    private TaskGroupReportResponse toResponse(TaskGroupReport report) {
        return TaskGroupReportResponse.builder()
                .reportId(report.getId())
                .taskId(report.getTaskId())
                .groupId(report.getGroupId())
                .submitterId(report.getSubmitterId())
                .versionNo(report.getVersionNo())
                .reportContent(report.getReportContent())
                .submittedAt(report.getSubmittedAt())
                .histories(loadHistories(report.getId()))
                .build();
    }

    private List<TaskGroupReportHistoryResponse> loadHistories(Long reportId) {
        return taskGroupReportHistoryRepository.findByReportIdOrderByVersionNoDesc(reportId).stream()
                .map(this::toHistoryResponse)
                .collect(Collectors.toList());
    }

    private TaskGroupReportHistoryResponse toHistoryResponse(TaskGroupReportHistory history) {
        return TaskGroupReportHistoryResponse.builder()
                .id(history.getId())
                .reportId(history.getReportId())
                .submitterId(history.getSubmitterId())
                .versionNo(history.getVersionNo())
                .reportContent(history.getReportContent())
                .submittedAt(history.getSubmittedAt())
                .current(Integer.valueOf(1).equals(history.getIsCurrent()))
                .build();
    }
}
