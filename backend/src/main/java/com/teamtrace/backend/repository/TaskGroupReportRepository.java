package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.TaskGroupReport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskGroupReportRepository extends JpaRepository<TaskGroupReport, Long> {

    Optional<TaskGroupReport> findByTaskIdAndGroupIdAndIsDeleted(Long taskId, Long groupId, Integer isDeleted);
}
