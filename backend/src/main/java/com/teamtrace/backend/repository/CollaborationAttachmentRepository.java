package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.CollaborationAttachment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollaborationAttachmentRepository extends JpaRepository<CollaborationAttachment, Long> {

    List<CollaborationAttachment> findByTargetTypeAndTargetIdAndIsDeletedOrderBySortOrderAscIdAsc(
            String targetType,
            Long targetId,
            Integer isDeleted);

    List<CollaborationAttachment> findByTargetTypeAndTargetIdInAndIsDeletedOrderByTargetIdAscSortOrderAscIdAsc(
            String targetType,
            Collection<Long> targetIds,
            Integer isDeleted);
}
