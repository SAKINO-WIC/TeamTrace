package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.CollaborationProject;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollaborationProjectRepository extends JpaRepository<CollaborationProject, Long> {
    List<CollaborationProject> findBySpaceIdOrderByIdDesc(Long spaceId);
    Optional<CollaborationProject> findByIdAndSpaceId(Long id, Long spaceId);
}
