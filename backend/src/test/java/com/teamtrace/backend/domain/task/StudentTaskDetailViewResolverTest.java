package com.teamtrace.backend.domain.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.teamtrace.backend.dto.teacher.TaskDetailResponse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class StudentTaskDetailViewResolverTest {

    private final StudentTaskDetailViewResolver resolver = new StudentTaskDetailViewResolver();

    @Test
    void resolveReturnsDisabledWhenPeerReviewOff() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 23, 12, 0);
        TaskDetailResponse detail = TaskDetailResponse.builder()
                .taskId(1L)
                .deadline(now.minusHours(1))
                .enablePeerReview(false)
                .build();

        TaskDetailResponse result = resolver.resolve(detail, true, now);

        assertTrue(Boolean.TRUE.equals(result.getIsOverdue()));
        assertFalse(Boolean.TRUE.equals(result.getCanPeerReviewNow()));
        assertEquals("disabled", result.getPeerReviewPhase());
        assertTrue(Boolean.TRUE.equals(result.getCanSubmitAppeal()));
    }

    @Test
    void resolveReturnsOpenDuringPeerReviewWindow() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 23, 12, 0);
        TaskDetailResponse detail = TaskDetailResponse.builder()
                .taskId(2L)
                .deadline(now.minusMinutes(30))
                .enablePeerReview(true)
                .peerReviewDeadline(now.plusMinutes(30))
                .build();

        TaskDetailResponse result = resolver.resolve(detail, false, now);

        assertTrue(Boolean.TRUE.equals(result.getCanPeerReviewNow()));
        assertEquals("open", result.getPeerReviewPhase());
        assertFalse(Boolean.TRUE.equals(result.getCanSubmitAppeal()));
    }

    @Test
    void resolveReturnsClosedAfterPeerReviewDeadline() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 23, 12, 0);
        TaskDetailResponse detail = TaskDetailResponse.builder()
                .taskId(3L)
                .deadline(now.minusHours(2))
                .enablePeerReview(true)
                .peerReviewDeadline(now.minusMinutes(1))
                .build();

        TaskDetailResponse result = resolver.resolve(detail, true, now);

        assertFalse(Boolean.TRUE.equals(result.getCanPeerReviewNow()));
        assertEquals("closed", result.getPeerReviewPhase());
    }
}
