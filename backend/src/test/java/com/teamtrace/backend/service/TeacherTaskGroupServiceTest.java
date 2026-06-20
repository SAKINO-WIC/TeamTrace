package com.teamtrace.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamtrace.backend.dto.group.CreateTaskGroupRequest;
import com.teamtrace.backend.dto.group.MoveMemberRequest;
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
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeacherTaskGroupServiceTest {

    @Mock
    private ClassRepository classRepository;

    @Mock
    private TaskGroupRepository taskGroupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private ClassMembershipService classMembershipService;

    @InjectMocks
    private TeacherTaskGroupService teacherTaskGroupService;

    @Test
    void createSemesterGroupFailsWhenStudentAlreadyInAnotherGroupInClass() {
        ClassEntity clazz = new ClassEntity();
        clazz.setId(10L);
        clazz.setTeacherId(99L);
        clazz.setGroupSizeMin(1);
        clazz.setGroupSizeMax(5);
        clazz.setIsDeleted(0);
        when(classRepository.findById(10L)).thenReturn(Optional.of(clazz));

        TaskGroup other = new TaskGroup();
        other.setId(100L);
        other.setClassId(10L);
        other.setTaskId(null);
        when(taskGroupRepository.findByClassIdAndIsDeletedOrderByIdAsc(10L, 0)).thenReturn(List.of(other));

        GroupMember gm = new GroupMember();
        gm.setUserId(5L);
        gm.setGroupId(100L);
        when(groupMemberRepository.findByGroupIdInAndStatus(List.of(100L), GroupMember.STATUS_ACTIVE)).thenReturn(List.of(gm));

        CreateTaskGroupRequest req = new CreateTaskGroupRequest();
        req.setName("组B");
        req.setLeaderId(5L);
        req.setMemberStudentIds(List.of(5L));

        assertThrows(BusinessException.class, () -> teacherTaskGroupService.createSemesterGroup(99L, 10L, req));
    }

    @Test
    void createSemesterGroupAllowsSoloEvenWhenClassMinIsTwo() {
        ClassEntity clazz = new ClassEntity();
        clazz.setId(10L);
        clazz.setTeacherId(99L);
        clazz.setGroupSizeMin(2);
        clazz.setGroupSizeMax(5);
        clazz.setIsDeleted(0);
        when(classRepository.findById(10L)).thenReturn(Optional.of(clazz));

        when(taskGroupRepository.findByClassIdAndIsDeletedOrderByIdAsc(10L, 0)).thenReturn(List.of());

        TaskGroup saved = new TaskGroup();
        saved.setId(200L);
        saved.setClassId(10L);
        saved.setTaskId(null);
        saved.setName("单人组");
        saved.setLeaderId(5L);
        when(taskGroupRepository.save(any(TaskGroup.class))).thenReturn(saved);

        doNothing().when(classMembershipService).requireActiveStudentInClass(anyLong(), eq(10L));

        CreateTaskGroupRequest req = new CreateTaskGroupRequest();
        req.setName("单人组");
        req.setLeaderId(5L);
        req.setMemberStudentIds(List.of(5L));

        teacherTaskGroupService.createSemesterGroup(99L, 10L, req);
    }

    @Test
    void createSemesterGroupSavesWhenNoOverlap() {
        ClassEntity clazz = new ClassEntity();
        clazz.setId(10L);
        clazz.setTeacherId(99L);
        clazz.setGroupSizeMin(1);
        clazz.setGroupSizeMax(5);
        clazz.setIsDeleted(0);
        when(classRepository.findById(10L)).thenReturn(Optional.of(clazz));

        when(taskGroupRepository.findByClassIdAndIsDeletedOrderByIdAsc(10L, 0)).thenReturn(List.of());

        TaskGroup saved = new TaskGroup();
        saved.setId(200L);
        saved.setClassId(10L);
        saved.setTaskId(null);
        saved.setName("组A");
        saved.setLeaderId(5L);
        when(taskGroupRepository.save(any(TaskGroup.class))).thenReturn(saved);

        doNothing().when(classMembershipService).requireActiveStudentInClass(anyLong(), eq(10L));

        CreateTaskGroupRequest req = new CreateTaskGroupRequest();
        req.setName("组A");
        req.setLeaderId(5L);
        req.setMemberStudentIds(List.of(5L));

        var r = teacherTaskGroupService.createSemesterGroup(99L, 10L, req);
        assertEquals(null, r.getTaskId());
    }

    @Test
    void moveMemberBetweenSemesterGroupsFailsWhenSoleMember() {
        ClassEntity clazz = new ClassEntity();
        clazz.setId(10L);
        clazz.setTeacherId(99L);
        clazz.setGroupSizeMax(5);
        clazz.setIsDeleted(0);
        when(classRepository.findById(10L)).thenReturn(Optional.of(clazz));

        TaskGroup source = new TaskGroup();
        source.setId(1L);
        source.setClassId(10L);
        source.setTaskId(null);
        source.setLeaderId(5L);
        TaskGroup target = new TaskGroup();
        target.setId(2L);
        target.setClassId(10L);
        target.setTaskId(null);
        when(taskGroupRepository.findByIdAndClassIdAndTaskIdIsNullAndIsDeleted(1L, 10L, 0))
                .thenReturn(Optional.of(source));
        when(taskGroupRepository.findByIdAndClassIdAndTaskIdIsNullAndIsDeleted(2L, 10L, 0))
                .thenReturn(Optional.of(target));

        doNothing().when(classMembershipService).requireActiveStudentInClass(5L, 10L);

        GroupMember only = new GroupMember();
        only.setId(10L);
        only.setGroupId(1L);
        only.setUserId(5L);
        only.setStatus(GroupMember.STATUS_ACTIVE);
        when(groupMemberRepository.findByGroupIdAndUserIdAndStatus(1L, 5L, GroupMember.STATUS_ACTIVE))
                .thenReturn(Optional.of(only));
        when(groupMemberRepository.findByGroupIdAndStatusOrderByIdAsc(1L, GroupMember.STATUS_ACTIVE))
                .thenReturn(List.of(only));

        MoveMemberRequest req = new MoveMemberRequest();
        req.setTargetGroupId(2L);

        assertThrows(
                BusinessException.class,
                () -> teacherTaskGroupService.moveMemberBetweenSemesterGroups(99L, 10L, 1L, 5L, req));
    }

    @Test
    void moveMemberBetweenSemesterGroupsReassignsLeaderWhenMovingLeader() {
        ClassEntity clazz = new ClassEntity();
        clazz.setId(10L);
        clazz.setTeacherId(99L);
        clazz.setGroupSizeMax(5);
        clazz.setIsDeleted(0);
        when(classRepository.findById(10L)).thenReturn(Optional.of(clazz));

        TaskGroup source = new TaskGroup();
        source.setId(1L);
        source.setClassId(10L);
        source.setTaskId(null);
        source.setLeaderId(5L);
        TaskGroup target = new TaskGroup();
        target.setId(2L);
        target.setClassId(10L);
        target.setTaskId(null);
        target.setLeaderId(8L);
        when(taskGroupRepository.findByIdAndClassIdAndTaskIdIsNullAndIsDeleted(1L, 10L, 0))
                .thenReturn(Optional.of(source));
        when(taskGroupRepository.findByIdAndClassIdAndTaskIdIsNullAndIsDeleted(2L, 10L, 0))
                .thenReturn(Optional.of(target));
        when(taskGroupRepository.save(any(TaskGroup.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        doNothing().when(classMembershipService).requireActiveStudentInClass(5L, 10L);

        GroupMember gm5 = new GroupMember();
        gm5.setId(10L);
        gm5.setGroupId(1L);
        gm5.setUserId(5L);
        gm5.setStatus(GroupMember.STATUS_ACTIVE);
        GroupMember gm7 = new GroupMember();
        gm7.setId(11L);
        gm7.setGroupId(1L);
        gm7.setUserId(7L);
        gm7.setStatus(GroupMember.STATUS_ACTIVE);
        when(groupMemberRepository.findByGroupIdAndUserIdAndStatus(1L, 5L, GroupMember.STATUS_ACTIVE))
                .thenReturn(Optional.of(gm5));
        when(groupMemberRepository.findByGroupIdAndStatusOrderByIdAsc(1L, GroupMember.STATUS_ACTIVE))
                .thenReturn(List.of(gm5, gm7));

        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatus(2L, 5L, GroupMember.STATUS_ACTIVE))
                .thenReturn(false);
        when(groupMemberRepository.countByGroupIdAndStatus(2L, GroupMember.STATUS_ACTIVE)).thenReturn(1L);

        GroupMember afterTarget8 = new GroupMember();
        afterTarget8.setUserId(8L);
        GroupMember afterTarget5 = new GroupMember();
        afterTarget5.setGroupId(2L);
        afterTarget5.setUserId(5L);
        when(groupMemberRepository.findByGroupIdAndStatusOrderByIdAsc(2L, GroupMember.STATUS_ACTIVE))
                .thenReturn(List.of(afterTarget8, afterTarget5));

        MoveMemberRequest req = new MoveMemberRequest();
        req.setTargetGroupId(2L);

        teacherTaskGroupService.moveMemberBetweenSemesterGroups(99L, 10L, 1L, 5L, req);

        ArgumentCaptor<TaskGroup> groupCaptor = ArgumentCaptor.forClass(TaskGroup.class);
        verify(taskGroupRepository).save(groupCaptor.capture());
        assertEquals(7L, groupCaptor.getValue().getLeaderId());

        ArgumentCaptor<GroupMember> memberCaptor = ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).save(memberCaptor.capture());
        assertEquals(2L, memberCaptor.getValue().getGroupId());
    }

    @Test
    void moveMemberBetweenSemesterGroupsDoesNotTouchSourceLeaderWhenMovingNonLeader() {
        ClassEntity clazz = new ClassEntity();
        clazz.setId(10L);
        clazz.setTeacherId(99L);
        clazz.setGroupSizeMax(5);
        clazz.setIsDeleted(0);
        when(classRepository.findById(10L)).thenReturn(Optional.of(clazz));

        TaskGroup source = new TaskGroup();
        source.setId(1L);
        source.setClassId(10L);
        source.setTaskId(null);
        source.setLeaderId(7L);
        TaskGroup target = new TaskGroup();
        target.setId(2L);
        target.setClassId(10L);
        target.setTaskId(null);
        when(taskGroupRepository.findByIdAndClassIdAndTaskIdIsNullAndIsDeleted(1L, 10L, 0))
                .thenReturn(Optional.of(source));
        when(taskGroupRepository.findByIdAndClassIdAndTaskIdIsNullAndIsDeleted(2L, 10L, 0))
                .thenReturn(Optional.of(target));

        doNothing().when(classMembershipService).requireActiveStudentInClass(5L, 10L);

        GroupMember gm5 = new GroupMember();
        gm5.setGroupId(1L);
        gm5.setUserId(5L);
        gm5.setStatus(GroupMember.STATUS_ACTIVE);
        GroupMember gm7 = new GroupMember();
        gm7.setGroupId(1L);
        gm7.setUserId(7L);
        gm7.setStatus(GroupMember.STATUS_ACTIVE);
        when(groupMemberRepository.findByGroupIdAndUserIdAndStatus(1L, 5L, GroupMember.STATUS_ACTIVE))
                .thenReturn(Optional.of(gm5));
        when(groupMemberRepository.findByGroupIdAndStatusOrderByIdAsc(1L, GroupMember.STATUS_ACTIVE))
                .thenReturn(List.of(gm5, gm7));

        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatus(2L, 5L, GroupMember.STATUS_ACTIVE))
                .thenReturn(false);
        when(groupMemberRepository.countByGroupIdAndStatus(2L, GroupMember.STATUS_ACTIVE)).thenReturn(0L);

        GroupMember afterOnly7 = new GroupMember();
        afterOnly7.setUserId(7L);
        when(groupMemberRepository.findByGroupIdAndStatusOrderByIdAsc(2L, GroupMember.STATUS_ACTIVE))
                .thenReturn(List.of(gm5, afterOnly7));

        MoveMemberRequest req = new MoveMemberRequest();
        req.setTargetGroupId(2L);

        teacherTaskGroupService.moveMemberBetweenSemesterGroups(99L, 10L, 1L, 5L, req);

        verify(taskGroupRepository, never()).save(any(TaskGroup.class));
    }

    @Test
    void moveMemberBetweenSemesterGroupsFailsWhenGroupingLocked() {
        ClassEntity clazz = new ClassEntity();
        clazz.setId(10L);
        clazz.setTeacherId(99L);
        clazz.setGroupingLocked(1);
        clazz.setIsDeleted(0);
        when(classRepository.findById(10L)).thenReturn(Optional.of(clazz));
        MoveMemberRequest req = new MoveMemberRequest();
        req.setTargetGroupId(2L);
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> teacherTaskGroupService.moveMemberBetweenSemesterGroups(99L, 10L, 1L, 5L, req));
        assertEquals("GROUPING_LOCKED", ex.getCode());
    }

    @Test
    void createSemesterGroupFailsWhenGroupingLocked() {
        ClassEntity clazz = new ClassEntity();
        clazz.setId(10L);
        clazz.setTeacherId(99L);
        clazz.setGroupingLocked(1);
        clazz.setGroupSizeMax(5);
        clazz.setIsDeleted(0);
        when(classRepository.findById(10L)).thenReturn(Optional.of(clazz));
        CreateTaskGroupRequest req = new CreateTaskGroupRequest();
        req.setName("组");
        req.setLeaderId(5L);
        req.setMemberStudentIds(List.of(5L));
        BusinessException ex =
                assertThrows(BusinessException.class, () -> teacherTaskGroupService.createSemesterGroup(99L, 10L, req));
        assertEquals("GROUPING_LOCKED", ex.getCode());
    }
}
