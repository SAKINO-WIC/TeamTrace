package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.CourseScore;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseScoreRepository extends JpaRepository<CourseScore, Long> {

    List<CourseScore> findByClassIdAndIsDeletedOrderByIdAsc(Long classId, Integer isDeleted);

    Optional<CourseScore> findByClassIdAndStudentIdAndIsDeleted(Long classId, Long studentId, Integer isDeleted);
}
