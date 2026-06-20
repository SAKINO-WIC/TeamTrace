package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.OperationLog;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    Page<OperationLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 路径包含某班级前缀（如 {@code /api/teacher/classes/10/}），用于按班过滤 HTTP 审计。 */
    Page<OperationLog> findByUserIdAndPathContainingOrderByCreatedAtDesc(
            Long userId, String pathContains, Pageable pageable);

    @Query("""
            SELECT o
            FROM OperationLog o
            WHERE o.userId = :userId
              AND (:classPathPrefix IS NULL OR o.path LIKE CONCAT('%', :classPathPrefix, '%'))
              AND (:pathContains IS NULL OR o.path LIKE CONCAT('%', :pathContains, '%'))
            """)
    Page<OperationLog> searchForStudent(
            @Param("userId") Long userId,
            @Param("classPathPrefix") String classPathPrefix,
            @Param("pathContains") String pathContains,
            Pageable pageable);

    @Query("""
            SELECT o
            FROM OperationLog o
            WHERE (:userId IS NULL OR o.userId = :userId)
              AND (:role IS NULL OR o.role = :role)
              AND (:pathContains IS NULL OR o.path LIKE CONCAT('%', :pathContains, '%'))
              AND (:createdFrom IS NULL OR o.createdAt >= :createdFrom)
              AND (:createdTo IS NULL OR o.createdAt <= :createdTo)
            """)
    Page<OperationLog> searchForAdmin(
            @Param("userId") Long userId,
            @Param("role") String role,
            @Param("pathContains") String pathContains,
            @Param("createdFrom") LocalDateTime createdFrom,
            @Param("createdTo") LocalDateTime createdTo,
            Pageable pageable);
}
