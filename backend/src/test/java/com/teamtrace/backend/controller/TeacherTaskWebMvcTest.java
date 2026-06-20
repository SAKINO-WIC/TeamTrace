package com.teamtrace.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamtrace.backend.config.ObjectMapperConfig;
import com.teamtrace.backend.dto.teacher.TaskDetailResponse;
import com.teamtrace.backend.dto.teacher.TaskSummaryResponse;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.TeacherTaskService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TeacherTaskController.class)
@Import(ObjectMapperConfig.class)
class TeacherTaskWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TeacherTaskService teacherTaskService;

    @Test
    void listTasksReturns200() throws Exception {
        when(jwtTokenProvider.resolveBearerToken("Bearer t")).thenReturn("t");
        when(jwtTokenProvider.isValid("t")).thenReturn(true);
        when(jwtTokenProvider.extractRole("t")).thenReturn("teacher");
        when(jwtTokenProvider.extractUserId("t")).thenReturn(2L);
        when(teacherTaskService.listTasks(2L, 1L))
                .thenReturn(
                        java.util.List.of(
                                TaskSummaryResponse.builder()
                                        .taskId(1L)
                                        .taskUuid(2L)
                                        .classId(1L)
                                        .name("n")
                                        .deadline(LocalDateTime.of(2026, 4, 20, 23, 59))
                                        .enablePeerReview(true)
                                        .peerReviewDeadline(LocalDateTime.of(2026, 4, 21, 1, 59))
                                        .peerReviewOffsetHours(2)
                                        .peerReviewMaxScore(100)
                                        .peerReviewWeight(new BigDecimal("0.40"))
                                        .teacherScoreWeight(new BigDecimal("0.60"))
                                        .status(1)
                                        .build()));

        mockMvc.perform(get("/api/teacher/classes/1/tasks").header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }

    @Test
    void createTaskReturns200() throws Exception {
        when(jwtTokenProvider.resolveBearerToken("Bearer t")).thenReturn("t");
        when(jwtTokenProvider.isValid("t")).thenReturn(true);
        when(jwtTokenProvider.extractRole("t")).thenReturn("teacher");
        when(jwtTokenProvider.extractUserId("t")).thenReturn(2L);
        TaskSummaryResponse summary = TaskSummaryResponse.builder()
                .taskId(1L)
                .taskUuid(2L)
                .classId(1L)
                .name("n")
                .deadline(LocalDateTime.of(2026, 4, 20, 23, 59))
                .enablePeerReview(true)
                .peerReviewDeadline(LocalDateTime.of(2026, 4, 21, 1, 59))
                .peerReviewOffsetHours(2)
                .peerReviewMaxScore(100)
                .peerReviewWeight(new BigDecimal("0.40"))
                .teacherScoreWeight(new BigDecimal("0.60"))
                .status(1)
                .build();
        when(teacherTaskService.createTask(eq(2L), eq(1L), any())).thenReturn(summary);

        mockMvc.perform(
                        post("/api/teacher/classes/1/tasks")
                                .header("Authorization", "Bearer t")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"第一次小组作业\",\"description\":\"x\",\"deadline\":\"2026-04-20T23:59:00+08:00\",\"enablePeerReview\":true,\"peerReviewOffsetHours\":2}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateTaskReturns200() throws Exception {
        when(jwtTokenProvider.resolveBearerToken("Bearer t")).thenReturn("t");
        when(jwtTokenProvider.isValid("t")).thenReturn(true);
        when(jwtTokenProvider.extractRole("t")).thenReturn("teacher");
        when(jwtTokenProvider.extractUserId("t")).thenReturn(2L);
        TaskDetailResponse detail =
                TaskDetailResponse.builder().taskId(1L).classId(1L).name("改名").description("d").build();
        when(teacherTaskService.updateTask(eq(2L), eq(1L), eq(1L), any())).thenReturn(detail);

        mockMvc.perform(
                        put("/api/teacher/classes/1/tasks/1")
                                .header("Authorization", "Bearer t")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"改名\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateTaskWithPeerReviewDeadlineReturns200() throws Exception {
        when(jwtTokenProvider.resolveBearerToken("Bearer t")).thenReturn("t");
        when(jwtTokenProvider.isValid("t")).thenReturn(true);
        when(jwtTokenProvider.extractRole("t")).thenReturn("teacher");
        when(jwtTokenProvider.extractUserId("t")).thenReturn(2L);
        TaskDetailResponse detail =
                TaskDetailResponse.builder().taskId(1L).classId(1L).name("n").description("d").build();
        when(teacherTaskService.updateTask(eq(2L), eq(1L), eq(1L), any())).thenReturn(detail);

        mockMvc.perform(
                        put("/api/teacher/classes/1/tasks/1")
                                .header("Authorization", "Bearer t")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"peerReviewDeadline\":\"2026-12-31T23:59:59+08:00\"}"))
                .andExpect(status().isOk());
    }
}
