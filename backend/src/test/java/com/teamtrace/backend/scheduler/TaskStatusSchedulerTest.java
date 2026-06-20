package com.teamtrace.backend.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamtrace.backend.domain.task.TaskStatusCodes;
import com.teamtrace.backend.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskStatusSchedulerTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskStatusScheduler taskStatusScheduler;

    @Test
    void closeTasksByDeadlineCallsRepositoryUpdate() {
        when(taskRepository.markClosedByDeadline(any(), eq(TaskStatusCodes.CLOSED))).thenReturn(0);

        taskStatusScheduler.closeTasksByDeadline();

        verify(taskRepository).markClosedByDeadline(any(), eq(TaskStatusCodes.CLOSED));
    }
}
