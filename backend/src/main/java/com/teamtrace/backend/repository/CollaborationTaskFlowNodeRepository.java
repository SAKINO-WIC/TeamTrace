package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.CollaborationTaskFlowNode;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollaborationTaskFlowNodeRepository extends JpaRepository<CollaborationTaskFlowNode, Long> {
    List<CollaborationTaskFlowNode> findByTaskIdOrderByStepOrderAsc(Long taskId);
    List<CollaborationTaskFlowNode> findByTaskIdInOrderByTaskIdAscStepOrderAsc(Collection<Long> taskIds);
    Optional<CollaborationTaskFlowNode> findByIdAndTaskId(Long id, Long taskId);
    Optional<CollaborationTaskFlowNode> findFirstByTaskIdAndStatusInOrderByStepOrderAsc(Long taskId, Collection<String> statuses);
    long countByTaskId(Long taskId);
    void deleteByTaskId(Long taskId);
}
