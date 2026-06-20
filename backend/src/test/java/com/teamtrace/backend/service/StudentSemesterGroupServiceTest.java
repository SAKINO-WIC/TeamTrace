package com.teamtrace.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamtrace.backend.dto.group.StudentCreateSemesterGroupRequest;
import com.teamtrace.backend.dto.group.StudentCreatedGroupResponse;
import com.teamtrace.backend.entity.ClassEntity;
import com.teamtrace.backend.entity.GroupMember;
import com.teamtrace.backend.entity.TaskGroup;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.GroupMemberRepository;
import com.teamtrace.backend.repository.TaskGroupRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class StudentSemesterGroupServiceTest {

    @Mock
    private ClassRepository classRepository;

    @Mock
    private TaskGroupRepository taskGroupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private ClassMembershipService classMembershipService;

    @InjectMocks
    private StudentSemesterGroupService studentSemesterGroupService;

    @Test
    void createGroupRejectedWhenGroupingLocked() {
        ClassEntity clazz = new ClassEntity();
        clazz.setId(10L);
        clazz.setGroupingLocked(1);
        clazz.setGroupSizeMax(5);
        clazz.setIsDeleted(0);
        clazz.setStatus(1);
        when(classRepository.findById(10L)).thenReturn(Optional.of(clazz));

        StudentCreateSemesterGroupRequest req = new StudentCreateSemesterGroupRequest();
        req.setName("自建组");
        req.setJoinMode(1);

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> studentSemesterGroupService.createSemesterGroup(4L, 10L, req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("GROUPING_LOCKED", ex.getCode());
        verify(taskGroupRepository, never()).save(any());
    }

    @Test
    void createGroupSavesStudentGroupWithInviteCode() {
        ClassEntity clazz = new ClassEntity();
        clazz.setId(10L);
        clazz.setGroupingLocked(0);
        clazz.setGroupSizeMax(5);
        clazz.setIsDeleted(0);
        clazz.setStatus(1);
        when(classRepository.findById(10L)).thenReturn(Optional.of(clazz));
        when(groupMemberRepository.findActiveOrPendingInClassSemesterGroups(10L, 4L)).thenReturn(List.of());
        when(taskGroupRepository.findByInviteCodeAndTaskIdIsNullAndIsDeletedAndStatus(any(), eq(0), eq(1)))
                .thenReturn(Optional.empty());
        when(taskGroupRepository.save(any(TaskGroup.class)))
                .thenAnswer(
                        inv -> {
                            TaskGroup g = inv.getArgument(0);
                            g.setId(100L);
                            return g;
                        });

        StudentCreateSemesterGroupRequest req = new StudentCreateSemesterGroupRequest();
        req.setName("自建组A");
        req.setJoinMode(2);
        req.setInviteCodeExpireMinutes(60);

        StudentCreatedGroupResponse r = studentSemesterGroupService.createSemesterGroup(4L, 10L, req);

        assertEquals(100L, r.getGroup().getGroupId());
        assertEquals(4L, r.getGroup().getLeaderId());
        assertEquals("自建组A", r.getGroup().getName());
        assertEquals(8, r.getInviteCode().length());

        ArgumentCaptor<TaskGroup> groupCap = ArgumentCaptor.forClass(TaskGroup.class);
        verify(taskGroupRepository).save(groupCap.capture());
        assertEquals(0, groupCap.getValue().getIsTeacherCreated());
        assertEquals(2, groupCap.getValue().getJoinMode());
        assertEquals(4L, groupCap.getValue().getLeaderId());
    }

    @Test
    void leaderCanManageJoinPendingWhenTeacherCreatedGroup() {
        ClassEntity clazz = unlockedClass(10L);
        when(classRepository.findById(10L)).thenReturn(Optional.of(clazz));

        TaskGroup g = teacherCreatedGroup(100L, 10L, 4L);
        when(taskGroupRepository.findByIdAndClassIdAndTaskIdIsNullAndIsDeleted(100L, 10L, 0))
                .thenReturn(Optional.of(g));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatus(100L, 4L, GroupMember.STATUS_ACTIVE))
                .thenReturn(true);

        GroupMember pending = new GroupMember();
        pending.setGroupId(100L);
        pending.setUserId(9L);
        pending.setStatus(GroupMember.STATUS_PENDING);
        when(groupMemberRepository.findByGroupIdAndStatusOrderByIdAsc(100L, GroupMember.STATUS_PENDING))
                .thenReturn(List.of(pending));

        List<Long> pendingIds = studentSemesterGroupService.listPendingApplicants(4L, 10L, 100L);

        assertEquals(List.of(9L), pendingIds);
    }

    @Test
    void leaderCanRefreshInviteCodeWhenTeacherCreatedGroup() {
        ClassEntity clazz = unlockedClass(10L);
        when(classRepository.findById(10L)).thenReturn(Optional.of(clazz));

        TaskGroup g = teacherCreatedGroup(100L, 10L, 4L);
        g.setInviteCodeExpireMinutes(1440);
        when(taskGroupRepository.findByIdAndClassIdAndTaskIdIsNullAndIsDeleted(100L, 10L, 0))
                .thenReturn(Optional.of(g));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatus(100L, 4L, GroupMember.STATUS_ACTIVE))
                .thenReturn(true);
        when(groupMemberRepository.findByGroupIdAndStatusOrderByIdAsc(100L, GroupMember.STATUS_ACTIVE))
                .thenReturn(List.of(activeMember(100L, 4L)));
        when(taskGroupRepository.findByInviteCodeAndTaskIdIsNullAndIsDeletedAndStatus(any(), eq(0), eq(1)))
                .thenReturn(Optional.empty());
        when(taskGroupRepository.save(any(TaskGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentCreatedGroupResponse response =
                studentSemesterGroupService.refreshInviteCode(4L, 10L, 100L);

        assertEquals(8, response.getInviteCode().length());
        verify(taskGroupRepository).save(g);
    }

    @Test
    void leaderCanRemoveMemberEvenWhenTeacherCreatedGroup() {
        ClassEntity clazz = new ClassEntity();
        clazz.setId(10L);
        clazz.setGroupingLocked(0);
        clazz.setGroupSizeMax(5);
        clazz.setIsDeleted(0);
        clazz.setStatus(1);
        when(classRepository.findById(10L)).thenReturn(Optional.of(clazz));

        TaskGroup g = new TaskGroup();
        g.setId(100L);
        g.setClassId(10L);
        g.setTaskId(null);
        g.setLeaderId(4L);
        g.setIsDeleted(0);
        g.setIsTeacherCreated(1);
        when(taskGroupRepository.findByIdAndClassIdAndTaskIdIsNullAndIsDeleted(100L, 10L, 0))
                .thenReturn(Optional.of(g));

        GroupMember member = new GroupMember();
        member.setId(1L);
        member.setGroupId(100L);
        member.setUserId(8L);
        member.setStatus(GroupMember.STATUS_ACTIVE);
        member.setVersion(0);
        when(groupMemberRepository.findByGroupIdAndUserIdAndStatus(100L, 8L, GroupMember.STATUS_ACTIVE))
                .thenReturn(Optional.of(member));

        studentSemesterGroupService.removeMemberByLeader(4L, 10L, 100L, 8L);

        assertEquals(GroupMember.STATUS_LEFT, member.getStatus());
        assertEquals("leader_remove", member.getLeaveReason());
        verify(groupMemberRepository).save(member);
    }

    private static ClassEntity unlockedClass(long classId) {
        ClassEntity clazz = new ClassEntity();
        clazz.setId(classId);
        clazz.setGroupingLocked(0);
        clazz.setGroupSizeMax(5);
        clazz.setIsDeleted(0);
        clazz.setStatus(1);
        return clazz;
    }

    private static TaskGroup teacherCreatedGroup(long groupId, long classId, long leaderId) {
        TaskGroup g = new TaskGroup();
        g.setId(groupId);
        g.setClassId(classId);
        g.setTaskId(null);
        g.setLeaderId(leaderId);
        g.setIsDeleted(0);
        g.setIsTeacherCreated(1);
        return g;
    }

    private static GroupMember activeMember(long groupId, long userId) {
        GroupMember member = new GroupMember();
        member.setGroupId(groupId);
        member.setUserId(userId);
        member.setStatus(GroupMember.STATUS_ACTIVE);
        return member;
    }
}
