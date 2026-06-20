package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.publicapi.PublicStatsResponse;
import com.teamtrace.backend.service.PublicStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicStatsController {

    private final PublicStatsService publicStatsService;

    public PublicStatsController(PublicStatsService publicStatsService) {
        this.publicStatsService = publicStatsService;
    }

    @GetMapping("/stats")
    public ApiResponse<PublicStatsResponse> stats() {
        return ApiResponse.success(publicStatsService.getStats());
    }
}
