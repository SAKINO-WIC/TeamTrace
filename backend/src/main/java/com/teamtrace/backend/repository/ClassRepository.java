package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.ClassEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassRepository extends JpaRepository<ClassEntity, Long> {
    Optional<ClassEntity> findFirstByClassCodeStartingWithOrderByClassCodeDesc(String classCodePrefix);
    List<ClassEntity> findByClassCodeStartingWith(String classCodePrefix);
    List<ClassEntity> findByTeacherIdAndIsDeletedOrderByIdDesc(Long teacherId, Integer isDeleted);

    Page<ClassEntity> findByIsDeletedOrderByIdDesc(Integer isDeleted, Pageable pageable);

    long countByIsDeleted(Integer isDeleted);

    long countByStatusAndIsDeleted(Integer status, Integer isDeleted);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ClassEntity c WHERE c.isDeleted = 1 AND c.deletedAt < :cutoff")
    int deleteExpiredSoftDeletes(@Param("cutoff") LocalDateTime cutoff);
}

