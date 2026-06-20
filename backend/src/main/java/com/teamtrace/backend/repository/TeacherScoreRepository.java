package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.TeacherScore;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherScoreRepository extends JpaRepository<TeacherScore, Long> {

    long countByTaskIdAndTargetTypeAndIsDeleted(Long taskId, String targetType, Integer isDeleted);

    Optional<TeacherScore> findByTaskIdAndTargetTypeAndTargetIdAndIsDeleted(
            Long taskId, String targetType, Long targetId, Integer isDeleted);

    List<TeacherScore> findByTaskIdAndTargetTypeAndIsDeleted(Long taskId, String targetType, Integer isDeleted);
}
