package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.student.StudentClassDetailResponse;
import com.teamtrace.backend.dto.student.StudentClassListItemResponse;
import com.teamtrace.backend.dto.student.StudentClassmateResponse;
import com.teamtrace.backend.entity.ClassEntity;
import com.teamtrace.backend.entity.TaskGroup;
import com.teamtrace.backend.entity.ClassInviteCode;
import com.teamtrace.backend.entity.ClassStudent;
import com.teamtrace.backend.entity.GroupMember;
import com.teamtrace.backend.entity.User;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.ClassInviteCodeRepository;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.ClassStudentRepository;
import com.teamtrace.backend.repository.GroupMemberRepository;
import com.teamtrace.backend.repository.TaskGroupRepository;
import com.teamtrace.backend.repository.UserRepository;
import com.teamtrace.backend.util.AppTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentClassService {

    private final ClassInviteCodeRepository classInviteCodeRepository;
    private final ClassStudentRepository classStudentRepository;
    private final ClassRepository classRepository;
    private final ClassMembershipService classMembershipService;
    private final GroupMemberRepository groupMemberRepository;
    private final TaskGroupRepository taskGroupRepository;
    private final UserRepository userRepository;

    public StudentClassService(
            ClassInviteCodeRepository classInviteCodeRepository,
            ClassStudentRepository classStudentRepository,
            ClassRepository classRepository,
            ClassMembershipService classMembershipService,
            GroupMemberRepository groupMemberRepository,
            TaskGroupRepository taskGroupRepository,
            UserRepository userRepository) {
        this.classInviteCodeRepository = classInviteCodeRepository;
        this.classStudentRepository = classStudentRepository;
        this.classRepository = classRepository;
        this.classMembershipService = classMembershipService;
        this.groupMemberRepository = groupMemberRepository;
        this.taskGroupRepository = taskGroupRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Long joinClass(Long studentId, String inviteCode) {
        String code = inviteCode == null ? "" : inviteCode.trim();
        if (code.isEmpty()) {
            throw new BusinessException("BAD_REQUEST", "邀请码不能为空", HttpStatus.BAD_REQUEST);
        }

        ClassInviteCode cic = classInviteCodeRepository
                .findByCode(code)
                .orElseThrow(() -> new BusinessException("BAD_REQUEST", "邀请码无效", HttpStatus.BAD_REQUEST));

        LocalDateTime now = AppTime.now();
        if (cic.getExpireAt() != null && now.isAfter(cic.getExpireAt())) {
            throw new BusinessException("BAD_REQUEST", "邀请码已过期", HttpStatus.BAD_REQUEST);
        }
        // 允许多人使用：只拒绝已刷新作废的码（status=2），未使用(0)和已使用(1)均有效
        if (cic.getStatus() != null && cic.getStatus() == 2) {
            throw new BusinessException("BAD_REQUEST", "邀请码已失效，请联系老师重新生成", HttpStatus.BAD_REQUEST);
        }

        ClassEntity clazz = classRepository
                .findById(cic.getClassId())
                .orElseThrow(() -> new BusinessException("BAD_REQUEST", "班级不存在", HttpStatus.BAD_REQUEST));
        if (clazz.getIsDeleted() != null && clazz.getIsDeleted() == 1) {
            throw new BusinessException("BAD_REQUEST", "班级不存在", HttpStatus.BAD_REQUEST);
        }
        if (clazz.getStatus() != null && clazz.getStatus() == 0) {
            throw new BusinessException("BAD_REQUEST", "班级已结束", HttpStatus.BAD_REQUEST);
        }

        var existingOpt = classStudentRepository.findByClassIdAndStudentId(clazz.getId(), studentId);
        if (existingOpt.isPresent()) {
            ClassStudent existing = existingOpt.get();
            if (existing.getIsDeleted() == null || existing.getIsDeleted() == 0) {
                throw new BusinessException("BUSINESS_CONFLICT", "你已加入该班级", HttpStatus.CONFLICT);
            }
            // 曾被移除：允许凭新邀请码重新加入，复用同一行（唯一约束 class_id + student_id）
            existing.setIsDeleted(0);
            existing.setDeletedAt(null);
            existing.setJoinTime(now);
            classStudentRepository.save(existing);
        } else {
            ClassStudent cs = new ClassStudent();
            cs.setClassId(clazz.getId());
            cs.setStudentId(studentId);
            cs.setIsDeleted(0);
            classStudentRepository.save(cs);
        }

        // 多人使用模式：不再标记邀请码为已使用，保留 status 不变
        // cic.setStatus(1); cic.setUsedBy(studentId); cic.setUsedAt(now);

        return clazz.getId();
    }

    @Transactional(readOnly = true)
    public List<StudentClassListItemResponse> listClasses(Long studentId) {
        List<ClassStudent> memberships = classStudentRepository.findByStudentIdAndIsDeletedOrderByIdDesc(studentId, 0);
        if (memberships.isEmpty()) {
            return List.of();
        }

        Map<Long, String> teacherNameMap = new HashMap<>();
        return memberships.stream()
                .map(membership -> toListItem(membership, teacherNameMap, studentId))
                .filter(item -> item != null)
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentClassDetailResponse getClassDetail(Long studentId, Long classId) {
        ClassEntity clazz = requireActiveClass(classId);
        classMembershipService.requireActiveStudentInClass(studentId, classId);

        GroupMember selectedMembership = groupMemberRepository
                .findActiveOrPendingInClassSemesterGroups(classId, studentId)
                .stream()
                .sorted(Comparator.comparingInt(this::groupMembershipSortOrder).thenComparing(GroupMember::getId))
                .findFirst()
                .orElse(null);

        Long groupId = null;
        String groupName = null;
        String groupJoinStatus = null;
        if (selectedMembership != null) {
            groupId = selectedMembership.getGroupId();
            groupJoinStatus = selectedMembership.getStatus() != null && selectedMembership.getStatus() == GroupMember.STATUS_PENDING
                    ? "待审批"
                    : "已加入";
            groupName = taskGroupRepository
                    .findByIdAndClassIdAndTaskIdIsNullAndIsDeleted(groupId, classId, 0)
                    .map(group -> group.getName())
                    .orElse(null);
        }

        Long teacherId = clazz.getTeacherId();
        return StudentClassDetailResponse.builder()
                .classId(clazz.getId())
                .classCode(clazz.getClassCode())
                .name(clazz.getName())
                .semester(clazz.getSemester())
                .groupingLocked(clazz.getGroupingLocked())
                .studentStatus("在班")
                .teacherId(teacherId)
                .teacherName(loadTeacherName(teacherId))
                .studentCount(classStudentRepository.countByClassIdAndIsDeleted(classId, 0))
                .groupId(groupId)
                .groupName(groupName)
                .groupJoinStatus(groupJoinStatus)
                .build();
    }

    @Transactional(readOnly = true)
    public List<StudentClassmateResponse> listClassmates(Long studentId, Long classId) {
        requireActiveClass(classId);
        classMembershipService.requireActiveStudentInClass(studentId, classId);

        List<ClassStudent> enrollments =
                classStudentRepository.findByClassIdAndIsDeletedOrderByIdAsc(classId, 0);
        if (enrollments.isEmpty()) {
            return List.of();
        }

        Map<Long, GroupPlacement> placementByStudent = loadSemesterGroupPlacements(classId);
        List<Long> studentIds = enrollments.stream().map(ClassStudent::getStudentId).toList();
        Map<Long, User> usersById = userRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));

        List<StudentClassmateResponse> classmates = new ArrayList<>();
        for (ClassStudent enrollment : enrollments) {
            Long sid = enrollment.getStudentId();
            User user = usersById.get(sid);
            GroupPlacement placement = placementByStudent.get(sid);
            String name = user == null || user.getName() == null || user.getName().isBlank()
                    ? "学生 " + sid
                    : user.getName();
            classmates.add(StudentClassmateResponse.builder()
                    .studentId(sid)
                    .name(name)
                    .groupName(placement == null ? null : placement.groupName())
                    .isLeader(placement != null && placement.leader())
                    .build());
        }
        return classmates;
    }

    private Map<Long, GroupPlacement> loadSemesterGroupPlacements(Long classId) {
        Map<Long, GroupPlacement> placementByStudent = new HashMap<>();
        List<TaskGroup> groups =
                taskGroupRepository.findByClassIdAndTaskIdIsNullAndIsDeletedOrderByIdAsc(classId, 0);
        for (TaskGroup group : groups) {
            List<GroupMember> members =
                    groupMemberRepository.findByGroupIdAndStatusOrderByIdAsc(group.getId(), GroupMember.STATUS_ACTIVE);
            for (GroupMember member : members) {
                placementByStudent.putIfAbsent(
                        member.getUserId(),
                        new GroupPlacement(
                                group.getName(),
                                group.getLeaderId() != null && group.getLeaderId().equals(member.getUserId())));
            }
        }
        return placementByStudent;
    }

    private record GroupPlacement(String groupName, boolean leader) {}

    private StudentClassListItemResponse toListItem(
            ClassStudent membership, Map<Long, String> teacherNameMap, Long studentId) {
        ClassEntity clazz = classRepository.findById(membership.getClassId()).orElse(null);
        if (clazz == null) {
            return null;
        }
        // 过滤已解散（is_deleted=1）和已结束（status=0）的班级
        if (clazz.getIsDeleted() != null && clazz.getIsDeleted() == 1) {
            return null;
        }
        if (clazz.getStatus() != null && clazz.getStatus() == 0) {
            return null;
        }

        GroupMember selectedMembership = groupMemberRepository
                .findActiveOrPendingInClassSemesterGroups(clazz.getId(), studentId)
                .stream()
                .sorted(Comparator.comparingInt(this::groupMembershipSortOrder).thenComparing(GroupMember::getId))
                .findFirst()
                .orElse(null);

        Long groupId = null;
        String groupName = null;
        String groupJoinStatus = null;
        if (selectedMembership != null) {
            groupId = selectedMembership.getGroupId();
            groupJoinStatus = selectedMembership.getStatus() != null && selectedMembership.getStatus() == GroupMember.STATUS_PENDING
                    ? "待审批"
                    : "已加入";
            groupName = taskGroupRepository
                    .findByIdAndClassIdAndTaskIdIsNullAndIsDeleted(groupId, clazz.getId(), 0)
                    .map(group -> group.getName())
                    .orElse(null);
        }

        Long teacherId = clazz.getTeacherId();
        String teacherName = teacherNameMap.computeIfAbsent(teacherId, this::loadTeacherName);

        return StudentClassListItemResponse.builder()
                .classId(clazz.getId())
                .classCode(clazz.getClassCode())
                .name(clazz.getName())
                .semester(clazz.getSemester())
                .groupingLocked(clazz.getGroupingLocked())
                .studentStatus("在班")
                .groupId(groupId)
                .groupName(groupName)
                .groupJoinStatus(groupJoinStatus)
                .teacherId(teacherId)
                .teacherName(teacherName)
                .build();
    }

    private String loadTeacherName(Long teacherId) {
        if (teacherId == null) {
            return null;
        }
        return userRepository.findById(teacherId).map(User::getName).orElse(null);
    }

    private ClassEntity requireActiveClass(Long classId) {
        ClassEntity clazz = classRepository
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

    private int groupMembershipSortOrder(GroupMember member) {
        if (member.getStatus() != null && member.getStatus() == GroupMember.STATUS_ACTIVE) {
            return 0;
        }
        if (member.getStatus() != null && member.getStatus() == GroupMember.STATUS_PENDING) {
            return 1;
        }
        return 2;
    }
}

