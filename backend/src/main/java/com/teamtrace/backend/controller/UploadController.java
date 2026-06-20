package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.UploadService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/common")
public class UploadController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UploadService uploadService;

    public UploadController(JwtTokenProvider jwtTokenProvider, UploadService uploadService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.uploadService = uploadService;
    }

    @PostMapping("/uploads")
    public ApiResponse<Map<String, String>> upload(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("file") MultipartFile file) {
        requireLoggedIn(authorization);
        String url = uploadService.store(file);
        return ApiResponse.success(Map.of("url", url));
    }

    private void requireLoggedIn(String authorization) {
        String token = jwtTokenProvider.resolveBearerToken(authorization);
        if (token == null || !jwtTokenProvider.isValid(token)) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期", HttpStatus.UNAUTHORIZED);
        }
    }
}
