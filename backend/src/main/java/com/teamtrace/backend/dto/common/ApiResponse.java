package com.teamtrace.backend.dto.common;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ApiResponse<T> {
    private boolean success;
    private String code;
    private String message;
    private T data;
    private String traceId;
    private String timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code("OK")
                .message("操作成功")
                .data(data)
                .traceId("")
                .timestamp(OffsetDateTime.now().toString())
                .build();
    }

    public static <T> ApiResponse<T> failure(String code, String message, T data) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .data(data)
                .traceId("")
                .timestamp(OffsetDateTime.now().toString())
                .build();
    }
}
