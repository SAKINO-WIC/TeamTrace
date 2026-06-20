package com.teamtrace.backend.dto.admin;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TeacherInviteCodePageResponse {
    private List<TeacherInviteCodeListItemResponse> list;
    private Integer page;
    private Integer size;
    private Long total;
    private Integer pages;
    private Boolean hasNext;
}

