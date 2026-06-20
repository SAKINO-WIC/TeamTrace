package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.PeerReview;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PeerReviewRepository extends JpaRepository<PeerReview, Long> {

    long countByTaskIdAndIsDeleted(Long taskId, Integer isDeleted);

    boolean existsByTaskIdAndGroupIdAndReviewerIdAndRevieweeIdAndIsDeleted(
            Long taskId, Long groupId, Long reviewerId, Long revieweeId, Integer isDeleted);

    Optional<PeerReview> findByTaskIdAndGroupIdAndReviewerIdAndRevieweeIdAndIsDeleted(
            Long taskId, Long groupId, Long reviewerId, Long revieweeId, Integer isDeleted);

    List<PeerReview> findByTaskIdAndGroupIdAndIsDeletedOrderBySubmittedAtAsc(
            Long taskId, Long groupId, Integer isDeleted);

    @Query(
            "SELECT r.revieweeId, AVG(r.score) FROM PeerReview r WHERE r.taskId = :taskId AND r.groupId = :groupId AND"
                    + " r.isDeleted = 0 GROUP BY r.revieweeId")
    List<Object[]> averagePeerScoreByReviewee(@Param("taskId") Long taskId, @Param("groupId") Long groupId);

    @Query("SELECT DISTINCT r.reviewerId FROM PeerReview r WHERE r.taskId = :taskId AND r.groupId = :groupId AND r.isDeleted = 0")
    List<Long> findDistinctReviewerIdsByTaskIdAndGroupId(@Param("taskId") Long taskId, @Param("groupId") Long groupId);

    /** Batch: average peer score by reviewee across multiple groups for a task. Returns [groupId, revieweeId, avgScore]. */
    @Query(
            """
            SELECT r.groupId, r.revieweeId, AVG(r.score)
            FROM PeerReview r
            WHERE r.taskId = :taskId
              AND r.groupId IN :groupIds
              AND r.isDeleted = 0
            GROUP BY r.groupId, r.revieweeId
            """)
    List<Object[]> averagePeerScoreByRevieweeForGroups(
            @Param("taskId") Long taskId, @Param("groupIds") List<Long> groupIds);

}
