package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.ClassInviteCode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassInviteCodeRepository extends JpaRepository<ClassInviteCode, Long> {
    Optional<ClassInviteCode> findByCode(String code);
    Optional<ClassInviteCode> findFirstByClassIdAndStatusAndExpireAtAfterOrderByIdDesc(
            Long classId, Integer status, LocalDateTime now);

    @Query("""
            SELECT c FROM ClassInviteCode c
            WHERE c.classId IN :classIds AND c.status = :status AND c.expireAt > :now
            ORDER BY c.classId ASC, c.id DESC
            """)
    List<ClassInviteCode> findActiveInvitesForClassIds(
            @Param("classIds") Collection<Long> classIds,
            @Param("status") Integer status,
            @Param("now") LocalDateTime now);

    @Modifying
    @Query("update ClassInviteCode c set c.status = :newStatus where c.classId = :classId and c.status = :oldStatus")
    int bulkUpdateStatusByClassIdAndStatus(
            @Param("classId") Long classId,
            @Param("oldStatus") Integer oldStatus,
            @Param("newStatus") Integer newStatus);
}

