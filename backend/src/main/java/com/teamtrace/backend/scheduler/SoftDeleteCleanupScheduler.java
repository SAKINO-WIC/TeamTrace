package com.teamtrace.backend.scheduler;

import com.teamtrace.backend.repository.AppealRepository;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.ClassStudentRepository;
import com.teamtrace.backend.repository.TaskRepository;
import com.teamtrace.backend.repository.UserRepository;
import com.teamtrace.backend.util.AppTime;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 软删除物理清理：每小时扫描一次，将超过 30 天的软删除记录物理删除。
 */
@Component
public class SoftDeleteCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(SoftDeleteCleanupScheduler.class);
    private static final int RETENTION_DAYS = 30;

    private final ClassRepository classRepository;
    private final ClassStudentRepository classStudentRepository;
    private final TaskRepository taskRepository;
    private final AppealRepository appealRepository;
    private final UserRepository userRepository;

    public SoftDeleteCleanupScheduler(
            ClassRepository classRepository,
            ClassStudentRepository classStudentRepository,
            TaskRepository taskRepository,
            AppealRepository appealRepository,
            UserRepository userRepository) {
        this.classRepository = classRepository;
        this.classStudentRepository = classStudentRepository;
        this.taskRepository = taskRepository;
        this.appealRepository = appealRepository;
        this.userRepository = userRepository;
    }

    @Scheduled(fixedDelay = 3600000, initialDelay = 600000) // Every hour, start after 10 min
    @Transactional
    public void purgeExpiredSoftDeletes() {
        LocalDateTime cutoff = AppTime.now().minusDays(RETENTION_DAYS);

        int classes = classRepository.deleteExpiredSoftDeletes(cutoff);
        int classStudents = classStudentRepository.deleteExpiredSoftDeletes(cutoff);
        int tasks = taskRepository.deleteExpiredSoftDeletes(cutoff);
        int appeals = appealRepository.deleteExpiredSoftDeletes(cutoff);
        int users = userRepository.deleteExpiredSoftDeletes(cutoff);

        int total = classes + classStudents + tasks + appeals + users;
        if (total > 0) {
            log.info("soft-delete purge: classes={}, classStudents={}, tasks={}, appeals={}, users={}, total={}",
                    classes, classStudents, tasks, appeals, users, total);
        }
    }
}
