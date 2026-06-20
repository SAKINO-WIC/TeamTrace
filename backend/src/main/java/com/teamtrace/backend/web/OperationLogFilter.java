package com.teamtrace.backend.web;

import com.teamtrace.backend.entity.OperationLog;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.OperationLogService;
import com.teamtrace.backend.util.AppTime;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 记录 HTTP 请求元数据（路径、方法、用户、耗时、状态码），异步写入 {@code operation_logs}，**不记录请求体**。
 * 由 {@link com.teamtrace.backend.autoconfigure.OperationAuditAutoConfiguration} 注册为 Filter Bean。
 */
public class OperationLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OperationLogFilter.class);

    private static final int MAX_QS = 512;
    private static final int MAX_UA = 512;
    private static final int MAX_ACTION = 50;
    private static final int MAX_TARGET_TYPE = 50;
    private static final String HTTP_AUDIT_TARGET_TYPE = "http";
    private static final long HTTP_AUDIT_TARGET_ID = 0L;
    private static final String ANONYMOUS_ROLE = "anonymous";

    private final JwtTokenProvider jwtTokenProvider;
    private final OperationLogService operationLogService;

    public OperationLogFilter(JwtTokenProvider jwtTokenProvider, OperationLogService operationLogService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.operationLogService = operationLogService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String m = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(m)) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri.startsWith("/api/health") || uri.startsWith("/error");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startNs = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long ms = Math.min(Integer.MAX_VALUE, (System.nanoTime() - startNs) / 1_000_000L);
            OperationLog row = buildRow(request, response, (int) ms);
            // Write audit log asynchronously to avoid blocking the request thread
            CompletableFuture.runAsync(() -> {
                try {
                    operationLogService.record(row);
                } catch (Exception ex) {
                    log.warn("async operation log failed: {}", ex.getMessage());
                }
            });
        }
    }

    private OperationLog buildRow(HttpServletRequest request, HttpServletResponse response, int durationMs) {
        OperationLog log = new OperationLog();
        log.setHttpMethod(trunc(request.getMethod(), 16));
        log.setPath(trunc(request.getRequestURI(), 512));
        log.setAction(trunc(request.getMethod() + " " + request.getRequestURI(), MAX_ACTION));
        log.setTargetType(trunc(HTTP_AUDIT_TARGET_TYPE, MAX_TARGET_TYPE));
        log.setTargetId(HTTP_AUDIT_TARGET_ID);
        String qs = request.getQueryString();
        log.setQueryString(qs == null ? null : trunc(qs, MAX_QS));
        log.setIp(trunc(clientIp(request), 64));
        String ua = request.getHeader("User-Agent");
        log.setUserAgent(ua == null ? null : trunc(ua, MAX_UA));
        log.setHttpStatus(response.getStatus());
        log.setDurationMs(durationMs);
        log.setCreatedAt(AppTime.now());

        String auth = request.getHeader("Authorization");
        String token = jwtTokenProvider.resolveBearerToken(auth);
        if (token != null && jwtTokenProvider.isValid(token)) {
            log.setUserId(jwtTokenProvider.extractUserId(token));
            log.setRole(trunc(jwtTokenProvider.extractRole(token), 32));
        } else {
            log.setUserId(null);
            log.setRole(ANONYMOUS_ROLE);
        }
        return log;
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(",");
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return request.getRemoteAddr();
    }

    private static String trunc(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
