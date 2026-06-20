package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.GroupMember;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    @Query(
            "SELECT COUNT(gm) FROM GroupMember gm WHERE gm.status = :status AND gm.groupId IN (SELECT g.id FROM"
                    + " TaskGroup g WHERE g.taskId = :taskId AND g.isDeleted = 0)")
    long countMembersForTaskGroups(@Param("taskId") Long taskId, @Param("status") Integer status);

    @Query(
            "SELECT COUNT(DISTINCT gm.userId) FROM GroupMember gm WHERE gm.status = :status AND gm.groupId IN (SELECT g.id"
                    + " FROM TaskGroup g WHERE g.taskId = :taskId AND g.isDeleted = 0)")
    long countDistinctStudentsForTaskGroups(@Param("taskId") Long taskId, @Param("status") Integer status);

    @Query(
            "SELECT COUNT(gm) FROM GroupMember gm WHERE gm.status = :status AND gm.groupId IN (SELECT g.id FROM TaskGroup g"
                    + " WHERE g.classId = :classId AND g.taskId IS NULL AND g.isDeleted = 0)")
    long countMembersForClassSemesterGroups(@Param("classId") Long classId, @Param("status") Integer status);

    @Query(
            "SELECT COUNT(DISTINCT gm.userId) FROM GroupMember gm WHERE gm.status = :status AND gm.groupId IN (SELECT g.id"
                    + " FROM TaskGroup g WHERE g.classId = :classId AND g.taskId IS NULL AND g.isDeleted = 0)")
    long countDistinctStudentsForClassSemesterGroups(@Param("classId") Long classId, @Param("status") Integer status);

    List<GroupMember> findByGroupIdAndStatusOrderByIdAsc(Long groupId, Integer status);

    List<GroupMember> findByGroupIdInAndStatus(List<Long> groupIds, Integer status);

    boolean existsByGroupIdAndUserIdAndStatus(Long groupId, Long userId, Integer status);

    Optional<GroupMember> findByGroupIdAndUserIdAndStatus(Long groupId, Long userId, Integer status);

    /** 不区分状态；用于处理目标组存在历史非活跃行时的唯一键冲突 */
    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

    long countByGroupIdAndStatus(Long groupId, Integer status);

    long countByGroupIdAndStatusIn(Long groupId, Collection<Integer> statuses);

    @Query(
            "SELECT gm FROM GroupMember gm WHERE gm.userId = :userId AND gm.status IN (1, 2) AND gm.groupId IN (SELECT g.id"
                    + " FROM TaskGroup g WHERE g.classId = :classId AND g.taskId IS NULL AND g.isDeleted = 0)")
    List<GroupMember> findActiveOrPendingInClassSemesterGroups(
            @Param("classId") Long classId, @Param("userId") Long userId);
}
