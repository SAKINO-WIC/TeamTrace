package com.teamtrace.backend.dto.admin;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminEmailSendResponse {
    private String recipientScope;
    private String subject;
    private int requestedCount;
    private int sentCount;
    private int failedCount;
    private int skippedCount;
    private List<FailureItem> failures;

    @Getter
    @Builder
    public static class FailureItem {
        private String email;
        private String reason;
    }
}
