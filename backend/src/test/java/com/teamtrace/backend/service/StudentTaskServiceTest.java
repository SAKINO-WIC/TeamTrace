package com.teamtrace.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamtrace.backend.dto.teacher.TaskDetailResponse;
import com.teamtrace.backend.dto.teacher.TaskSummaryResponse;
import com.teamtrace.backend.domain.task.StudentTaskDetailViewResolver;
import com.teamtrace.backend.entity.ClassEntity;
import com.teamtrace.backend.entity.Task;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.AppealRepository;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.TaskRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentTaskServiceTest {

    @Mock
    private ClassRepository classRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private AppealRepository appealRepository;

    @Mock
    private ClassMembershipService classMembershipService;

    @Mock
    private TaskSummaryMapper taskSummaryMapper;

    @Mock
    private StudentTaskDetailViewResolver studentTaskDetailViewResolver;

    @InjectMocks
    private StudentTaskService studentTaskService;

    @Test
    void shouldListWhenActiveInClass() {
        ClassEntity clazz = new ClassEntity();
        clazz.setId(1L);
        clazz.setIsDeleted(0);
        clazz.setStatus(1);
        when(classRepository.findById(1L)).thenReturn(Optional.of(clazz));
        Task task = new Task();
        task.setId(10L);
        when(taskRepository.searchStudentTasks(1L, 0, null, null)).thenReturn(List.of(task));
        TaskSummaryResponse summary = TaskSummaryResponse.builder().taskId(10L).build();
        when(taskSummaryMapper.toSummary(task)).thenReturn(summary);

        List<TaskSummaryResponse> list = studentTaskService.listClassTasks(5L, 1L, null, null);

        assertEquals(1, list.size());
        assertEquals(10L, list.get(0).getTaskId());
        verify(classMembershipService).requireActiveStudentInClass(5L, 1L);
    }

    @Test
    void shouldListWithStatusAndKeyword() {
        ClassEntity clazz = new ClassEntity();
        clazz.setId(1L);
        clazz.setIsDeleted(0);
        clazz.setStatus(1);
        when(classRepository.findById(1L)).thenReturn(Optional.of(clazz));
        when(taskRepository.searchStudentTasks(1L, 0, 2, "作业"))
                .thenReturn(List.of());

        List<TaskSummaryResponse> list = studentTaskService.listClassTasks(5L, 1L, 2, " 作业 ");

        assertEquals(0, list.size());
        verify(classMembershipService).requireActiveStudentInClass(5L, 1L);
    }

    @Test
    void shouldRejectWhenClassEnded() {
        ClassEntity clazz = new ClassEntity();
        clazz.setId(1L);
        clazz.setIsDeleted(0);
        clazz.setStatus(0);
        when(classRepository.findById(1L)).thenReturn(Optional.of(clazz));

        assertThrows(BusinessException.class, () -> studentTaskService.listClassTasks(5L, 1L, null, null));
    }

    @Test
    void shouldRejectWhenStatusInvalid() {
        ClassEntity clazz = new ClassEntity();
        clazz.setId(1L);
        clazz.setIsDeleted(0);
        clazz.setStatus(1);
        when(classRepository.findById(1L)).thenReturn(Optional.of(clazz));

        BusinessException ex =
                assertThrows(BusinessException.class, () -> studentTaskService.listClassTasks(5L, 1L, 9, null));
        assertEquals("BAD_REQUEST", ex.getCode());
    }

    @Test
    void shouldReturnStudentTaskDetailComputedFields() {
        ClassEntity clazz = new ClassEntity();
        clazz.setId(1L);
        clazz.setIsDeleted(0);
        clazz.setStatus(1);
        when(classRepository.findById(1L)).thenReturn(Optional.of(clazz));
        Task task = new Task();
        task.setId(9L);
        when(taskRepository.findByIdAndClassIdAndIsDeleted(9L, 1L, 0)).thenReturn(Optional.of(task));

        TaskDetailResponse detail = TaskDetailResponse.builder()
                .taskId(9L)
                .classId(1L)
                .name("任务A")
                .deadline(LocalDateTime.now().minusMinutes(5))
                .enablePeerReview(true)
                .peerReviewDeadline(LocalDateTime.now().plusMinutes(30))
                .build();
        TaskDetailResponse enriched = TaskDetailResponse.builder()
                .taskId(9L)
                .classId(1L)
                .name("任务A")
                .deadline(detail.getDeadline())
                .enablePeerReview(true)
                .peerReviewDeadline(detail.getPeerReviewDeadline())
                .isOverdue(true)
                .canPeerReviewNow(true)
                .peerReviewPhase("open")
                .canSubmitAppeal(true)
                .build();
        when(taskSummaryMapper.toDetail(task)).thenReturn(detail);
        when(appealRepository.existsByTaskIdAndStudentIdAndStatusAndIsDeleted(9L, 5L, 0, 0)).thenReturn(false);
        when(studentTaskDetailViewResolver.resolve(detail, true)).thenReturn(enriched);

        TaskDetailResponse result = studentTaskService.getClassTask(5L, 1L, 9L);

        assertEquals(9L, result.getTaskId());
        assertTrue(Boolean.TRUE.equals(result.getIsOverdue()));
        assertTrue(Boolean.TRUE.equals(result.getCanPeerReviewNow()));
        assertEquals("open", result.getPeerReviewPhase());
        assertTrue(Boolean.TRUE.equals(result.getCanSubmitAppeal()));
    }
}
