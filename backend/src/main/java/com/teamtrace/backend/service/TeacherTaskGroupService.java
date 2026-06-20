package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.group.CreateTaskGroupRequest;
import com.teamtrace.backend.dto.group.MoveMemberRequest;
import com.teamtrace.backend.dto.group.TaskGroupResponse;
import com.teamtrace.backend.entity.ClassEntity;
import com.teamtrace.backend.entity.GroupMember;
import com.teamtrace.backend.entity.User;
import com.teamtrace.backend.entity.TaskGroup;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.GroupMemberRepository;
import com.teamtrace.backend.repository.UserRepository;
import com.teamtrace.backend.repository.TaskGroupRepository;
import com.teamtrace.backend.util.AppTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 班级学期固定小组：{@code groups.task_id = NULL}（库注释「课程级固定小组」），全学期各任务共用同一分组。
 * 所有逻辑按 {@code class_id} 隔离——每个班级独立一套组，班级之间互不影响。
 */
@Service
public class TeacherTaskGroupService {

    private final ClassRepository classRepository;
    private final TaskGroupRepository taskGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ClassMembershipService classMembershipService;
    private final UserRepository userRepository;

    public TeacherTaskGroupService(
            ClassRepository classRepository,
            TaskGroupRepository taskGroupRepository,
            GroupMemberRepository groupMemberRepository,
            ClassMembershipService classMembershipService,
            UserRepository userRepository) {
        this.classRepository = classRepository;
        this.taskGroupRepository = taskGroupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.classMembershipService = classMembershipService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<TaskGroupResponse> listSemesterGroupsForTeacher(Long teacherId, Long classId) {
        requireTeacherClass(teacherId, classId);
        return loadSemesterGroupResponses(classId);
    }

    @Transactional
    public TaskGroupResponse createSemesterGroup(Long teacherId, Long classId, CreateTaskGroupRequest request) {
        ClassEntity clazz = requireTeacherClass(teacherId, classId);
        requireGroupingUnlockedForSemesterGroupChanges(clazz);

        LinkedHashSet<Long> members = new LinkedHashSet<>(request.getMemberStudentIds());
        members.add(request.getLeaderId());
        if (members.isEmpty()) {
            throw new BusinessException(
                    "BAD_REQUEST",
                    "小组至少需要 1 名成员",
                    HttpStatus.BAD_REQUEST);
        }

        for (Long sid : members) {
            classMembershipService.requireActiveStudentInClass(sid, classId);
        }

        // 同一班级内一名学生只能属于一个小组（学期组 + 历史按任务建组行一并检查）
        List<TaskGroup> existingInClass = taskGroupRepository.findByClassIdAndIsDeletedOrderByIdAsc(classId, 0);
        List<Long> existingGroupIds =
                existingInClass.stream().map(TaskGroup::getId).collect(Collectors.toList());
        if (!existingGroupIds.isEmpty()) {
            List<GroupMember> occupied =
                    groupMemberRepository.findByGroupIdInAndStatus(existingGroupIds, GroupMember.STATUS_ACTIVE);
            Set<Long> busyStudentIds =
                    occupied.stream().map(GroupMember::getUserId).collect(Collectors.toSet());
            for (Long sid : members) {
                if (busyStudentIds.contains(sid)) {
                    throw new BusinessException(
                            "CONFLICT",
                            "学生 " + sid + " 已在本班级的小组中，每位学生只能加入一个小组",
                            HttpStatus.CONFLICT);
                }
            }
        }

        TaskGroup g = new TaskGroup();
        g.setClassId(classId);
        g.setTaskId(null);
        g.setName(request.getName().trim());
        g.setLeaderId(request.getLeaderId());
        g.setInviteCode(null);
        g.setInviteCodeExpire(null);
        g.setInviteCodeExpireMinutes(1440);
        g.setJoinMode(1);
        g.setIsTeacherCreated(1);
        g.setStatus(1);
        g.setIsDeleted(0);
        TaskGroup saved = taskGroupRepository.save(g);

        for (Long sid : members) {
            GroupMember gm = new GroupMember();
            gm.setGroupId(saved.getId());
            gm.setUserId(sid);
            gm.setStatus(GroupMember.STATUS_ACTIVE);
            gm.setVersion(0);
            groupMemberRepository.save(gm);
        }

        return toResponse(saved, new ArrayList<>(members));
    }

    /**
     * 教师将学生在**同一班级**的两个**学期固定小组**之间移动（源/目标 {@code task_id} 均为 NULL）。
     * 若移动者为组长且组内仍有他人，先按成员记录 id 升序将组长指给下一名成员；若该生为组内最后一人则不允许移出。
     */
    @Transactional
    public TaskGroupResponse moveMemberBetweenSemesterGroups(
            Long teacherId,
            Long classId,
            Long sourceGroupId,
            Long studentId,
            MoveMemberRequest request) {
        ClassEntity clazz = requireTeacherClass(teacherId, classId);
        requireGroupingUnlockedForSemesterGroupChanges(clazz);

        TaskGroup source =
                taskGroupRepository
                        .findByIdAndClassIdAndTaskIdIsNullAndIsDeleted(sourceGroupId, classId, 0)
                        .orElseThrow(() -> new BusinessException("NOT_FOUND", "源小组不存在", HttpStatus.NOT_FOUND));
        TaskGroup target =
                taskGroupRepository
                        .findByIdAndClassIdAndTaskIdIsNullAndIsDeleted(request.getTargetGroupId(), classId, 0)
                        .orElseThrow(() -> new BusinessException("NOT_FOUND", "目标小组不存在", HttpStatus.NOT_FOUND));

        if (source.getId().equals(target.getId())) {
            throw new BusinessException("BAD_REQUEST", "源小组与目标小组不能相同", HttpStatus.BAD_REQUEST);
        }

        classMembershipService.requireActiveStudentInClass(studentId, classId);

        GroupMember memberRow =
                groupMemberRepository
                        .findByGroupIdAndUserIdAndStatus(source.getId(), studentId, GroupMember.STATUS_ACTIVE)
                        .orElseThrow(
                                () -> new BusinessException(
                                        "NOT_FOUND", "该学生不在源小组中或状态非活跃", HttpStatus.NOT_FOUND));

        List<GroupMember> activeInSource =
                groupMemberRepository.findByGroupIdAndStatusOrderByIdAsc(source.getId(), GroupMember.STATUS_ACTIVE);
        long othersInSource =
                activeInSource.stream().filter(gm -> !gm.getUserId().equals(studentId)).count();
        if (othersInSource == 0) {
            throw new BusinessException("BAD_REQUEST", "不能将小组最后一名成员移出", HttpStatus.BAD_REQUEST);
        }

        if (studentId.equals(source.getLeaderId())) {
            Long newLeader =
                    activeInSource.stream()
                            .filter(gm -> !gm.getUserId().equals(studentId))
                            .findFirst()
                            .map(GroupMember::getUserId)
                            .orElseThrow(
                                    () -> new BusinessException(
                                            "INTERNAL", "无法确定新组长", HttpStatus.INTERNAL_SERVER_ERROR));
            source.setLeaderId(newLeader);
            taskGroupRepository.save(source);
        }

        if (groupMemberRepository.existsByGroupIdAndUserIdAndStatus(target.getId(), studentId, GroupMember.STATUS_ACTIVE)) {
            throw new BusinessException("CONFLICT", "该学生已在目标小组中", HttpStatus.CONFLICT);
        }

        // 若目标组存在同一学生的非活跃历史行，会与 UPDATE group_id 后的 uk(group_id,user_id) 冲突，先移除
        groupMemberRepository
                .findByGroupIdAndUserId(target.getId(), studentId)
                .filter(stale -> !stale.getId().equals(memberRow.getId()))
                .ifPresent(stale -> groupMemberRepository.delete(stale));

        memberRow.setGroupId(target.getId());
        groupMemberRepository.save(memberRow);

        return toResponse(target, memberIdsForGroup(target.getId()));
    }

    private List<Long> memberIdsForGroup(Long groupId) {
        return groupMemberRepository.findByGroupIdAndStatusOrderByIdAsc(groupId, GroupMember.STATUS_ACTIVE).stream()
                .map(GroupMember::getUserId)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskGroupResponse> listSemesterGroupsForStudent(Long studentId, Long classId) {
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
        return loadSemesterGroupResponsesForStudent(classId, studentId);
    }

    private List<TaskGroupResponse> loadSemesterGroupResponses(Long classId) {
        List<TaskGroup> groups =
                taskGroupRepository.findByClassIdAndTaskIdIsNullAndIsDeletedOrderByIdAsc(classId, 0);
        List<TaskGroupResponse> out = new ArrayList<>();
        for (TaskGroup g : groups) {
            List<GroupMember> ms =
                    groupMemberRepository.findByGroupIdAndStatusOrderByIdAsc(g.getId(), GroupMember.STATUS_ACTIVE);
            List<Long> ids = ms.stream().map(GroupMember::getUserId).collect(Collectors.toList());
            out.add(toResponse(g, ids));
        }
        return out;
    }

    private List<TaskGroupResponse> loadSemesterGroupResponsesForStudent(Long classId, Long studentId) {
        List<TaskGroup> groups =
                taskGroupRepository.findByClassIdAndTaskIdIsNullAndIsDeletedOrderByIdAsc(classId, 0);
        List<TaskGroupResponse> out = new ArrayList<>();
        for (TaskGroup g : groups) {
            List<GroupMember> ms =
                    groupMemberRepository.findByGroupIdAndStatusOrderByIdAsc(g.getId(), GroupMember.STATUS_ACTIVE);
            List<Long> ids = ms.stream().map(GroupMember::getUserId).collect(Collectors.toList());
            boolean ownGroup = ids.stream().anyMatch(id -> id.equals(studentId));
            out.add(toResponse(g, ids, ownGroup));
        }
        return out;
    }

    /**
     * 将未分组学生直接加入目标学期小组。
     * 学生不能已在本班级的任何小组中。
     */
    @Transactional
    public TaskGroupResponse addStudentToSemesterGroup(
            Long teacherId, Long classId, Long targetGroupId, Long studentId) {
        ClassEntity clazz = requireTeacherClass(teacherId, classId);
        requireGroupingUnlockedForSemesterGroupChanges(clazz);

        TaskGroup target =
                taskGroupRepository
                        .findByIdAndClassIdAndTaskIdIsNullAndIsDeleted(targetGroupId, classId, 0)
                        .orElseThrow(() -> new BusinessException("NOT_FOUND", "目标小组不存在", HttpStatus.NOT_FOUND));

        classMembershipService.requireActiveStudentInClass(studentId, classId);

        // 检查学生是否已在本班级任何小组中
        List<TaskGroup> existingInClass = taskGroupRepository.findByClassIdAndIsDeletedOrderByIdAsc(classId, 0);
        List<Long> existingGroupIds = existingInClass.stream().map(TaskGroup::getId).toList();
        if (!existingGroupIds.isEmpty()) {
            List<GroupMember> occupied =
                    groupMemberRepository.findByGroupIdInAndStatus(existingGroupIds, GroupMember.STATUS_ACTIVE);
            boolean alreadyInGroup = occupied.stream().anyMatch(gm -> gm.getUserId().equals(studentId));
            if (alreadyInGroup) {
                throw new BusinessException("CONFLICT", "该学生已在本班级的小组中", HttpStatus.CONFLICT);
            }
        }

        List<GroupMember> activeInTarget =
                groupMemberRepository.findByGroupIdAndStatusOrderByIdAsc(targetGroupId, GroupMember.STATUS_ACTIVE);

        GroupMember gm = new GroupMember();
        gm.setGroupId(targetGroupId);
        gm.setUserId(studentId);
        gm.setStatus(GroupMember.STATUS_ACTIVE);
        gm.setVersion(0);
        groupMemberRepository.save(gm);

        List<Long> memberIds = new ArrayList<>(activeInTarget.stream().map(GroupMember::getUserId).toList());
        memberIds.add(studentId);
        return toResponse(target, memberIds);
    }

    private TaskGroupResponse toResponse(TaskGroup g, List<Long> memberIds) {
        return toResponse(g, memberIds, false);
    }

    private TaskGroupResponse toResponse(TaskGroup g, List<Long> memberIds, boolean includeInvite) {
        Map<Long, String> memberNames = new HashMap<>();
        String leaderName = null;
        if (!memberIds.isEmpty()) {
            List<User> users = userRepository.findAllById(memberIds);
            for (User u : users) {
                memberNames.put(u.getId(), u.getName());
            }
            User leader = userRepository.findById(g.getLeaderId()).orElse(null);
            if (leader != null) {
                leaderName = leader.getName();
            }
        }
        return TaskGroupResponse.builder()
                .groupId(g.getId())
                .classId(g.getClassId())
                .taskId(g.getTaskId())
                .name(g.getName())
                .leaderId(g.getLeaderId())
                .memberStudentIds(memberIds)
                .memberNames(memberNames)
                .inviteCode(includeInvite && isInviteActive(g) ? g.getInviteCode() : null)
                .inviteCodeExpire(includeInvite && isInviteActive(g) ? g.getInviteCodeExpire() : null)
                .leaderName(leaderName)
                .build();
    }

    private static boolean isInviteActive(TaskGroup group) {
        return group.getInviteCode() != null
                && !group.getInviteCode().isBlank()
                && group.getInviteCodeExpire() != null
                && !AppTime.now().isAfter(group.getInviteCodeExpire());
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

    /**
     * 与 {@code classes.grouping_locked} 联动：锁定后禁止教师新建学期组、跨组移动成员；列表仍可读。
     */
    private static void requireGroupingUnlockedForSemesterGroupChanges(ClassEntity clazz) {
        if (clazz.getGroupingLocked() != null && clazz.getGroupingLocked() == 1) {
            throw new BusinessException(
                    "GROUPING_LOCKED",
                    "班级分组已锁定，无法创建或调整学期小组（请先解锁 grouping_lock）",
                    HttpStatus.CONFLICT);
        }
    }
}
