package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.TeacherInviteCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeacherInviteCodeRepository extends JpaRepository<TeacherInviteCode, Long> {
    Optional<TeacherInviteCode> findByCode(String code);
    Optional<TeacherInviteCode> findFirstByUsedByOrderByIdDesc(Long usedBy);
    List<TeacherInviteCode> findByUsedByOrderByIdDesc(Long usedBy);

    @Query("""
            SELECT t
            FROM TeacherInviteCode t
            WHERE (:code IS NULL OR t.code = :code)
              AND (:status IS NULL OR t.status = :status)
              AND (:expireFrom IS NULL OR t.expireAt >= :expireFrom)
              AND (:expireTo IS NULL OR t.expireAt <= :expireTo)
              AND (
                :expired IS NULL
                OR (:expired = true AND t.expireAt < :now)
                OR (:expired = false AND t.expireAt >= :now)
              )
            """)
    Page<TeacherInviteCode> search(
            @Param("code") String code,
            @Param("status") Integer status,
            @Param("expired") Boolean expired,
            @Param("now") LocalDateTime now,
            @Param("expireFrom") LocalDateTime expireFrom,
            @Param("expireTo") LocalDateTime expireTo,
            Pageable pageable);
}
