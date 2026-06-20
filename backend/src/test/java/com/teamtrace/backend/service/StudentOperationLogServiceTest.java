package com.teamtrace.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamtrace.backend.dto.audit.OperationLogPageResponse;
import com.teamtrace.backend.entity.OperationLog;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.OperationLogRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class StudentOperationLogServiceTest {

    @Mock
    private OperationLogRepository operationLogRepository;

    @Mock
    private ClassMembershipService classMembershipService;

    @InjectMocks
    private StudentOperationLogService studentOperationLogService;

    @Test
    void listOwnLogsReturnsPage() {
        OperationLog row = new OperationLog();
        row.setId(1L);
        row.setUserId(4L);
        row.setRole("student");
        row.setAction("GET /api/x");
        row.setTargetType("http");
        row.setTargetId(0L);
        row.setHttpMethod("GET");
        row.setPath("/api/x");
        row.setHttpStatus(200);
        row.setDurationMs(5);
        row.setCreatedAt(LocalDateTime.of(2026, 4, 18, 12, 0));
        Page<OperationLog> page = new PageImpl<>(List.of(row));
        when(operationLogRepository.findByUserIdOrderByCreatedAtDesc(eq(4L), any(Pageable.class))).thenReturn(page);

        OperationLogPageResponse r = studentOperationLogService.listOwnLogs(4L, 1, 20, null, null);
        assertEquals(1, r.getList().size());
        assertEquals("/api/x", r.getList().get(0).getPath());
        assertEquals(1L, r.getTotal());
        assertTrue(!r.isHasNext());
    }

    @Test
    void exportOwnLogsCsvContainsHeader() {
        when(operationLogRepository.findByUserIdOrderByCreatedAtDesc(eq(9L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        byte[] csv = studentOperationLogService.exportOwnLogsCsv(9L, 10, null, null);
        String s = new String(csv, StandardCharsets.UTF_8);
        assertTrue(s.startsWith("id,createdAt,httpMethod,path,httpStatus,durationMs,role\n"));
    }

    @Test
    void listOwnLogsWhenClassIdUsesPathContainingQuery() {
        Page<OperationLog> page = new PageImpl<>(List.of());
        when(operationLogRepository.searchForStudent(
                        eq(4L), eq("/api/student/classes/10/"), eq(null), any(Pageable.class)))
                .thenReturn(page);

        OperationLogPageResponse r = studentOperationLogService.listOwnLogs(4L, 1, 20, 10L, null);
        assertEquals(0, r.getList().size());
        verify(classMembershipService).requireActiveStudentInClass(4L, 10L);
    }

    @Test
    void listOwnLogsWhenPathContainsUsesSearchQuery() {
        Page<OperationLog> page = new PageImpl<>(List.of());
        when(operationLogRepository.searchForStudent(eq(4L), eq(null), eq("/student/tasks"), any(Pageable.class)))
                .thenReturn(page);

        OperationLogPageResponse r = studentOperationLogService.listOwnLogs(4L, 1, 20, null, " /student/tasks ");
        assertEquals(0, r.getList().size());
    }

    @Test
    void listOwnLogsWhenNotInClassForbidden() {
        doThrow(new BusinessException("FORBIDDEN", "你不在该班级中", HttpStatus.FORBIDDEN))
                .when(classMembershipService)
                .requireActiveStudentInClass(4L, 99L);

        assertThrows(BusinessException.class, () -> studentOperationLogService.listOwnLogs(4L, 1, 20, 99L, null));
    }
}
