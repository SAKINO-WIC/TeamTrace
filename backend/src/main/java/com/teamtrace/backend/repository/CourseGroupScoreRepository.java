package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.CourseGroupScore;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseGroupScoreRepository extends JpaRepository<CourseGroupScore, Long> {

    List<CourseGroupScore> findByClassIdAndIsDeletedOrderByIdAsc(Long classId, Integer isDeleted);

    Optional<CourseGroupScore> findByClassIdAndGroupIdAndIsDeleted(Long classId, Long groupId, Integer isDeleted);
}
