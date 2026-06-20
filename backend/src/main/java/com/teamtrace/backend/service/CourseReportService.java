package com.teamtrace.backend.service;

import com.teamtrace.backend.domain.score.WeightedScoreFormulas;
import com.teamtrace.backend.dto.report.CourseReportResponse;
import com.teamtrace.backend.dto.report.CourseReportResponse.StudentCourseReport;
import com.teamtrace.backend.dto.report.CourseReportResponse.TaskScore;
import com.teamtrace.backend.entity.ClassEntity;
import com.teamtrace.backend.entity.ClassStudent;
import com.teamtrace.backend.entity.Task;
import com.teamtrace.backend.entity.TeacherScore;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.ClassStudentRepository;
import com.teamtrace.backend.repository.PeerReviewRepository;
import com.teamtrace.backend.repository.TaskRepository;
import com.teamtrace.backend.repository.TeacherScoreRepository;
import com.teamtrace.backend.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseReportService {

    private final ClassRepository classRepository;
    private final ClassStudentRepository classStudentRepository;
    private final TaskRepository taskRepository;
    private final PeerReviewRepository peerReviewRepository;
    private final TeacherScoreRepository teacherScoreRepository;
    private final UserRepository userRepository;

    public CourseReportService(
            ClassRepository classRepository,
            ClassStudentRepository classStudentRepository,
            TaskRepository taskRepository,
            PeerReviewRepository peerReviewRepository,
            TeacherScoreRepository teacherScoreRepository,
            UserRepository userRepository) {
        this.classRepository = classRepository;
        this.classStudentRepository = classStudentRepository;
        this.taskRepository = taskRepository;
        this.peerReviewRepository = peerReviewRepository;
        this.teacherScoreRepository = teacherScoreRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public CourseReportResponse generate(Long teacherId, Long classId) {
        ClassEntity clazz = classRepository.findById(classId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND));
        if (!teacherId.equals(clazz.getTeacherId())) {
            throw new BusinessException("FORBIDDEN", "无权限", HttpStatus.FORBIDDEN);
        }

        List<Task> tasks = taskRepository.findByClassIdAndIsDeletedOrderByIdDesc(classId, 0);
        List<ClassStudent> classStudents = classStudentRepository.findByClassIdAndIsDeletedOrderByIdAsc(classId, 0);

        // Pre-load all teacher scores for this class's tasks
        Map<Long, Map<Long, BigDecimal>> taskStudentScores = new HashMap<>();
        for (Task task : tasks) {
            List<TeacherScore> scores = teacherScoreRepository
                    .findByTaskIdAndTargetTypeAndIsDeleted(task.getId(), TeacherScore.TARGET_STUDENT, 0);
            Map<Long, BigDecimal> scoreMap = new HashMap<>();
            for (TeacherScore ts : scores) {
                scoreMap.put(ts.getTargetId(), ts.getScore().setScale(WeightedScoreFormulas.SCALE_TOTAL, RoundingMode.HALF_UP));
            }
            taskStudentScores.put(task.getId(), scoreMap);
        }

        List<StudentCourseReport> studentReports = new ArrayList<>();

        for (ClassStudent cs : classStudents) {
            long studentId = cs.getStudentId();
            String name = userRepository.findById(studentId).map(u -> u.getName()).orElse("");

            List<TaskScore> taskScores = new ArrayList<>();
            int tasksCompleted = 0;

            for (Task task : tasks) {
                Map<Long, BigDecimal> scoreMap = taskStudentScores.get(task.getId());
                BigDecimal weighted = scoreMap != null ? scoreMap.get(studentId) : null;

                if (weighted != null) {
                    tasksCompleted++;
                }
                taskScores.add(TaskScore.builder()
                        .taskId(task.getId())
                        .taskName(task.getName())
                        .weightedTotal100(weighted)
                        .peerReviewEnabled(task.getEnablePeerReview() != null && task.getEnablePeerReview() == 1)
                        .build());
            }

            BigDecimal average = null;
            if (tasksCompleted > 0) {
                BigDecimal sum = taskScores.stream()
                        .map(TaskScore::getWeightedTotal100)
                        .filter(s -> s != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                average = sum.divide(BigDecimal.valueOf(tasksCompleted), 2, RoundingMode.HALF_UP);
            }

            studentReports.add(StudentCourseReport.builder()
                    .studentId(studentId)
                    .studentName(name)
                    .tasksCompleted(tasksCompleted)
                    .tasksTotal(tasks.size())
                    .averageScore(average)
                    .taskScores(taskScores)
                    .build());
        }

        return CourseReportResponse.builder()
                .classId(classId)
                .className(clazz.getName())
                .semester(clazz.getSemester())
                .totalStudents(classStudents.size())
                .totalTasks(tasks.size())
                .students(studentReports)
                .build();
    }
}
