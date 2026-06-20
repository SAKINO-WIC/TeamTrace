package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.CollaborationTaskReview;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollaborationTaskReviewRepository extends JpaRepository<CollaborationTaskReview, Long> {
    List<CollaborationTaskReview> findByTaskIdInOrderByCreatedAtDesc(Collection<Long> taskIds);
}
