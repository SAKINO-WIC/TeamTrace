package com.teamtrace.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamtrace.backend.domain.task.TaskStatusCodes;
import com.teamtrace.backend.dto.teacher.TaskDetailResponse;
import com.teamtrace.backend.dto.teacher.UpdateTaskRequest;
import com.teamtrace.backend.entity.ClassEntity;
import com.teamtrace.backend.entity.Task;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.TaskRepository;
import com.teamtrace.backend.util.SnowflakeIdGenerator;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeacherTaskServiceTest {

    @Mock
    private ClassRepository classRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private TaskSummaryMapper taskSummaryMapper;

    @InjectMocks
    private TeacherTaskService teacherTaskService;

    @Test
    void updateTaskSetsClosedWhenDeadlineInPast() {
        ClassEntity clazz = new ClassEntity();
        clazz.setId(1L);
        clazz.setIsDeleted(0);
        clazz.setTeacherId(2L);
        when(classRepository.findById(1L)).thenReturn(Optional.of(clazz));

        Task task = new Task();
        task.setId(5L);
        task.setClassId(1L);
        task.setIsDeleted(0);
        task.setStatus(TaskStatusCodes.IN_PROGRESS);
        task.setPeerReviewWeight(new BigDecimal("0.40"));
        task.setTeacherScoreWeight(new BigDecimal("0.60"));
        when(taskRepository.findByIdAndClassIdAndIsDeleted(5L, 1L, 0)).thenReturn(Optional.of(task));

        UpdateTaskRequest req = new UpdateTaskRequest();
        req.setDeadline(OffsetDateTime.now(ZoneOffset.ofHours(8)).minusMinutes(1).toString());
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        when(taskSummaryMapper.toDetail(any(Task.class)))
                .thenReturn(TaskDetailResponse.builder().taskId(5L).build());

        teacherTaskService.updateTask(2L, 1L, 5L, req);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertEquals(TaskStatusCodes.CLOSED, captor.getValue().getStatus());
    }

    @Test
    void updateTaskSetsInProgressWhenDeadlineInFuture() {
        ClassEntity clazz = new ClassEntity();
        clazz.setId(1L);
        clazz.setIsDeleted(0);
        clazz.setTeacherId(2L);
        when(classRepository.findById(1L)).thenReturn(Optional.of(clazz));

        Task task = new Task();
        task.setId(6L);
        task.setClassId(1L);
        task.setIsDeleted(0);
        task.setStatus(TaskStatusCodes.CLOSED);
        task.setPeerReviewWeight(new BigDecimal("0.40"));
        task.setTeacherScoreWeight(new BigDecimal("0.60"));
        when(taskRepository.findByIdAndClassIdAndIsDeleted(6L, 1L, 0)).thenReturn(Optional.of(task));

        UpdateTaskRequest req = new UpdateTaskRequest();
        req.setDeadline(OffsetDateTime.now(ZoneOffset.ofHours(8)).plusHours(1).toString());
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        when(taskSummaryMapper.toDetail(any(Task.class)))
                .thenReturn(TaskDetailResponse.builder().taskId(6L).build());

        teacherTaskService.updateTask(2L, 1L, 6L, req);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertEquals(TaskStatusCodes.IN_PROGRESS, captor.getValue().getStatus());
    }
}
