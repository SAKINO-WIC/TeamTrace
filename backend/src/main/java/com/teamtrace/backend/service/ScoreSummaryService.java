package com.teamtrace.backend.service;

import com.teamtrace.backend.domain.score.WeightedScoreFormulas;
import com.teamtrace.backend.dto.score.MemberScoreSummaryResponse;
import com.teamtrace.backend.entity.ClassEntity;
import com.teamtrace.backend.entity.GroupMember;
import com.teamtrace.backend.entity.Task;
import com.teamtrace.backend.entity.TaskGroup;
import com.teamtrace.backend.entity.TeacherScore;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.GroupMemberRepository;
import com.teamtrace.backend.repository.PeerReviewRepository;
import com.teamtrace.backend.repository.TaskRepository;
import com.teamtrace.backend.repository.TaskGroupRepository;
import com.teamtrace.backend.entity.TaskGroup;
import com.teamtrace.backend.repository.TeacherScoreRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScoreSummaryService {

    private final ClassRepository classRepository;
    private final TaskRepository taskRepository;
    private final SemesterGroupAccess semesterGroupAccess;
    private final GroupMemberRepository groupMemberRepository;
    private final PeerReviewRepository peerReviewRepository;
    private final TeacherScoreRepository teacherScoreRepository;
    private final TaskGroupRepository taskGroupRepository;
    private final ClassMembershipService classMembershipService;

    public ScoreSummaryService(
            ClassRepository classRepository,
            TaskRepository taskRepository,
            SemesterGroupAccess semesterGroupAccess,
            GroupMemberRepository groupMemberRepository,
            PeerReviewRepository peerReviewRepository,
            TeacherScoreRepository teacherScoreRepository,
            TaskGroupRepository taskGroupRepository,
            ClassMembershipService classMembershipService) {
        this.classRepository = classRepository;
        this.taskRepository = taskRepository;
        this.semesterGroupAccess = semesterGroupAccess;
        this.groupMemberRepository = groupMemberRepository;
        this.peerReviewRepository = peerReviewRepository;
        this.teacherScoreRepository = teacherScoreRepository;
        this.taskGroupRepository = taskGroupRepository;
        this.classMembershipService = classMembershipService;
    }

    @Transactional(readOnly = true)
    public MemberScoreSummaryResponse mySummary(Long studentId, Long classId, Long taskId, Long groupId) {
        requireStudentClassView(classId, studentId);
        Task task = requireActiveTaskInClass(classId, taskId);
        TaskGroup g = semesterGroupAccess.requireGroupForTask(classId, taskId, groupId);
        requireStudentInGroup(studentId, g.getId());
        return buildForStudent(task, g.getId(), studentId, loadPeerAvgMap(taskId, groupId), null, -1L);
    }

    @Transactional(readOnly = true)
    public List<MemberScoreSummaryResponse> listForTeacher(Long teacherId, Long classId, Long taskId, Long groupId) {
        requireTeacherClass(teacherId, classId);
        Task task = requireActiveTaskInClass(classId, taskId);
        semesterGroupAccess.requireGroupForTask(classId, taskId, groupId);
        Map<Long, BigDecimal> peerAvg = loadPeerAvgMap(taskId, groupId);

        // Batch: load all teacher scores for this task in one query
        Map<Long, TeacherScore> teacherScoreMap = new HashMap<>();
        for (TeacherScore ts : teacherScoreRepository.findByTaskIdAndTargetTypeAndIsDeleted(
                taskId, TeacherScore.TARGET_STUDENT, 0)) {
            teacherScoreMap.put(ts.getTargetId(), ts);
        }

        // Cache member count (same for all members in this group)
        long memberCount = groupMemberRepository.countByGroupIdAndStatus(groupId, GroupMember.STATUS_ACTIVE);

        List<Long> memberIds = groupMemberRepository.findByGroupIdAndStatusOrderByIdAsc(groupId, GroupMember.STATUS_ACTIVE)
                .stream()
                .map(GroupMember::getUserId)
                .sorted()
                .collect(Collectors.toList());
        return memberIds.stream()
                .map(uid -> buildForStudent(task, groupId, uid, peerAvg, teacherScoreMap, memberCount))
                .sorted(Comparator.comparing(MemberScoreSummaryResponse::getStudentId))
                .collect(Collectors.toList());
    }

    private Map<Long, BigDecimal> loadPeerAvgMap(Long taskId, Long groupId) {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (Object[] row : peerReviewRepository.averagePeerScoreByReviewee(taskId, groupId)) {
            if (row == null || row.length < 2 || row[0] == null) {
                continue;
            }
            long revieweeId = ((Number) row[0]).longValue();
            BigDecimal avg = toBigDecimal(row[1]);
            if (avg != null) {
                map.put(revieweeId, avg.setScale(2, RoundingMode.HALF_UP));
            }
        }
        return map;
    }

    private static BigDecimal toBigDecimal(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return new BigDecimal(raw.toString());
    }

    private MemberScoreSummaryResponse buildForStudent(
            Task task, Long groupId, Long studentId,
            Map<Long, BigDecimal> peerAvg,
            Map<Long, TeacherScore> teacherScoreMap, long cachedMemberCount) {
        long n = cachedMemberCount >= 0
                ? cachedMemberCount
                : groupMemberRepository.countByGroupIdAndStatus(groupId, GroupMember.STATUS_ACTIVE);
        boolean peerApplicable =
                task.getEnablePeerReview() != null && task.getEnablePeerReview() == 1 && n >= 2;

        int maxPeer = task.getPeerReviewMaxScore() != null ? task.getPeerReviewMaxScore() : 100;
        BigDecimal peerReceived = peerApplicable ? peerAvg.get(studentId) : null;
        BigDecimal peerOn100 =
                peerApplicable ? WeightedScoreFormulas.peerAverageTo100(peerReceived, maxPeer) : null;

        BigDecimal teacherOn100;
        if (teacherScoreMap != null) {
            // Use pre-loaded map (batch mode)
            TeacherScore ts = teacherScoreMap.get(studentId);
            teacherOn100 = ts != null
                    ? ts.getScore().setScale(WeightedScoreFormulas.SCALE_TOTAL, RoundingMode.HALF_UP)
                    : null;
        } else {
            // Individual query (single student mode)
            teacherOn100 = teacherScoreRepository
                    .findByTaskIdAndTargetTypeAndTargetIdAndIsDeleted(
                            task.getId(), TeacherScore.TARGET_STUDENT, studentId, 0)
                    .map(TeacherScore::getScore)
                    .map(s -> s.setScale(WeightedScoreFormulas.SCALE_TOTAL, RoundingMode.HALF_UP))
                    .orElse(null);
        }

        BigDecimal total = WeightedScoreFormulas.weightedTotal100(
                peerApplicable,
                peerOn100,
                teacherOn100,
                task.getPeerReviewWeight(),
                task.getTeacherScoreWeight());

        return MemberScoreSummaryResponse.builder()
                .studentId(studentId)
                .peerReviewApplicable(peerApplicable)
                .peerReviewWeight(task.getPeerReviewWeight())
                .teacherScoreWeight(task.getTeacherScoreWeight())
                .peerReviewMaxScore(maxPeer)
                .peerAverageReceived(peerReceived)
                .peerAverageOn100(peerOn100)
                .teacherScore(teacherOn100)
                .weightedTotal100(total)
                .build();
    }

    private void requireStudentInGroup(Long studentId, Long groupId) {
        if (!groupMemberRepository.existsByGroupIdAndUserIdAndStatus(groupId, studentId, GroupMember.STATUS_ACTIVE)) {
            throw new BusinessException("FORBIDDEN", "你不在该小组中", HttpStatus.FORBIDDEN);
        }
    }

    private void requireStudentClassView(Long classId, Long studentId) {
        ClassEntity clazz = classRepository
                .findById(classId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND));
        if (clazz.getIsDeleted() != null && clazz.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND);
        }
        if (clazz.getStatus() != null && clazz.getStatus() == 0) {
            throw new BusinessException("BAD_REQUEST", "班级已结束", HttpStatus.BAD_REQUEST);
        }
        classMembershipService.requireActiveStudentInClass(studentId, classId);
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

    /** Batch: score summaries for all semester groups in one call, eliminates N+1. */
    @Transactional(readOnly = true)
    public Map<Long, List<MemberScoreSummaryResponse>> listForTeacherAllGroups(
            Long teacherId, Long classId, Long taskId) {
        requireTeacherClass(teacherId, classId);
        Task task = requireActiveTaskInClass(classId, taskId);

        List<TaskGroup> groups = taskGroupRepository
                .findByClassIdAndTaskIdIsNullAndIsDeletedOrderByIdAsc(classId, 0);
        if (groups.isEmpty()) {
            return Map.of();
        }

        List<Long> groupIds = groups.stream().map(TaskGroup::getId).toList();

        // Batch load all teacher scores for this task once
        Map<Long, TeacherScore> teacherScoreMap = new HashMap<>();
        for (TeacherScore ts : teacherScoreRepository.findByTaskIdAndTargetTypeAndIsDeleted(
                taskId, TeacherScore.TARGET_STUDENT, 0)) {
            teacherScoreMap.put(ts.getTargetId(), ts);
        }

        // Batch load all peer review averages for all groups at once
        Map<Long, Map<Long, BigDecimal>> peerAvgByGroup = new HashMap<>();
        for (Object[] row : peerReviewRepository.averagePeerScoreByRevieweeForGroups(taskId, groupIds)) {
            if (row == null || row.length < 3 || row[0] == null) continue;
            long groupId = ((Number) row[0]).longValue();
            long revieweeId = ((Number) row[1]).longValue();
            BigDecimal avg = toBigDecimal(row[2]);
            if (avg != null) {
                peerAvgByGroup
                        .computeIfAbsent(groupId, k -> new HashMap<>())
                        .put(revieweeId, avg.setScale(2, RoundingMode.HALF_UP));
            }
        }

        // Batch load all group members once
        Map<Long, List<GroupMember>> membersByGroup = new HashMap<>();
        Map<Long, Long> memberCountByGroup = new HashMap<>();
        for (GroupMember gm : groupMemberRepository.findByGroupIdInAndStatus(groupIds, GroupMember.STATUS_ACTIVE)) {
            membersByGroup.computeIfAbsent(gm.getGroupId(), k -> new ArrayList<>()).add(gm);
        }
        for (TaskGroup g : groups) {
            List<GroupMember> members = membersByGroup.getOrDefault(g.getId(), List.of());
            memberCountByGroup.put(g.getId(), (long) members.size());
        }

        // Build per-group summaries
        Map<Long, List<MemberScoreSummaryResponse>> result = new LinkedHashMap<>();
        for (TaskGroup g : groups) {
            Map<Long, BigDecimal> peerAvg = peerAvgByGroup.getOrDefault(g.getId(), Map.of());
            long memberCount = memberCountByGroup.getOrDefault(g.getId(), 0L);

            List<Long> memberIds = membersByGroup
                    .getOrDefault(g.getId(), List.of())
                    .stream()
                    .map(GroupMember::getUserId)
                    .sorted()
                    .collect(Collectors.toList());

            List<MemberScoreSummaryResponse> summaries = memberIds.stream()
                    .map(uid -> buildForStudent(task, g.getId(), uid, peerAvg, teacherScoreMap, memberCount))
                    .sorted(Comparator.comparing(MemberScoreSummaryResponse::getStudentId))
                    .collect(Collectors.toList());

            result.put(g.getId(), summaries);
        }

        return result;
    }

}
