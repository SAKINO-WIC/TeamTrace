package com.teamtrace.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.teacher.TaskDetailResponse;
import com.teamtrace.backend.dto.teacher.TaskSummaryResponse;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.StudentTaskService;
import com.teamtrace.backend.service.StudentTaskWorkspaceService;
import java.util.List;
import org.junit.jupiter.api.Test;

class StudentTaskControllerTest {

    @Test
    void shouldListTasks() {
        JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        StudentTaskService service = mock(StudentTaskService.class);
        StudentTaskWorkspaceService workspaceService = mock(StudentTaskWorkspaceService.class);
        StudentTaskController controller = new StudentTaskController(jwt, service, workspaceService);
        String auth = "Bearer t";

        when(jwt.resolveBearerToken(auth)).thenReturn("t");
        when(jwt.isValid("t")).thenReturn(true);
        when(jwt.extractRole("t")).thenReturn("student");
        when(jwt.extractUserId("t")).thenReturn(5L);
        when(service.listClassTasks(5L, 1L, null, null)).thenReturn(List.of());

        ApiResponse<List<TaskSummaryResponse>> resp = controller.listTasks(auth, 1L, null, null);

        assertTrue(resp.isSuccess());
        verify(service).listClassTasks(5L, 1L, null, null);
    }

    @Test
    void shouldGetTaskDetail() {
        JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        StudentTaskService service = mock(StudentTaskService.class);
        StudentTaskWorkspaceService workspaceService = mock(StudentTaskWorkspaceService.class);
        StudentTaskController controller = new StudentTaskController(jwt, service, workspaceService);
        String auth = "Bearer t";

        when(jwt.resolveBearerToken(auth)).thenReturn("t");
        when(jwt.isValid("t")).thenReturn(true);
        when(jwt.extractRole("t")).thenReturn("student");
        when(jwt.extractUserId("t")).thenReturn(5L);
        TaskDetailResponse detail = TaskDetailResponse.builder().taskId(9L).classId(1L).name("d").build();
        when(service.getClassTask(5L, 1L, 9L)).thenReturn(detail);

        ApiResponse<TaskDetailResponse> resp = controller.getTask(auth, 1L, 9L);

        assertTrue(resp.isSuccess());
        assertEquals(9L, resp.getData().getTaskId());
        verify(service).getClassTask(5L, 1L, 9L);
    }
}
