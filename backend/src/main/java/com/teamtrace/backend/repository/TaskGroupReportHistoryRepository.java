package com.teamtrace.backend.repository;

import com.teamtrace.backend.entity.TaskGroupReportHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskGroupReportHistoryRepository extends JpaRepository<TaskGroupReportHistory, Long> {

    List<TaskGroupReportHistory> findByReportIdOrderByVersionNoDesc(Long reportId);
}
