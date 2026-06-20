package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.Notification;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    @Query("""
            SELECT n
            FROM Notification n
            WHERE n.userId = :userId
              AND (:type IS NULL OR n.type = :type)
              AND (:isRead IS NULL OR n.isRead = :isRead)
            ORDER BY n.id DESC
            """)
    Page<Notification> searchForUser(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("isRead") Integer isRead,
            Pageable pageable);

    long countByUserIdAndIsRead(Long userId, Integer isRead);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = 1 WHERE n.userId = :userId AND n.isRead = 0")
    int markAllReadForUser(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Notification n
            SET n.isRead = 1
            WHERE n.userId = :userId
              AND n.type = :type
              AND n.isRead = 0
            """)
    int markReadByTypeForUser(@Param("userId") Long userId, @Param("type") String type);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Notification n
            SET n.isRead = 1
            WHERE n.userId = :userId
              AND n.relatedId = :relatedId
              AND (:type IS NULL OR n.type = :type)
              AND n.isRead = 0
            """)
    int markReadByRelatedIdForUser(
            @Param("userId") Long userId, @Param("relatedId") Long relatedId, @Param("type") String type);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.id = :id AND n.userId = :userId")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.id IN :ids AND n.userId = :userId")
    int deleteByIdInAndUserId(@Param("ids") java.util.List<Long> ids, @Param("userId") Long userId);
}
