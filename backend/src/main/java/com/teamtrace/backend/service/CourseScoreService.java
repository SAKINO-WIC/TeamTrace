package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.teacher.CourseScoreItemResponse;
import com.teamtrace.backend.dto.teacher.CourseGroupScoreItemResponse;
import com.teamtrace.backend.dto.teacher.SaveCourseGroupScoreRequest;
import com.teamtrace.backend.dto.teacher.SaveCourseScoreRequest;
import com.teamtrace.backend.entity.ClassEntity;
import com.teamtrace.backend.entity.ClassStudent;
import com.teamtrace.backend.entity.CourseGroupScore;
import com.teamtrace.backend.entity.CourseScore;
import com.teamtrace.backend.entity.TaskGroup;
import com.teamtrace.backend.entity.User;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.ClassStudentRepository;
import com.teamtrace.backend.repository.CourseGroupScoreRepository;
import com.teamtrace.backend.repository.CourseScoreRepository;
import com.teamtrace.backend.repository.TaskGroupRepository;
import com.teamtrace.backend.repository.UserRepository;
import com.teamtrace.backend.util.AppTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseScoreService {

    private final ClassRepository classRepository;
    private final ClassStudentRepository classStudentRepository;
    private final CourseScoreRepository courseScoreRepository;
    private final CourseGroupScoreRepository courseGroupScoreRepository;
    private final TaskGroupRepository taskGroupRepository;
    private final UserRepository userRepository;

    public CourseScoreService(
            ClassRepository classRepository,
            ClassStudentRepository classStudentRepository,
            CourseScoreRepository courseScoreRepository,
            CourseGroupScoreRepository courseGroupScoreRepository,
            TaskGroupRepository taskGroupRepository,
            UserRepository userRepository) {
        this.classRepository = classRepository;
        this.classStudentRepository = classStudentRepository;
        this.courseScoreRepository = courseScoreRepository;
        this.courseGroupScoreRepository = courseGroupScoreRepository;
        this.taskGroupRepository = taskGroupRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CourseScoreItemResponse> listCourseScores(Long teacherId, Long classId) {
        validateTeacherOwnsClass(teacherId, classId);

        List<ClassStudent> students = classStudentRepository.findByClassIdAndIsDeletedOrderByIdAsc(classId, 0);
        Map<Long, User> userMap = userRepository.findAllById(
                students.stream().map(ClassStudent::getStudentId).toList()
        ).stream().collect(Collectors.toMap(User::getId, u -> u));

        Map<Long, CourseScore> scoreMap = courseScoreRepository.findByClassIdAndIsDeletedOrderByIdAsc(classId, 0)
                .stream().collect(Collectors.toMap(CourseScore::getStudentId, s -> s, (a, b) -> b));

        return students.stream().map(cs -> {
            User user = userMap.get(cs.getStudentId());
            CourseScore score = scoreMap.get(cs.getStudentId());
            return CourseScoreItemResponse.builder()
                    .studentId(cs.getStudentId())
                    .studentName(user == null ? "" : user.getName())
                    .totalScore(score == null ? null : score.getTotalScore())
                    .scoreType(score == null ? null : score.getScoreType())
                    .calculatedAt(score == null || score.getCalculatedAt() == null ? "" : score.getCalculatedAt().toString())
                    .build();
        }).toList();
    }

    @Transactional
    public CourseScoreItemResponse saveCourseScore(Long teacherId, Long classId, SaveCourseScoreRequest request) {
        validateTeacherOwnsClass(teacherId, classId);

        // 验证学生在班级中
        ClassStudent cs = classStudentRepository.findByClassIdAndStudentId(classId, request.getStudentId())
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "学生不在班级中", HttpStatus.NOT_FOUND));
        if (cs.getIsDeleted() != null && cs.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "学生不在班级中", HttpStatus.NOT_FOUND);
        }

        User user = userRepository.findById(request.getStudentId())
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "学生不存在", HttpStatus.NOT_FOUND));

        CourseScore score = courseScoreRepository.findByClassIdAndStudentIdAndIsDeleted(classId, request.getStudentId(), 0)
                .orElseGet(() -> {
                    CourseScore newScore = new CourseScore();
                    newScore.setClassId(classId);
                    newScore.setStudentId(request.getStudentId());
                    newScore.setIsDeleted(0);
                    return newScore;
                });

        score.setTotalScore(request.getTotalScore());
        score.setScoreType(2); // 教师手动覆盖
        score.setCalculatedAt(AppTime.now());
        courseScoreRepository.save(score);

        return CourseScoreItemResponse.builder()
                .studentId(request.getStudentId())
                .studentName(user.getName())
                .totalScore(score.getTotalScore())
                .scoreType(score.getScoreType())
                .calculatedAt(score.getCalculatedAt().toString())
                .build();
    }

    @Transactional(readOnly = true)
    public List<CourseGroupScoreItemResponse> listCourseGroupScores(Long teacherId, Long classId) {
        validateTeacherOwnsClass(teacherId, classId);

        Map<Long, CourseGroupScore> scoreMap = courseGroupScoreRepository.findByClassIdAndIsDeletedOrderByIdAsc(classId, 0)
                .stream().collect(Collectors.toMap(CourseGroupScore::getGroupId, s -> s, (a, b) -> b));

        return taskGroupRepository.findByClassIdAndTaskIdIsNullAndIsDeletedOrderByIdAsc(classId, 0).stream()
                .map(group -> {
                    CourseGroupScore score = scoreMap.get(group.getId());
                    return CourseGroupScoreItemResponse.builder()
                            .groupId(group.getId())
                            .groupName(group.getName())
                            .totalScore(score == null ? null : score.getTotalScore())
                            .scoreType(score == null ? null : score.getScoreType())
                            .calculatedAt(score == null || score.getCalculatedAt() == null ? "" : score.getCalculatedAt().toString())
                            .build();
                })
                .toList();
    }

    @Transactional
    public CourseGroupScoreItemResponse saveCourseGroupScore(
            Long teacherId, Long classId, SaveCourseGroupScoreRequest request) {
        validateTeacherOwnsClass(teacherId, classId);

        TaskGroup group = taskGroupRepository
                .findByIdAndClassIdAndTaskIdIsNullAndIsDeleted(request.getGroupId(), classId, 0)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Group does not exist in this class", HttpStatus.NOT_FOUND));

        CourseGroupScore score = courseGroupScoreRepository
                .findByClassIdAndGroupIdAndIsDeleted(classId, request.getGroupId(), 0)
                .orElseGet(() -> {
                    CourseGroupScore next = new CourseGroupScore();
                    next.setClassId(classId);
                    next.setGroupId(request.getGroupId());
                    next.setIsDeleted(0);
                    return next;
                });

        score.setTotalScore(request.getTotalScore());
        score.setScoreType(2);
        score.setCalculatedAt(AppTime.now());
        courseGroupScoreRepository.save(score);

        return CourseGroupScoreItemResponse.builder()
                .groupId(group.getId())
                .groupName(group.getName())
                .totalScore(score.getTotalScore())
                .scoreType(score.getScoreType())
                .calculatedAt(score.getCalculatedAt().toString())
                .build();
    }

    private void validateTeacherOwnsClass(Long teacherId, Long classId) {
        ClassEntity clazz = classRepository.findById(classId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND));
        if (clazz.getIsDeleted() != null && clazz.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND);
        }
        if (!teacherId.equals(clazz.getTeacherId())) {
            throw new BusinessException("FORBIDDEN", "无权限", HttpStatus.FORBIDDEN);
        }
    }
}
