package com.teamtrace.backend.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamtrace.backend.config.ObjectMapperConfig;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.AppealService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TeacherAnalyticsController.class)
@Import(ObjectMapperConfig.class)
class TeacherAnalyticsWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AppealService appealService;

    @Test
    void appealsWorkspaceReturns200() throws Exception {
        when(jwtTokenProvider.resolveBearerToken("Bearer t")).thenReturn("t");
        when(jwtTokenProvider.isValid("t")).thenReturn(true);
        when(jwtTokenProvider.extractRole("t")).thenReturn("teacher");
        when(jwtTokenProvider.extractUserId("t")).thenReturn(3L);
        when(appealService.listTeacherAppealsWorkspace(3L)).thenReturn(List.of());

        mockMvc.perform(
                        get("/api/teacher/analytics/appeals-workspace")
                                .header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }
}
