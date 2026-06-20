package com.teamtrace.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamtrace.backend.config.ObjectMapperConfig;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.StudentProfileService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StudentProfileController.class)
@Import(ObjectMapperConfig.class)
class StudentProfileWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private StudentProfileService studentProfileService;

    @Test
    void deleteAccountReturns200() throws Exception {
        when(jwtTokenProvider.resolveBearerToken("Bearer t")).thenReturn("t");
        when(jwtTokenProvider.isValid("t")).thenReturn(true);
        when(jwtTokenProvider.extractRole("t")).thenReturn("student");
        when(jwtTokenProvider.extractUserId("t")).thenReturn(5L);
        when(studentProfileService.deleteAccount(eq(5L), any()))
                .thenReturn(Map.of("message", "账号已注销"));

        mockMvc.perform(
                        delete("/api/student/account")
                                .header("Authorization", "Bearer t")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"Student@123\"}"))
                .andExpect(status().isOk());
    }
}
