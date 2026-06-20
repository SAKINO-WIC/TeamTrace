package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.Subtask;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubtaskRepository extends JpaRepository<Subtask, Long> {

    long countByIsDeleted(Integer isDeleted);

    long countByTaskIdAndIsDeleted(Long taskId, Integer isDeleted);

    long countByTaskIdAndIsDeletedAndStatus(Long taskId, Integer isDeleted, Integer status);

    List<Subtask> findByTaskIdAndGroupIdAndIsDeletedOrderByIdAsc(Long taskId, Long groupId, Integer isDeleted);

    List<Subtask> findByTaskIdAndIsDeletedOrderByIdAsc(Long taskId, Integer isDeleted);

    Optional<Subtask> findByIdAndTaskIdAndGroupIdAndIsDeleted(
            Long id, Long taskId, Long groupId, Integer isDeleted);

    long countByTaskIdAndGroupIdAndIsDeletedAndAssigneeIdIsNotNull(Long taskId, Long groupId, Integer isDeleted);

    long countByTaskIdAndGroupIdAndIsDeletedAndStatus(Long taskId, Long groupId, Integer isDeleted, Integer status);

    long countByTaskIdAndGroupIdAndIsDeletedAndAssigneeIdAndStatus(
            Long taskId, Long groupId, Integer isDeleted, Long assigneeId, Integer status);

    long countByTaskIdAndGroupIdAndIsDeletedAndAssigneeId(Long taskId, Long groupId, Integer isDeleted, Long assigneeId);

    long countByGroupIdAndIsDeletedAndAssigneeIdIsNotNull(Long groupId, Integer isDeleted);

    long countByGroupIdAndIsDeletedAndStatus(Long groupId, Integer isDeleted, Integer status);

    long countByGroupIdAndIsDeletedAndAssigneeIdAndStatus(
            Long groupId, Integer isDeleted, Long assigneeId, Integer status);

    long countByGroupIdAndIsDeletedAndAssigneeId(Long groupId, Integer isDeleted, Long assigneeId);

    /** 进行中/待审、已认领、未发过截止提醒、截止时间在 (now, until] 内。 */
    @Query(
            """
            SELECT s FROM Subtask s
            WHERE s.isDeleted = 0
            AND s.assigneeId IS NOT NULL
            AND s.status IN (2, 3)
            AND s.reminderTime IS NULL
            AND s.deadline > :now
            AND s.deadline <= :until
            """)
    List<Subtask> findNeedingDeadlineReminder(@Param("now") LocalDateTime now, @Param("until") LocalDateTime until);

    /**
     * Batch progress query: returns [taskId, groupId, assigneeId, status, count] for all given task/group pairs.
     * Replaces hundreds of individual count queries with a single GROUP BY.
     */
    @Query(
            """
            SELECT s.taskId, s.groupId, s.assigneeId, s.status, COUNT(s)
            FROM Subtask s
            WHERE s.isDeleted = 0
            AND s.taskId IN :taskIds
            AND s.groupId IN :groupIds
            GROUP BY s.taskId, s.groupId, s.assigneeId, s.status
            """)
    List<Object[]> countProgressByTaskIdsAndGroupIds(
            @Param("taskIds") List<Long> taskIds,
            @Param("groupIds") List<Long> groupIds);

    /** Count ALL subtasks in a task group (including unclaimed). */
    long countByTaskIdAndGroupIdAndIsDeleted(Long taskId, Long groupId, Integer isDeleted);
}
