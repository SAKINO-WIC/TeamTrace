package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.CollaborationSpace;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollaborationSpaceRepository extends JpaRepository<CollaborationSpace, Long> {
    List<CollaborationSpace> findByIdInAndIsDeletedOrderByIdDesc(Collection<Long> ids, Integer isDeleted);
    Optional<CollaborationSpace> findByIdAndIsDeleted(Long id, Integer isDeleted);
}
