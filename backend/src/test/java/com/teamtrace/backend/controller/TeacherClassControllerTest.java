package com.teamtrace.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.dto.teacher.ClassResponse;
import com.teamtrace.backend.dto.teacher.CreateClassRequest;
import com.teamtrace.backend.dto.teacher.GroupingLockRequest;
import com.teamtrace.backend.dto.teacher.TeacherClassDetailResponse;
import com.teamtrace.backend.dto.teacher.TeacherClassListItemResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.teamtrace.backend.security.JwtTokenProvider;
import com.teamtrace.backend.service.TeacherClassService;
import org.junit.jupiter.api.Test;

class TeacherClassControllerTest {

    @Test
    void shouldCreateClassForTeacher() {
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        TeacherClassService teacherClassService = mock(TeacherClassService.class);
        TeacherClassController controller = new TeacherClassController(jwtTokenProvider, teacherClassService);
        String authorization = "Bearer teacher-token";

        CreateClassRequest request = new CreateClassRequest();
        request.setName("软件工程1班");
        request.setSemester("2026-Spring");
        request.setGroupSizeMin(2);
        request.setGroupSizeMax(5);

        when(jwtTokenProvider.resolveBearerToken(authorization)).thenReturn("teacher-token");
        when(jwtTokenProvider.isValid("teacher-token")).thenReturn(true);
        when(jwtTokenProvider.extractRole("teacher-token")).thenReturn("teacher");
        when(jwtTokenProvider.extractUserId("teacher-token")).thenReturn(10L);
        when(teacherClassService.createClass(10L, request)).thenReturn(ClassResponse.builder()
                .classId(100L)
                .classCode("20260001")
                .name("软件工程1班")
                .semester("2026-Spring")
                .groupSizeMin(2)
                .groupSizeMax(5)
                .groupingLocked(0)
                .status(1)
                .build());

        ApiResponse<ClassResponse> response = controller.create(authorization, request);

        assertTrue(response.isSuccess());
        assertEquals("OK", response.getCode());
        assertEquals("20260001", response.getData().getClassCode());
        verify(teacherClassService).createClass(10L, request);
    }

    @Test
    void shouldListTeacherClasses() {
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        TeacherClassService teacherClassService = mock(TeacherClassService.class);
        TeacherClassController controller = new TeacherClassController(jwtTokenProvider, teacherClassService);
        String authorization = "Bearer teacher-token";

        when(jwtTokenProvider.resolveBearerToken(authorization)).thenReturn("teacher-token");
        when(jwtTokenProvider.isValid("teacher-token")).thenReturn(true);
        when(jwtTokenProvider.extractRole("teacher-token")).thenReturn("teacher");
        when(jwtTokenProvider.extractUserId("teacher-token")).thenReturn(10L);
        when(teacherClassService.listMyClasses(10L)).thenReturn(List.of(
                TeacherClassListItemResponse.builder()
                        .classId(1L)
                        .classCode("20260001")
                        .name("软件工程1班")
                        .semester("2026-Spring")
                        .status(1)
                        .groupingLocked(0)
                        .studentCount(3L)
                        .build()));

        ApiResponse<List<TeacherClassListItemResponse>> response = controller.list(authorization, null);

        assertTrue(response.isSuccess());
        assertEquals(1, response.getData().size());
        assertEquals("20260001", response.getData().get(0).getClassCode());
    }

    @Test
    void shouldGetTeacherClassDetail() {
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        TeacherClassService teacherClassService = mock(TeacherClassService.class);
        TeacherClassController controller = new TeacherClassController(jwtTokenProvider, teacherClassService);
        String authorization = "Bearer teacher-token";

        when(jwtTokenProvider.resolveBearerToken(authorization)).thenReturn("teacher-token");
        when(jwtTokenProvider.isValid("teacher-token")).thenReturn(true);
        when(jwtTokenProvider.extractRole("teacher-token")).thenReturn("teacher");
        when(jwtTokenProvider.extractUserId("teacher-token")).thenReturn(10L);
        when(teacherClassService.getClassDetail(10L, 1L)).thenReturn(TeacherClassDetailResponse.builder()
                .classId(1L)
                .classCode("20260001")
                .name("软件工程1班")
                .semester("2026-Spring")
                .groupSizeMin(2)
                .groupSizeMax(5)
                .groupingLocked(0)
                .status(1)
                .activeInviteCode("ABCD1234")
                .studentCount(3L)
                .build());

        ApiResponse<TeacherClassDetailResponse> response = controller.detail(authorization, 1L);

        assertTrue(response.isSuccess());
        assertEquals("20260001", response.getData().getClassCode());
        assertEquals("ABCD1234", response.getData().getActiveInviteCode());
    }

