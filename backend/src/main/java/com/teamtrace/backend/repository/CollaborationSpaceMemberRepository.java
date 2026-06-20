package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.CollaborationSpaceMember;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollaborationSpaceMemberRepository extends JpaRepository<CollaborationSpaceMember, Long> {
    List<CollaborationSpaceMember> findByStudentIdAndIsDeletedOrderByIdDesc(Long studentId, Integer isDeleted);
    List<CollaborationSpaceMember> findBySpaceIdAndIsDeletedOrderByIdAsc(Long spaceId, Integer isDeleted);
    List<CollaborationSpaceMember> findBySpaceIdInAndIsDeleted(Collection<Long> spaceIds, Integer isDeleted);
    Optional<CollaborationSpaceMember> findBySpaceIdAndStudentId(Long spaceId, Long studentId);
    Optional<CollaborationSpaceMember> findBySpaceIdAndStudentIdAndIsDeleted(Long spaceId, Long studentId, Integer isDeleted);
    long countBySpaceIdAndIsDeleted(Long spaceId, Integer isDeleted);
}
