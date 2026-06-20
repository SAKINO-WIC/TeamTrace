package com.teamtrace.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamtrace.backend.dto.freecollab.SubmitCollaborationTaskRequest;
import com.teamtrace.backend.dto.freecollab.UpdateCollaborationTaskRequest;
import com.teamtrace.backend.entity.Notification;
import com.teamtrace.backend.entity.CollaborationProject;
import com.teamtrace.backend.entity.CollaborationSpace;
import com.teamtrace.backend.entity.CollaborationSpaceMember;
import com.teamtrace.backend.entity.CollaborationTask;
import com.teamtrace.backend.entity.CollaborationTaskDependency;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.CollaborationActivityLogRepository;
import com.teamtrace.backend.repository.CollaborationProjectRepository;
import com.teamtrace.backend.repository.CollaborationSpaceInviteCodeRepository;
import com.teamtrace.backend.repository.CollaborationSpaceMemberRepository;
import com.teamtrace.backend.repository.CollaborationSpaceRepository;
import com.teamtrace.backend.repository.CollaborationTaskDependencyRepository;
import com.teamtrace.backend.repository.CollaborationTaskRepository;
import com.teamtrace.backend.repository.CollaborationTaskReviewRepository;
import com.teamtrace.backend.repository.CollaborationTaskSubmissionRepository;
import com.teamtrace.backend.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentFreeCollaborationServiceDependencyTest {

    @Mock
    private CollaborationSpaceRepository spaceRepository;
    @Mock
    private CollaborationSpaceMemberRepository memberRepository;
    @Mock
    private CollaborationSpaceInviteCodeRepository inviteCodeRepository;
    @Mock
    private CollaborationProjectRepository projectRepository;
    @Mock
    private CollaborationTaskRepository taskRepository;
    @Mock
    private CollaborationTaskDependencyRepository dependencyRepository;
    @Mock
    private CollaborationTaskSubmissionRepository submissionRepository;
    @Mock
    private CollaborationTaskReviewRepository reviewRepository;
    @Mock
    private CollaborationActivityLogRepository activityLogRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private StudentFreeCollaborationService service;

    @Test
    void shouldRejectTaskDependencyCycleBeforeReplacingDependencies() {
        Long ownerId = 10L;
        Long spaceId = 1L;
        Long projectId = 2L;
        Long taskAId = 101L;
        Long taskBId = 102L;

        CollaborationSpace space = new CollaborationSpace();
        space.setId(spaceId);
        space.setStatus(CollaborationSpace.STATUS_ACTIVE);

        CollaborationSpaceMember owner = new CollaborationSpaceMember();
        owner.setSpaceId(spaceId);
        owner.setStudentId(ownerId);
        owner.setRole(CollaborationSpaceMember.ROLE_OWNER);

        CollaborationProject project = new CollaborationProject();
        project.setId(projectId);
        project.setSpaceId(spaceId);
        project.setStatus(CollaborationProject.STATUS_ACTIVE);

        CollaborationTask taskA = new CollaborationTask();
        taskA.setId(taskAId);
        taskA.setSpaceId(spaceId);
        taskA.setProjectId(projectId);
        taskA.setTitle("A");
        taskA.setStatus(CollaborationTask.STATUS_CLAIMED);

        CollaborationTask taskB = new CollaborationTask();
        taskB.setId(taskBId);
        taskB.setSpaceId(spaceId);
        taskB.setProjectId(projectId);
        taskB.setTitle("B");
        taskB.setStatus(CollaborationTask.STATUS_CLAIMED);

        CollaborationTaskDependency bDependsOnA = new CollaborationTaskDependency();
        bDependsOnA.setTaskId(taskBId);
        bDependsOnA.setDependsOnTaskId(taskAId);

        when(spaceRepository.findByIdAndIsDeleted(spaceId, 0)).thenReturn(Optional.of(space));
        when(projectRepository.findByIdAndSpaceId(projectId, spaceId)).thenReturn(Optional.of(project));
        when(taskRepository.findByIdAndSpaceIdAndProjectId(taskAId, spaceId, projectId)).thenReturn(Optional.of(taskA));
        when(memberRepository.findBySpaceIdAndStudentIdAndIsDeleted(spaceId, ownerId, 0)).thenReturn(Optional.of(owner));
        when(taskRepository.save(taskA)).thenReturn(taskA);
        when(taskRepository.findByProjectIdOrderByIdAsc(projectId)).thenReturn(List.of(taskA, taskB));
        when(dependencyRepository.findByTaskIdIn(List.of(taskAId, taskBId))).thenReturn(List.of(bDependsOnA));

        UpdateCollaborationTaskRequest request = new UpdateCollaborationTaskRequest();
        request.setDependsOnTaskIds(List.of(taskBId));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.updateTask(ownerId, spaceId, projectId, taskAId, request));

        assertEquals("BAD_REQUEST", ex.getCode());
        verify(dependencyRepository, never()).deleteByTaskId(taskAId);
        verify(dependencyRepository, never()).save(any(CollaborationTaskDependency.class));
    }

    @Test
    void shouldNotifyReceiverWhenTaskSubmitted() {
        Long assigneeId = 10L;
        Long receiverId = 11L;
        Long spaceId = 1L;
        Long projectId = 2L;
        Long taskId = 101L;

        CollaborationSpace space = new CollaborationSpace();
        space.setId(spaceId);
        space.setStatus(CollaborationSpace.STATUS_ACTIVE);

        CollaborationSpaceMember member = new CollaborationSpaceMember();
        member.setSpaceId(spaceId);
        member.setStudentId(assigneeId);
        member.setRole(CollaborationSpaceMember.ROLE_MEMBER);

        CollaborationProject project = new CollaborationProject();
        project.setId(projectId);
        project.setSpaceId(spaceId);

        CollaborationTask task = new CollaborationTask();
        task.setId(taskId);
        task.setSpaceId(spaceId);
        task.setProjectId(projectId);
        task.setTitle("任务A");
        task.setAssigneeId(assigneeId);
        task.setReceiverId(receiverId);
        task.setStatus(CollaborationTask.STATUS_IN_PROGRESS);

        when(spaceRepository.findByIdAndIsDeleted(spaceId, 0)).thenReturn(Optional.of(space));
        when(memberRepository.findBySpaceIdAndStudentIdAndIsDeleted(spaceId, assigneeId, 0)).thenReturn(Optional.of(member));
        when(projectRepository.findByIdAndSpaceId(projectId, spaceId)).thenReturn(Optional.of(project));
        when(taskRepository.findByIdAndSpaceIdAndProjectId(taskId, spaceId, projectId)).thenReturn(Optional.of(task));
        when(dependencyRepository.findByTaskIdIn(List.of(taskId))).thenReturn(List.of());
        when(submissionRepository.countByTaskId(taskId)).thenReturn(0L);
        when(taskRepository.save(task)).thenReturn(task);

        SubmitCollaborationTaskRequest request = new SubmitCollaborationTaskRequest();
        request.setContent("已完成");

        service.submitTask(assigneeId, spaceId, projectId, taskId, request);

        verify(notificationService).notifyUser(
                eq(receiverId),
                eq(Notification.TYPE_COLLABORATION_REVIEW),
                eq("自由协作任务待接收"),
                eq("任务「任务A」已提交，请确认接收或打回。"),
                eq(taskId));
    }
}
