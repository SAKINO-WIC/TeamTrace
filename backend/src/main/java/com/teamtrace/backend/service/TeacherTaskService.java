package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.teacher.CreateTaskRequest;
import com.teamtrace.backend.dto.teacher.TaskDetailResponse;
import com.teamtrace.backend.dto.teacher.UpdateTaskRequest;
import com.teamtrace.backend.dto.teacher.TaskSummaryResponse;
import com.teamtrace.backend.domain.task.TaskStatusCodes;
import com.teamtrace.backend.entity.ClassEntity;
import com.teamtrace.backend.entity.Subtask;
import com.teamtrace.backend.entity.Task;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.SubtaskRepository;
import com.teamtrace.backend.repository.TaskRepository;
import com.teamtrace.backend.util.AppTime;
import com.teamtrace.backend.util.SnowflakeIdGenerator;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeacherTaskService {

    private static final BigDecimal WEIGHT_SUM_MIN = new BigDecimal("0.999");
    private static final BigDecimal WEIGHT_SUM_MAX = new BigDecimal("1.001");
    private static final BigDecimal DEFAULT_PEER = new BigDecimal("0.40");
    private static final BigDecimal DEFAULT_TEACHER = new BigDecimal("0.60");

    private final ClassRepository classRepository;
    private final TaskRepository taskRepository;
    private final SubtaskRepository subtaskRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final TaskSummaryMapper taskSummaryMapper;
    private final TaskAttachmentService taskAttachmentService;

    public TeacherTaskService(
            ClassRepository classRepository,
            TaskRepository taskRepository,
            SubtaskRepository subtaskRepository,
            SnowflakeIdGenerator snowflakeIdGenerator,
            TaskSummaryMapper taskSummaryMapper,
            TaskAttachmentService taskAttachmentService) {
        this.classRepository = classRepository;
        this.taskRepository = taskRepository;
        this.subtaskRepository = subtaskRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.taskSummaryMapper = taskSummaryMapper;
        this.taskAttachmentService = taskAttachmentService;
    }

    @Transactional
    public TaskSummaryResponse createTask(Long teacherId, Long classId, CreateTaskRequest request) {
        ClassEntity clazz = requireTeacherClass(teacherId, classId);

        LocalDateTime deadline = parseDeadline(request.getDeadline());
        boolean enablePeer = Boolean.TRUE.equals(request.getEnablePeerReview());

        BigDecimal peerW = request.getPeerReviewWeight() != null ? request.getPeerReviewWeight() : DEFAULT_PEER;
        BigDecimal teachW = request.getTeacherScoreWeight() != null ? request.getTeacherScoreWeight() : DEFAULT_TEACHER;
        BigDecimal sum = peerW.add(teachW);
        if (sum.compareTo(WEIGHT_SUM_MIN) < 0 || sum.compareTo(WEIGHT_SUM_MAX) > 0) {
            throw new BusinessException("BAD_REQUEST", "互评权重与教师评分权重之和须为 1（允许微小误差）", HttpStatus.BAD_REQUEST);
        }

        Task task = new Task();
        task.setTaskUuid(snowflakeIdGenerator.nextId());
        task.setClassId(clazz.getId());
        task.setTeacherId(teacherId);
        task.setName(request.getName().trim());
        task.setDescription(request.getDescription() == null ? null : request.getDescription().trim());
        task.setDeadline(deadline);
        task.setEnablePeerReview(enablePeer ? 1 : 0);
        task.setPeerReviewWeight(peerW);
        task.setTeacherScoreWeight(teachW);
        task.setStatus(TaskStatusCodes.IN_PROGRESS);
        task.setIsDeleted(0);

        if (enablePeer) {
            int offsetHours = request.getPeerReviewOffsetHours() == null ? 1 : request.getPeerReviewOffsetHours();
            task.setPeerReviewOffsetHours(offsetHours);
            task.setPeerReviewMaxScore(request.getPeerReviewMaxScore() == null ? 100 : request.getPeerReviewMaxScore());
        } else {
            task.setPeerReviewOffsetHours(null);
            task.setPeerReviewMaxScore(null);
        }
        applyPeerReviewColumnsFromState(task);
        applyTaskStatusFromDeadline(task, AppTime.now());

        Task saved = taskRepository.save(task);
        taskAttachmentService.createForTask(saved.getId(), request.getAttachments());
        return taskSummaryMapper.toSummary(saved);
    }

    @Transactional(readOnly = true)
    public List<TaskSummaryResponse> listTasks(Long teacherId, Long classId) {
        requireTeacherClass(teacherId, classId);
        return taskRepository.findByClassIdAndIsDeletedOrderByIdDesc(classId, 0).stream()
                .map(taskSummaryMapper::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskDetailResponse getTask(Long teacherId, Long classId, Long taskId) {
        requireTeacherClass(teacherId, classId);
        Task task = requireActiveTaskInClass(classId, taskId);
        return taskSummaryMapper.toDetail(task, taskAttachmentService.listResponses(task.getId()));
    }

    @Transactional
    public void softDeleteTask(Long teacherId, Long classId, Long taskId) {
        requireTeacherClass(teacherId, classId);
        Task task = requireActiveTaskInClass(classId, taskId);
        task.setIsDeleted(1);
        task.setDeletedAt(AppTime.now());
        taskRepository.save(task);
    }

    @Transactional
    public TaskDetailResponse updateTask(Long teacherId, Long classId, Long taskId, UpdateTaskRequest req) {
        requireTeacherClass(teacherId, classId);
        Task task = requireActiveTaskInClass(classId, taskId);
        if (!hasAnyFieldToUpdate(req)) {
            throw new BusinessException("BAD_REQUEST", "至少需要提供一项要修改的字段", HttpStatus.BAD_REQUEST);
        }

        if (req.getName() != null) {
            String n = req.getName().trim();
            if (n.isEmpty()) {
                throw new BusinessException("BAD_REQUEST", "任务名称不能为空", HttpStatus.BAD_REQUEST);
            }
            task.setName(n);
        }
        if (req.getDescription() != null) {
            String d = req.getDescription().trim();
            task.setDescription(d.isEmpty() ? null : d);
        }
        LocalDateTime newDeadline = null;
        boolean deadlineChanged = false;
        if (req.getDeadline() != null) {
            newDeadline = parseDeadline(req.getDeadline());
            deadlineChanged = !newDeadline.equals(task.getDeadline());
            task.setDeadline(newDeadline);
        }
        if (req.getEnablePeerReview() != null) {
            task.setEnablePeerReview(Boolean.TRUE.equals(req.getEnablePeerReview()) ? 1 : 0);
        }
        if (req.getPeerReviewOffsetHours() != null) {
            task.setPeerReviewOffsetHours(req.getPeerReviewOffsetHours());
        }
        if (req.getPeerReviewMaxScore() != null) {
            task.setPeerReviewMaxScore(req.getPeerReviewMaxScore());
        }

        BigDecimal peerW = req.getPeerReviewWeight() != null ? req.getPeerReviewWeight() : task.getPeerReviewWeight();
        BigDecimal teachW = req.getTeacherScoreWeight() != null ? req.getTeacherScoreWeight() : task.getTeacherScoreWeight();
        BigDecimal sum = peerW.add(teachW);
        if (sum.compareTo(WEIGHT_SUM_MIN) < 0 || sum.compareTo(WEIGHT_SUM_MAX) > 0) {
            throw new BusinessException("BAD_REQUEST", "互评权重与教师评分权重之和须为 1（允许微小误差）", HttpStatus.BAD_REQUEST);
        }
        task.setPeerReviewWeight(peerW);
        task.setTeacherScoreWeight(teachW);

        applyPeerReviewColumnsFromState(task);

        if (req.getPeerReviewDeadline() != null && !req.getPeerReviewDeadline().isBlank()) {
            applyExplicitPeerReviewDeadlineExtension(task, req.getPeerReviewDeadline().trim());
        }

        applyTaskStatusFromDeadline(task, AppTime.now());
        Task saved = taskRepository.save(task);
        if (deadlineChanged) {
            syncSubtaskDeadlines(saved.getId(), newDeadline);
        }
        if (req.getAttachments() != null) {
            taskAttachmentService.replaceForTask(saved.getId(), req.getAttachments());
        }
        return taskSummaryMapper.toDetail(saved, taskAttachmentService.listResponses(saved.getId()));
    }

    private static void applyTaskStatusFromDeadline(Task task, LocalDateTime now) {
        if (task.getDeadline() == null) {
            return;
        }
        if (!now.isBefore(task.getDeadline())) {
            task.setStatus(TaskStatusCodes.CLOSED);
        } else {
            task.setStatus(TaskStatusCodes.IN_PROGRESS);
        }
    }

    private static void applyPeerReviewColumnsFromState(Task task) {
        boolean enablePeer = task.getEnablePeerReview() != null && task.getEnablePeerReview() == 1;
        if (enablePeer) {
            if (task.getDeadline() == null) {
                throw new BusinessException("BAD_REQUEST", "任务截止时间未设置，无法开启互评", HttpStatus.BAD_REQUEST);
            }
            int offsetH = task.getPeerReviewOffsetHours() != null ? task.getPeerReviewOffsetHours() : 1;
            int maxS = task.getPeerReviewMaxScore() != null ? task.getPeerReviewMaxScore() : 100;
            task.setPeerReviewOffsetHours(offsetH);
            task.setPeerReviewMaxScore(maxS);
            task.setPeerReviewDeadline(task.getDeadline().plusHours(offsetH));
        } else {
            task.setPeerReviewOffsetHours(null);
            task.setPeerReviewMaxScore(null);
            task.setPeerReviewDeadline(null);
        }
    }

    private static boolean hasAnyFieldToUpdate(UpdateTaskRequest req) {
        return req.getName() != null
                || req.getDescription() != null
                || req.getDeadline() != null
                || (req.getPeerReviewDeadline() != null && !req.getPeerReviewDeadline().isBlank())
                || req.getEnablePeerReview() != null
                || req.getPeerReviewOffsetHours() != null
                || req.getPeerReviewMaxScore() != null
                || req.getPeerReviewWeight() != null
                || req.getTeacherScoreWeight() != null
                || req.getAttachments() != null;
    }

    private void syncSubtaskDeadlines(Long taskId, LocalDateTime deadline) {
        if (taskId == null || deadline == null) {
            return;
        }
        List<Subtask> subtasks = subtaskRepository.findByTaskIdAndIsDeletedOrderByIdAsc(taskId, 0);
        if (subtasks.isEmpty()) {
            return;
        }
        subtasks.forEach(subtask -> subtask.setDeadline(deadline));
        subtaskRepository.saveAll(subtasks);
    }

    /**
     * P1：仅允许将互评截止时间延后；新时间须晚于任务截止与当前时间。互评关闭后可通过延后重新开放窗口。
     */
    private void applyExplicitPeerReviewDeadlineExtension(Task task, String rawIso) {
        if (task.getEnablePeerReview() == null || task.getEnablePeerReview() != 1) {
            throw new BusinessException("BAD_REQUEST", "未开启互评，无法设置互评截止时间", HttpStatus.BAD_REQUEST);
        }
        if (task.getDeadline() == null) {
            throw new BusinessException("BAD_REQUEST", "任务截止时间未设置，无法设置互评截止时间", HttpStatus.BAD_REQUEST);
        }
        LocalDateTime newEnd = parseDeadline(rawIso);
        LocalDateTime now = AppTime.now();
        if (!newEnd.isAfter(task.getDeadline())) {
            throw new BusinessException("BAD_REQUEST", "互评截止时间须晚于任务截止时间", HttpStatus.BAD_REQUEST);
        }
        if (!newEnd.isAfter(now)) {
            throw new BusinessException("BAD_REQUEST", "互评截止时间须晚于当前时间", HttpStatus.BAD_REQUEST);
        }
        LocalDateTime baseline = task.getPeerReviewDeadline();
        if (baseline == null) {
            throw new BusinessException("BAD_REQUEST", "当前任务缺少互评截止时间，请先开启互评或保存任务后再试", HttpStatus.BAD_REQUEST);
        }
        if (!newEnd.isAfter(baseline)) {
            throw new BusinessException("BAD_REQUEST", "互评截止时间仅允许延后", HttpStatus.BAD_REQUEST);
        }
        task.setPeerReviewDeadline(newEnd);
        long hours = Duration.between(task.getDeadline(), newEnd).toHours();
        if (hours < 1L) {
            task.setPeerReviewOffsetHours(1);
        } else if (hours > 8760L) {
            task.setPeerReviewOffsetHours(8760);
        } else {
            task.setPeerReviewOffsetHours((int) hours);
        }
    }

    private Task requireActiveTaskInClass(Long classId, Long taskId) {
        return taskRepository
                .findByIdAndClassIdAndIsDeleted(taskId, classId, 0)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND));
    }

    private ClassEntity requireTeacherClass(Long teacherId, Long classId) {
        ClassEntity clazz = classRepository.findById(classId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND));
        if (clazz.getIsDeleted() != null && clazz.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND);
        }
        if (!teacherId.equals(clazz.getTeacherId())) {
            throw new BusinessException("FORBIDDEN", "无权限", HttpStatus.FORBIDDEN);
        }
        return clazz;
    }

    private static LocalDateTime parseDeadline(String raw) {
        try {
            return OffsetDateTime.parse(raw.trim(), DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .atZoneSameInstant(AppTime.DEFAULT_ZONE)
                    .toLocalDateTime();
        } catch (DateTimeParseException ex) {
            throw new BusinessException("BAD_REQUEST", "截止时间格式无效，请使用 ISO-8601（含时区）", HttpStatus.BAD_REQUEST);
        }
    }

}
