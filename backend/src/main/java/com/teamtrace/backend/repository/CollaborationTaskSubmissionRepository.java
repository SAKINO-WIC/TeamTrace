package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.CollaborationTaskSubmission;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollaborationTaskSubmissionRepository extends JpaRepository<CollaborationTaskSubmission, Long> {
    List<CollaborationTaskSubmission> findByTaskIdInOrderByTaskIdAscVersionNoDesc(Collection<Long> taskIds);
    List<CollaborationTaskSubmission> findByTaskIdOrderByVersionNoDesc(Long taskId);
    Optional<CollaborationTaskSubmission> findFirstByTaskIdOrderByVersionNoDesc(Long taskId);
    long countByTaskId(Long taskId);
}
