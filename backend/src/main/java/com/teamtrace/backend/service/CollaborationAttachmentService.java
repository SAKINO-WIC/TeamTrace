package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.freecollab.CollaborationAttachmentRequest;
import com.teamtrace.backend.dto.freecollab.CollaborationAttachmentResponse;
import com.teamtrace.backend.entity.CollaborationAttachment;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.CollaborationAttachmentRepository;
import com.teamtrace.backend.util.AppTime;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CollaborationAttachmentService {

    private static final int NOT_DELETED = 0;
    private static final int MAX_ATTACHMENTS = 10;

    private final CollaborationAttachmentRepository attachmentRepository;

    public CollaborationAttachmentService(CollaborationAttachmentRepository attachmentRepository) {
        this.attachmentRepository = attachmentRepository;
    }

    public void createForProject(Long spaceId, Long projectId, List<CollaborationAttachmentRequest> requests) {
        create(spaceId, projectId, null, CollaborationAttachment.TARGET_PROJECT, projectId, requests);
    }

    public void createForTask(Long spaceId, Long projectId, Long taskId, List<CollaborationAttachmentRequest> requests) {
        create(spaceId, projectId, taskId, CollaborationAttachment.TARGET_TASK, taskId, requests);
    }

    public void replaceForProject(Long spaceId, Long projectId, List<CollaborationAttachmentRequest> requests) {
        replace(CollaborationAttachment.TARGET_PROJECT, projectId);
        createForProject(spaceId, projectId, requests);
    }

    public void replaceForTask(Long spaceId, Long projectId, Long taskId, List<CollaborationAttachmentRequest> requests) {
        replace(CollaborationAttachment.TARGET_TASK, taskId);
        createForTask(spaceId, projectId, taskId, requests);
    }

    public List<CollaborationAttachmentResponse> listProjectResponses(Long projectId) {
        return listResponses(CollaborationAttachment.TARGET_PROJECT, projectId);
    }

    public List<CollaborationAttachmentResponse> listTaskResponses(Long taskId) {
        return listResponses(CollaborationAttachment.TARGET_TASK, taskId);
    }

    public Map<Long, List<CollaborationAttachmentResponse>> listProjectResponsesByTargetId(Collection<Long> projectIds) {
        return listResponsesByTargetId(CollaborationAttachment.TARGET_PROJECT, projectIds);
    }

    public Map<Long, List<CollaborationAttachmentResponse>> listTaskResponsesByTargetId(Collection<Long> taskIds) {
        return listResponsesByTargetId(CollaborationAttachment.TARGET_TASK, taskIds);
    }

    private void create(
            Long spaceId,
            Long projectId,
            Long taskId,
            String targetType,
            Long targetId,
            List<CollaborationAttachmentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        if (requests.size() > MAX_ATTACHMENTS) {
            throw new BusinessException("BAD_REQUEST", "附件最多 10 个", HttpStatus.BAD_REQUEST);
        }

        List<CollaborationAttachment> rows = new ArrayList<>();
        int sort = 0;
        for (CollaborationAttachmentRequest request : requests) {
            NormalizedAttachment normalized = normalize(request, sort);
            if (normalized == null) {
                continue;
            }
            CollaborationAttachment row = new CollaborationAttachment();
            row.setSpaceId(spaceId);
            row.setProjectId(projectId);
            row.setTaskId(taskId);
            row.setTargetType(targetType);
            row.setTargetId(targetId);
            row.setType(normalized.type());
            row.setName(normalized.name());
            row.setUrl(normalized.url());
            row.setSizeBytes(normalized.size());
            row.setSortOrder(sort);
            row.setIsDeleted(NOT_DELETED);
            rows.add(row);
            sort += 1;
        }
        if (!rows.isEmpty()) {
            attachmentRepository.saveAll(rows);
        }
    }

    private void replace(String targetType, Long targetId) {
        List<CollaborationAttachment> existing = attachmentRepository
                .findByTargetTypeAndTargetIdAndIsDeletedOrderBySortOrderAscIdAsc(targetType, targetId, NOT_DELETED);
        if (existing.isEmpty()) {
            return;
        }
        existing.forEach(row -> {
            row.setIsDeleted(1);
            row.setDeletedAt(AppTime.now());
        });
        attachmentRepository.saveAll(existing);
    }

    private List<CollaborationAttachmentResponse> listResponses(String targetType, Long targetId) {
        if (targetId == null) {
            return List.of();
        }
        return attachmentRepository
                .findByTargetTypeAndTargetIdAndIsDeletedOrderBySortOrderAscIdAsc(targetType, targetId, NOT_DELETED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private Map<Long, List<CollaborationAttachmentResponse>> listResponsesByTargetId(
            String targetType,
            Collection<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return Map.of();
        }
        return attachmentRepository
                .findByTargetTypeAndTargetIdInAndIsDeletedOrderByTargetIdAscSortOrderAscIdAsc(
                        targetType,
                        targetIds,
                        NOT_DELETED)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.groupingBy(
                        CollaborationAttachmentResponse::getTargetId,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    private CollaborationAttachmentResponse toResponse(CollaborationAttachment row) {
        return CollaborationAttachmentResponse.builder()
                .attachmentId(row.getId())
                .targetType(row.getTargetType())
                .targetId(row.getTargetId())
                .type(row.getType())
                .name(row.getName())
                .url(row.getUrl())
                .size(row.getSizeBytes())
                .sortOrder(row.getSortOrder())
                .build();
    }

    private NormalizedAttachment normalize(CollaborationAttachmentRequest request, int index) {
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
            type = uploadPath ? CollaborationAttachment.TYPE_FILE : CollaborationAttachment.TYPE_LINK;
        }
        if (!CollaborationAttachment.TYPE_FILE.equals(type) && !CollaborationAttachment.TYPE_LINK.equals(type)) {
            throw new BusinessException("BAD_REQUEST", "附件类型仅支持 file 或 link", HttpStatus.BAD_REQUEST);
        }
        if (CollaborationAttachment.TYPE_FILE.equals(type) && !uploadPath) {
            throw new BusinessException("BAD_REQUEST", "文件附件必须来自系统上传地址", HttpStatus.BAD_REQUEST);
        }
        if (CollaborationAttachment.TYPE_LINK.equals(type) && !isHttpLink(url)) {
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