    @Test
    void shouldGetClassStudentsWithPagination() {
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        TeacherClassService teacherClassService = mock(TeacherClassService.class);
        TeacherClassController controller = new TeacherClassController(jwtTokenProvider, teacherClassService);
        String authorization = "Bearer teacher-token";

        when(jwtTokenProvider.resolveBearerToken(authorization)).thenReturn("teacher-token");
        when(jwtTokenProvider.isValid("teacher-token")).thenReturn(true);
        when(jwtTokenProvider.extractRole("teacher-token")).thenReturn("teacher");
        when(jwtTokenProvider.extractUserId("teacher-token")).thenReturn(10L);

        Map<String, Object> pageData = new HashMap<>();
        pageData.put("list", List.of());
        pageData.put("page", 1);
        pageData.put("size", 10);
        pageData.put("total", 0L);
        pageData.put("pages", 0);
        pageData.put("hasNext", false);
        when(teacherClassService.getClassStudents(10L, 1L, 1, 10)).thenReturn(pageData);

        ApiResponse<Map<String, Object>> response = controller.students(authorization, 1L, 1, 10);

        assertTrue(response.isSuccess());
        assertEquals(1, response.getData().get("page"));
        assertEquals(false, response.getData().get("hasNext"));
    }

    @Test
    void shouldRemoveClassStudent() {
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        TeacherClassService teacherClassService = mock(TeacherClassService.class);
        TeacherClassController controller = new TeacherClassController(jwtTokenProvider, teacherClassService);
        String authorization = "Bearer teacher-token";

        when(jwtTokenProvider.resolveBearerToken(authorization)).thenReturn("teacher-token");
        when(jwtTokenProvider.isValid("teacher-token")).thenReturn(true);
        when(jwtTokenProvider.extractRole("teacher-token")).thenReturn("teacher");
        when(jwtTokenProvider.extractUserId("teacher-token")).thenReturn(10L);

        ApiResponse<Map<String, String>> response = controller.removeStudent(authorization, 1L, 100L);

        assertTrue(response.isSuccess());
        assertEquals("移除成功", response.getData().get("message"));
        verify(teacherClassService).removeClassStudent(10L, 1L, 100L);
    }

    @Test
    void shouldSetGroupingLock() {
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        TeacherClassService teacherClassService = mock(TeacherClassService.class);
        TeacherClassController controller = new TeacherClassController(jwtTokenProvider, teacherClassService);
        String authorization = "Bearer teacher-token";

        when(jwtTokenProvider.resolveBearerToken(authorization)).thenReturn("teacher-token");
        when(jwtTokenProvider.isValid("teacher-token")).thenReturn(true);
        when(jwtTokenProvider.extractRole("teacher-token")).thenReturn("teacher");
        when(jwtTokenProvider.extractUserId("teacher-token")).thenReturn(10L);

        Map<String, Object> payload = new HashMap<>();
        payload.put("classId", 1L);
        payload.put("groupingLocked", 1);
        when(teacherClassService.setGroupingLocked(10L, 1L, true)).thenReturn(payload);

        GroupingLockRequest body = new GroupingLockRequest();
        body.setLocked(true);
        ApiResponse<Map<String, Object>> response = controller.groupingLock(authorization, 1L, body);

        assertTrue(response.isSuccess());
        assertEquals(1, response.getData().get("groupingLocked"));
        verify(teacherClassService).setGroupingLocked(10L, 1L, true);
    }

    @Test
    void shouldDissolveClass() {
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        TeacherClassService teacherClassService = mock(TeacherClassService.class);
        TeacherClassController controller = new TeacherClassController(jwtTokenProvider, teacherClassService);
        String authorization = "Bearer teacher-token";

        when(jwtTokenProvider.resolveBearerToken(authorization)).thenReturn("teacher-token");
        when(jwtTokenProvider.isValid("teacher-token")).thenReturn(true);
        when(jwtTokenProvider.extractRole("teacher-token")).thenReturn("teacher");
        when(jwtTokenProvider.extractUserId("teacher-token")).thenReturn(10L);

        Map<String, Object> payload = new HashMap<>();
        payload.put("classId", 1L);
        payload.put("tasksSoftDeleted", 2);
        payload.put("groupsSoftDeleted", 1);
        when(teacherClassService.dissolveClass(10L, 1L)).thenReturn(payload);

        ApiResponse<Map<String, Object>> response = controller.dissolve(authorization, 1L);

        assertTrue(response.isSuccess());
        assertEquals(2, response.getData().get("tasksSoftDeleted"));
        assertEquals(1, response.getData().get("groupsSoftDeleted"));
        verify(teacherClassService).dissolveClass(10L, 1L);
    }

    @Test
    void shouldRestoreClass() {
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        TeacherClassService teacherClassService = mock(TeacherClassService.class);
        TeacherClassController controller = new TeacherClassController(jwtTokenProvider, teacherClassService);
        String authorization = "Bearer teacher-token";

        when(jwtTokenProvider.resolveBearerToken(authorization)).thenReturn("teacher-token");
        when(jwtTokenProvider.isValid("teacher-token")).thenReturn(true);
        when(jwtTokenProvider.extractRole("teacher-token")).thenReturn("teacher");
        when(jwtTokenProvider.extractUserId("teacher-token")).thenReturn(10L);

        Map<String, Object> payload = new HashMap<>();
        payload.put("classId", 1L);
        payload.put("tasksRestored", 0);
        payload.put("groupsRestored", 1);
        payload.put("needNewClassInviteCode", true);
        when(teacherClassService.restoreClass(10L, 1L)).thenReturn(payload);

        ApiResponse<Map<String, Object>> response = controller.restore(authorization, 1L);

        assertTrue(response.isSuccess());
        assertEquals(true, response.getData().get("needNewClassInviteCode"));
        verify(teacherClassService).restoreClass(10L, 1L);
    }
}
