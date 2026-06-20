package com.teamtrace.backend.dto.admin;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WelcomeEmailResendResponse {
    private int requestedCount;
    private int sentCount;
    private int failedCount;
    private long remainingCount;
    private List<FailureItem> failures;

    @Getter
    @Builder
    public static class FailureItem {
        private Long userId;
        private String email;
        private String reason;
    }
}
