package com.teamtrace.backend.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamtrace.backend.dto.audit.OperationLogPageResponse;
import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.TeacherOperationLogService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TeacherOperationLogControllerTest {

    @Test
    void listReturnsServicePageWhenTeacherToken() {
        JwtTokenProvider jwt = Mockito.mock(JwtTokenProvider.class);
        TeacherOperationLogService svc = Mockito.mock(TeacherOperationLogService.class);
        when(jwt.resolveBearerToken("Bearer x")).thenReturn("tok");
        when(jwt.isValid("tok")).thenReturn(true);
        when(jwt.extractRole("tok")).thenReturn("teacher");
        when(jwt.extractUserId("tok")).thenReturn(2L);
        OperationLogPageResponse page =
                OperationLogPageResponse.builder()
                        .list(List.of())
                        .page(1)
                        .size(20)
                        .total(0)
                        .pages(0)
                        .hasNext(false)
                        .build();
        when(svc.listOwnLogs(2L, 1, 20, null)).thenReturn(page);

        TeacherOperationLogController c = new TeacherOperationLogController(jwt, svc);
        ApiResponse<OperationLogPageResponse> r = c.list("Bearer x", 1, 20, null);

        assertTrue(r.isSuccess());
        assertEquals(0, r.getData().getTotal());
        verify(svc).listOwnLogs(2L, 1, 20, null);
    }

    @Test
    void listForbiddenWhenRoleNotTeacher() {
        JwtTokenProvider jwt = Mockito.mock(JwtTokenProvider.class);
        TeacherOperationLogService svc = Mockito.mock(TeacherOperationLogService.class);
        when(jwt.resolveBearerToken("Bearer x")).thenReturn("tok");
        when(jwt.isValid("tok")).thenReturn(true);
        when(jwt.extractRole("tok")).thenReturn("student");

        TeacherOperationLogController c = new TeacherOperationLogController(jwt, svc);
        BusinessException ex = assertThrows(BusinessException.class, () -> c.list("Bearer x", 1, 20, null));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void exportCsvReturnsBodyFromService() {
        JwtTokenProvider jwt = Mockito.mock(JwtTokenProvider.class);
        TeacherOperationLogService svc = Mockito.mock(TeacherOperationLogService.class);
        when(jwt.resolveBearerToken("Bearer x")).thenReturn("tok");
        when(jwt.isValid("tok")).thenReturn(true);
        when(jwt.extractRole("tok")).thenReturn("teacher");
        when(jwt.extractUserId("tok")).thenReturn(2L);
        byte[] csv = new byte[] {97};
        when(svc.exportOwnLogsCsv(eq(2L), eq(10), any())).thenReturn(csv);

        TeacherOperationLogController c = new TeacherOperationLogController(jwt, svc);
        ResponseEntity<byte[]> res = c.exportCsv("Bearer x", 10, null);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertArrayEquals(csv, res.getBody());
        verify(svc).exportOwnLogsCsv(2L, 10, null);
    }
}
