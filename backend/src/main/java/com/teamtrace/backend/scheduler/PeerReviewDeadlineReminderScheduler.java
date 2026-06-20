package com.teamtrace.backend.scheduler;

import com.teamtrace.backend.entity.GroupMember;
import com.teamtrace.backend.entity.Notification;
import com.teamtrace.backend.entity.Task;
import com.teamtrace.backend.entity.TaskGroup;
import com.teamtrace.backend.repository.GroupMemberRepository;
import com.teamtrace.backend.repository.PeerReviewRepository;
import com.teamtrace.backend.repository.TaskGroupRepository;
import com.teamtrace.backend.repository.TaskRepository;
import com.teamtrace.backend.service.NotificationService;
import com.teamtrace.backend.util.AppTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 互评截止前提醒：在 peer_review_deadline 前 24 小时内，对尚未完成互评的学生发送通知。
 */
@Component
public class PeerReviewDeadlineReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(PeerReviewDeadlineReminderScheduler.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TaskRepository taskRepository;
    private final TaskGroupRepository taskGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final PeerReviewRepository peerReviewRepository;
    private final NotificationService notificationService;

    public PeerReviewDeadlineReminderScheduler(
            TaskRepository taskRepository,
            TaskGroupRepository taskGroupRepository,
            GroupMemberRepository groupMemberRepository,
            PeerReviewRepository peerReviewRepository,
            NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.taskGroupRepository = taskGroupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.peerReviewRepository = peerReviewRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelay = 900000, initialDelay = 180000) // Every 15 min, start after 3 min
    @Transactional
    public void sendPeerReviewReminders() {
        LocalDateTime now = AppTime.now();
        LocalDateTime until = now.plusHours(24);

        // Find tasks with peer review enabled and deadline approaching
        List<Task> tasks = taskRepository.findTasksNeedingPeerReviewReminder(now, until);
        if (tasks.isEmpty()) {
            return;
        }

        for (Task task : tasks) {
            try {
                List<TaskGroup> groups = taskGroupRepository.findByTaskIdAndIsDeletedOrderByIdAsc(task.getId(), 0);
                for (TaskGroup group : groups) {
                    List<GroupMember> members = groupMemberRepository.findByGroupIdAndStatusOrderByIdAsc(
                            group.getId(), GroupMember.STATUS_ACTIVE);
                    if (members.size() < 2) continue;

                    // Find who has already submitted peer reviews
                    Set<Long> reviewers = new HashSet<>(
                            peerReviewRepository.findDistinctReviewerIdsByTaskIdAndGroupId(task.getId(), group.getId()));

                    String deadlineStr = task.getPeerReviewDeadline() != null
                            ? task.getPeerReviewDeadline().format(FMT) : "";

                    for (GroupMember member : members) {
                        if (reviewers.contains(member.getUserId())) continue;
                        notificationService.notifyUser(
                                member.getUserId(),
                                "peer_review_deadline_soon",
                                "互评即将截止",
                                "「" + task.getName() + "」的互评将于 " + deadlineStr + " 截止，你尚未完成互评，请尽快提交。",
                                task.getId());
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("peer review reminder failed taskId={}", task.getId(), ex);
            }
        }
    }
}
