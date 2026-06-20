package com.teamtrace.backend.domain.task;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TaskStatusCodesTest {

    @Test
    void isSupportedReturnsTrueForDefinedStatuses() {
        assertTrue(TaskStatusCodes.isSupported(TaskStatusCodes.NOT_STARTED));
        assertTrue(TaskStatusCodes.isSupported(TaskStatusCodes.IN_PROGRESS));
        assertTrue(TaskStatusCodes.isSupported(TaskStatusCodes.CLOSED));
    }

    @Test
    void isSupportedReturnsFalseForNullOrUnknownStatus() {
        assertFalse(TaskStatusCodes.isSupported(null));
        assertFalse(TaskStatusCodes.isSupported(-1));
        assertFalse(TaskStatusCodes.isSupported(3));
    }
}
