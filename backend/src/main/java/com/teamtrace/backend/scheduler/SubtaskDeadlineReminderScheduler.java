package com.teamtrace.backend.scheduler;

import com.teamtrace.backend.entity.Notification;
import com.teamtrace.backend.entity.Subtask;
import com.teamtrace.backend.entity.Task;
import com.teamtrace.backend.repository.SubtaskRepository;
import com.teamtrace.backend.repository.TaskRepository;
import com.teamtrace.backend.service.NotificationService;
import com.teamtrace.backend.util.AppTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 子任务截止前站内提醒：扫描已认领且未完成、{@code reminder_time IS NULL}、截止时间在未来若干小时内。
 * 发送后将 {@link Subtask#setReminderTime} 置为当前时间，避免重复推送（每子任务生命周期一次）。
 */
@Component
@ConditionalOnProperty(
        name = "teamtrace.scheduler.subtask-deadline-reminder.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SubtaskDeadlineReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubtaskDeadlineReminderScheduler.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SubtaskRepository subtaskRepository;
    private final TaskRepository taskRepository;
    private final NotificationService notificationService;

    @Value("${teamtrace.scheduler.subtask-deadline-reminder.lookahead-hours:24}")
    private int lookaheadHours;

    public SubtaskDeadlineReminderScheduler(
            SubtaskRepository subtaskRepository,
            TaskRepository taskRepository,
            NotificationService notificationService) {
        this.subtaskRepository = subtaskRepository;
        this.taskRepository = taskRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(
            fixedDelayString = "${teamtrace.scheduler.subtask-deadline-reminder.fixed-delay-ms:900000}",
            initialDelayString = "${teamtrace.scheduler.subtask-deadline-reminder.initial-delay-ms:120000}")
    @Transactional
    public void sendDeadlineReminders() {
        LocalDateTime now = AppTime.now();
        LocalDateTime until = now.plusHours(Math.max(1, lookaheadHours));
        List<Subtask> batch = subtaskRepository.findNeedingDeadlineReminder(now, until);
        if (batch.isEmpty()) {
            return;
        }
        log.debug("subtask deadline reminder batch size={}", batch.size());
        for (Subtask s : batch) {
            try {
                Long assigneeId = s.getAssigneeId();
                if (assigneeId == null) {
                    continue;
                }
                Task task = taskRepository.findById(s.getTaskId()).orElse(null);
                if (task == null || (task.getIsDeleted() != null && task.getIsDeleted() == 1)) {
                    s.setReminderTime(now);
                    subtaskRepository.save(s);
                    continue;
                }
                String taskName = task.getName() != null ? task.getName() : "任务";
                String deadlineStr = s.getDeadline() != null ? s.getDeadline().format(FMT) : "";
                notificationService.notifyUser(
                        assigneeId,
                        Notification.TYPE_SUBTASK_DEADLINE_SOON,
                        "子任务即将截止",
                        "你在「" + taskName + "」中的子任务「" + s.getName() + "」将于 " + deadlineStr + " 截止，请及时处理。",
                        s.getId());
                s.setReminderTime(now);
                subtaskRepository.save(s);
            } catch (RuntimeException ex) {
                log.warn("subtask deadline reminder failed subtaskId={}", s.getId(), ex);
            }
        }
    }
}
