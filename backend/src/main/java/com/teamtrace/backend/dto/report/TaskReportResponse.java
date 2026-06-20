package com.teamtrace.backend.dto.report;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TaskReportResponse {

    private Long taskId;
    private String taskName;
    private String deadline;
    private Integer taskStatus;
    private boolean peerReviewEnabled;
    private BigDecimal peerReviewWeight;
    private BigDecimal teacherScoreWeight;
    private int totalGroups;
    private int totalMembers;
    private List<GroupReport> groups;

    @Getter
    @Builder
    public static class GroupReport {
        private Long groupId;
        private String groupName;
        private Long leaderId;
        private String leaderName;
        private List<MemberReport> members;
    }

    @Getter
    @Builder
    public static class MemberReport {
        private Long studentId;
        private String studentName;
        private int assignedSubtasks;
        private int completedSubtasks;
        private BigDecimal completionRate;
        private BigDecimal peerAverageOn100;
        private BigDecimal teacherScore;
        private BigDecimal weightedTotal100;
    }
}
