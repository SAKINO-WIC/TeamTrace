package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.CollaborationSpaceInviteCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CollaborationSpaceInviteCodeRepository extends JpaRepository<CollaborationSpaceInviteCode, Long> {
    Optional<CollaborationSpaceInviteCode> findByCode(String code);
    Optional<CollaborationSpaceInviteCode> findFirstBySpaceIdAndStatusAndExpiresAtAfterOrderByIdDesc(
            Long spaceId, Integer status, LocalDateTime now);

    @Modifying
    @Query("update CollaborationSpaceInviteCode c set c.status = :newStatus where c.spaceId = :spaceId and c.status = :oldStatus")
    int bulkUpdateStatusBySpaceIdAndStatus(
            @Param("spaceId") Long spaceId,
            @Param("oldStatus") Integer oldStatus,
            @Param("newStatus") Integer newStatus);
}
