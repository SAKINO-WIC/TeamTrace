package com.teamtrace.backend.dto.auth;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CeremonyInfoResponse {
    private Long ceremonyNo;
    private String ceremonyCode;
    private String title;
    private String subtitle;
    private boolean shouldShow;
}
