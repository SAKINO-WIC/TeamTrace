package com.teamtrace.backend.service;

import com.teamtrace.backend.domain.progress.ProgressFormulas;
import com.teamtrace.backend.dto.subtask.CreateSubtaskRequest;
import com.teamtrace.backend.dto.subtask.MemberSubtaskProgress;
import com.teamtrace.backend.dto.subtask.ReviewSubtaskRequest;
import com.teamtrace.backend.dto.subtask.SendBackSubtaskRequest;
import com.teamtrace.backend.dto.subtask.SubmitSubtaskRequest;
import com.teamtrace.backend.dto.subtask.SubtaskProgressResponse;
import com.teamtrace.backend.dto.subtask.SubtaskResponse;
import com.teamtrace.backend.dto.subtask.SubtaskSubmissionHistoryResponse;
import com.teamtrace.backend.entity.ClassEntity;
import com.teamtrace.backend.entity.GroupMember;
import com.teamtrace.backend.entity.Notification;
import com.teamtrace.backend.entity.Subtask;
import com.teamtrace.backend.entity.SubtaskSubmissionHistory;
import com.teamtrace.backend.entity.Task;
import com.teamtrace.backend.entity.TaskGroup;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.GroupMemberRepository;
import com.teamtrace.backend.repository.SubtaskRepository;
import com.teamtrace.backend.repository.SubtaskSubmissionHistoryRepository;
import com.teamtrace.backend.repository.UserRepository;
import com.teamtrace.backend.repository.TaskGroupRepository;
import com.teamtrace.backend.entity.User;
import com.teamtrace.backend.repository.TaskRepository;
import com.teamtrace.backend.util.AppTime;
import com.teamtrace.backend.util.SnowflakeIdGenerator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubtaskService {

    private final ClassRepository classRepository;
    private final TaskRepository taskRepository;
    private final SemesterGroupAccess semesterGroupAccess;
    private final GroupMemberRepository groupMemberRepository;
    private final SubtaskRepository subtaskRepository;
    private final SubtaskSubmissionHistoryRepository subtaskSubmissionHistoryRepository;
    private final TaskGroupRepository taskGroupRepository;
    private final ClassMembershipService classMembershipService;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public SubtaskService(
            ClassRepository classRepository,
            TaskRepository taskRepository,
            SemesterGroupAccess semesterGroupAccess,
            GroupMemberRepository groupMemberRepository,
            TaskGroupRepository taskGroupRepository,
            SubtaskRepository subtaskRepository,
            SubtaskSubmissionHistoryRepository subtaskSubmissionHistoryRepository,
            ClassMembershipService classMembershipService,
            SnowflakeIdGenerator snowflakeIdGenerator,
            NotificationService notificationService,
            UserRepository userRepository) {
        this.classRepository = classRepository;
        this.taskRepository = taskRepository;
        this.semesterGroupAccess = semesterGroupAccess;
        this.groupMemberRepository = groupMemberRepository;
        this.subtaskRepository = subtaskRepository;
        this.subtaskSubmissionHistoryRepository = subtaskSubmissionHistoryRepository;
        this.taskGroupRepository = taskGroupRepository;
        this.classMembershipService = classMembershipService;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<SubtaskResponse> listForTeacher(Long teacherId, Long classId, Long taskId, Long groupId) {
        requireTeacherClass(teacherId, classId);
        semesterGroupAccess.requireGroupForTask(classId, taskId, groupId);
        return loadList(taskId, groupId);
    }

    @Transactional(readOnly = true)
    public List<SubtaskResponse> listForStudent(Long studentId, Long classId, Long taskId, Long groupId) {
        requireStudentClassView(classId, studentId);
        TaskGroup g = semesterGroupAccess.requireGroupForTask(classId, taskId, groupId);
        requireStudentInGroup(studentId, g.getId());
        return loadList(taskId, groupId);
    }

    @Transactional
    public SubtaskResponse createByTeacher(Long teacherId, Long classId, Long taskId, Long groupId, CreateSubtaskRequest req) {
        requireTeacherClass(teacherId, classId);
        semesterGroupAccess.requireGroupForTask(classId, taskId, groupId);
        Task task = requireOpenTask(classId, taskId);
        return createEntity(task, groupId, req);
    }

    @Transactional
    public SubtaskResponse createByLeader(Long studentId, Long classId, Long taskId, Long groupId, CreateSubtaskRequest req) {
        requireStudentClassView(classId, studentId);
        TaskGroup g = semesterGroupAccess.requireGroupForTask(classId, taskId, groupId);
        requireLeader(studentId, g);
        Task task = requireOpenTask(classId, taskId);
        return createEntity(task, groupId, req);
    }

    @Transactional
    public SubtaskResponse claim(Long studentId, Long classId, Long taskId, Long groupId, Long subtaskId) {
        requireStudentClassView(classId, studentId);
        TaskGroup g = semesterGroupAccess.requireGroupForTask(classId, taskId, groupId);
        requireStudentInGroup(studentId, g.getId());
        Subtask st = requireSubtask(taskId, groupId, subtaskId);
        if (st.getStatus() != Subtask.STATUS_PENDING_CLAIM) {
            throw new BusinessException("BAD_REQUEST", "子任务不可认领", HttpStatus.BAD_REQUEST);
        }
        if (st.getAssigneeId() != null) {
            throw new BusinessException("CONFLICT", "子任务已被认领", HttpStatus.CONFLICT);
        }
        st.setAssigneeId(studentId);
        st.setStatus(Subtask.STATUS_IN_PROGRESS);
        return toResponse(subtaskRepository.save(st));
    }

    @Transactional
    public SubtaskResponse submit(Long studentId, Long classId, Long taskId, Long groupId, Long subtaskId, SubmitSubtaskRequest req) {
        requireStudentClassView(classId, studentId);
        TaskGroup g = semesterGroupAccess.requireGroupForTask(classId, taskId, groupId);
        Subtask st = requireSubtask(taskId, groupId, subtaskId);
        if (!studentId.equals(st.getAssigneeId())) {
            throw new BusinessException("FORBIDDEN", "仅负责人可提交", HttpStatus.FORBIDDEN);
        }
        LocalDateTime now = AppTime.now();
        boolean modifyingSubmitted =
                st.getStatus() == Subtask.STATUS_PENDING_REVIEW || st.getStatus() == Subtask.STATUS_DONE;
        if (st.getStatus() != Subtask.STATUS_IN_PROGRESS && !modifyingSubmitted) {
            throw new BusinessException("BAD_REQUEST", "当前状态不可提交", HttpStatus.BAD_REQUEST);
        }
        Task task = taskRepository
                .findByIdAndClassIdAndIsDeleted(taskId, classId, 0)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND));
        if (task.getDeadline() == null || !now.isBefore(task.getDeadline())) {
            throw new BusinessException("BAD_REQUEST", "总任务已超过截止时间，不可提交或修改提交", HttpStatus.BAD_REQUEST);
        }
        if (st.getDeadline() == null || !now.isBefore(st.getDeadline())) {
            throw new BusinessException("BAD_REQUEST", "已超过截止时间，不可提交或修改提交", HttpStatus.BAD_REQUEST);
        }
        String content = req.getSubmissionContent().trim();
        saveSubmissionHistory(st, studentId, content, now);
        st.setSubmissionContent(content);
        st.setSubmittedAt(now);
        st.setStatus(Subtask.STATUS_PENDING_REVIEW);
        Subtask saved = subtaskRepository.save(st);
        notifyLeaderSubtaskPendingReview(classId, taskId, g, saved);
        return toResponse(saved);
    }

    @Transactional
    public SubtaskResponse review(
            Long leaderId, Long classId, Long taskId, Long groupId, Long subtaskId, ReviewSubtaskRequest req) {
        requireStudentClassView(classId, leaderId);
        TaskGroup g = semesterGroupAccess.requireGroupForTask(classId, taskId, groupId);
        requireLeader(leaderId, g);
        Subtask st = requireSubtask(taskId, groupId, subtaskId);
        if (st.getStatus() != Subtask.STATUS_PENDING_REVIEW) {
            throw new BusinessException("BAD_REQUEST", "当前无需组长审批", HttpStatus.BAD_REQUEST);
        }
        LocalDateTime now = AppTime.now();
        st.setReviewerId(leaderId);
        st.setReviewedAt(now);
        st.setReviewComment(trimToNull(req.getReviewComment()));
        if (Boolean.TRUE.equals(req.getApproved())) {
            st.setStatus(Subtask.STATUS_DONE);
        } else {
            st.setStatus(Subtask.STATUS_IN_PROGRESS);
        }
        Subtask saved = subtaskRepository.save(st);
        notifyAssigneeSubtaskReviewResult(classId, taskId, leaderId, saved, Boolean.TRUE.equals(req.getApproved()));
        return toResponse(saved);
    }

    /** 将「已完成」打回为进行中（进度回退） */
    @Transactional
    public SubtaskResponse sendBackCompleted(
            Long leaderId, Long classId, Long taskId, Long groupId, Long subtaskId, SendBackSubtaskRequest req) {
        requireStudentClassView(classId, leaderId);
        TaskGroup g = semesterGroupAccess.requireGroupForTask(classId, taskId, groupId);
        requireLeader(leaderId, g);
        Subtask st = requireSubtask(taskId, groupId, subtaskId);
        if (st.getStatus() != Subtask.STATUS_DONE) {
            throw new BusinessException("BAD_REQUEST", "仅已完成子任务可打回", HttpStatus.BAD_REQUEST);
        }
        LocalDateTime now = AppTime.now();
        st.setStatus(Subtask.STATUS_IN_PROGRESS);
        st.setReviewerId(leaderId);
        st.setReviewedAt(now);
        st.setReviewComment(req.getReviewComment().trim());
        Subtask saved = subtaskRepository.save(st);
        notifyAssigneeSubtaskSentBack(classId, taskId, leaderId, saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public SubtaskProgressResponse progressForTeacher(Long teacherId, Long classId, Long taskId, Long groupId) {
        requireTeacherClass(teacherId, classId);
        TaskGroup g = semesterGroupAccess.requireGroupForTask(classId, taskId, groupId);
        return buildProgress(taskId, g.getId());
    }

    @Transactional(readOnly = true)
    public SubtaskProgressResponse progressForStudent(Long studentId, Long classId, Long taskId, Long groupId) {
        requireStudentClassView(classId, studentId);
        TaskGroup g = semesterGroupAccess.requireGroupForTask(classId, taskId, groupId);
        requireStudentInGroup(studentId, g.getId());
        return buildProgress(taskId, g.getId());
    }

    /** 进度必须与 {@link #loadList} 一致，按当前任务 {@code taskId} + 小组 {@code groupId} 统计，避免跨任务子任务混入。 */
    private SubtaskProgressResponse buildProgress(Long taskId, Long groupId) {
        long all =
                subtaskRepository.countByTaskIdAndGroupIdAndIsDeleted(taskId, groupId, 0);
        long claimed =
                subtaskRepository.countByTaskIdAndGroupIdAndIsDeletedAndAssigneeIdIsNotNull(taskId, groupId, 0);
        long done = subtaskRepository.countByTaskIdAndGroupIdAndIsDeletedAndStatus(taskId, groupId, 0, Subtask.STATUS_DONE);
        BigDecimal groupPct = ProgressFormulas.groupProgressPercent(done, all);

        List<GroupMember> members =
                groupMemberRepository.findByGroupIdAndStatusOrderByIdAsc(groupId, GroupMember.STATUS_ACTIVE);
        List<MemberSubtaskProgress> mp = new ArrayList<>();
        java.util.Map<Long, String> memberNameMap = buildMemberNameMap(members);
        for (GroupMember gm : members) {
            long uid = gm.getUserId();
            long cDone =
                    subtaskRepository.countByTaskIdAndGroupIdAndIsDeletedAndAssigneeIdAndStatus(
                            taskId, groupId, 0, uid, Subtask.STATUS_DONE);
            long cClaimed = subtaskRepository.countByTaskIdAndGroupIdAndIsDeletedAndAssigneeId(taskId, groupId, 0, uid);
            BigDecimal p = ProgressFormulas.memberProgressPercent(cDone, cClaimed);
            mp.add(MemberSubtaskProgress.builder()
                    .studentId(uid)
                    .studentName(memberNameMap.getOrDefault(uid, null))
                    .completedSubtasks(cDone)
                    .claimedSubtasks(cClaimed)
                    .progressPercent(p)
                    .build());
        }
        return SubtaskProgressResponse.builder()
                .groupId(groupId)
                .groupCompletedSubtasks(done)
                .groupClaimedSubtasks(claimed)
                .groupTotalSubtasks(all)
                .groupProgressPercent(groupPct)
                .members(mp)
                .build();
    }

    /**
     * Batch version of buildProgress: computes progress for multiple (taskId, groupId) pairs in one DB round-trip.
     * Returns a Map of "taskId-groupId" -> SubtaskProgressResponse.
     */
    public Map<String, SubtaskProgressResponse> buildProgressBatch(List<Long> taskIds, List<Long> groupIds) {
        if (taskIds.isEmpty() || groupIds.isEmpty()) {
            return Map.of();
        }

        // Single GROUP BY query replaces hundreds of individual count queries
        List<Object[]> rows = subtaskRepository.countProgressByTaskIdsAndGroupIds(taskIds, groupIds);

        // Parse results: Map<taskId, Map<groupId, Map<assigneeId, Map<status, count>>>>
        Map<Long, Map<Long, Map<Long, Map<Integer, Long>>>> taskGroupCounts = new HashMap<>();
        for (Object[] row : rows) {
            Long taskId = ((Number) row[0]).longValue();
            Long groupId = ((Number) row[1]).longValue();
            Long assigneeId = row[2] != null ? ((Number) row[2]).longValue() : null;
            int status = ((Number) row[3]).intValue();
            long count = ((Number) row[4]).longValue();

            taskGroupCounts
                    .computeIfAbsent(taskId, k -> new HashMap<>())
                    .computeIfAbsent(groupId, k -> new HashMap<>())
                    .computeIfAbsent(assigneeId != null ? assigneeId : -1L, k -> new HashMap<>())
                    .put(status, count);
        }

        // Batch load all group members
        List<GroupMember> allMembers =
                groupMemberRepository.findByGroupIdInAndStatus(groupIds, GroupMember.STATUS_ACTIVE);
        Map<Long, List<GroupMember>> membersByGroup = allMembers.stream()
                .collect(Collectors.groupingBy(GroupMember::getGroupId));

        // Build responses
        Map<String, SubtaskProgressResponse> result = new HashMap<>();
        for (Long taskId : taskIds) {
            for (Long groupId : groupIds) {
                Map<Long, Map<Integer, Long>> assigneeCounts =
                        taskGroupCounts.getOrDefault(taskId, Map.of()).getOrDefault(groupId, Map.of());

                // Group-level: claimed = sum of all rows where assigneeId != null
                long all = 0;
                long claimed = 0;
                long done = 0;
                for (var entry : assigneeCounts.entrySet()) {
                    for (var statusEntry : entry.getValue().entrySet()) {
                        all += statusEntry.getValue();
                    }
                    if (entry.getKey() != -1L) { // has assignee
                        for (var statusEntry : entry.getValue().entrySet()) {
                            claimed += statusEntry.getValue();
                            if (statusEntry.getKey() == Subtask.STATUS_DONE) {
                                done += statusEntry.getValue();
                            }
                        }
                    }
                }
                // Also count done from unassigned (shouldn\'t happen but safe)
                Map<Integer, Long> unassignedCounts = assigneeCounts.getOrDefault(-1L, Map.of());
                done += unassignedCounts.getOrDefault(Subtask.STATUS_DONE, 0L);

                BigDecimal groupPct = ProgressFormulas.groupProgressPercent(done, all);

                // Member-level progress
                List<GroupMember> members = membersByGroup.getOrDefault(groupId, List.of());
                List<MemberSubtaskProgress> mp = new ArrayList<>();
                java.util.Map<Long, String> memberNameMap = buildMemberNameMap(members);
                for (GroupMember gm : members) {
                    long uid = gm.getUserId();
                    Map<Integer, Long> memberCounts = assigneeCounts.getOrDefault(uid, Map.of());
                    long cDone = memberCounts.getOrDefault(Subtask.STATUS_DONE, 0L);
                    long cClaimed = memberCounts.values().stream().mapToLong(Long::longValue).sum();
                    BigDecimal p = ProgressFormulas.memberProgressPercent(cDone, cClaimed);
                    mp.add(MemberSubtaskProgress.builder()
                            .studentId(uid)
                            .studentName(memberNameMap.getOrDefault(uid, null))
                            .completedSubtasks(cDone)
                            .claimedSubtasks(cClaimed)
                            .progressPercent(p)
                            .build());
                }

                result.put(taskId + "-" + groupId,
                        SubtaskProgressResponse.builder()
                                .groupId(groupId)
                                .groupCompletedSubtasks(done)
                                .groupClaimedSubtasks(claimed)
                                .groupTotalSubtasks(all)
                                .groupProgressPercent(groupPct)
                                .members(mp)
                                .build());
            }
        }
        return result;
    }



    /** Batch: progress for all semester groups in one call, eliminates N+1. */
    @Transactional(readOnly = true)
    public Map<Long, SubtaskProgressResponse> progressForTaskAllGroups(
            Long teacherId, Long classId, Long taskId) {
        requireTeacherClass(teacherId, classId);

        List<TaskGroup> groups = taskGroupRepository
                .findByClassIdAndTaskIdIsNullAndIsDeletedOrderByIdAsc(classId, 0);
        if (groups.isEmpty()) {
            return Map.of();
        }

        List<Long> groupIds = groups.stream().map(TaskGroup::getId).toList();
        Map<String, SubtaskProgressResponse> batch = buildProgressBatch(List.of(taskId), groupIds);

        Map<Long, SubtaskProgressResponse> result = new LinkedHashMap<>();
        for (Long gid : groupIds) {
            SubtaskProgressResponse resp = batch.get(taskId + "-" + gid);
            if (resp != null) {
                result.put(gid, resp);
            }
        }
        return result;
    }

    private List<SubtaskResponse> loadList(Long taskId, Long groupId) {
        return subtaskRepository.findByTaskIdAndGroupIdAndIsDeletedOrderByIdAsc(taskId, groupId, 0).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private SubtaskResponse createEntity(Task task, Long groupId, CreateSubtaskRequest req) {
        LocalDateTime deadline = parseDeadline(req.getDeadline());
        if (deadline != null && task.getDeadline() != null && deadline.isAfter(task.getDeadline())) {
            throw new BusinessException("BAD_REQUEST", "子任务截止时间不能晚于总任务截止时间", HttpStatus.BAD_REQUEST);
        }
        Subtask s = new Subtask();
        s.setSubtaskUuid(snowflakeIdGenerator.nextId());
        s.setTaskId(task.getId());
        s.setGroupId(groupId);
        s.setName(req.getName().trim());
        s.setDescription(trimToNull(req.getDescription()));
        s.setQualityRequirement(trimToNull(req.getQualityRequirement()));
        s.setDeadline(deadline);
        s.setReminderTime(null);
        s.setStatus(Subtask.STATUS_PENDING_CLAIM);
        s.setIsOverdue(0);
        s.setVersion(0);
        s.setIsDeleted(0);
        return toResponse(subtaskRepository.save(s));
    }

    private Task requireOpenTask(Long classId, Long taskId) {
        Task task = taskRepository
                .findByIdAndClassIdAndIsDeleted(taskId, classId, 0)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND));
        LocalDateTime now = AppTime.now();
        if (task.getDeadline() == null || !now.isBefore(task.getDeadline())) {
            throw new BusinessException("BAD_REQUEST", "总任务已超过截止时间，不能再创建子任务", HttpStatus.BAD_REQUEST);
        }
        return task;
    }

    private Subtask requireSubtask(Long taskId, Long groupId, Long subtaskId) {
        return subtaskRepository
                .findByIdAndTaskIdAndGroupIdAndIsDeleted(subtaskId, taskId, groupId, 0)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "子任务不存在", HttpStatus.NOT_FOUND));
    }

    private void requireStudentInGroup(Long studentId, Long groupId) {
        if (!groupMemberRepository.existsByGroupIdAndUserIdAndStatus(groupId, studentId, GroupMember.STATUS_ACTIVE)) {
            throw new BusinessException("FORBIDDEN", "你不在该小组中", HttpStatus.FORBIDDEN);
        }
    }

    private void requireLeader(Long studentId, TaskGroup g) {
        if (!g.getLeaderId().equals(studentId)) {
            throw new BusinessException("FORBIDDEN", "仅组长可操作", HttpStatus.FORBIDDEN);
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

    private SubtaskResponse toResponse(Subtask s) {
        return SubtaskResponse.builder()
                .subtaskId(s.getId())
                .subtaskUuid(s.getSubtaskUuid())
                .taskId(s.getTaskId())
                .groupId(s.getGroupId())
                .name(s.getName())
                .description(s.getDescription())
                .qualityRequirement(s.getQualityRequirement())
                .assigneeId(s.getAssigneeId())
                .deadline(s.getDeadline())
                .status(s.getStatus())
                .submittedAt(s.getSubmittedAt())
                .submissionContent(s.getSubmissionContent())
                .submissionHistories(loadSubmissionHistories(s.getId()))
                .reviewerId(s.getReviewerId())
                .reviewComment(s.getReviewComment())
                .reviewedAt(s.getReviewedAt())
                .build();
    }

    private void saveSubmissionHistory(Subtask st, Long submitterId, String content, LocalDateTime submittedAt) {
        List<SubtaskSubmissionHistory> histories =
                subtaskSubmissionHistoryRepository.findBySubtaskIdOrderByVersionNoDesc(st.getId());
        int nextVersion = histories.size() + 1;

        if (histories.isEmpty() && st.getSubmissionContent() != null && !st.getSubmissionContent().isBlank()) {
            SubtaskSubmissionHistory legacy = buildSubmissionHistory(
                    st,
                    submitterId,
                    1,
                    st.getSubmissionContent(),
                    st.getSubmittedAt() != null ? st.getSubmittedAt() : submittedAt,
                    0);
            subtaskSubmissionHistoryRepository.save(legacy);
            nextVersion = 2;
        } else if (!histories.isEmpty()) {
            histories.forEach(history -> history.setIsCurrent(0));
            subtaskSubmissionHistoryRepository.saveAll(histories);
        }

        SubtaskSubmissionHistory current = buildSubmissionHistory(st, submitterId, nextVersion, content, submittedAt, 1);
        subtaskSubmissionHistoryRepository.save(current);
    }

    private SubtaskSubmissionHistory buildSubmissionHistory(
            Subtask st,
            Long submitterId,
            Integer versionNo,
            String content,
            LocalDateTime submittedAt,
            Integer isCurrent) {
        SubtaskSubmissionHistory history = new SubtaskSubmissionHistory();
        history.setSubtaskId(st.getId());
        history.setTaskId(st.getTaskId());
        history.setGroupId(st.getGroupId());
        history.setSubmitterId(submitterId);
        history.setVersionNo(versionNo);
        history.setSubmissionContent(content);
        history.setSubmittedAt(submittedAt);
        history.setIsCurrent(isCurrent);
        return history;
    }

    private List<SubtaskSubmissionHistoryResponse> loadSubmissionHistories(Long subtaskId) {
        return subtaskSubmissionHistoryRepository.findBySubtaskIdOrderByVersionNoDesc(subtaskId).stream()
                .map(this::toSubmissionHistoryResponse)
                .collect(Collectors.toList());
    }

    private SubtaskSubmissionHistoryResponse toSubmissionHistoryResponse(SubtaskSubmissionHistory history) {
        return SubtaskSubmissionHistoryResponse.builder()
                .id(history.getId())
                .subtaskId(history.getSubtaskId())
                .submitterId(history.getSubmitterId())
                .versionNo(history.getVersionNo())
                .submissionContent(history.getSubmissionContent())
                .submittedAt(history.getSubmittedAt())
                .current(Integer.valueOf(1).equals(history.getIsCurrent()))
                .build();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
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

    /** 组员提交后提醒组长审批（组长本人提交则不发给自己）。 */
    private void notifyLeaderSubtaskPendingReview(Long classId, Long taskId, TaskGroup g, Subtask st) {
        Long leaderId = g.getLeaderId();
        Long assigneeId = st.getAssigneeId();
        if (leaderId == null || assigneeId == null || leaderId.equals(assigneeId)) {
            return;
        }
        Task task = taskRepository
                .findByIdAndClassIdAndIsDeleted(taskId, classId, 0)
                .orElse(null);
        String taskName = task != null ? task.getName() : "任务";
        notificationService.notifyUser(
                leaderId,
                Notification.TYPE_SUBTASK_PENDING_REVIEW,
                "子任务待审批",
                "组员在「" + taskName + "」中提交了子任务「" + st.getName() + "」，请你审批。",
                st.getId());
    }

    /** 组长审批后通知负责人（组长审批自己的提交则不发）。 */
    private void notifyAssigneeSubtaskReviewResult(Long classId, Long taskId, Long leaderId, Subtask st, boolean approved) {
        Long assigneeId = st.getAssigneeId();
        if (assigneeId == null || leaderId.equals(assigneeId)) {
            return;
        }
        Task task = taskRepository
                .findByIdAndClassIdAndIsDeleted(taskId, classId, 0)
                .orElse(null);
        String taskName = task != null ? task.getName() : "任务";
        String title = approved ? "子任务已通过" : "子任务需修改";
        String content =
                approved
                        ? "组长已通过你在「" + taskName + "」子任务「" + st.getName() + "」的提交。"
                        : "组长未通过你在「" + taskName + "」子任务「" + st.getName() + "」的提交，请修改后重新提交。";
        notificationService.notifyUser(
                assigneeId, Notification.TYPE_SUBTASK_REVIEW_RESULT, title, content, st.getId());
    }

    /** 组长打回已完成子任务后通知负责人（打回自己则不发）。 */
    private void notifyAssigneeSubtaskSentBack(Long classId, Long taskId, Long leaderId, Subtask st) {
        Long assigneeId = st.getAssigneeId();
        if (assigneeId == null || leaderId.equals(assigneeId)) {
            return;
        }
        Task task = taskRepository
                .findByIdAndClassIdAndIsDeleted(taskId, classId, 0)
                .orElse(null);
        String taskName = task != null ? task.getName() : "任务";
        notificationService.notifyUser(
                assigneeId,
                Notification.TYPE_SUBTASK_SENT_BACK,
                "子任务已打回",
                "组长将你在「" + taskName + "」中已完成的子任务「" + st.getName() + "」打回为进行中，请按要求修改。",
                st.getId());
    }
    private java.util.Map<Long, String> buildMemberNameMap(java.util.List<GroupMember> members) {
        java.util.List<Long> ids = members.stream().map(GroupMember::getUserId).collect(java.util.stream.Collectors.toList());
        java.util.Map<Long, String> map = new java.util.HashMap<>();
        if (!ids.isEmpty()) {
            java.util.List<User> users = userRepository.findAllById(ids);
            for (User u : users) {
                map.put(u.getId(), u.getName());
            }
        }
        return map;
    }
}
