package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.Appeal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppealRepository extends JpaRepository<Appeal, Long> {

    long countByIsDeleted(Integer isDeleted);

    long countByTaskIdAndStatusAndIsDeleted(Long taskId, Integer status, Integer isDeleted);

    @Query(
            "SELECT COUNT(a) FROM Appeal a, Task t WHERE a.taskId = t.id AND t.classId = :classId AND t.isDeleted = 0 AND"
                    + " a.isDeleted = 0 AND a.status = 0")
    long countPendingAppealsForClass(@Param("classId") Long classId);

    boolean existsByTaskIdAndStudentIdAndStatusAndIsDeleted(Long taskId, Long studentId, Integer status, Integer isDeleted);

    boolean existsByTaskIdAndStudentIdAndStatusAndIsDeletedAndType(
            Long taskId, Long studentId, Integer status, Integer isDeleted, String type);

    long countByTaskIdAndStudentIdAndIsDeleted(Long taskId, Long studentId, Integer isDeleted);

    List<Appeal> findByTaskIdAndIsDeletedOrderByIdDesc(Long taskId, Integer isDeleted);

    List<Appeal> findByTaskIdInAndIsDeletedOrderByIdDesc(List<Long> taskIds, Integer isDeleted);

    List<Appeal> findByTaskIdAndStudentIdAndIsDeletedOrderByIdDesc(Long taskId, Long studentId, Integer isDeleted);

    List<Appeal> findByStudentIdAndIsDeletedOrderByIdDesc(Long studentId, Integer isDeleted);

    Optional<Appeal> findByIdAndTaskIdAndIsDeleted(Long id, Long taskId, Integer isDeleted);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Appeal a WHERE a.isDeleted = 1 AND a.deletedAt < :cutoff")
    int deleteExpiredSoftDeletes(@Param("cutoff") LocalDateTime cutoff);
}
