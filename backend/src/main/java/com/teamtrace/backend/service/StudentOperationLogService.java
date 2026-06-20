package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.audit.OperationLogItemResponse;
import com.teamtrace.backend.dto.audit.OperationLogPageResponse;
import com.teamtrace.backend.entity.OperationLog;
import com.teamtrace.backend.operationlog.OperationLogRowMapper;
import com.teamtrace.backend.repository.OperationLogRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentOperationLogService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int EXPORT_MAX_ROWS = 2000;

    private final OperationLogRepository operationLogRepository;
    private final ClassMembershipService classMembershipService;

    public StudentOperationLogService(
            OperationLogRepository operationLogRepository, ClassMembershipService classMembershipService) {
        this.operationLogRepository = operationLogRepository;
        this.classMembershipService = classMembershipService;
    }

    @Transactional(readOnly = true)
    public OperationLogPageResponse listOwnLogs(
            Long studentUserId, int page, int size, Long classId, String pathContains) {
        int p = Math.max(1, page);
        int s = Math.min(MAX_PAGE_SIZE, Math.max(1, size <= 0 ? DEFAULT_PAGE_SIZE : size));
        Page<OperationLog> pg = pageOwnLogs(studentUserId, classId, pathContains, PageRequest.of(p - 1, s));
        List<OperationLogItemResponse> list =
                pg.getContent().stream().map(OperationLogRowMapper::toItem).collect(Collectors.toList());
        return OperationLogPageResponse.builder()
                .list(list)
                .page(p)
                .size(s)
                .total(pg.getTotalElements())
                .pages(pg.getTotalPages())
                .hasNext(pg.hasNext())
                .build();
    }

    @Transactional(readOnly = true)
    public byte[] exportOwnLogsCsv(Long studentUserId, int limit, Long classId, String pathContains) {
        int n = Math.min(EXPORT_MAX_ROWS, Math.max(1, limit));
        Page<OperationLog> pg = pageOwnLogs(studentUserId, classId, pathContains, PageRequest.of(0, n));
        return OperationLogRowMapper.toCsvBytes(pg.getContent());
    }

    private Page<OperationLog> pageOwnLogs(
            Long studentUserId, Long classId, String pathContains, Pageable pageable) {
        String normalizedPathContains = normalizePathContains(pathContains);
        if (classId == null && normalizedPathContains == null) {
            return operationLogRepository.findByUserIdOrderByCreatedAtDesc(studentUserId, pageable);
        }
        String classPathPrefix = null;
        if (classId != null) {
            classMembershipService.requireActiveStudentInClass(studentUserId, classId);
            classPathPrefix = studentClassPathPrefix(classId);
        }
        return operationLogRepository.searchForStudent(
                studentUserId, classPathPrefix, normalizedPathContains, pageable);
    }

    private static String studentClassPathPrefix(Long classId) {
        return "/api/student/classes/" + classId + "/";
    }

    private static String normalizePathContains(String pathContains) {
        if (pathContains == null) {
            return null;
        }
        String trimmed = pathContains.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
