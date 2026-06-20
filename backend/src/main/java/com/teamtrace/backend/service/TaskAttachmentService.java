package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.teacher.TaskAttachmentRequest;
import com.teamtrace.backend.dto.teacher.TaskAttachmentResponse;
import com.teamtrace.backend.entity.TaskAttachment;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.TaskAttachmentRepository;
import com.teamtrace.backend.util.AppTime;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TaskAttachmentService {

    private static final int MAX_ATTACHMENTS = 10;

    private final TaskAttachmentRepository taskAttachmentRepository;

    public TaskAttachmentService(TaskAttachmentRepository taskAttachmentRepository) {
        this.taskAttachmentRepository = taskAttachmentRepository;
    }

    public void createForTask(Long taskId, List<TaskAttachmentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        if (requests.size() > MAX_ATTACHMENTS) {
            throw new BusinessException("BAD_REQUEST", "任务附件最多 10 个", HttpStatus.BAD_REQUEST);
        }

        List<TaskAttachment> rows = new ArrayList<>();
        int sort = 0;
        for (TaskAttachmentRequest request : requests) {
            NormalizedAttachment normalized = normalize(request, sort);
            if (normalized == null) {
                continue;
            }
            TaskAttachment row = new TaskAttachment();
            row.setTaskId(taskId);
            row.setType(normalized.type());
            row.setName(normalized.name());
            row.setUrl(normalized.url());
            row.setSizeBytes(normalized.size());
            row.setSortOrder(sort);
            row.setIsDeleted(0);
            rows.add(row);
            sort += 1;
        }
        if (!rows.isEmpty()) {
            taskAttachmentRepository.saveAll(rows);
        }
    }

    public List<TaskAttachmentResponse> listResponses(Long taskId) {
        if (taskId == null) {
            return List.of();
        }
        return taskAttachmentRepository.findByTaskIdAndIsDeletedOrderBySortOrderAscIdAsc(taskId, 0).stream()
                .map(this::toResponse)
                .toList();
    }

    public void replaceForTask(Long taskId, List<TaskAttachmentRequest> requests) {
        if (taskId == null) {
            return;
        }
        List<TaskAttachment> existing = taskAttachmentRepository.findByTaskIdAndIsDeletedOrderBySortOrderAscIdAsc(taskId, 0);
        if (!existing.isEmpty()) {
            existing.forEach(row -> {
                row.setIsDeleted(1);
                row.setDeletedAt(AppTime.now());
            });
            taskAttachmentRepository.saveAll(existing);
        }
        createForTask(taskId, requests);
    }

    private TaskAttachmentResponse toResponse(TaskAttachment row) {
        return TaskAttachmentResponse.builder()
                .attachmentId(row.getId())
                .type(row.getType())
                .name(row.getName())
                .url(row.getUrl())
                .size(row.getSizeBytes())
                .sortOrder(row.getSortOrder())
                .build();
    }

    private NormalizedAttachment normalize(TaskAttachmentRequest request, int index) {
        if (request == null) {
            return null;
        }
        String url = safeTrim(request.getUrl());
        if (url.isEmpty()) {
            return null;
        }
        String type = safeTrim(request.getType()).toLowerCase();
        boolean uploadPath = isUploadPath(url);
        if (type.isEmpty()) {
            type = uploadPath ? TaskAttachment.TYPE_FILE : TaskAttachment.TYPE_LINK;
        }
        if (!TaskAttachment.TYPE_FILE.equals(type) && !TaskAttachment.TYPE_LINK.equals(type)) {
            throw new BusinessException("BAD_REQUEST", "附件类型仅支持 file 或 link", HttpStatus.BAD_REQUEST);
        }
        if (TaskAttachment.TYPE_FILE.equals(type) && !uploadPath) {
            throw new BusinessException("BAD_REQUEST", "文件附件必须来自系统上传地址", HttpStatus.BAD_REQUEST);
        }
        if (TaskAttachment.TYPE_LINK.equals(type) && !isHttpLink(url)) {
            throw new BusinessException("BAD_REQUEST", "链接附件仅支持 http 或 https", HttpStatus.BAD_REQUEST);
        }
        String name = safeTrim(request.getName());
        if (name.isEmpty()) {
            name = filenameFromUrl(url, index);
        }
        if (name.length() > 255) {
            name = name.substring(0, 255);
        }
        Long size = request.getSize();
        if (size != null && size < 0) {
            size = null;
        }
        return new NormalizedAttachment(type, name, normalizeUploadPath(url), size);
    }

    private static boolean isUploadPath(String url) {
        return url.startsWith("/uploads/") || url.startsWith("uploads/");
    }

    private static String normalizeUploadPath(String url) {
        if (url.startsWith("uploads/")) {
            return "/" + url;
        }
        return url;
    }

    private static boolean isHttpLink(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static String filenameFromUrl(String url, int index) {
        String source = url;
        int query = source.indexOf('?');
        if (query >= 0) {
            source = source.substring(0, query);
        }
        int slash = source.lastIndexOf('/');
        String name = slash >= 0 ? source.substring(slash + 1) : source;
        return name.isBlank() ? "附件 " + (index + 1) : name;
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private record NormalizedAttachment(String type, String name, String url, Long size) {
    }
}
