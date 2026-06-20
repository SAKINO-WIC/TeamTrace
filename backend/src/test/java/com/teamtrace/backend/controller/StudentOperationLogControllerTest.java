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
import com.teamtrace.backend.service.StudentOperationLogService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class StudentOperationLogControllerTest {

    @Test
    void listReturnsServicePageWhenStudentToken() {
        JwtTokenProvider jwt = Mockito.mock(JwtTokenProvider.class);
        StudentOperationLogService svc = Mockito.mock(StudentOperationLogService.class);
        when(jwt.resolveBearerToken("Bearer x")).thenReturn("tok");
        when(jwt.isValid("tok")).thenReturn(true);
        when(jwt.extractRole("tok")).thenReturn("student");
        when(jwt.extractUserId("tok")).thenReturn(4L);
        OperationLogPageResponse page =
                OperationLogPageResponse.builder()
                        .list(List.of())
                        .page(1)
                        .size(20)
                        .total(0)
                        .pages(0)
                        .hasNext(false)
                        .build();
        when(svc.listOwnLogs(4L, 1, 20, null, null)).thenReturn(page);

        StudentOperationLogController c = new StudentOperationLogController(jwt, svc);
        ApiResponse<OperationLogPageResponse> r = c.list("Bearer x", 1, 20, null, null);

        assertTrue(r.isSuccess());
        assertEquals(0, r.getData().getTotal());
        verify(svc).listOwnLogs(4L, 1, 20, null, null);
    }

    @Test
    void listForbiddenWhenRoleNotStudent() {
        JwtTokenProvider jwt = Mockito.mock(JwtTokenProvider.class);
        StudentOperationLogService svc = Mockito.mock(StudentOperationLogService.class);
        when(jwt.resolveBearerToken("Bearer x")).thenReturn("tok");
        when(jwt.isValid("tok")).thenReturn(true);
        when(jwt.extractRole("tok")).thenReturn("teacher");

        StudentOperationLogController c = new StudentOperationLogController(jwt, svc);
        BusinessException ex = assertThrows(BusinessException.class, () -> c.list("Bearer x", 1, 20, null, null));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void exportCsvReturnsBodyFromService() {
        JwtTokenProvider jwt = Mockito.mock(JwtTokenProvider.class);
        StudentOperationLogService svc = Mockito.mock(StudentOperationLogService.class);
        when(jwt.resolveBearerToken("Bearer x")).thenReturn("tok");
        when(jwt.isValid("tok")).thenReturn(true);
        when(jwt.extractRole("tok")).thenReturn("student");
        when(jwt.extractUserId("tok")).thenReturn(4L);
        byte[] csv = new byte[] {98};
        when(svc.exportOwnLogsCsv(eq(4L), eq(10), any(), any())).thenReturn(csv);

        StudentOperationLogController c = new StudentOperationLogController(jwt, svc);
        ResponseEntity<byte[]> res = c.exportCsv("Bearer x", 10, null, null);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertArrayEquals(csv, res.getBody());
        verify(svc).exportOwnLogsCsv(4L, 10, null, null);
    }
}
