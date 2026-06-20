package com.teamtrace.backend.service;

import com.teamtrace.backend.domain.peerreview.PeerReviewDisplayCode;
import com.teamtrace.backend.dto.peerreview.PeerReviewAnonymousItem;
import com.teamtrace.backend.dto.peerreview.SubmitPeerReviewRequest;
import com.teamtrace.backend.entity.ClassEntity;
import com.teamtrace.backend.entity.GroupMember;
import com.teamtrace.backend.entity.PeerReview;
import com.teamtrace.backend.entity.Task;
import com.teamtrace.backend.entity.TaskGroup;
import com.teamtrace.backend.entity.User;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.repository.GroupMemberRepository;
import com.teamtrace.backend.repository.PeerReviewRepository;
import com.teamtrace.backend.repository.TaskRepository;
import com.teamtrace.backend.repository.UserRepository;
import com.teamtrace.backend.util.AppTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PeerReviewService {

    private final ClassRepository classRepository;
    private final TaskRepository taskRepository;
    private final SemesterGroupAccess semesterGroupAccess;
    private final GroupMemberRepository groupMemberRepository;
    private final PeerReviewRepository peerReviewRepository;
    private final ClassMembershipService classMembershipService;
    private final UserRepository userRepository;
    private final String jwtSecret;

    public PeerReviewService(
            ClassRepository classRepository,
            TaskRepository taskRepository,
            SemesterGroupAccess semesterGroupAccess,
            GroupMemberRepository groupMemberRepository,
            PeerReviewRepository peerReviewRepository,
            ClassMembershipService classMembershipService,
            UserRepository userRepository,
            @Value("${jwt.secret}") String jwtSecret) {
        this.classRepository = classRepository;
        this.taskRepository = taskRepository;
        this.semesterGroupAccess = semesterGroupAccess;
        this.groupMemberRepository = groupMemberRepository;
        this.peerReviewRepository = peerReviewRepository;
        this.classMembershipService = classMembershipService;
        this.userRepository = userRepository;
        this.jwtSecret = jwtSecret;
    }

    @Transactional
    public PeerReviewAnonymousItem submitForStudent(
            Long studentId, Long classId, Long taskId, Long groupId, SubmitPeerReviewRequest req) {
        requireStudentClassView(classId, studentId);
        Task task = requirePeerReviewEnabled(classId, taskId);
        TaskGroup g = semesterGroupAccess.requireGroupForTask(classId, taskId, groupId);
        requireMultiMemberGroup(groupId);
        requireStudentInGroup(studentId, g.getId());
        requirePeerReviewWindow(task);

        Long revieweeId = req.getRevieweeId();
        if (revieweeId.equals(studentId)) {
            throw new BusinessException("BAD_REQUEST", "不能评价自己", HttpStatus.BAD_REQUEST);
        }
        requireStudentInGroup(revieweeId, g.getId());

        BigDecimal score = normalizeScore(req.getScore(), task);
        String comment = trimToNull(req.getComment());

        PeerReview pr = peerReviewRepository
                .findByTaskIdAndGroupIdAndReviewerIdAndRevieweeIdAndIsDeleted(
                        taskId, groupId, studentId, revieweeId, 0)
                .orElseGet(() -> {
                    PeerReview next = new PeerReview();
                    next.setVersion(0);
                    return next;
                });
        pr.setTaskId(taskId);
        pr.setGroupId(groupId);
        pr.setReviewerId(studentId);
        pr.setRevieweeId(revieweeId);
        pr.setScore(score);
        pr.setComment(comment);
        pr.setSubmittedAt(AppTime.now());
        pr.setIsDeleted(0);
        peerReviewRepository.save(pr);

        Map<Long, String> names = loadUserNames(Set.of(studentId, revieweeId));
        return toStudentVisibleItem(taskId, groupId, studentId, pr, names);
    }

    @Transactional(readOnly = true)
    public List<PeerReviewAnonymousItem> listForStudent(Long studentId, Long classId, Long taskId, Long groupId) {
        requireStudentClassView(classId, studentId);
        requirePeerReviewEnabled(classId, taskId);
        TaskGroup g = semesterGroupAccess.requireGroupForTask(classId, taskId, groupId);
        requireStudentInGroup(studentId, g.getId());
        return loadStudentVisibleList(studentId, taskId, groupId);
    }

    @Transactional(readOnly = true)
    public List<PeerReviewAnonymousItem> listForTeacher(Long teacherId, Long classId, Long taskId, Long groupId) {
        requireTeacherClass(teacherId, classId);
        requirePeerReviewEnabled(classId, taskId);
        semesterGroupAccess.requireGroupForTask(classId, taskId, groupId);
        return loadTeacherVisibleList(taskId, groupId);
    }

    private List<PeerReviewAnonymousItem> loadStudentVisibleList(Long studentId, Long taskId, Long groupId) {
        List<PeerReview> reviews = peerReviewRepository
                .findByTaskIdAndGroupIdAndIsDeletedOrderBySubmittedAtAsc(taskId, groupId, 0)
                .stream()
                .toList();
        Set<Long> visibleUserIds = reviews.stream()
                .filter(pr -> studentId.equals(pr.getReviewerId()))
                .map(PeerReview::getRevieweeId)
                .collect(Collectors.toCollection(HashSet::new));
        visibleUserIds.add(studentId);
        Map<Long, String> names = loadUserNames(visibleUserIds);
        return reviews.stream()
                .map(pr -> toStudentVisibleItem(taskId, groupId, studentId, pr, names))
                .collect(Collectors.toList());
    }

    private List<PeerReviewAnonymousItem> loadTeacherVisibleList(Long taskId, Long groupId) {
        List<PeerReview> reviews = peerReviewRepository
                .findByTaskIdAndGroupIdAndIsDeletedOrderBySubmittedAtAsc(taskId, groupId, 0)
                .stream()
                .toList();
        Set<Long> visibleUserIds = reviews.stream()
                .flatMap(pr -> List.of(pr.getReviewerId(), pr.getRevieweeId()).stream())
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, String> names = loadUserNames(visibleUserIds);
        return reviews.stream()
                .map(pr -> toTeacherVisibleItem(taskId, groupId, pr, names))
                .collect(Collectors.toList());
    }

    private PeerReviewAnonymousItem toStudentVisibleItem(
            long taskId, long groupId, Long currentStudentId, PeerReview pr, Map<Long, String> names) {
        boolean isOwnOutgoingReview = currentStudentId != null && currentStudentId.equals(pr.getReviewerId());
        return PeerReviewAnonymousItem.builder()
                .reviewerId(isOwnOutgoingReview ? pr.getReviewerId() : null)
                .revieweeId(isOwnOutgoingReview ? pr.getRevieweeId() : null)
                .reviewerAlias(isOwnOutgoingReview ? null
                        : PeerReviewDisplayCode.forMember(taskId, groupId, pr.getReviewerId(), jwtSecret))
                .revieweeAlias(isOwnOutgoingReview ? null
                        : PeerReviewDisplayCode.forMember(taskId, groupId, pr.getRevieweeId(), jwtSecret))
                .reviewerName(isOwnOutgoingReview ? "我" : null)
                .revieweeName(isOwnOutgoingReview ? names.get(pr.getRevieweeId()) : null)
                .score(pr.getScore())
                .comment(pr.getComment())
                .submittedAt(pr.getSubmittedAt())
                .build();
    }

    private PeerReviewAnonymousItem toTeacherVisibleItem(long taskId, long groupId, PeerReview pr,
            Map<Long, String> names) {
        return PeerReviewAnonymousItem.builder()
                .reviewerId(pr.getReviewerId())
                .revieweeId(pr.getRevieweeId())
                .reviewerAlias(PeerReviewDisplayCode.forMember(taskId, groupId, pr.getReviewerId(), jwtSecret))
                .revieweeAlias(PeerReviewDisplayCode.forMember(taskId, groupId, pr.getRevieweeId(), jwtSecret))
                .reviewerName(names.get(pr.getReviewerId()))
                .revieweeName(names.get(pr.getRevieweeId()))
                .score(pr.getScore())
                .comment(pr.getComment())
                .submittedAt(pr.getSubmittedAt())
                .build();
    }

    private Map<Long, String> loadUserNames(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName, (left, right) -> left));
    }

    private Task requirePeerReviewEnabled(Long classId, Long taskId) {
        Task task = requireActiveTaskInClass(classId, taskId);
        if (task.getEnablePeerReview() == null || task.getEnablePeerReview() != 1) {
            throw new BusinessException("BAD_REQUEST", "本任务未开启互评", HttpStatus.BAD_REQUEST);
        }
        return task;
    }

    private void requirePeerReviewWindow(Task task) {
        LocalDateTime taskDeadline = task.getDeadline();
        if (taskDeadline != null && AppTime.now().isBefore(taskDeadline)) {
            throw new BusinessException("BAD_REQUEST", "互评尚未开始", HttpStatus.BAD_REQUEST);
        }
        LocalDateTime deadline = task.getPeerReviewDeadline();
        if (deadline != null && AppTime.now().isAfter(deadline)) {
            throw new BusinessException("BAD_REQUEST", "互评已截止", HttpStatus.BAD_REQUEST);
        }
    }

    private void requireMultiMemberGroup(Long groupId) {
        long n = groupMemberRepository.countByGroupIdAndStatus(groupId, GroupMember.STATUS_ACTIVE);
        if (n < 2) {
            throw new BusinessException("BAD_REQUEST", "一人成组不可互评", HttpStatus.BAD_REQUEST);
        }
    }

    private BigDecimal normalizeScore(BigDecimal raw, Task task) {
        if (raw == null) {
            throw new BusinessException("BAD_REQUEST", "分数不能为空", HttpStatus.BAD_REQUEST);
        }
        BigDecimal score = raw.setScale(1, RoundingMode.HALF_UP);
        int max = task.getPeerReviewMaxScore() != null ? task.getPeerReviewMaxScore() : 100;
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw new BusinessException("BAD_REQUEST", "分数须在 0～" + max + " 之间", HttpStatus.BAD_REQUEST);
        }
        return score;
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

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
