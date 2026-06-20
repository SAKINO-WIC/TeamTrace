package com.teamtrace.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamtrace.backend.dto.student.CreateStudentAppealRequest;
import com.teamtrace.backend.dto.teacher.ResolveAppealRequest;
import com.teamtrace.backend.entity.Appeal;
import com.teamtrace.backend.entity.ClassEntity;
import com.teamtrace.backend.entity.Task;
import com.teamtrace.backend.entity.TeacherScore;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.AppealRepository;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.GroupMemberRepository;
import com.teamtrace.backend.repository.SubtaskRepository;
import com.teamtrace.backend.repository.TaskGroupRepository;
import com.teamtrace.backend.repository.TaskRepository;
import com.teamtrace.backend.repository.TeacherScoreRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppealServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ClassRepository classRepository;

    @Mock
    private ClassMembershipService classMembershipService;

    @Mock
    private AppealRepository appealRepository;

    @Mock
    private TeacherScoreRepository teacherScoreRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private SubtaskRepository subtaskRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private TaskGroupRepository taskGroupRepository;

    @InjectMocks
    private AppealService appealService;

    @Test
    void createAppealWhenPendingExists() {
        Task task = new Task();
        task.setId(10L);
        task.setClassId(2L);
        when(taskRepository.findByIdAndIsDeleted(10L, 0)).thenReturn(Optional.of(task));
        when(classRepository.findById(2L)).thenReturn(Optional.of(activeClass(2L)));
        when(appealRepository.existsByTaskIdAndStudentIdAndStatusAndIsDeletedAndType(
                        10L, 5L, 0, 0, Appeal.TYPE_TEACHER_SCORE))
                .thenReturn(true);

        CreateStudentAppealRequest req = new CreateStudentAppealRequest();
        req.setReason("r");
        assertThrows(BusinessException.class, () -> appealService.createStudentAppeal(5L, 10L, req));
    }

    @Test
    void resolveSuccessPersistsTeacherScore() {
        when(classRepository.findById(2L)).thenReturn(Optional.of(teacherClass(2L, 99L)));
        Task task = new Task();
        task.setId(10L);
        task.setClassId(2L);
        when(taskRepository.findByIdAndClassIdAndIsDeleted(10L, 2L, 0)).thenReturn(Optional.of(task));

        Appeal pending = new Appeal();
        pending.setId(1L);
        pending.setStudentId(5L);
        pending.setTaskId(10L);
        pending.setType(Appeal.TYPE_TEACHER_SCORE);
        pending.setStatus(0);
        when(appealRepository.findByIdAndTaskIdAndIsDeleted(1L, 10L, 0)).thenReturn(Optional.of(pending));
        when(teacherScoreRepository.findByTaskIdAndTargetTypeAndTargetIdAndIsDeleted(
                        10L, TeacherScore.TARGET_STUDENT, 5L, 0))
                .thenReturn(Optional.empty());
        when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

        ResolveAppealRequest req = new ResolveAppealRequest();
        req.setOutcome(3);
        req.setAdjustedTeacherScore(new BigDecimal("88.5"));
        appealService.resolveAppeal(99L, 2L, 10L, 1L, req);

        ArgumentCaptor<TeacherScore> cap = ArgumentCaptor.forClass(TeacherScore.class);
        verify(teacherScoreRepository).save(cap.capture());
        assertEquals(new BigDecimal("88.5"), cap.getValue().getScore());
        assertEquals(5L, cap.getValue().getTargetId());
    }

    @Test
    void resolveRejectDoesNotTouchTeacherScore() {
        when(classRepository.findById(2L)).thenReturn(Optional.of(teacherClass(2L, 99L)));
        Task task = new Task();
        task.setId(10L);
        task.setClassId(2L);
        when(taskRepository.findByIdAndClassIdAndIsDeleted(10L, 2L, 0)).thenReturn(Optional.of(task));

        Appeal pending = new Appeal();
        pending.setId(1L);
        pending.setStudentId(5L);
        pending.setTaskId(10L);
        pending.setType(Appeal.TYPE_TEACHER_SCORE);
        pending.setStatus(0);
        when(appealRepository.findByIdAndTaskIdAndIsDeleted(1L, 10L, 0)).thenReturn(Optional.of(pending));
        when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

        ResolveAppealRequest req = new ResolveAppealRequest();
        req.setOutcome(2);
        req.setTeacherResponse("理由不充分");
        req.setAdjustedTeacherScore(new BigDecimal("99"));
        appealService.resolveAppeal(99L, 2L, 10L, 1L, req);

        verify(teacherScoreRepository, never()).save(any());
    }

    private static ClassEntity teacherClass(Long id, Long teacherId) {
        ClassEntity c = new ClassEntity();
        c.setId(id);
        c.setTeacherId(teacherId);
        c.setIsDeleted(0);
        return c;
    }

    private static ClassEntity activeClass(Long id) {
        ClassEntity c = new ClassEntity();
        c.setId(id);
        c.setIsDeleted(0);
        c.setStatus(1);
        return c;
    }
}
