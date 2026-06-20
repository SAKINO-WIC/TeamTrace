package com.teamtrace.backend.dto.admin;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserPageResponse {
    private List<UserListItemResponse> list;
    private int page;
    private int size;
    private long total;
    private long pages;
    private boolean hasNext;
}

