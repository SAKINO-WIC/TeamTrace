package com.teamtrace.backend.dto.admin;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SystemAnnouncementPageResponse {
    private List<SystemAnnouncementResponse> items;
    private long totalElements;
    private int page;
    private int size;
}
