package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.SubtaskSubmissionHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubtaskSubmissionHistoryRepository extends JpaRepository<SubtaskSubmissionHistory, Long> {

    List<SubtaskSubmissionHistory> findBySubtaskIdOrderByVersionNoDesc(Long subtaskId);

    long countBySubtaskId(Long subtaskId);
}
