package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.TaskGroup;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskGroupRepository extends JpaRepository<TaskGroup, Long> {

    List<TaskGroup> findByTaskIdAndIsDeletedOrderByIdAsc(Long taskId, Integer isDeleted);

    /** 班级下全部未删除小组（含历史按任务建组） */
    List<TaskGroup> findByClassIdAndIsDeletedOrderByIdAsc(Long classId, Integer isDeleted);

    /** 学期固定小组：{@code task_id IS NULL}（与库注释「课程级固定小组」一致） */
    List<TaskGroup> findByClassIdAndTaskIdIsNullAndIsDeletedOrderByIdAsc(Long classId, Integer isDeleted);

    long countByClassIdAndTaskIdIsNullAndIsDeleted(Long classId, Integer isDeleted);

    @Query("""
            SELECT tg.classId, COUNT(tg)
            FROM TaskGroup tg
            WHERE tg.classId IN :classIds AND tg.taskId IS NULL AND tg.isDeleted = :isDeleted
            GROUP BY tg.classId
            """)
    List<Object[]> countSemesterGroupsByClassIds(
            @Param("classIds") Collection<Long> classIds,
            @Param("isDeleted") Integer isDeleted);

    long countByTaskIdAndIsDeleted(Long taskId, Integer isDeleted);

    long countByIsDeleted(Integer isDeleted);

    List<TaskGroup> findByClassIdAndTaskIdAndIsDeletedOrderByIdAsc(Long classId, Long taskId, Integer isDeleted);

    Optional<TaskGroup> findByIdAndClassIdAndIsDeleted(Long id, Long classId, Integer isDeleted);

    Optional<TaskGroup> findByIdAndClassIdAndTaskIdAndIsDeleted(
            Long id, Long classId, Long taskId, Integer isDeleted);

    /** 学期固定小组：{@code task_id IS NULL} */
    Optional<TaskGroup> findByIdAndClassIdAndTaskIdIsNullAndIsDeleted(Long id, Long classId, Integer isDeleted);

    /** 学生自建组等非空邀请码；与教师手动组（invite_code NULL）区分 */
    Optional<TaskGroup> findByInviteCodeAndTaskIdIsNullAndIsDeletedAndStatus(
            String inviteCode, Integer isDeleted, Integer status);
}
