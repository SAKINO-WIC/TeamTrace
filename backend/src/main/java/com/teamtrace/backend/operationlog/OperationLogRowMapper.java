package com.teamtrace.backend.operationlog;

import com.teamtrace.backend.dto.audit.OperationLogItemResponse;
import com.teamtrace.backend.entity.OperationLog;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 将 {@link OperationLog} 实体映射为 API / CSV 共用结构。 */
public final class OperationLogRowMapper {

    private OperationLogRowMapper() {}

    public static OperationLogItemResponse toItem(OperationLog e) {
        return OperationLogItemResponse.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .role(e.getRole())
                .action(e.getAction())
                .targetType(e.getTargetType())
                .targetId(e.getTargetId())
                .httpMethod(e.getHttpMethod())
                .path(e.getPath())
                .queryString(e.getQueryString())
                .ip(e.getIp())
                .userAgent(e.getUserAgent())
                .httpStatus(e.getHttpStatus())
                .durationMs(e.getDurationMs())
                .createdAt(e.getCreatedAt())
                .build();
    }

    public static byte[] toCsvBytes(List<OperationLog> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,createdAt,httpMethod,path,httpStatus,durationMs,role\n");
        for (OperationLog row : rows) {
            sb.append(row.getId())
                    .append(',')
                    .append(row.getCreatedAt() != null ? row.getCreatedAt().toString() : "")
                    .append(',')
                    .append(csvEscape(row.getHttpMethod()))
                    .append(',')
                    .append(csvEscape(row.getPath()))
                    .append(',')
                    .append(row.getHttpStatus() != null ? row.getHttpStatus() : "")
                    .append(',')
                    .append(row.getDurationMs() != null ? row.getDurationMs() : "")
                    .append(',')
                    .append(csvEscape(row.getRole()))
                    .append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String csvEscape(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\"", "\"\"");
        if (t.indexOf(',') >= 0 || t.indexOf('\n') >= 0 || t.indexOf('\r') >= 0 || t.indexOf('"') >= 0) {
            return "\"" + t + "\"";
        }
        return t;
    }
}
