package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.SystemAnnouncement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SystemAnnouncementRepository extends JpaRepository<SystemAnnouncement, Long> {
    @Query("""
            SELECT a FROM SystemAnnouncement a
            WHERE (:status IS NULL OR a.status = :status)
              AND (:targetScope IS NULL OR a.targetScope = :targetScope)
              AND (:keyword IS NULL OR a.title LIKE CONCAT('%', :keyword, '%') OR a.content LIKE CONCAT('%', :keyword, '%'))
            ORDER BY a.id DESC
            """)
    Page<SystemAnnouncement> search(
            @Param("status") Integer status,
            @Param("targetScope") String targetScope,
            @Param("keyword") String keyword,
            Pageable pageable);
}
