package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.teacher.TaskAttachmentResponse;
import com.teamtrace.backend.dto.teacher.TaskDetailResponse;
import com.teamtrace.backend.dto.teacher.TaskSummaryResponse;
import com.teamtrace.backend.entity.Task;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TaskSummaryMapper {

    public TaskDetailResponse toDetail(Task t) {
        return toDetail(t, List.of());
    }

    public TaskDetailResponse toDetail(Task t, List<TaskAttachmentResponse> attachments) {
        return TaskDetailResponse.builder()
                .taskId(t.getId())
                .taskUuid(t.getTaskUuid())
                .classId(t.getClassId())
                .name(t.getName())
                .description(t.getDescription())
                .deadline(t.getDeadline())
                .enablePeerReview(t.getEnablePeerReview() != null && t.getEnablePeerReview() == 1)
                .peerReviewDeadline(t.getPeerReviewDeadline())
                .peerReviewOffsetHours(t.getPeerReviewOffsetHours())
                .peerReviewMaxScore(t.getPeerReviewMaxScore())
                .peerReviewWeight(t.getPeerReviewWeight())
                .teacherScoreWeight(t.getTeacherScoreWeight())
                .status(t.getStatus())
                .attachments(attachments == null ? List.of() : attachments)
                .build();
    }

    public TaskSummaryResponse toSummary(Task t) {
        return TaskSummaryResponse.builder()
                .taskId(t.getId())
                .taskUuid(t.getTaskUuid())
                .classId(t.getClassId())
                .name(t.getName())
                .deadline(t.getDeadline())
                .enablePeerReview(t.getEnablePeerReview() != null && t.getEnablePeerReview() == 1)
                .peerReviewDeadline(t.getPeerReviewDeadline())
                .peerReviewOffsetHours(t.getPeerReviewOffsetHours())
                .peerReviewMaxScore(t.getPeerReviewMaxScore())
                .peerReviewWeight(t.getPeerReviewWeight())
                .teacherScoreWeight(t.getTeacherScoreWeight())
                .status(t.getStatus())
                .build();
    }
}
