package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.teacher.ClassResponse;
import com.teamtrace.backend.dto.teacher.CreateClassRequest;
import com.teamtrace.backend.dto.teacher.TeacherClassDetailResponse;
import com.teamtrace.backend.dto.teacher.TeacherClassListItemResponse;
import com.teamtrace.backend.dto.teacher.TeacherClassStudentItemResponse;
import com.teamtrace.backend.entity.ClassEntity;
import com.teamtrace.backend.entity.ClassInviteCode;
import com.teamtrace.backend.entity.ClassStudent;
import com.teamtrace.backend.entity.Task;
import com.teamtrace.backend.entity.TaskGroup;
import com.teamtrace.backend.entity.User;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.ClassInviteCodeRepository;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.ClassStudentRepository;
import com.teamtrace.backend.repository.TaskGroupRepository;
import com.teamtrace.backend.repository.TaskRepository;
import com.teamtrace.backend.repository.UserRepository;
import com.teamtrace.backend.util.AppTime;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeacherClassService {

    /** 与 PRD「30 天内可恢复」一致。 */
    private static final int CLASS_RESTORE_WINDOW_DAYS = 30;

    private final ClassRepository classRepository;
    private final ClassStudentRepository classStudentRepository;
    private final ClassInviteCodeRepository classInviteCodeRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final TaskGroupRepository taskGroupRepository;

    public TeacherClassService(
            ClassRepository classRepository,
            ClassStudentRepository classStudentRepository,
            ClassInviteCodeRepository classInviteCodeRepository,
            UserRepository userRepository,
            TaskRepository taskRepository,
            TaskGroupRepository taskGroupRepository) {
        this.classRepository = classRepository;
        this.classStudentRepository = classStudentRepository;
        this.classInviteCodeRepository = classInviteCodeRepository;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.taskGroupRepository = taskGroupRepository;
    }

    @Transactional
    public ClassResponse createClass(Long teacherId, CreateClassRequest request) {
        Integer min = request.getGroupSizeMin() == null ? 1 : request.getGroupSizeMin();
        Integer max = request.getGroupSizeMax() == null ? 10 : request.getGroupSizeMax();
        if (min > max) {
            throw new BusinessException("BAD_REQUEST", "小组最小人数不能大于最大人数", HttpStatus.BAD_REQUEST);
        }

        ClassEntity saved = insertWithRetry(teacherId, request, min, max);
        return ClassResponse.builder()
                .classId(saved.getId())
                .classCode(saved.getClassCode())
                .name(saved.getName())
                .semester(saved.getSemester())
                .groupSizeMin(saved.getGroupSizeMin())
                .groupSizeMax(saved.getGroupSizeMax())
                .groupingLocked(saved.getGroupingLocked())
                .status(saved.getStatus())
                .build();
    }

    private ClassEntity insertWithRetry(Long teacherId, CreateClassRequest request, Integer min, Integer max) {
        int maxAttempts = 6;
        for (int i = 0; i < maxAttempts; i++) {
            String classCode = generateNextClassCode();
            ClassEntity clazz = new ClassEntity();
            clazz.setClassCode(classCode);
            clazz.setTeacherId(teacherId);
            clazz.setName(request.getName().trim());
            clazz.setSemester(request.getSemester().trim());
            clazz.setGroupSizeMin(min);
            clazz.setGroupSizeMax(max);
            clazz.setGroupingLocked(0);
            clazz.setStatus(1);
            clazz.setIsDeleted(0);
            try {
                return classRepository.save(clazz);
            } catch (DataIntegrityViolationException ex) {
                // class_code 唯一冲突时重试
            }
        }
        throw new BusinessException("INTERNAL_ERROR", "创建班级失败，请重试", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String generateNextClassCode() {
        String year = String.valueOf(Year.now().getValue());
        int nextSerial = classRepository.findByClassCodeStartingWith(year).stream()
                .map(ClassEntity::getClassCode)
                .map(code -> parseValidClassSerial(code, year))
                .flatMapToInt(OptionalInt::stream)
                .max()
                .orElse(0) + 1;

        if (nextSerial > 9999) {
            throw new BusinessException("BAD_REQUEST", "当年班级编号已达上限", HttpStatus.BAD_REQUEST);
        }
        return String.format("%s%04d", year, nextSerial);
    }

    /**
     * 仅接受“4位年份 + 4位数字流水号”的合法班级编号，兼容历史脏数据。
     */
    private OptionalInt parseValidClassSerial(String classCode, String year) {
        if (classCode == null || classCode.length() != 8 || !classCode.startsWith(year)) {
            return OptionalInt.empty();
        }

        String serialPart = classCode.substring(4);
        for (int i = 0; i < serialPart.length(); i++) {
            if (!Character.isDigit(serialPart.charAt(i))) {
                return OptionalInt.empty();
            }
        }
        return OptionalInt.of(Integer.parseInt(serialPart));
    }

    @Transactional(readOnly = true)
    public List<TeacherClassListItemResponse> listMyClasses(Long teacherId) {
        List<ClassEntity> classes = classRepository.findByTeacherIdAndIsDeletedOrderByIdDesc(teacherId, 0);
        return buildClassListItems(classes, null);
    }

    @Transactional(readOnly = true)
    public List<TeacherClassListItemResponse> listArchivedClasses(Long teacherId) {
        LocalDateTime cutoff = AppTime.now().minusDays(CLASS_RESTORE_WINDOW_DAYS);
        List<ClassEntity> classes = classRepository.findByTeacherIdAndIsDeletedOrderByIdDesc(teacherId, 1).stream()
                .filter(clazz -> clazz.getDeletedAt() != null && clazz.getDeletedAt().isAfter(cutoff))
                .toList();
        return buildClassListItems(classes, ClassEntity::getDeletedAt);
    }

    private List<TeacherClassListItemResponse> buildClassListItems(
            List<ClassEntity> classes,
            java.util.function.Function<ClassEntity, LocalDateTime> deletedAtResolver) {
        if (classes.isEmpty()) {
            return List.of();
        }

        List<Long> classIds = classes.stream().map(ClassEntity::getId).toList();
        LocalDateTime now = AppTime.now();
        Map<Long, ClassInviteCode> inviteByClassId = loadActiveInvitesByClassId(classIds, now);
        Map<Long, Long> studentCountByClassId = loadStudentCountsByClassId(classIds);
        Map<Long, Long> groupCountByClassId = loadSemesterGroupCountsByClassId(classIds);

        return classes.stream()
                .map(clazz -> {
                    Long classId = clazz.getId();
                    ClassInviteCode activeInvite = inviteByClassId.get(classId);
                    LocalDateTime deletedAt = deletedAtResolver == null ? null : deletedAtResolver.apply(clazz);
                    return TeacherClassListItemResponse.builder()
                            .classId(classId)
                            .classCode(clazz.getClassCode())
                            .name(clazz.getName())
                            .semester(clazz.getSemester())
                            .status(clazz.getStatus())
                            .groupingLocked(clazz.getGroupingLocked())
                            .studentCount(studentCountByClassId.getOrDefault(classId, 0L))
                            .groupCount(groupCountByClassId.getOrDefault(classId, 0L))
                            .activeInviteCode(activeInvite == null ? null : activeInvite.getCode())
                            .inviteExpireAt(activeInvite == null ? null : activeInvite.getExpireAt())
                            .deletedAt(deletedAt)
                            .build();
                })
                .toList();
    }

    private Map<Long, ClassInviteCode> loadActiveInvitesByClassId(List<Long> classIds, LocalDateTime now) {
        if (classIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, ClassInviteCode> inviteByClassId = new HashMap<>();
        for (ClassInviteCode invite : classInviteCodeRepository.findActiveInvitesForClassIds(classIds, 0, now)) {
            inviteByClassId.putIfAbsent(invite.getClassId(), invite);
        }
        return inviteByClassId;
    }

    private Map<Long, Long> loadStudentCountsByClassId(List<Long> classIds) {
        if (classIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : classStudentRepository.countActiveStudentsByClassIds(classIds, 0)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private Map<Long, Long> loadSemesterGroupCountsByClassId(List<Long> classIds) {
        if (classIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : taskGroupRepository.countSemesterGroupsByClassIds(classIds, 0)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public TeacherClassDetailResponse getClassDetail(Long teacherId, Long classId) {
        ClassEntity clazz = classRepository.findById(classId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND));
        if (clazz.getIsDeleted() != null && clazz.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND);
        }
        if (!teacherId.equals(clazz.getTeacherId())) {
            throw new BusinessException("FORBIDDEN", "无权限", HttpStatus.FORBIDDEN);
        }

        ClassInviteCode activeInviteCode = classInviteCodeRepository
                .findFirstByClassIdAndStatusAndExpireAtAfterOrderByIdDesc(classId, 0, AppTime.now())
                .orElse(null);

        return TeacherClassDetailResponse.builder()
                .classId(clazz.getId())
                .classCode(clazz.getClassCode())
                .name(clazz.getName())
                .semester(clazz.getSemester())
                .groupSizeMin(clazz.getGroupSizeMin())
                .groupSizeMax(clazz.getGroupSizeMax())
                .groupingLocked(clazz.getGroupingLocked())
                .status(clazz.getStatus())
                .activeInviteCode(activeInviteCode == null ? null : activeInviteCode.getCode())
                .inviteExpireAt(activeInviteCode == null ? null : activeInviteCode.getExpireAt())
                .studentCount(classStudentRepository.countByClassIdAndIsDeleted(classId, 0))
                .build();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getClassStudents(Long teacherId, Long classId, int page, int size) {
        ClassEntity clazz = classRepository.findById(classId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND));
        if (clazz.getIsDeleted() != null && clazz.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND);
        }
        if (!teacherId.equals(clazz.getTeacherId())) {
            throw new BusinessException("FORBIDDEN", "无权限", HttpStatus.FORBIDDEN);
        }

        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        Page<ClassStudent> classStudentPage = classStudentRepository.findByClassIdAndIsDeletedOrderByIdDesc(
                classId, 0, PageRequest.of(normalizedPage - 1, normalizedSize));

        List<Long> studentIds = classStudentPage.getContent().stream()
                .map(ClassStudent::getStudentId)
                .toList();
        Map<Long, User> userMap = userRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        List<TeacherClassStudentItemResponse> list = classStudentPage.getContent().stream()
                .map(cs -> {
                    User user = userMap.get(cs.getStudentId());
                    return TeacherClassStudentItemResponse.builder()
                            .studentId(cs.getStudentId())
                            .name(user == null ? "" : user.getName())
                            .phone(user == null ? "" : user.getPhone())
                            .email(user == null ? "" : user.getEmail())
                            .joinTime(cs.getJoinTime())
                            .build();
                })
                .toList();

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("page", normalizedPage);
        data.put("size", normalizedSize);
        data.put("total", classStudentPage.getTotalElements());
        data.put("pages", classStudentPage.getTotalPages());
        data.put("hasNext", classStudentPage.hasNext());
        return data;
    }

    @Transactional
    public void removeClassStudent(Long teacherId, Long classId, Long studentId) {
        ClassEntity clazz = classRepository.findById(classId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND));
        if (clazz.getIsDeleted() != null && clazz.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND);
        }
        if (!teacherId.equals(clazz.getTeacherId())) {
            throw new BusinessException("FORBIDDEN", "无权限", HttpStatus.FORBIDDEN);
        }

        ClassStudent relation = classStudentRepository.findByClassIdAndStudentId(classId, studentId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "学生不在班级中", HttpStatus.NOT_FOUND));
        if (relation.getIsDeleted() != null && relation.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "学生不在班级中", HttpStatus.NOT_FOUND);
        }

        relation.setIsDeleted(1);
        relation.setDeletedAt(AppTime.now());
        classStudentRepository.save(relation);
    }

    /**
     * 分组锁定：{@code grouping_locked=1} 时，教师端学期组**新建**与**跨组移动成员**由 {@link TeacherTaskGroupService} 拒绝
     * （{@code GROUPING_LOCKED} / 409）；学生端后续「自建/改组」接口接入时亦应校验同一字段。
     */
    @Transactional
    public Map<String, Object> setGroupingLocked(Long teacherId, Long classId, boolean locked) {
        ClassEntity clazz = classRepository.findById(classId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND));
        if (clazz.getIsDeleted() != null && clazz.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND);
        }
        if (!teacherId.equals(clazz.getTeacherId())) {
            throw new BusinessException("FORBIDDEN", "无权限", HttpStatus.FORBIDDEN);
        }

        clazz.setGroupingLocked(locked ? 1 : 0);
        classRepository.save(clazz);

        Map<String, Object> data = new HashMap<>();
        data.put("classId", classId);
        data.put("groupingLocked", clazz.getGroupingLocked());
        return data;
    }

    /**
     * 解散班级：班级软删除，并软删除该班下全部未删除任务与小组（与 PRD「班级内任务、小组等一并软删除」对齐）。
     */
    @Transactional
    public Map<String, Object> dissolveClass(Long teacherId, Long classId) {
        ClassEntity clazz = classRepository.findById(classId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND));
        if (clazz.getIsDeleted() != null && clazz.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND);
        }
        if (!teacherId.equals(clazz.getTeacherId())) {
            throw new BusinessException("FORBIDDEN", "无权限", HttpStatus.FORBIDDEN);
        }

        LocalDateTime now = AppTime.now();
        List<Task> activeTasks = taskRepository.findByClassIdAndIsDeletedOrderByIdDesc(classId, 0);
        for (Task task : activeTasks) {
            task.setIsDeleted(1);
            task.setDeletedAt(now);
        }
        taskRepository.saveAll(activeTasks);

        List<TaskGroup> activeGroups = taskGroupRepository.findByClassIdAndIsDeletedOrderByIdAsc(classId, 0);
        for (TaskGroup group : activeGroups) {
            group.setIsDeleted(1);
            group.setDeletedAt(now);
        }
        taskGroupRepository.saveAll(activeGroups);

        clazz.setIsDeleted(1);
        clazz.setDeletedAt(now);
        classRepository.save(clazz);

        Map<String, Object> data = new HashMap<>();
        data.put("classId", classId);
        data.put("tasksSoftDeleted", activeTasks.size());
        data.put("groupsSoftDeleted", activeGroups.size());
        data.put("dissolvedAt", now);
        return data;
    }

    /**
     * 恢复已解散班级（软删除回滚）：仅当 {@code deleted_at} 在 30 天内。
     * 仅恢复与解散时同一批次软删的任务、小组（以 {@code deleted_at} 与班级解散时刻对齐为准，避免误恢复此前教师单独软删的任务）。
     */
    @Transactional
    public Map<String, Object> restoreClass(Long teacherId, Long classId) {
        ClassEntity clazz = classRepository.findById(classId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND));
        if (clazz.getIsDeleted() == null || clazz.getIsDeleted() != 1) {
            throw new BusinessException("BAD_REQUEST", "班级未解散，无需恢复", HttpStatus.BAD_REQUEST);
        }
        if (!teacherId.equals(clazz.getTeacherId())) {
            throw new BusinessException("FORBIDDEN", "无权限", HttpStatus.FORBIDDEN);
        }
        LocalDateTime dissolvedAt = clazz.getDeletedAt();
        if (dissolvedAt == null) {
            throw new BusinessException("BAD_REQUEST", "班级数据异常，无法恢复", HttpStatus.BAD_REQUEST);
        }
        LocalDateTime now = AppTime.now();
        if (dissolvedAt.isBefore(now.minusDays(CLASS_RESTORE_WINDOW_DAYS))) {
            throw new BusinessException("BAD_REQUEST", "已超过30天，无法恢复班级", HttpStatus.BAD_REQUEST);
        }

        List<Task> toRestoreTasks = taskRepository.findByClassIdAndIsDeletedOrderByIdDesc(classId, 1).stream()
                .filter(t -> sameDissolveBatch(t.getDeletedAt(), dissolvedAt))
                .toList();
        for (Task task : toRestoreTasks) {
            task.setIsDeleted(0);
            task.setDeletedAt(null);
        }
        taskRepository.saveAll(toRestoreTasks);

        List<TaskGroup> toRestoreGroups = taskGroupRepository.findByClassIdAndIsDeletedOrderByIdAsc(classId, 1).stream()
                .filter(g -> sameDissolveBatch(g.getDeletedAt(), dissolvedAt))
                .toList();
        for (TaskGroup group : toRestoreGroups) {
            group.setIsDeleted(0);
            group.setDeletedAt(null);
        }
        taskGroupRepository.saveAll(toRestoreGroups);

        clazz.setIsDeleted(0);
        clazz.setDeletedAt(null);
        classRepository.save(clazz);

        Map<String, Object> data = new HashMap<>();
        data.put("classId", classId);
        data.put("tasksRestored", toRestoreTasks.size());
        data.put("groupsRestored", toRestoreGroups.size());
        data.put("restoredAt", now);
        data.put("needNewClassInviteCode", true);
        return data;
    }

    /** 同一事务内解散时任务/组与班级使用同一 {@link LocalDateTime}；库精度可能导致亚秒差异。 */
    private static boolean sameDissolveBatch(LocalDateTime rowDeletedAt, LocalDateTime classDeletedAt) {
        if (rowDeletedAt == null) {
            return false;
        }
        return Duration.between(rowDeletedAt, classDeletedAt).abs().getSeconds() <= 2;
    }
}
