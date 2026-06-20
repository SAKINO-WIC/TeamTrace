package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.ClassStudent;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassStudentRepository extends JpaRepository<ClassStudent, Long> {
    Optional<ClassStudent> findByClassIdAndStudentId(Long classId, Long studentId);
    long countByClassIdAndIsDeleted(Long classId, Integer isDeleted);

    @Query("""
            SELECT cs.classId, COUNT(cs)
            FROM ClassStudent cs
            WHERE cs.classId IN :classIds AND cs.isDeleted = :isDeleted
            GROUP BY cs.classId
            """)
    List<Object[]> countActiveStudentsByClassIds(
            @Param("classIds") Collection<Long> classIds,
            @Param("isDeleted") Integer isDeleted);

    Page<ClassStudent> findByClassIdAndIsDeletedOrderByIdDesc(Long classId, Integer isDeleted, Pageable pageable);
    List<ClassStudent> findByStudentIdAndIsDeletedOrderByIdDesc(Long studentId, Integer isDeleted);
    List<ClassStudent> findByClassIdAndIsDeletedOrderByIdAsc(Long classId, Integer isDeleted);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ClassStudent cs WHERE cs.isDeleted = 1 AND cs.deletedAt < :cutoff")
    int deleteExpiredSoftDeletes(@Param("cutoff") LocalDateTime cutoff);
}

