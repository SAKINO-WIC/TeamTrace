package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.teacher.SaveTeacherScoreRequest;
import com.teamtrace.backend.dto.teacher.TeacherScoreResponse;
import com.teamtrace.backend.entity.ClassEntity;
import com.teamtrace.backend.entity.Task;
import com.teamtrace.backend.entity.TeacherScore;
import com.teamtrace.backend.entity.User;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.TaskRepository;
import com.teamtrace.backend.repository.TeacherScoreRepository;
import com.teamtrace.backend.repository.UserRepository;
import com.teamtrace.backend.util.AppTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeacherScoreService {

    private final TeacherScoreRepository teacherScoreRepository;
    private final TaskRepository taskRepository;
    private final ClassRepository classRepository;
    private final UserRepository userRepository;

    public TeacherScoreService(
            TeacherScoreRepository teacherScoreRepository,
            TaskRepository taskRepository,
            ClassRepository classRepository,
            UserRepository userRepository) {
        this.teacherScoreRepository = teacherScoreRepository;
        this.taskRepository = taskRepository;
        this.classRepository = classRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TeacherScoreResponse saveScore(Long teacherId, Long classId, Long taskId, SaveTeacherScoreRequest request) {
        // 验证班级归属
        ClassEntity clazz = classRepository.findById(classId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND));
        if (clazz.getTeacherId() == null || !clazz.getTeacherId().equals(teacherId)) {
            throw new BusinessException("FORBIDDEN", "无权限操作该班级", HttpStatus.FORBIDDEN);
        }
        if (clazz.getIsDeleted() != null && clazz.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND);
        }

        // 验证任务归属
        Task task = taskRepository.findByIdAndClassIdAndIsDeleted(taskId, classId, 0)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND));

        // 判断评分类型
        boolean isGroupScore = "group".equalsIgnoreCase(request.getTargetType());
        String targetType = isGroupScore ? TeacherScore.TARGET_GROUP : TeacherScore.TARGET_STUDENT;
        Long targetId = isGroupScore ? request.getGroupId() : request.getStudentId();

        if (targetId == null) {
            throw new BusinessException("BAD_REQUEST",
                    isGroupScore ? "groupId不能为空" : "studentId不能为空", HttpStatus.BAD_REQUEST);
        }

        // 验证目标存在
        String targetName;
        if (isGroupScore) {
            targetName = "小组"; // Group name lookup omitted for simplicity
        } else {
            User student = userRepository.findById(targetId)
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "学生不存在", HttpStatus.NOT_FOUND));
            if (!"student".equals(student.getRole().name())) {
                throw new BusinessException("BAD_REQUEST", "目标用户不是学生", HttpStatus.BAD_REQUEST);
            }
            targetName = student.getName();
        }

        LocalDateTime now = AppTime.now();

        // upsert: 已有则更新，没有则新建
        TeacherScore existing = teacherScoreRepository
                .findByTaskIdAndTargetTypeAndTargetIdAndIsDeleted(taskId, targetType, targetId, 0)
                .orElse(null);

        TeacherScore saved;
        if (existing != null) {
            existing.setScore(request.getScore());
            existing.setScoredBy(teacherId);
            existing.setScoredAt(now);
            saved = teacherScoreRepository.save(existing);
        } else {
            TeacherScore ts = new TeacherScore();
            ts.setTaskId(taskId);
            ts.setTargetType(targetType);
            ts.setTargetId(targetId);
            ts.setScore(request.getScore());
            ts.setScoredBy(teacherId);
            ts.setScoredAt(now);
            ts.setIsDeleted(0);
            ts.setVersion(0);
            saved = teacherScoreRepository.save(ts);
        }

        return TeacherScoreResponse.builder()
                .studentId(isGroupScore ? null : targetId)
                .groupId(isGroupScore ? targetId : null)
                .studentName(targetName)
                .score(saved.getScore())
                .scoredBy(saved.getScoredBy())
                .scoredAt(saved.getScoredAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<TeacherScoreResponse> listScores(Long teacherId, Long classId, Long taskId) {
        ClassEntity clazz = classRepository.findById(classId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND));
        if (clazz.getTeacherId() == null || !clazz.getTeacherId().equals(teacherId)) {
            throw new BusinessException("FORBIDDEN", "无权限操作该班级", HttpStatus.FORBIDDEN);
        }

        taskRepository.findByIdAndClassIdAndIsDeleted(taskId, classId, 0)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND));

        // Load both student and group scores
        List<TeacherScore> studentScores = teacherScoreRepository
                .findByTaskIdAndTargetTypeAndIsDeleted(taskId, TeacherScore.TARGET_STUDENT, 0);
        List<TeacherScore> groupScores = teacherScoreRepository
                .findByTaskIdAndTargetTypeAndIsDeleted(taskId, TeacherScore.TARGET_GROUP, 0);

        List<TeacherScore> allScores = new ArrayList<>(studentScores);
        allScores.addAll(groupScores);

        // 批量查用户名
        List<Long> studentIds = studentScores.stream().map(TeacherScore::getTargetId).distinct().toList();
        Map<Long, String> nameMap = userRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName));

        List<TeacherScoreResponse> result = new ArrayList<>();
        for (TeacherScore s : allScores) {
            boolean isGroup = TeacherScore.TARGET_GROUP.equals(s.getTargetType());
            result.add(TeacherScoreResponse.builder()
                    .studentId(isGroup ? null : s.getTargetId())
                    .groupId(isGroup ? s.getTargetId() : null)
                    .studentName(isGroup ? "小组" : nameMap.getOrDefault(s.getTargetId(), "未知"))
                    .score(s.getScore())
                    .scoredBy(s.getScoredBy())
                    .scoredAt(s.getScoredAt())
                    .build());
        }
        return result;
    }
}
