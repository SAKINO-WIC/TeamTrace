package com.teamtrace.backend.dto.report;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CourseReportResponse {

    private Long classId;
    private String className;
    private String semester;
    private int totalStudents;
    private int totalTasks;
    private List<StudentCourseReport> students;

    @Getter
    @Builder
    public static class StudentCourseReport {
        private Long studentId;
        private String studentName;
        private int tasksCompleted;
        private int tasksTotal;
        private BigDecimal averageScore;
        private List<TaskScore> taskScores;
    }

    @Getter
    @Builder
    public static class TaskScore {
        private Long taskId;
        private String taskName;
        private BigDecimal weightedTotal100;
        private boolean peerReviewEnabled;
    }
}
