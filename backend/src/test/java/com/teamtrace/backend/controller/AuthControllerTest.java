package com.teamtrace.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamtrace.backend.dto.auth.AuthResponse;
import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.service.AuthService;
import com.teamtrace.backend.service.VerificationCodeService;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthControllerTest {

    @Test
    void shouldLogoutSuccessfully() {
        AuthService authService = mock(AuthService.class);
        AuthController authController = new AuthController(authService, mock(VerificationCodeService.class));
        String authorization = "Bearer token-value";

        ApiResponse<Map<String, String>> response = authController.logout(authorization);

        assertTrue(response.isSuccess());
        assertEquals("OK", response.getCode());
        assertEquals("退出成功", response.getData().get("message"));
        verify(authService).logout(authorization);
    }

    @Test
    void shouldReturnRefreshPayloadFromService() {
        AuthService authService = mock(AuthService.class);
        AuthController authController = new AuthController(authService, mock(VerificationCodeService.class));
        AuthResponse refreshed = AuthResponse.builder()
                .token("new-token")
                .role("student")
                .user(null)
                .build();
        when(authService.refreshToken("Bearer test-refresh-token")).thenReturn(refreshed);

        ApiResponse<AuthResponse> response = authController.refresh("Bearer test-refresh-token");

        assertTrue(response.isSuccess());
        assertEquals("OK", response.getCode());
        assertEquals("new-token", response.getData().getToken());
    }
}
