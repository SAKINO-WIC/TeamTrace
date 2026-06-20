package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.appeal.AppealResponse;
import com.teamtrace.backend.dto.student.CreateStudentAppealRequest;
import com.teamtrace.backend.dto.teacher.ResolveAppealRequest;
import com.teamtrace.backend.dto.teacher.TeacherAppealWorkspaceItem;
import com.teamtrace.backend.entity.Appeal;
import com.teamtrace.backend.entity.ClassEntity;
import com.teamtrace.backend.entity.GroupMember;
import com.teamtrace.backend.entity.Notification;
import com.teamtrace.backend.entity.Subtask;
import com.teamtrace.backend.entity.Task;
import com.teamtrace.backend.entity.TaskGroup;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.AppealRepository;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.GroupMemberRepository;
import com.teamtrace.backend.repository.SubtaskRepository;
import com.teamtrace.backend.repository.TaskGroupRepository;
import com.teamtrace.backend.repository.TaskRepository;
import com.teamtrace.backend.util.AppTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppealService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_REJECTED = 2;
    private static final int STATUS_SUCCESS = 3;
    private static final int MAX_APPEALS_PER_STUDENT_TASK = 3;

    private final TaskRepository taskRepository;
    private final ClassRepository classRepository;
    private final ClassMembershipService classMembershipService;
    private final AppealRepository appealRepository;
    private final NotificationService notificationService;
    private final SubtaskRepository subtaskRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final TaskGroupRepository taskGroupRepository;

    public AppealService(
            TaskRepository taskRepository,
            ClassRepository classRepository,
            ClassMembershipService classMembershipService,
            AppealRepository appealRepository,
            NotificationService notificationService,
            SubtaskRepository subtaskRepository,
            GroupMemberRepository groupMemberRepository,
            TaskGroupRepository taskGroupRepository) {
        this.taskRepository = taskRepository;
        this.classRepository = classRepository;
        this.classMembershipService = classMembershipService;
        this.appealRepository = appealRepository;
        this.notificationService = notificationService;
        this.subtaskRepository = subtaskRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.taskGroupRepository = taskGroupRepository;
    }

    @Transactional
    public AppealResponse createStudentAppeal(Long studentId, Long taskId, CreateStudentAppealRequest request) {
        Task task = requireActiveTask(taskId);
        requireStudentViewableClassForTask(studentId, task);

        String appealType = normalizeAppealType(request.getType());
        long appealCount = appealRepository.countByTaskIdAndStudentIdAndIsDeleted(taskId, studentId, 0);
        if (appealCount >= MAX_APPEALS_PER_STUDENT_TASK) {
            throw new BusinessException("CONFLICT", "同一任务最多提交 3 次申诉", HttpStatus.CONFLICT);
        }

        Long subtaskId = null;
        if (Appeal.TYPE_TASK_REVIEW.equals(appealType)) {
            Long sid = request.getSubtaskId();
            if (sid == null) {
                throw new BusinessException("BAD_REQUEST", "子任务类申诉须填写 subtaskId", HttpStatus.BAD_REQUEST);
            }
            Subtask st =
                    subtaskRepository
                            .findById(sid)
                            .filter(s -> s.getIsDeleted() != null && s.getIsDeleted() == 0)
                            .orElseThrow(() -> new BusinessException("NOT_FOUND", "子任务不存在", HttpStatus.NOT_FOUND));
            if (!taskId.equals(st.getTaskId())) {
                throw new BusinessException("BAD_REQUEST", "子任务不属于当前任务", HttpStatus.BAD_REQUEST);
            }
            if (!groupMemberRepository.existsByGroupIdAndUserIdAndStatus(
                    st.getGroupId(), studentId, GroupMember.STATUS_ACTIVE)) {
                throw new BusinessException("FORBIDDEN", "仅所在小组成员可发起本子任务申诉", HttpStatus.FORBIDDEN);
            }
            subtaskId = sid;
        } else if (request.getSubtaskId() != null) {
            throw new BusinessException("BAD_REQUEST", "仅 task_review 类型可携带 subtaskId", HttpStatus.BAD_REQUEST);
        }

        if (Appeal.TYPE_PEER_REVIEW.equals(appealType)) {
            if (task.getEnablePeerReview() == null || task.getEnablePeerReview() != 1) {
                throw new BusinessException("BAD_REQUEST", "本任务未开启互评，无法发起互评申诉", HttpStatus.BAD_REQUEST);
            }
            if (!studentActiveInSemesterGroupOfClass(studentId, task.getClassId())) {
                throw new BusinessException(
                        "FORBIDDEN", "须为本班学期小组在籍成员方可发起互评申诉", HttpStatus.FORBIDDEN);
            }
        }

        Appeal a = new Appeal();
        a.setStudentId(studentId);
        a.setTaskId(taskId);
        a.setSubtaskId(subtaskId);
        a.setType(appealType);
        a.setReason(request.getReason().trim());
        a.setAttachments(trimToNull(request.getAttachments()));
        a.setStatus(STATUS_PENDING);
        a.setIsDeleted(0);
        a.setVersion(0);
        Appeal saved = appealRepository.save(a);

        final String notifyTitle;
        final String notifyBody;
        if (Appeal.TYPE_PEER_REVIEW.equals(appealType)) {
            notifyTitle = "新的互评申诉";
            notifyBody = "学生在任务「" + task.getName() + "」提交了互评相关申诉。";
        } else if (Appeal.TYPE_TASK_REVIEW.equals(appealType)) {
            notifyTitle = "新的子任务申诉";
            notifyBody = "学生在任务「" + task.getName() + "」提交了子任务/进度相关申诉。";
        } else {
            notifyTitle = "新的申诉";
            notifyBody = "学生在任务「" + task.getName() + "」提交了申诉。";
        }

        classRepository
                .findById(task.getClassId())
                .ifPresent(
                        clazz ->
                                notificationService.notifyUser(
                                        clazz.getTeacherId(),
                                        Notification.TYPE_NEW_APPEAL,
                                        notifyTitle,
                                        notifyBody,
                                        saved.getId()));

        return toResponse(saved);
    }

    private boolean studentActiveInSemesterGroupOfClass(Long studentId, Long classId) {
        List<TaskGroup> groups =
                taskGroupRepository.findByClassIdAndTaskIdIsNullAndIsDeletedOrderByIdAsc(classId, 0);
        for (TaskGroup g : groups) {
            if (groupMemberRepository.existsByGroupIdAndUserIdAndStatus(g.getId(), studentId, GroupMember.STATUS_ACTIVE)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeAppealType(String raw) {
        if (raw == null || raw.isBlank()) {
            return Appeal.TYPE_TEACHER_SCORE;
        }
        String t = raw.trim().toLowerCase(Locale.ROOT);
        if (Appeal.TYPE_TEACHER_SCORE.equals(t) || "teacher-score".equals(t)) {
            return Appeal.TYPE_TEACHER_SCORE;
        }
        if (Appeal.TYPE_PEER_REVIEW.equals(t) || "peer-review".equals(t)) {
            return Appeal.TYPE_PEER_REVIEW;
        }
        if (Appeal.TYPE_TASK_REVIEW.equals(t) || "task_review".equals(t) || "task-review".equals(t)) {
            return Appeal.TYPE_TASK_REVIEW;
        }
        throw new BusinessException("BAD_REQUEST", "申诉类型无效，须为 teacher_score / peer_review / task_review", HttpStatus.BAD_REQUEST);
    }

    @Transactional(readOnly = true)
    public List<AppealResponse> listStudentAppeals(Long studentId) {
        return appealRepository.findByStudentIdAndIsDeletedOrderByIdDesc(studentId, 0).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppealResponse> listStudentAppealsForTask(Long studentId, Long taskId) {
        Task task = requireActiveTask(taskId);
        requireStudentViewableClassForTask(studentId, task);
        return appealRepository.findByTaskIdAndStudentIdAndIsDeletedOrderByIdDesc(taskId, studentId, 0).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppealResponse> listTeacherAppealsForTask(Long teacherId, Long classId, Long taskId) {
        requireTeacherClass(teacherId, classId);
        requireActiveTaskInClass(classId, taskId);
        return appealRepository.findByTaskIdAndIsDeletedOrderByIdDesc(taskId, 0).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 教师申诉中心工作台：一次查询该教师全部班级下的申诉，避免前端按班级×任务逐条请求。
     */
    @Transactional(readOnly = true)
    public List<TeacherAppealWorkspaceItem> listTeacherAppealsWorkspace(Long teacherId) {
        List<ClassEntity> classes = classRepository.findByTeacherIdAndIsDeletedOrderByIdDesc(teacherId, 0);
        if (classes.isEmpty()) {
            return List.of();
        }

        Map<Long, ClassEntity> classById = classes.stream().collect(Collectors.toMap(ClassEntity::getId, c -> c, (a, b) -> a));
        List<Long> classIds = new ArrayList<>(classById.keySet());
        List<Task> tasks = taskRepository.findByClassIdInAndIsDeletedOrderByIdDesc(classIds, 0);
        if (tasks.isEmpty()) {
            return List.of();
        }

        Map<Long, Task> taskById =
                tasks.stream().collect(Collectors.toMap(Task::getId, t -> t, (a, b) -> a));
        List<Long> taskIds = new ArrayList<>(taskById.keySet());
        List<Appeal> appeals = appealRepository.findByTaskIdInAndIsDeletedOrderByIdDesc(taskIds, 0);
        if (appeals.isEmpty()) {
            return List.of();
        }

        return appeals.stream()
                .map(appeal -> {
                    Task task = taskById.get(appeal.getTaskId());
                    if (task == null) {
                        return null;
                    }
                    ClassEntity clazz = classById.get(task.getClassId());
                    if (clazz == null) {
                        return null;
                    }
                    return TeacherAppealWorkspaceItem.builder()
                            .appealId(appeal.getId())
                            .classId(clazz.getId())
                            .className(clazz.getName())
                            .taskId(task.getId())
                            .taskName(task.getName())
                            .studentId(appeal.getStudentId())
                            .subtaskId(appeal.getSubtaskId())
                            .type(appeal.getType())
                            .attachments(appeal.getAttachments())
                            .status(appeal.getStatus())
                            .reason(appeal.getReason())
                            .createdAt(appeal.getCreatedAt())
                            .handledAt(appeal.getHandledAt())
                            .teacherResponse(appeal.getTeacherResponse())
                            .build();
                })
                .filter(item -> item != null)
                .toList();
    }

    @Transactional
    public AppealResponse resolveAppeal(
            Long teacherId, Long classId, Long taskId, Long appealId, ResolveAppealRequest request) {
        requireTeacherClass(teacherId, classId);
        requireActiveTaskInClass(classId, taskId);

        Appeal appeal = appealRepository
                .findByIdAndTaskIdAndIsDeleted(appealId, taskId, 0)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "申诉不存在", HttpStatus.NOT_FOUND));

        String atype = appeal.getType();
        if (!(Appeal.TYPE_TEACHER_SCORE.equals(atype)
                || Appeal.TYPE_PEER_REVIEW.equals(atype)
                || Appeal.TYPE_TASK_REVIEW.equals(atype))) {
            throw new BusinessException("BAD_REQUEST", "不支持的申诉类型", HttpStatus.BAD_REQUEST);
        }
        if (appeal.getStatus() == null || appeal.getStatus() != STATUS_PENDING) {
            throw new BusinessException("BAD_REQUEST", "该申诉已处理", HttpStatus.BAD_REQUEST);
        }

        int outcome = request.getOutcome();
        appeal.setStatus(outcome);
        appeal.setTeacherResponse(trimToNull(request.getTeacherResponse()));
        appeal.setHandledAt(AppTime.now());

        if (request.getAdjustedTeacherScore() != null) {
            throw new BusinessException(
                    "BAD_REQUEST",
                    "申诉处理仅记录教师意见；如需调整成绩，请到评分中心修改教师评分",
                    HttpStatus.BAD_REQUEST);
        }

        Appeal saved = appealRepository.save(appeal);
        String outcomeLine;
        if (outcome == STATUS_SUCCESS) {
            outcomeLine = "申诉结果为：成功。";
        } else if (outcome == STATUS_REJECTED) {
            outcomeLine = "申诉结果为：驳回。";
        } else {
            outcomeLine = "申诉已处理。";
        }
        String resp = trimToNull(request.getTeacherResponse());
        String body = outcomeLine + (resp != null ? " 教师说明：" + resp : "");

        String notifTitle = "申诉处理通知";
        if (Appeal.TYPE_PEER_REVIEW.equals(atype)) {
            notifTitle = "互评申诉处理通知";
        } else if (Appeal.TYPE_TASK_REVIEW.equals(atype)) {
            notifTitle = "子任务申诉处理通知";
        } else {
            notifTitle = "教师评分申诉处理通知";
        }

        notificationService.notifyUser(
                saved.getStudentId(), Notification.TYPE_APPEAL_RESOLVED, notifTitle, body, saved.getId());
        return toResponse(saved);
    }

    private Task requireActiveTask(Long taskId) {
        return taskRepository
                .findByIdAndIsDeleted(taskId, 0)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND));
    }

    /** 与 {@link StudentTaskService} 一致：班级有效、未结束且在班。 */
    private void requireStudentViewableClassForTask(Long studentId, Task task) {
        Long classId = task.getClassId();
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

    private Task requireActiveTaskInClass(Long classId, Long taskId) {
        return taskRepository
                .findByIdAndClassIdAndIsDeleted(taskId, classId, 0)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND));
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

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private AppealResponse toResponse(Appeal a) {
        return AppealResponse.builder()
                .id(a.getId())
                .studentId(a.getStudentId())
                .taskId(a.getTaskId())
                .subtaskId(a.getSubtaskId())
                .type(a.getType())
                .reason(a.getReason())
                .attachments(a.getAttachments())
                .status(a.getStatus())
                .teacherResponse(a.getTeacherResponse())
                .createdAt(a.getCreatedAt())
                .handledAt(a.getHandledAt())
                .build();
    }
}
