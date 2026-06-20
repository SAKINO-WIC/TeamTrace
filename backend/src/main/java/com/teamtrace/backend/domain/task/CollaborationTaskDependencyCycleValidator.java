package com.teamtrace.backend.domain.task;

import com.teamtrace.backend.exception.BusinessException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

public class CollaborationTaskDependencyCycleValidator {

    public record Edge(Long taskId, Long dependsOnTaskId) {
    }

    public void validateAcyclic(List<Edge> edges) {
        Map<Long, Set<Long>> graph = new HashMap<>();
        for (Edge edge : edges) {
            if (edge == null || edge.taskId() == null || edge.dependsOnTaskId() == null) {
                continue;
            }
            if (edge.taskId().equals(edge.dependsOnTaskId())) {
                throw cycleException();
            }
            graph.computeIfAbsent(edge.taskId(), ignored -> new HashSet<>()).add(edge.dependsOnTaskId());
            graph.computeIfAbsent(edge.dependsOnTaskId(), ignored -> new HashSet<>());
        }

        Set<Long> visiting = new HashSet<>();
        Set<Long> visited = new HashSet<>();
        for (Long taskId : graph.keySet()) {
            if (hasCycle(taskId, graph, visiting, visited)) {
                throw cycleException();
            }
        }
    }

    private boolean hasCycle(
            Long taskId,
            Map<Long, Set<Long>> graph,
            Set<Long> visiting,
            Set<Long> visited) {
        if (visited.contains(taskId)) {
            return false;
        }
        if (!visiting.add(taskId)) {
            return true;
        }
        for (Long dependsOnTaskId : graph.getOrDefault(taskId, Set.of())) {
            if (hasCycle(dependsOnTaskId, graph, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(taskId);
        visited.add(taskId);
        return false;
    }

    private BusinessException cycleException() {
        return new BusinessException("BAD_REQUEST", "前置任务不能形成循环依赖", HttpStatus.BAD_REQUEST);
    }
}
