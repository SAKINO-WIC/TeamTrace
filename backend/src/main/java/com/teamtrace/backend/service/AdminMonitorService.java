package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.admin.monitor.AdminMonitorClassItemResponse;
import com.teamtrace.backend.dto.admin.monitor.AdminMonitorClassPageResponse;
import com.teamtrace.backend.dto.admin.monitor.AdminMonitorOverviewResponse;
import com.teamtrace.backend.dto.admin.monitor.AdminMonitorTaskItemResponse;
import com.teamtrace.backend.dto.admin.monitor.AdminMonitorTaskPageResponse;
import com.teamtrace.backend.entity.ClassEntity;
import com.teamtrace.backend.entity.Task;
import com.teamtrace.backend.entity.User;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.ClassStudentRepository;
import com.teamtrace.backend.repository.TaskGroupRepository;
import com.teamtrace.backend.repository.TaskRepository;
import com.teamtrace.backend.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminMonitorService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final ClassRepository classRepository;
    private final TaskRepository taskRepository;
    private final TaskGroupRepository taskGroupRepository;
    private final ClassStudentRepository classStudentRepository;
    private final UserRepository userRepository;

    public AdminMonitorService(
            ClassRepository classRepository,
            TaskRepository taskRepository,
            TaskGroupRepository taskGroupRepository,
            ClassStudentRepository classStudentRepository,
            UserRepository userRepository
    ) {
        this.classRepository = classRepository;
        this.taskRepository = taskRepository;
        this.taskGroupRepository = taskGroupRepository;
        this.classStudentRepository = classStudentRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AdminMonitorOverviewResponse overview() {
        long classCount = classRepository.countByIsDeleted(0);
        long taskCount = taskRepository.countByIsDeleted(0);
        long groupCount = taskGroupRepository.countByIsDeleted(0);
        long activeClassCount = classRepository.countByStatusAndIsDeleted(1, 0);
        return AdminMonitorOverviewResponse.builder()
                .classCount(classCount)
                .taskCount(taskCount)
                .groupCount(groupCount)
                .activeClassCount(activeClassCount)
                .build();
    }

    @Transactional(readOnly = true)
    public AdminMonitorClassPageResponse listClasses(int page, int size, String keyword, String status) {
        int p = Math.max(1, page);
        int s = Math.min(MAX_PAGE_SIZE, Math.max(1, size <= 0 ? DEFAULT_PAGE_SIZE : size));
        PageRequest pageable = PageRequest.of(p - 1, s, Sort.by(Sort.Direction.DESC, "id"));

        // 监控页当前以“全局只读”为主，先返回实际数据；keyword/status 先保留参数位，后续再做更细筛选。
        Page<ClassEntity> pg = classRepository.findByIsDeletedOrderByIdDesc(0, pageable);
        List<ClassEntity> rows = pg.getContent();

        Map<Long, User> teacherMap = userRepository.findAllById(
                        rows.stream().map(ClassEntity::getTeacherId).distinct().toList()
                ).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        List<AdminMonitorClassItemResponse> list = rows.stream().map(row -> {
            long memberCount = classStudentRepository.countByClassIdAndIsDeleted(row.getId(), 0);
            User teacher = teacherMap.get(row.getTeacherId());
            return AdminMonitorClassItemResponse.builder()
                    .id(row.getId())
                    .name(row.getName())
                    .teacherName(teacher == null ? null : teacher.getName())
                    .status(row.getStatus())
                    .memberCount(memberCount)
                    .build();
        }).toList();

        return AdminMonitorClassPageResponse.builder()
                .list(list)
                .page(p)
                .size(s)
                .total(pg.getTotalElements())
                .pages(pg.getTotalPages())
                .hasNext(pg.hasNext())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminMonitorTaskPageResponse listTasks(int page, int size, String keyword, String status) {
        int p = Math.max(1, page);
        int s = Math.min(MAX_PAGE_SIZE, Math.max(1, size <= 0 ? DEFAULT_PAGE_SIZE : size));
        PageRequest pageable = PageRequest.of(p - 1, s, Sort.by(Sort.Direction.DESC, "id"));

        // 监控页当前以“全局只读”为主，先返回实际数据；keyword/status 先保留参数位，后续再做更细筛选。
        Page<Task> pg = taskRepository.findByIsDeletedOrderByIdDesc(0, pageable);
        List<Task> rows = pg.getContent();

        Map<Long, ClassEntity> classMap = classRepository.findAllById(
                        rows.stream().map(Task::getClassId).distinct().toList()
                ).stream()
                .collect(Collectors.toMap(ClassEntity::getId, Function.identity(), (a, b) -> a));

        List<AdminMonitorTaskItemResponse> list = rows.stream().map(row -> {
            ClassEntity cls = classMap.get(row.getClassId());
            return AdminMonitorTaskItemResponse.builder()
                    .id(row.getId())
                    .name(row.getName())
                    .className(cls == null ? null : cls.getName())
                    .status(row.getStatus())
                    .deadline(row.getDeadline())
                    .build();
        }).toList();

        return AdminMonitorTaskPageResponse.builder()
                .list(list)
                .page(p)
                .size(s)
                .total(pg.getTotalElements())
                .pages(pg.getTotalPages())
                .hasNext(pg.hasNext())
                .build();
    }
}

