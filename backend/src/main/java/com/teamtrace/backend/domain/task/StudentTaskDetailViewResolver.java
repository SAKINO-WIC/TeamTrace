package com.teamtrace.backend.domain.task;

import com.teamtrace.backend.dto.teacher.TaskDetailResponse;
import com.teamtrace.backend.util.AppTime;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class StudentTaskDetailViewResolver {

    public TaskDetailResponse resolve(TaskDetailResponse detail, boolean canSubmitAppeal) {
        return resolve(detail, canSubmitAppeal, AppTime.now());
    }

    TaskDetailResponse resolve(TaskDetailResponse detail, boolean canSubmitAppeal, LocalDateTime now) {
        boolean isOverdue = detail.getDeadline() != null && now.isAfter(detail.getDeadline());
        boolean canPeerReviewNow = false;
        String peerReviewPhase = "disabled";
        if (Boolean.TRUE.equals(detail.getEnablePeerReview())
                && detail.getDeadline() != null
                && detail.getPeerReviewDeadline() != null) {
            boolean reachedDeadline = !now.isBefore(detail.getDeadline());
            boolean beforePeerReviewDeadline = !now.isAfter(detail.getPeerReviewDeadline());
            canPeerReviewNow = reachedDeadline && beforePeerReviewDeadline;
            if (now.isBefore(detail.getDeadline())) {
                peerReviewPhase = "not_started";
            } else if (beforePeerReviewDeadline) {
                peerReviewPhase = "open";
            } else {
                peerReviewPhase = "closed";
            }
        }
        return TaskDetailResponse.builder()
                .taskId(detail.getTaskId())
                .taskUuid(detail.getTaskUuid())
                .classId(detail.getClassId())
                .name(detail.getName())
                .description(detail.getDescription())
                .deadline(detail.getDeadline())
                .enablePeerReview(detail.getEnablePeerReview())
                .peerReviewDeadline(detail.getPeerReviewDeadline())
                .peerReviewOffsetHours(detail.getPeerReviewOffsetHours())
                .peerReviewMaxScore(detail.getPeerReviewMaxScore())
                .peerReviewWeight(detail.getPeerReviewWeight())
                .teacherScoreWeight(detail.getTeacherScoreWeight())
                .status(detail.getStatus())
                .isOverdue(isOverdue)
                .canPeerReviewNow(canPeerReviewNow)
                .peerReviewPhase(peerReviewPhase)
                .canSubmitAppeal(canSubmitAppeal)
                .attachments(detail.getAttachments())
                .build();
    }
}
