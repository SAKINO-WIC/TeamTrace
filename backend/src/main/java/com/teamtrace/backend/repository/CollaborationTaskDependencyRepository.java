package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.CollaborationTaskDependency;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollaborationTaskDependencyRepository extends JpaRepository<CollaborationTaskDependency, Long> {
    List<CollaborationTaskDependency> findByTaskIdIn(Collection<Long> taskIds);
    void deleteByTaskId(Long taskId);
}
