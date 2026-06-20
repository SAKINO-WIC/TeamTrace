package com.teamtrace.backend.domain.task;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.teamtrace.backend.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

class CollaborationTaskDependencyCycleValidatorTest {

    private final CollaborationTaskDependencyCycleValidator validator =
            new CollaborationTaskDependencyCycleValidator();

    @Test
    void shouldAllowAcyclicDependencies() {
        List<CollaborationTaskDependencyCycleValidator.Edge> edges = List.of(
                new CollaborationTaskDependencyCycleValidator.Edge(2L, 1L),
                new CollaborationTaskDependencyCycleValidator.Edge(3L, 2L),
                new CollaborationTaskDependencyCycleValidator.Edge(4L, 2L));

        assertDoesNotThrow(() -> validator.validateAcyclic(edges));
    }

    @Test
    void shouldRejectDirectSelfDependency() {
        List<CollaborationTaskDependencyCycleValidator.Edge> edges = List.of(
                new CollaborationTaskDependencyCycleValidator.Edge(1L, 1L));

        assertThrows(BusinessException.class, () -> validator.validateAcyclic(edges));
    }

    @Test
    void shouldRejectIndirectCycle() {
        List<CollaborationTaskDependencyCycleValidator.Edge> edges = List.of(
                new CollaborationTaskDependencyCycleValidator.Edge(2L, 1L),
                new CollaborationTaskDependencyCycleValidator.Edge(3L, 2L),
                new CollaborationTaskDependencyCycleValidator.Edge(1L, 3L));

        assertThrows(BusinessException.class, () -> validator.validateAcyclic(edges));
    }
}
