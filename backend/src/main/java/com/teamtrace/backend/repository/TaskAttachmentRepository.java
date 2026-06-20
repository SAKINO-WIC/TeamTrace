package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.TaskAttachment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {

    List<TaskAttachment> findByTaskIdAndIsDeletedOrderBySortOrderAscIdAsc(Long taskId, Integer isDeleted);

    List<TaskAttachment> findByTaskIdInAndIsDeleted(Collection<Long> taskIds, Integer isDeleted);
}
