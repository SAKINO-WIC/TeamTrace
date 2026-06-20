package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.audit.OperationLogItemResponse;
import com.teamtrace.backend.dto.audit.OperationLogPageResponse;
import com.teamtrace.backend.entity.ClassEntity;
import com.teamtrace.backend.entity.OperationLog;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.operationlog.OperationLogRowMapper;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.OperationLogRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeacherOperationLogService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int EXPORT_MAX_ROWS = 2000;

    private final OperationLogRepository operationLogRepository;
    private final ClassRepository classRepository;

    public TeacherOperationLogService(
            OperationLogRepository operationLogRepository, ClassRepository classRepository) {
        this.operationLogRepository = operationLogRepository;
        this.classRepository = classRepository;
    }

    @Transactional(readOnly = true)
    public OperationLogPageResponse listOwnLogs(Long teacherUserId, int page, int size, Long classId) {
        int p = Math.max(1, page);
        int s = Math.min(MAX_PAGE_SIZE, Math.max(1, size <= 0 ? DEFAULT_PAGE_SIZE : size));
        Page<OperationLog> pg = pageOwnLogs(teacherUserId, classId, PageRequest.of(p - 1, s));
        List<OperationLogItemResponse> list =
                pg.getContent().stream().map(OperationLogRowMapper::toItem).collect(Collectors.toList());
        long total = pg.getTotalElements();
        long pages = pg.getTotalPages();
        return OperationLogPageResponse.builder()
                .list(list)
                .page(p)
                .size(s)
                .total(total)
                .pages(pages)
                .hasNext(pg.hasNext())
                .build();
    }

    /**
     * 导出本人最近若干条审计为 CSV（UTF-8），列含 id、时间、方法、路径、状态码、耗时、角色。
     *
     * @param limit 最大行数，超过 {@value #EXPORT_MAX_ROWS} 时按上限截断
     */
    @Transactional(readOnly = true)
    public byte[] exportOwnLogsCsv(Long teacherUserId, int limit, Long classId) {
        int n = Math.min(EXPORT_MAX_ROWS, Math.max(1, limit));
        Page<OperationLog> pg = pageOwnLogs(teacherUserId, classId, PageRequest.of(0, n));
        return OperationLogRowMapper.toCsvBytes(pg.getContent());
    }

    private Page<OperationLog> pageOwnLogs(Long teacherUserId, Long classId, Pageable pageable) {
        if (classId == null) {
            return operationLogRepository.findByUserIdOrderByCreatedAtDesc(teacherUserId, pageable);
        }
        requireTeacherOwnsClass(teacherUserId, classId);
        String fragment = teacherClassPathPrefix(classId);
        return operationLogRepository.findByUserIdAndPathContainingOrderByCreatedAtDesc(
                teacherUserId, fragment, pageable);
    }

    private void requireTeacherOwnsClass(Long teacherUserId, Long classId) {
        ClassEntity clazz =
                classRepository
                        .findById(classId)
                        .orElseThrow(() -> new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND));
        if (clazz.getIsDeleted() != null && clazz.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND);
        }
        if (!teacherUserId.equals(clazz.getTeacherId())) {
            throw new BusinessException("FORBIDDEN", "无权限查看该班级的操作日志", HttpStatus.FORBIDDEN);
        }
    }

    private static String teacherClassPathPrefix(Long classId) {
        return "/api/teacher/classes/" + classId + "/";
    }
}
