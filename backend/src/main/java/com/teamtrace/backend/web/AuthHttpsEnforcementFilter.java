package com.teamtrace.backend.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamtrace.backend.dto.common.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 密码传输层安全依赖 HTTPS/TLS，而不是前端再做一层固定密钥“加密”。
 * 本过滤器在非本地环境下强制认证接口走 HTTPS，兼容反向代理透传的 https 头。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthHttpsEnforcementFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH_PREFIX = "/api/auth/";

    private final ObjectMapper objectMapper;
    private final boolean requireHttpsForAuth;

    public AuthHttpsEnforcementFilter(
            ObjectMapper objectMapper,
            @Value("${teamtrace.security.require-https-for-auth:true}") boolean requireHttpsForAuth) {
        this.objectMapper = objectMapper;
        this.requireHttpsForAuth = requireHttpsForAuth;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!requireHttpsForAuth) {
            return true;
        }
        String requestUri = request.getRequestURI();
        if (requestUri == null || !requestUri.startsWith(AUTH_PATH_PREFIX)) {
            return true;
        }
        return isLocalRequest(request) || isSecureRequest(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.failure("HTTPS_REQUIRED", "认证接口仅允许通过 HTTPS 访问", null));
    }

    private boolean isLocalRequest(HttpServletRequest request) {
        String serverName = request.getServerName();
        if (serverName == null) {
            return false;
        }
        String normalized = serverName.trim().toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized)
                || "0:0:0:0:0:0:0:1".equals(normalized);
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (containsHttpsValue(forwardedProto)) {
            return true;
        }
        String forwarded = request.getHeader("Forwarded");
        if (forwarded != null && forwarded.toLowerCase(Locale.ROOT).contains("proto=https")) {
            return true;
        }
        String forwardedSsl = request.getHeader("X-Forwarded-Ssl");
        return "on".equalsIgnoreCase(forwardedSsl);
    }

    private boolean containsHttpsValue(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return false;
        }
        return Arrays.stream(headerValue.split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch("https"::equals);
    }
}
