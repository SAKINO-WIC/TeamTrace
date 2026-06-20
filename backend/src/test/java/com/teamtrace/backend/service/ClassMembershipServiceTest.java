package com.teamtrace.backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.teamtrace.backend.entity.ClassStudent;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.ClassStudentRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ClassMembershipServiceTest {

    @Test
    void shouldDetectActiveMembership() {
        ClassStudentRepository repo = Mockito.mock(ClassStudentRepository.class);
        ClassMembershipService service = new ClassMembershipService(repo);

        ClassStudent active = new ClassStudent();
        active.setIsDeleted(0);
        when(repo.findByClassIdAndStudentId(1L, 10L)).thenReturn(Optional.of(active));

        assertTrue(service.isActiveStudentInClass(10L, 1L));
    }

    @Test
    void shouldDetectRemovedMembership() {
        ClassStudentRepository repo = Mockito.mock(ClassStudentRepository.class);
        ClassMembershipService service = new ClassMembershipService(repo);

        ClassStudent removed = new ClassStudent();
        removed.setIsDeleted(1);
        when(repo.findByClassIdAndStudentId(1L, 10L)).thenReturn(Optional.of(removed));

        assertFalse(service.isActiveStudentInClass(10L, 1L));
    }

    @Test
    void shouldThrowWhenNotActive() {
        ClassStudentRepository repo = Mockito.mock(ClassStudentRepository.class);
        ClassMembershipService service = new ClassMembershipService(repo);

        when(repo.findByClassIdAndStudentId(1L, 10L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.requireActiveStudentInClass(10L, 1L));
    }
}
