package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.audit.OperationLogItemResponse;
import com.teamtrace.backend.dto.audit.OperationLogPageResponse;
import com.teamtrace.backend.entity.OperationLog;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.operationlog.OperationLogRowMapper;
import com.teamtrace.backend.repository.OperationLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOperationLogService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int EXPORT_MAX_ROWS = 5000;

    private final OperationLogRepository operationLogRepository;

    public AdminOperationLogService(OperationLogRepository operationLogRepository) {
        this.operationLogRepository = operationLogRepository;
    }

    @Transactional(readOnly = true)
    public OperationLogPageResponse listLogs(
            int page,
            int size,
            Long userId,
            String role,
            String pathContains,
            LocalDateTime createdFrom,
            LocalDateTime createdTo
    ) {
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new BusinessException("BAD_REQUEST", "createdFrom/createdTo范围无效", HttpStatus.BAD_REQUEST);
        }
        int p = Math.max(1, page);
        int s = Math.min(MAX_PAGE_SIZE, Math.max(1, size <= 0 ? DEFAULT_PAGE_SIZE : size));
        PageRequest pageable = PageRequest.of(p - 1, s, Sort.by(Sort.Direction.DESC, "createdAt"));

        String roleFilter = (role == null || role.isBlank()) ? null : role.trim();
        String pathFilter = (pathContains == null || pathContains.isBlank()) ? null : pathContains.trim();

        Page<OperationLog> pg = operationLogRepository.searchForAdmin(
                userId, roleFilter, pathFilter, createdFrom, createdTo, pageable);

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
    public byte[] exportLogsCsv(
            int limit,
            Long userId,
            String role,
            String pathContains,
            LocalDateTime createdFrom,
            LocalDateTime createdTo
    ) {
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new BusinessException("BAD_REQUEST", "createdFrom/createdTo范围无效", HttpStatus.BAD_REQUEST);
        }
        int n = Math.min(EXPORT_MAX_ROWS, Math.max(1, limit));
        PageRequest pageable = PageRequest.of(0, n, Sort.by(Sort.Direction.DESC, "createdAt"));

        String roleFilter = (role == null || role.isBlank()) ? null : role.trim();
        String pathFilter = (pathContains == null || pathContains.isBlank()) ? null : pathContains.trim();
        Page<OperationLog> pg = operationLogRepository.searchForAdmin(
                userId, roleFilter, pathFilter, createdFrom, createdTo, pageable);
        return OperationLogRowMapper.toCsvBytes(pg.getContent());
    }
}

