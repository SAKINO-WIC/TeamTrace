package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.publicapi.PublicStatsResponse;
import com.teamtrace.backend.entity.User;
import com.teamtrace.backend.repository.AppealRepository;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.SubtaskRepository;
import com.teamtrace.backend.repository.TaskGroupRepository;
import com.teamtrace.backend.repository.TaskRepository;
import com.teamtrace.backend.repository.UserRepository;
import com.teamtrace.backend.util.AppTime;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicStatsService {

    private static final LocalDate FIRST_DEV_LOG_DATE = LocalDate.of(2026, 4, 23);

    private final UserRepository userRepository;
    private final ClassRepository classRepository;
    private final TaskRepository taskRepository;
    private final TaskGroupRepository taskGroupRepository;
    private final SubtaskRepository subtaskRepository;
    private final AppealRepository appealRepository;

    public PublicStatsService(
            UserRepository userRepository,
            ClassRepository classRepository,
            TaskRepository taskRepository,
            TaskGroupRepository taskGroupRepository,
            SubtaskRepository subtaskRepository,
            AppealRepository appealRepository) {
        this.userRepository = userRepository;
        this.classRepository = classRepository;
        this.taskRepository = taskRepository;
        this.taskGroupRepository = taskGroupRepository;
        this.subtaskRepository = subtaskRepository;
        this.appealRepository = appealRepository;
    }

    @Transactional(readOnly = true)
    public PublicStatsResponse getStats() {
        var now = AppTime.now();
        long studentCount = userRepository.countByRoleAndIsDeleted(User.Role.student, 0);
        long teacherCount = userRepository.countByRoleAndIsDeleted(User.Role.teacher, 0);
        long totalUsers = userRepository.countByRoleInAndIsDeleted(
                List.of(User.Role.student, User.Role.teacher), 0);

        return PublicStatsResponse.builder()
                .totalUsers(totalUsers)
                .studentCount(studentCount)
                .teacherCount(teacherCount)
                .classCount(classRepository.countByIsDeleted(0))
                .taskCount(taskRepository.countByIsDeleted(0))
                .groupCount(taskGroupRepository.countByIsDeleted(0))
                .subtaskCount(subtaskRepository.countByIsDeleted(0))
                .appealCount(appealRepository.countByIsDeleted(0))
                .daysSinceFirstDevLog(Math.max(1, ChronoUnit.DAYS.between(FIRST_DEV_LOG_DATE, now.toLocalDate()) + 1))
                .generatedAt(now.toString())
                .build();
    }
}
