package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.teacher.ClassDashboardResponse;
import com.teamtrace.backend.dto.teacher.TaskDashboardResponse;
import com.teamtrace.backend.entity.ClassEntity;
import com.teamtrace.backend.entity.GroupMember;
import com.teamtrace.backend.entity.Subtask;
import com.teamtrace.backend.entity.Task;
import com.teamtrace.backend.entity.TeacherScore;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.AppealRepository;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.ClassStudentRepository;
import com.teamtrace.backend.repository.GroupMemberRepository;
import com.teamtrace.backend.repository.PeerReviewRepository;
import com.teamtrace.backend.repository.SubtaskRepository;
import com.teamtrace.backend.repository.TaskGroupRepository;
import com.teamtrace.backend.repository.TaskRepository;
import com.teamtrace.backend.repository.TeacherScoreRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    /** 申诉：待处理 */
    public static final int APPEAL_STATUS_PENDING = 0;

    private final ClassRepository classRepository;
    private final ClassStudentRepository classStudentRepository;
    private final TaskRepository taskRepository;
    private final TaskGroupRepository taskGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final SubtaskRepository subtaskRepository;
    private final PeerReviewRepository peerReviewRepository;
    private final TeacherScoreRepository teacherScoreRepository;
    private final AppealRepository appealRepository;

    public DashboardService(
            ClassRepository classRepository,
            ClassStudentRepository classStudentRepository,
            TaskRepository taskRepository,
            TaskGroupRepository taskGroupRepository,
            GroupMemberRepository groupMemberRepository,
            SubtaskRepository subtaskRepository,
            PeerReviewRepository peerReviewRepository,
            TeacherScoreRepository teacherScoreRepository,
            AppealRepository appealRepository) {
        this.classRepository = classRepository;
        this.classStudentRepository = classStudentRepository;
        this.taskRepository = taskRepository;
        this.taskGroupRepository = taskGroupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.subtaskRepository = subtaskRepository;
        this.peerReviewRepository = peerReviewRepository;
        this.teacherScoreRepository = teacherScoreRepository;
        this.appealRepository = appealRepository;
    }

    @Transactional(readOnly = true)
    public ClassDashboardResponse classDashboard(Long teacherId, Long classId) {
        requireTeacherClass(teacherId, classId);
        long students = classStudentRepository.countByClassIdAndIsDeleted(classId, 0);
        long tasks = taskRepository.countByClassIdAndIsDeleted(classId, 0);
        long appeals = appealRepository.countPendingAppealsForClass(classId);
        return ClassDashboardResponse.builder()
                .classId(classId)
                .activeStudentCount(students)
                .taskCount(tasks)
                .pendingAppealsCount(appeals)
                .build();
    }

    @Transactional(readOnly = true)
    public TaskDashboardResponse taskDashboard(Long teacherId, Long classId, Long taskId) {
        requireTeacherClass(teacherId, classId);
        Task task = requireActiveTaskInClass(classId, taskId);

        long cid = task.getClassId();
        long groups = taskGroupRepository.countByClassIdAndTaskIdIsNullAndIsDeleted(cid, 0);
        long members = groupMemberRepository.countMembersForClassSemesterGroups(cid, GroupMember.STATUS_ACTIVE);
        long distinctStudents =
                groupMemberRepository.countDistinctStudentsForClassSemesterGroups(cid, GroupMember.STATUS_ACTIVE);

        long stTotal = subtaskRepository.countByTaskIdAndIsDeleted(task.getId(), 0);
        long st1 = subtaskRepository.countByTaskIdAndIsDeletedAndStatus(task.getId(), 0, Subtask.STATUS_PENDING_CLAIM);
        long st2 = subtaskRepository.countByTaskIdAndIsDeletedAndStatus(task.getId(), 0, Subtask.STATUS_IN_PROGRESS);
        long st3 = subtaskRepository.countByTaskIdAndIsDeletedAndStatus(task.getId(), 0, Subtask.STATUS_PENDING_REVIEW);
        long st4 = subtaskRepository.countByTaskIdAndIsDeletedAndStatus(task.getId(), 0, Subtask.STATUS_DONE);

        long peer = peerReviewRepository.countByTaskIdAndIsDeleted(task.getId(), 0);
        long teach = teacherScoreRepository.countByTaskIdAndTargetTypeAndIsDeleted(
                task.getId(), TeacherScore.TARGET_STUDENT, 0);
        long pend = appealRepository.countByTaskIdAndStatusAndIsDeleted(task.getId(), APPEAL_STATUS_PENDING, 0);

        boolean enablePeer = task.getEnablePeerReview() != null && task.getEnablePeerReview() == 1;

        return TaskDashboardResponse.builder()
                .taskId(task.getId())
                .classId(classId)
                .taskName(task.getName())
                .deadline(task.getDeadline())
                .taskStatus(task.getStatus())
                .enablePeerReview(enablePeer)
                .groupCount(groups)
                .activeMembersInGroups(members)
                .activeDistinctStudentsInGroups(distinctStudents)
                .subtasksTotal(stTotal)
                .subtasksPendingClaim(st1)
                .subtasksInProgress(st2)
                .subtasksPendingReview(st3)
                .subtasksCompleted(st4)
                .peerReviewsTotal(peer)
                .teacherScoresStudentCount(teach)
                .pendingAppealsCount(pend)
                .build();
    }

    private Task requireActiveTaskInClass(Long classId, Long taskId) {
        return taskRepository
                .findByIdAndClassIdAndIsDeleted(taskId, classId, 0)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND));
    }

    private ClassEntity requireTeacherClass(Long teacherId, Long classId) {
        ClassEntity clazz = classRepository
                .findById(classId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND));
        if (clazz.getIsDeleted() != null && clazz.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND);
        }
        if (!teacherId.equals(clazz.getTeacherId())) {
            throw new BusinessException("FORBIDDEN", "无权限", HttpStatus.FORBIDDEN);
        }
        return clazz;
    }
}
