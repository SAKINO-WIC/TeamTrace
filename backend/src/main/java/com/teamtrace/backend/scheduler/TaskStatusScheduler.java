package com.teamtrace.backend.scheduler;

import com.teamtrace.backend.domain.task.TaskStatusCodes;
import com.teamtrace.backend.repository.TaskRepository;
import com.teamtrace.backend.util.AppTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TaskStatusScheduler {

    private final TaskRepository taskRepository;
    private final boolean enabled;

    public TaskStatusScheduler(
            TaskRepository taskRepository,
            @Value("${teamtrace.scheduler.task-status.enabled:true}") boolean enabled) {
        this.taskRepository = taskRepository;
        this.enabled = enabled;
    }

    /**
     * 每分钟扫描：将已到截止时间的任务置为已截止（幂等）。
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void closeTasksByDeadline() {
        if (!enabled) {
            return;
        }
        taskRepository.markClosedByDeadline(AppTime.now(), TaskStatusCodes.CLOSED);
    }
}
