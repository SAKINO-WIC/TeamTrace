package com.teamtrace.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.teacher.CreateTaskRequest;
import com.teamtrace.backend.dto.teacher.UpdateTaskRequest;
import com.teamtrace.backend.dto.teacher.TaskDetailResponse;
import com.teamtrace.backend.dto.teacher.TaskSummaryResponse;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.TeacherTaskService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeacherTaskControllerTest {

    @Test
    void shouldCreateTask() {
        JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        TeacherTaskService service = mock(TeacherTaskService.class);
        TeacherTaskController controller = new TeacherTaskController(jwt, service);
        String auth = "Bearer t";

        when(jwt.resolveBearerToken(auth)).thenReturn("t");
        when(jwt.isValid("t")).thenReturn(true);
        when(jwt.extractRole("t")).thenReturn("teacher");
        when(jwt.extractUserId("t")).thenReturn(2L);

        CreateTaskRequest body = new CreateTaskRequest();
        body.setName("第一次作业");
        body.setDescription("完成需求分析");
        body.setDeadline("2026-04-20T23:59:00+08:00");
        body.setEnablePeerReview(true);
        body.setPeerReviewOffsetHours(2);

        TaskSummaryResponse summary = TaskSummaryResponse.builder()
                .taskId(100L)
                .taskUuid(9001L)
                .classId(1L)
                .name("第一次作业")
                .deadline(LocalDateTime.of(2026, 4, 20, 23, 59))
                .enablePeerReview(true)
                .peerReviewOffsetHours(2)
                .peerReviewMaxScore(100)
                .peerReviewWeight(new BigDecimal("0.40"))
                .teacherScoreWeight(new BigDecimal("0.60"))
                .status(1)
                .build();
        when(service.createTask(2L, 1L, body)).thenReturn(summary);

        ApiResponse<TaskSummaryResponse> resp = controller.createTask(auth, 1L, body);

        assertTrue(resp.isSuccess());
        assertEquals(100L, resp.getData().getTaskId());
        verify(service).createTask(2L, 1L, body);
    }

    @Test
    void shouldListTasks() {
        JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        TeacherTaskService service = mock(TeacherTaskService.class);
        TeacherTaskController controller = new TeacherTaskController(jwt, service);
        String auth = "Bearer t";

        when(jwt.resolveBearerToken(auth)).thenReturn("t");
        when(jwt.isValid("t")).thenReturn(true);
        when(jwt.extractRole("t")).thenReturn("teacher");
        when(jwt.extractUserId("t")).thenReturn(2L);
        when(service.listTasks(2L, 1L)).thenReturn(List.of());

        ApiResponse<List<TaskSummaryResponse>> resp = controller.listTasks(auth, 1L);

        assertTrue(resp.isSuccess());
        assertTrue(resp.getData().isEmpty());
    }

    @Test
    void shouldGetTaskDetail() {
        JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        TeacherTaskService service = mock(TeacherTaskService.class);
        TeacherTaskController controller = new TeacherTaskController(jwt, service);
        String auth = "Bearer t";

        when(jwt.resolveBearerToken(auth)).thenReturn("t");
        when(jwt.isValid("t")).thenReturn(true);
        when(jwt.extractRole("t")).thenReturn("teacher");
        when(jwt.extractUserId("t")).thenReturn(2L);

        TaskDetailResponse detail = TaskDetailResponse.builder().taskId(5L).classId(1L).name("x").build();
        when(service.getTask(2L, 1L, 5L)).thenReturn(detail);

        ApiResponse<TaskDetailResponse> resp = controller.getTask(auth, 1L, 5L);

        assertTrue(resp.isSuccess());
        assertEquals(5L, resp.getData().getTaskId());
        verify(service).getTask(2L, 1L, 5L);
    }

    @Test
    void shouldDeleteTask() {
        JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        TeacherTaskService service = mock(TeacherTaskService.class);
        TeacherTaskController controller = new TeacherTaskController(jwt, service);
        String auth = "Bearer t";

        when(jwt.resolveBearerToken(auth)).thenReturn("t");
        when(jwt.isValid("t")).thenReturn(true);
        when(jwt.extractRole("t")).thenReturn("teacher");
        when(jwt.extractUserId("t")).thenReturn(2L);

        ApiResponse<?> resp = controller.deleteTask(auth, 1L, 5L);

        assertTrue(resp.isSuccess());
        verify(service).softDeleteTask(2L, 1L, 5L);
    }

    @Test
    void shouldUpdateTask() {
        JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        TeacherTaskService service = mock(TeacherTaskService.class);
        TeacherTaskController controller = new TeacherTaskController(jwt, service);
        String auth = "Bearer t";

        when(jwt.resolveBearerToken(auth)).thenReturn("t");
        when(jwt.isValid("t")).thenReturn(true);
        when(jwt.extractRole("t")).thenReturn("teacher");
        when(jwt.extractUserId("t")).thenReturn(2L);

        UpdateTaskRequest body = new UpdateTaskRequest();
        body.setName("新名称");
        TaskDetailResponse detail = TaskDetailResponse.builder().taskId(5L).name("新名称").build();
        when(service.updateTask(2L, 1L, 5L, body)).thenReturn(detail);

        ApiResponse<TaskDetailResponse> resp = controller.updateTask(auth, 1L, 5L, body);

        assertTrue(resp.isSuccess());
        assertEquals("新名称", resp.getData().getName());
        verify(service).updateTask(2L, 1L, 5L, body);
    }
}
