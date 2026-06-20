package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.CollaborationActivityLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollaborationActivityLogRepository extends JpaRepository<CollaborationActivityLog, Long> {
    List<CollaborationActivityLog> findTop80BySpaceIdOrderByIdDesc(Long spaceId);
    List<CollaborationActivityLog> findTop80BySpaceIdAndProjectIdOrderByIdDesc(Long spaceId, Long projectId);
}
