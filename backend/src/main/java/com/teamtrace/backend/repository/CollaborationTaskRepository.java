package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.CollaborationTask;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CollaborationTaskRepository extends JpaRepository<CollaborationTask, Long> {
    List<CollaborationTask> findBySpaceIdOrderByIdDesc(Long spaceId);
    List<CollaborationTask> findByProjectIdOrderByIdAsc(Long projectId);
    List<CollaborationTask> findByProjectIdInOrderByIdAsc(Collection<Long> projectIds);
    Optional<CollaborationTask> findByIdAndSpaceIdAndProjectId(Long id, Long spaceId, Long projectId);

    @Query("""
            SELECT COUNT(t)
            FROM CollaborationTask t
            WHERE t.spaceId = :spaceId
              AND t.status <> :completedStatus
              AND (t.assigneeId = :studentId OR t.receiverId = :studentId)
            """)
    long countOpenTasksForMember(
            @Param("spaceId") Long spaceId,
            @Param("studentId") Long studentId,
            @Param("completedStatus") String completedStatus);
}
