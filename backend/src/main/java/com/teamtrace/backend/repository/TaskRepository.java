package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.Task;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByClassIdAndIsDeletedOrderByIdDesc(Long classId, Integer isDeleted);

    @Query("""
            SELECT t
            FROM Task t
            WHERE t.classId IN :classIds
              AND t.isDeleted = :isDeleted
            ORDER BY t.id DESC
            """)
    List<Task> findByClassIdInAndIsDeletedOrderByIdDesc(
            @Param("classIds") List<Long> classIds, @Param("isDeleted") Integer isDeleted);

    @Query("""
            SELECT t
            FROM Task t
            WHERE t.classId IN :classIds
              AND t.isDeleted = :isDeleted
              AND (:status IS NULL OR t.status = :status)
              AND (:keyword IS NULL OR t.name LIKE CONCAT('%', :keyword, '%'))
            ORDER BY t.id DESC
            """)
    List<Task> searchStudentTasksInClasses(
            @Param("classIds") List<Long> classIds,
            @Param("isDeleted") Integer isDeleted,
            @Param("status") Integer status,
            @Param("keyword") String keyword);

    @Query("""
            SELECT t
            FROM Task t
            WHERE t.classId = :classId
              AND t.isDeleted = :isDeleted
              AND (:status IS NULL OR t.status = :status)
              AND (:keyword IS NULL OR t.name LIKE CONCAT('%', :keyword, '%'))
            ORDER BY t.id DESC
            """)
    List<Task> searchStudentTasks(
            @Param("classId") Long classId,
            @Param("isDeleted") Integer isDeleted,
            @Param("status") Integer status,
            @Param("keyword") String keyword);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Task t
            SET t.status = :closedStatus
            WHERE t.isDeleted = 0
              AND t.status <> :closedStatus
              AND t.deadline <= :now
            """)
    int markClosedByDeadline(@Param("now") LocalDateTime now, @Param("closedStatus") Integer closedStatus);

    long countByClassIdAndIsDeleted(Long classId, Integer isDeleted);

    Page<Task> findByIsDeletedOrderByIdDesc(Integer isDeleted, Pageable pageable);

    long countByIsDeleted(Integer isDeleted);

    Optional<Task> findByIdAndClassIdAndIsDeleted(Long id, Long classId, Integer isDeleted);

    Optional<Task> findByIdAndIsDeleted(Long id, Integer isDeleted);

    @Query("""
            SELECT t FROM Task t
            WHERE t.isDeleted = 0
              AND t.enablePeerReview = 1
              AND t.peerReviewDeadline IS NOT NULL
              AND t.peerReviewDeadline > :now
              AND t.peerReviewDeadline <= :until
            """)
    List<Task> findTasksNeedingPeerReviewReminder(
            @Param("now") LocalDateTime now, @Param("until") LocalDateTime until);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Task t WHERE t.isDeleted = 1 AND t.deletedAt < :cutoff")
    int deleteExpiredSoftDeletes(@Param("cutoff") LocalDateTime cutoff);
}
