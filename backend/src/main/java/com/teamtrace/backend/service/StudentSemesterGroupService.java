package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.group.StudentCreateSemesterGroupRequest;
import com.teamtrace.backend.dto.group.StudentCreatedGroupResponse;
import com.teamtrace.backend.dto.group.StudentJoinGroupResponse;
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
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学生在本班创建**学期固定小组**（{@code groups.task_id = NULL}）、凭小组邀请码入组（直通/审批），
 * 与教师建组共用同一张表；受 {@code classes.grouping_locked} 与「每班每人仅一处活跃/待审成员关系」约束。
 */
@Service
public class StudentSemesterGroupService {

    private static final int INVITE_CODE_LEN = 8;
    private static final int MAX_INVITE_GENERATION_ATTEMPTS = 40;
    private static final char[] INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final ClassRepository classRepository;
    private final TaskGroupRepository taskGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final ClassMembershipService classMembershipService;
    private final SecureRandom secureRandom = new SecureRandom();

    public StudentSemesterGroupService(
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

    @Transactional
    public StudentCreatedGroupResponse createSemesterGroup(
            Long studentId, Long classId, StudentCreateSemesterGroupRequest request) {
        ClassEntity clazz = requireClassForStudent(classId, studentId);
        requireGroupingUnlocked(clazz);
        classMembershipService.requireActiveStudentInClass(studentId, classId);
        ensureNoActiveOrPendingSemesterMembershipElsewhere(classId, studentId, null);

        int expireMin =
                request.getInviteCodeExpireMinutes() == null ? 1440 : request.getInviteCodeExpireMinutes();
        String code = generateUniqueInviteCode();
        LocalDateTime expireAt = AppTime.now().plusMinutes(expireMin);

        TaskGroup g = new TaskGroup();
        g.setClassId(classId);
        g.setTaskId(null);
        g.setName(request.getName().trim());
        g.setLeaderId(studentId);
        g.setInviteCode(code);
        g.setInviteCodeExpire(expireAt);
        g.setInviteCodeExpireMinutes(expireMin);
        g.setJoinMode(request.getJoinMode());
        g.setIsTeacherCreated(0);
        g.setStatus(1);
        g.setIsDeleted(0);
        TaskGroup saved = taskGroupRepository.save(g);

        GroupMember leader = new GroupMember();
        leader.setGroupId(saved.getId());
        leader.setUserId(studentId);
        leader.setStatus(GroupMember.STATUS_ACTIVE);
        leader.setVersion(0);
        groupMemberRepository.save(leader);

        TaskGroupResponse view = toResponse(saved, List.of(studentId));
        return StudentCreatedGroupResponse.builder()
                .group(view)
                .inviteCode(code)
                .inviteCodeExpireAt(expireAt)
                .build();
    }

    @Transactional
    public StudentJoinGroupResponse joinByInviteCode(Long studentId, Long classId, String rawCode) {
        ClassEntity clazz = requireClassForStudent(classId, studentId);
        requireGroupingUnlocked(clazz);
        classMembershipService.requireActiveStudentInClass(studentId, classId);

        String code = normalizeInviteCode(rawCode);
        TaskGroup group =
                taskGroupRepository
                        .findByInviteCodeAndTaskIdIsNullAndIsDeletedAndStatus(code, 0, 1)
                        .orElseThrow(() -> new BusinessException("NOT_FOUND", "邀请码无效或小组已不可用", HttpStatus.NOT_FOUND));

        if (!group.getClassId().equals(classId)) {
            throw new BusinessException("BAD_REQUEST", "该邀请码不属于当前班级", HttpStatus.BAD_REQUEST);
        }
        if (group.getInviteCodeExpire() != null && AppTime.now().isAfter(group.getInviteCodeExpire())) {
            throw new BusinessException("BAD_REQUEST", "小组邀请码已过期", HttpStatus.BAD_REQUEST);
        }

        Optional<GroupMember> existing = groupMemberRepository.findByGroupIdAndUserId(group.getId(), studentId);
        if (existing.isPresent()) {
            GroupMember gm = existing.get();
            if (gm.getStatus() == GroupMember.STATUS_ACTIVE) {
                throw new BusinessException("CONFLICT", "你已在该小组中", HttpStatus.CONFLICT);
            }
            if (gm.getStatus() == GroupMember.STATUS_PENDING) {
                throw new BusinessException("CONFLICT", "你的入组申请待审批中", HttpStatus.CONFLICT);
            }
            if (gm.getStatus() == GroupMember.STATUS_REJECTED) {
                return reapplyAfterReject(group, gm, studentId);
            }
            if (gm.getStatus() == GroupMember.STATUS_LEFT) {
                return rejoinAfterLeave(group, gm, studentId, classId);
            }
        }

        ensureNoActiveOrPendingSemesterMembershipElsewhere(classId, studentId, group.getId());

        if (group.getJoinMode() != null && group.getJoinMode() == 2) {
            GroupMember pending = new GroupMember();
            pending.setGroupId(group.getId());
            pending.setUserId(studentId);
            pending.setStatus(GroupMember.STATUS_PENDING);
            pending.setVersion(0);
            groupMemberRepository.save(pending);
            return StudentJoinGroupResponse.builder()
                    .groupId(group.getId())
                    .classId(classId)
                    .membershipStatus("pending")
                    .build();
        }

        GroupMember gm = new GroupMember();
        gm.setGroupId(group.getId());
        gm.setUserId(studentId);
        gm.setStatus(GroupMember.STATUS_ACTIVE);
        gm.setVersion(0);
        groupMemberRepository.save(gm);
        return StudentJoinGroupResponse.builder()
                .groupId(group.getId())
                .classId(classId)
                .membershipStatus("active")
                .build();
    }

    private StudentJoinGroupResponse rejoinAfterLeave(TaskGroup group, GroupMember row, Long studentId, Long classId) {
        ensureNoActiveOrPendingSemesterMembershipElsewhere(classId, studentId, group.getId());
        if (group.getJoinMode() != null && group.getJoinMode() == 2) {
            row.setStatus(GroupMember.STATUS_PENDING);
            row.setLeaveTime(null);
            row.setLeaveReason(null);
            groupMemberRepository.save(row);
            return StudentJoinGroupResponse.builder()
                    .groupId(group.getId())
                    .classId(classId)
                    .membershipStatus("pending")
                    .build();
        }
        row.setStatus(GroupMember.STATUS_ACTIVE);
        row.setLeaveTime(null);
        row.setLeaveReason(null);
        groupMemberRepository.save(row);
        return StudentJoinGroupResponse.builder()
                .groupId(group.getId())
                .classId(classId)
                .membershipStatus("active")
                .build();
    }

    private StudentJoinGroupResponse reapplyAfterReject(TaskGroup group, GroupMember row, Long studentId) {
        ensureNoActiveOrPendingSemesterMembershipElsewhere(group.getClassId(), studentId, group.getId());
        if (group.getJoinMode() != null && group.getJoinMode() == 2) {
            row.setStatus(GroupMember.STATUS_PENDING);
            groupMemberRepository.save(row);
            return StudentJoinGroupResponse.builder()
                    .groupId(group.getId())
                    .classId(group.getClassId())
                    .membershipStatus("pending")
                    .build();
        }
        row.setStatus(GroupMember.STATUS_ACTIVE);
        groupMemberRepository.save(row);
        return StudentJoinGroupResponse.builder()
                .groupId(group.getId())
                .classId(group.getClassId())
                .membershipStatus("active")
                .build();
    }

    @Transactional(readOnly = true)
    public List<Long> listPendingApplicants(Long studentId, Long classId, Long groupId) {
        requireClassForStudent(classId, studentId);
        classMembershipService.requireActiveStudentInClass(studentId, classId);
        TaskGroup g = requireStudentManagedSemesterGroup(classId, groupId, studentId);
        if (!g.getLeaderId().equals(studentId)) {
            throw new BusinessException("FORBIDDEN", "仅组长可查看入组申请", HttpStatus.FORBIDDEN);
        }
        return groupMemberRepository.findByGroupIdAndStatusOrderByIdAsc(groupId, GroupMember.STATUS_PENDING).stream()
                .map(GroupMember::getUserId)
                .collect(Collectors.toList());
    }

    @Transactional
    public void approveJoin(Long leaderId, Long classId, Long groupId, Long applicantUserId) {
        ClassEntity clazz = requireClassForStudent(classId, leaderId);
        requireGroupingUnlocked(clazz);
        classMembershipService.requireActiveStudentInClass(leaderId, classId);
        classMembershipService.requireActiveStudentInClass(applicantUserId, classId);
        TaskGroup g = requireStudentManagedSemesterGroup(classId, groupId, leaderId);
        if (!g.getLeaderId().equals(leaderId)) {
            throw new BusinessException("FORBIDDEN", "仅组长可通过入组申请", HttpStatus.FORBIDDEN);
        }

        GroupMember row =
                groupMemberRepository
                        .findByGroupIdAndUserIdAndStatus(groupId, applicantUserId, GroupMember.STATUS_PENDING)
                        .orElseThrow(() -> new BusinessException("NOT_FOUND", "没有待审批的入组申请", HttpStatus.NOT_FOUND));

        row.setStatus(GroupMember.STATUS_ACTIVE);
        groupMemberRepository.save(row);
    }

    @Transactional
    public void rejectJoin(Long leaderId, Long classId, Long groupId, Long applicantUserId) {
        ClassEntity clazz = requireClassForStudent(classId, leaderId);
        requireGroupingUnlocked(clazz);
        TaskGroup g = requireStudentManagedSemesterGroup(classId, groupId, leaderId);
        if (!g.getLeaderId().equals(leaderId)) {
            throw new BusinessException("FORBIDDEN", "仅组长可拒绝入组申请", HttpStatus.FORBIDDEN);
        }

        GroupMember row =
                groupMemberRepository
                        .findByGroupIdAndUserIdAndStatus(groupId, applicantUserId, GroupMember.STATUS_PENDING)
                        .orElseThrow(() -> new BusinessException("NOT_FOUND", "没有待审批的入组申请", HttpStatus.NOT_FOUND));

        row.setStatus(GroupMember.STATUS_REJECTED);
        groupMemberRepository.save(row);
    }

    @Transactional
    public StudentCreatedGroupResponse refreshInviteCode(Long leaderId, Long classId, Long groupId) {
        ClassEntity clazz = requireClassForStudent(classId, leaderId);
        requireGroupingUnlocked(clazz);
        TaskGroup g = requireStudentManagedSemesterGroup(classId, groupId, leaderId);
        if (!g.getLeaderId().equals(leaderId)) {
            throw new BusinessException("FORBIDDEN", "仅组长可刷新小组邀请码", HttpStatus.FORBIDDEN);
        }

        int expireMin = g.getInviteCodeExpireMinutes() == null ? 1440 : g.getInviteCodeExpireMinutes();
        String code = generateUniqueInviteCode();
        LocalDateTime expireAt = AppTime.now().plusMinutes(expireMin);
        g.setInviteCode(code);
        g.setInviteCodeExpire(expireAt);
        taskGroupRepository.save(g);

        List<Long> members = memberIdsActive(groupId);
        TaskGroupResponse view = toResponse(g, members);
        return StudentCreatedGroupResponse.builder()
                .group(view)
                .inviteCode(code)
                .inviteCodeExpireAt(expireAt)
                .build();
    }

    @Transactional
    public void removeMemberByLeader(Long leaderId, Long classId, Long groupId, Long targetUserId) {
        ClassEntity clazz = requireClassForStudent(classId, leaderId);
        requireGroupingUnlocked(clazz);
        TaskGroup g = requireSemesterGroup(classId, groupId);
        if (!g.getLeaderId().equals(leaderId)) {
            throw new BusinessException("FORBIDDEN", "仅组长可移除组员", HttpStatus.FORBIDDEN);
        }
        if (targetUserId.equals(leaderId)) {
            throw new BusinessException("BAD_REQUEST", "组长不能移除自己", HttpStatus.BAD_REQUEST);
        }

        GroupMember row =
                groupMemberRepository
                        .findByGroupIdAndUserIdAndStatus(groupId, targetUserId, GroupMember.STATUS_ACTIVE)
                        .orElseThrow(() -> new BusinessException("NOT_FOUND", "该成员不在小组中", HttpStatus.NOT_FOUND));

        row.setStatus(GroupMember.STATUS_LEFT);
        row.setLeaveTime(AppTime.now());
        row.setLeaveReason("leader_remove");
        groupMemberRepository.save(row);
    }

    private TaskGroup requireStudentManagedSemesterGroup(Long classId, Long groupId, Long studentId) {
        TaskGroup g = requireSemesterGroup(classId, groupId);
        boolean inGroup =
                groupMemberRepository.existsByGroupIdAndUserIdAndStatus(
                        groupId, studentId, GroupMember.STATUS_ACTIVE);
        if (!inGroup) {
            throw new BusinessException("FORBIDDEN", "你不在该小组中", HttpStatus.FORBIDDEN);
        }
        return g;
    }

    private TaskGroup requireSemesterGroup(Long classId, Long groupId) {
        return taskGroupRepository
                .findByIdAndClassIdAndTaskIdIsNullAndIsDeleted(groupId, classId, 0)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "小组不存在", HttpStatus.NOT_FOUND));
    }

    private ClassEntity requireClassForStudent(Long classId, Long studentId) {
        ClassEntity clazz =
                classRepository
                        .findById(classId)
                        .orElseThrow(() -> new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND));
        if (clazz.getIsDeleted() != null && clazz.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND);
        }
        if (clazz.getStatus() != null && clazz.getStatus() == 0) {
            throw new BusinessException("BAD_REQUEST", "班级已结束", HttpStatus.BAD_REQUEST);
        }
        return clazz;
    }

    private static void requireGroupingUnlocked(ClassEntity clazz) {
        if (clazz.getGroupingLocked() != null && clazz.getGroupingLocked() == 1) {
            throw new BusinessException(
                    "GROUPING_LOCKED",
                    "班级分组已锁定，学生无法创建小组、加入小组或调整组员（请先由教师解锁）",
                    HttpStatus.CONFLICT);
        }
    }

    /** 除 {@code exceptGroupId}（可为 null）外，本班学期组内不得已有活跃或待审身份 */
    private void ensureNoActiveOrPendingSemesterMembershipElsewhere(
            Long classId, Long studentId, Long exceptGroupId) {
        List<GroupMember> rows = groupMemberRepository.findActiveOrPendingInClassSemesterGroups(classId, studentId);
        for (GroupMember gm : rows) {
            if (exceptGroupId == null || !gm.getGroupId().equals(exceptGroupId)) {
                throw new BusinessException(
                        "CONFLICT",
                        "你在本班已有小组归属或待审申请，每位学生同一时间只能参与一个学期小组流程",
                        HttpStatus.CONFLICT);
            }
        }
    }

    private String generateUniqueInviteCode() {
        for (int i = 0; i < MAX_INVITE_GENERATION_ATTEMPTS; i++) {
            String candidate = randomInviteToken(INVITE_CODE_LEN);
            if (taskGroupRepository
                    .findByInviteCodeAndTaskIdIsNullAndIsDeletedAndStatus(candidate, 0, 1)
                    .isPresent()) {
                continue;
            }
            return candidate;
        }
        throw new BusinessException(
                "INTERNAL", "生成小组邀请码失败，请重试", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String randomInviteToken(int len) {
        char[] buf = new char[len];
        for (int i = 0; i < len; i++) {
            buf[i] = INVITE_ALPHABET[secureRandom.nextInt(INVITE_ALPHABET.length)];
        }
        return new String(buf);
    }

    private static String normalizeInviteCode(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toUpperCase();
    }

    private List<Long> memberIdsActive(Long groupId) {
        return groupMemberRepository.findByGroupIdAndStatusOrderByIdAsc(groupId, GroupMember.STATUS_ACTIVE).stream()
                .map(GroupMember::getUserId)
                .collect(Collectors.toList());
    }

    private TaskGroupResponse toResponse(TaskGroup g, List<Long> memberIds) {
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
                .inviteCode(isInviteActive(g) ? g.getInviteCode() : null)
                .inviteCodeExpire(isInviteActive(g) ? g.getInviteCodeExpire() : null)
                .leaderName(leaderName)
                .build();
    }

    private static boolean isInviteActive(TaskGroup group) {
        return group.getInviteCode() != null
                && !group.getInviteCode().isBlank()
                && group.getInviteCodeExpire() != null
                && !AppTime.now().isAfter(group.getInviteCodeExpire());
    }
}
