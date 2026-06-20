package com.teamtrace.backend.service;

import com.teamtrace.backend.domain.progress.ProgressFormulas;
import com.teamtrace.backend.domain.score.WeightedScoreFormulas;
import com.teamtrace.backend.dto.report.TaskReportResponse;
import com.teamtrace.backend.dto.report.TaskReportResponse.GroupReport;
import com.teamtrace.backend.dto.report.TaskReportResponse.MemberReport;
import com.teamtrace.backend.entity.ClassEntity;
import com.teamtrace.backend.entity.GroupMember;
import com.teamtrace.backend.entity.Subtask;
import com.teamtrace.backend.entity.Task;
import com.teamtrace.backend.entity.TaskGroup;
import com.teamtrace.backend.entity.TeacherScore;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.GroupMemberRepository;
import com.teamtrace.backend.repository.PeerReviewRepository;
import com.teamtrace.backend.repository.SubtaskRepository;
import com.teamtrace.backend.repository.TaskGroupRepository;
import com.teamtrace.backend.repository.TaskRepository;
import com.teamtrace.backend.repository.TeacherScoreRepository;
import com.teamtrace.backend.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskReportService {

    private final ClassRepository classRepository;
    private final TaskRepository taskRepository;
    private final TaskGroupRepository taskGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final SubtaskRepository subtaskRepository;
    private final PeerReviewRepository peerReviewRepository;
    private final TeacherScoreRepository teacherScoreRepository;
    private final UserRepository userRepository;

    public TaskReportService(
            ClassRepository classRepository,
            TaskRepository taskRepository,
            TaskGroupRepository taskGroupRepository,
            GroupMemberRepository groupMemberRepository,
            SubtaskRepository subtaskRepository,
            PeerReviewRepository peerReviewRepository,
            TeacherScoreRepository teacherScoreRepository,
            UserRepository userRepository) {
        this.classRepository = classRepository;
        this.taskRepository = taskRepository;
        this.taskGroupRepository = taskGroupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.subtaskRepository = subtaskRepository;
        this.peerReviewRepository = peerReviewRepository;
        this.teacherScoreRepository = teacherScoreRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public TaskReportResponse generate(Long teacherId, Long classId, Long taskId) {
        ClassEntity clazz = classRepository.findById(classId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND));
        if (!teacherId.equals(clazz.getTeacherId())) {
            throw new BusinessException("FORBIDDEN", "无权限", HttpStatus.FORBIDDEN);
        }

        Task task = taskRepository.findByIdAndClassIdAndIsDeleted(taskId, classId, 0)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND));

        List<TaskGroup> groups = taskGroupRepository.findByClassIdAndTaskIdAndIsDeletedOrderByIdAsc(classId, taskId, 0);

        List<GroupReport> groupReports = new ArrayList<>();
        int totalMembers = 0;

        for (TaskGroup group : groups) {
            List<GroupMember> members = groupMemberRepository.findByGroupIdAndStatusOrderByIdAsc(
                    group.getId(), GroupMember.STATUS_ACTIVE);

            Map<Long, BigDecimal> peerAvg = loadPeerAvgMap(taskId, group.getId());
            List<Subtask> subtasks = subtaskRepository.findByTaskIdAndGroupIdAndIsDeletedOrderByIdAsc(
                    taskId, group.getId(), 0);

            List<MemberReport> memberReports = new ArrayList<>();
            for (GroupMember member : members) {
                long uid = member.getUserId();
                String name = userRepository.findById(uid).map(u -> u.getName()).orElse("");

                long assigned = subtasks.stream()
                        .filter(s -> s.getAssigneeId() != null && s.getAssigneeId() == uid)
                        .count();
                long completed = subtasks.stream()
                        .filter(s -> s.getAssigneeId() != null && s.getAssigneeId() == uid
                                && s.getStatus() != null && s.getStatus() >= Subtask.STATUS_DONE)
                        .count();
                BigDecimal completionRate = ProgressFormulas.memberProgressPercent(completed, assigned);

                boolean peerApplicable = task.getEnablePeerReview() != null && task.getEnablePeerReview() == 1
                        && members.size() >= 2;
                int maxPeer = task.getPeerReviewMaxScore() != null ? task.getPeerReviewMaxScore() : 100;
                BigDecimal peerReceived = peerApplicable ? peerAvg.get(uid) : null;
                BigDecimal peerOn100 = peerApplicable
                        ? WeightedScoreFormulas.peerAverageTo100(peerReceived, maxPeer)
                        : null;

                BigDecimal teacherOn100 = teacherScoreRepository
                        .findByTaskIdAndTargetTypeAndTargetIdAndIsDeleted(
                                task.getId(), TeacherScore.TARGET_STUDENT, uid, 0)
                        .map(TeacherScore::getScore)
                        .map(s -> s.setScale(WeightedScoreFormulas.SCALE_TOTAL, RoundingMode.HALF_UP))
                        .orElse(null);

                BigDecimal total = WeightedScoreFormulas.weightedTotal100(
                        peerApplicable, peerOn100, teacherOn100,
                        task.getPeerReviewWeight(), task.getTeacherScoreWeight());

                memberReports.add(MemberReport.builder()
                        .studentId(uid)
                        .studentName(name)
                        .assignedSubtasks((int) assigned)
                        .completedSubtasks((int) completed)
                        .completionRate(completionRate)
                        .peerAverageOn100(peerOn100)
                        .teacherScore(teacherOn100)
                        .weightedTotal100(total)
                        .build());
            }

            String leaderName = userRepository.findById(group.getLeaderId())
                    .map(u -> u.getName()).orElse("");

            groupReports.add(GroupReport.builder()
                    .groupId(group.getId())
                    .groupName(group.getName())
                    .leaderId(group.getLeaderId())
                    .leaderName(leaderName)
                    .members(memberReports)
                    .build());

            totalMembers += members.size();
        }

        return TaskReportResponse.builder()
                .taskId(task.getId())
                .taskName(task.getName())
                .deadline(task.getDeadline() != null ? task.getDeadline().toString() : "")
                .taskStatus(task.getStatus())
                .peerReviewEnabled(task.getEnablePeerReview() != null && task.getEnablePeerReview() == 1)
                .peerReviewWeight(task.getPeerReviewWeight())
                .teacherScoreWeight(task.getTeacherScoreWeight())
                .totalGroups(groups.size())
                .totalMembers(totalMembers)
                .groups(groupReports)
                .build();
    }

    private Map<Long, BigDecimal> loadPeerAvgMap(Long taskId, Long groupId) {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (Object[] row : peerReviewRepository.averagePeerScoreByReviewee(taskId, groupId)) {
            if (row == null || row.length < 2 || row[0] == null) continue;
            long revieweeId = ((Number) row[0]).longValue();
            BigDecimal avg = toBigDecimal(row[1]);
            if (avg != null) {
                map.put(revieweeId, avg.setScale(2, RoundingMode.HALF_UP));
            }
        }
        return map;
    }

    private static BigDecimal toBigDecimal(Object raw) {
        if (raw == null) return null;
        if (raw instanceof BigDecimal bd) return bd;
        if (raw instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(raw.toString());
    }
}
