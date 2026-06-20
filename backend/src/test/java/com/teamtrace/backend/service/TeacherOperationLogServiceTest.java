package com.teamtrace.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.teamtrace.backend.dto.audit.OperationLogPageResponse;
import com.teamtrace.backend.entity.ClassEntity;
import com.teamtrace.backend.entity.OperationLog;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.OperationLogRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TeacherOperationLogServiceTest {

    @Mock
    private OperationLogRepository operationLogRepository;

    @Mock
    private ClassRepository classRepository;

    @InjectMocks
    private TeacherOperationLogService teacherOperationLogService;

    @Test
    void listOwnLogsReturnsPage() {
        OperationLog row = new OperationLog();
        row.setId(1L);
        row.setUserId(2L);
        row.setRole("teacher");
        row.setAction("GET /api/x");
        row.setTargetType("http");
        row.setTargetId(0L);
        row.setHttpMethod("GET");
        row.setPath("/api/x");
        row.setHttpStatus(200);
        row.setDurationMs(5);
        row.setCreatedAt(LocalDateTime.of(2026, 4, 18, 12, 0));
        Page<OperationLog> page = new PageImpl<>(List.of(row));
        when(operationLogRepository.findByUserIdOrderByCreatedAtDesc(eq(2L), any(Pageable.class))).thenReturn(page);

        OperationLogPageResponse r = teacherOperationLogService.listOwnLogs(2L, 1, 20, null);
        assertEquals(1, r.getList().size());
        assertEquals("/api/x", r.getList().get(0).getPath());
        assertEquals(1L, r.getTotal());
        assertTrue(r.isHasNext() == false);
    }

    @Test
    void exportOwnLogsCsvContainsHeader() {
        when(operationLogRepository.findByUserIdOrderByCreatedAtDesc(eq(9L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        byte[] csv = teacherOperationLogService.exportOwnLogsCsv(9L, 10, null);
        String s = new String(csv, StandardCharsets.UTF_8);
        assertTrue(s.startsWith("id,createdAt,httpMethod,path,httpStatus,durationMs,role\n"));
    }

    @Test
    void listOwnLogsWhenClassIdUsesPathContainingQuery() {
        ClassEntity clazz = new ClassEntity();
        clazz.setId(10L);
        clazz.setTeacherId(2L);
        clazz.setIsDeleted(0);
        when(classRepository.findById(10L)).thenReturn(Optional.of(clazz));
        Page<OperationLog> page = new PageImpl<>(List.of());
        when(operationLogRepository.findByUserIdAndPathContainingOrderByCreatedAtDesc(
                        eq(2L), eq("/api/teacher/classes/10/"), any(Pageable.class)))
                .thenReturn(page);

        OperationLogPageResponse r = teacherOperationLogService.listOwnLogs(2L, 1, 20, 10L);
        assertEquals(0, r.getList().size());
    }

    @Test
    void listOwnLogsWhenNotOwnerOfClassForbidden() {
        ClassEntity clazz = new ClassEntity();
        clazz.setId(10L);
        clazz.setTeacherId(99L);
        clazz.setIsDeleted(0);
        when(classRepository.findById(10L)).thenReturn(Optional.of(clazz));

        assertThrows(BusinessException.class, () -> teacherOperationLogService.listOwnLogs(2L, 1, 20, 10L));
    }
}
