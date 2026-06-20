package com.teamtrace.backend.dto.audit;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OperationLogPageResponse {
    private List<OperationLogItemResponse> list;
    private int page;
    private int size;
    private long total;
    private long pages;
    private boolean hasNext;
}
