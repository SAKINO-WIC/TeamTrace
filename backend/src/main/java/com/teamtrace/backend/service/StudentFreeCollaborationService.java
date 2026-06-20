package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.freecollab.CollaborationActivityLogResponse;
import com.teamtrace.backend.dto.freecollab.CollaborationAttachmentResponse;
import com.teamtrace.backend.dto.freecollab.CollaborationDashboardResponse;
import com.teamtrace.backend.dto.freecollab.CollaborationProgressResponse;
import com.teamtrace.backend.dto.freecollab.CollaborationProjectResponse;
import com.teamtrace.backend.dto.freecollab.CollaborationSpaceInviteCodeResponse;
import com.teamtrace.backend.dto.freecollab.CollaborationSpaceMemberResponse;
import com.teamtrace.backend.dto.freecollab.CollaborationSpaceResponse;
import com.teamtrace.backend.dto.freecollab.CollaborationTaskDependencyResponse;
import com.teamtrace.backend.dto.freecollab.CollaborationTaskFlowNodeRequest;
import com.teamtrace.backend.dto.freecollab.CollaborationTaskFlowNodeResponse;
import com.teamtrace.backend.dto.freecollab.CollaborationTaskResponse;
import com.teamtrace.backend.dto.freecollab.CollaborationTaskSubmissionResponse;
import com.teamtrace.backend.dto.freecollab.CreateCollaborationProjectRequest;
import com.teamtrace.backend.dto.freecollab.CreateCollaborationProjectWithTasksRequest;
import com.teamtrace.backend.dto.freecollab.CreateCollaborationSpaceRequest;
import com.teamtrace.backend.dto.freecollab.CreateCollaborationTaskRequest;
import com.teamtrace.backend.dto.freecollab.ReviewCollaborationTaskRequest;
import com.teamtrace.backend.dto.freecollab.SubmitCollaborationTaskRequest;
import com.teamtrace.backend.dto.freecollab.UpdateCollaborationProjectRequest;
import com.teamtrace.backend.dto.freecollab.UpdateCollaborationTaskRequest;
import com.teamtrace.backend.domain.task.CollaborationTaskDependencyCycleValidator;
import com.teamtrace.backend.entity.CollaborationActivityLog;
import com.teamtrace.backend.entity.CollaborationProject;
import com.teamtrace.backend.entity.CollaborationSpace;
import com.teamtrace.backend.entity.CollaborationSpaceInviteCode;
import com.teamtrace.backend.entity.CollaborationSpaceMember;
import com.teamtrace.backend.entity.CollaborationTask;
import com.teamtrace.backend.entity.CollaborationTaskDependency;
import com.teamtrace.backend.entity.CollaborationTaskFlowNode;
import com.teamtrace.backend.entity.CollaborationTaskReview;
import com.teamtrace.backend.entity.CollaborationTaskSubmission;
import com.teamtrace.backend.entity.Notification;
import com.teamtrace.backend.entity.User;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.CollaborationActivityLogRepository;
import com.teamtrace.backend.repository.CollaborationProjectRepository;
import com.teamtrace.backend.repository.CollaborationSpaceInviteCodeRepository;
import com.teamtrace.backend.repository.CollaborationSpaceMemberRepository;
import com.teamtrace.backend.repository.CollaborationSpaceRepository;
import com.teamtrace.backend.repository.CollaborationTaskDependencyRepository;
import com.teamtrace.backend.repository.CollaborationTaskFlowNodeRepository;
import com.teamtrace.backend.repository.CollaborationTaskRepository;
import com.teamtrace.backend.repository.CollaborationTaskReviewRepository;
import com.teamtrace.backend.repository.CollaborationTaskSubmissionRepository;
import com.teamtrace.backend.repository.UserRepository;
import com.teamtrace.backend.util.AppTime;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentFreeCollaborationService {

    private static final int NOT_DELETED = 0;
    private static final int INVITE_CODE_HOURS = 72;
    private static final int MAX_CODE_ATTEMPTS = 8;
    private static final int DUE_SOON_HOURS = 72;
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final CollaborationSpaceRepository spaceRepository;
    private final CollaborationSpaceMemberRepository memberRepository;
    private final CollaborationSpaceInviteCodeRepository inviteCodeRepository;
    private final CollaborationProjectRepository projectRepository;
    private final CollaborationTaskRepository taskRepository;
    private final CollaborationTaskDependencyRepository dependencyRepository;
    private final CollaborationTaskFlowNodeRepository flowNodeRepository;
    private final CollaborationTaskSubmissionRepository submissionRepository;
    private final CollaborationTaskReviewRepository reviewRepository;
    private final CollaborationActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final CollaborationAttachmentService attachmentService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final CollaborationTaskDependencyCycleValidator dependencyCycleValidator =
            new CollaborationTaskDependencyCycleValidator();

    public StudentFreeCollaborationService(
            CollaborationSpaceRepository spaceRepository,
            CollaborationSpaceMemberRepository memberRepository,
            CollaborationSpaceInviteCodeRepository inviteCodeRepository,
            CollaborationProjectRepository projectRepository,
            CollaborationTaskRepository taskRepository,
            CollaborationTaskDependencyRepository dependencyRepository,
            CollaborationTaskFlowNodeRepository flowNodeRepository,
            CollaborationTaskSubmissionRepository submissionRepository,
            CollaborationTaskReviewRepository reviewRepository,
            CollaborationActivityLogRepository activityLogRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            CollaborationAttachmentService attachmentService) {
        this.spaceRepository = spaceRepository;
        this.memberRepository = memberRepository;
        this.inviteCodeRepository = inviteCodeRepository;
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.dependencyRepository = dependencyRepository;
        this.flowNodeRepository = flowNodeRepository;
        this.submissionRepository = submissionRepository;
        this.reviewRepository = reviewRepository;
        this.activityLogRepository = activityLogRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.attachmentService = attachmentService;
    }

    @Transactional(readOnly = true)
    public List<CollaborationSpaceResponse> listSpaces(Long studentId) {
        List<CollaborationSpaceMember> memberships =
                memberRepository.findByStudentIdAndIsDeletedOrderByIdDesc(studentId, NOT_DELETED);
        if (memberships.isEmpty()) {
            return List.of();
        }

        Map<Long, CollaborationSpaceMember> myMembershipBySpaceId = memberships.stream()
                .collect(Collectors.toMap(
                        CollaborationSpaceMember::getSpaceId,
                        item -> item,
                        (left, right) -> left));
        List<Long> spaceIds = new ArrayList<>(myMembershipBySpaceId.keySet());
        Map<Long, Long> memberCounts = loadMemberCounts(spaceIds);

        return spaceRepository.findByIdInAndIsDeletedOrderByIdDesc(spaceIds, NOT_DELETED).stream()
                .filter(space -> Objects.equals(space.getStatus(), CollaborationSpace.STATUS_ACTIVE))
                .map(space -> toSpaceResponse(space, myMembershipBySpaceId.get(space.getId()), memberCounts, false))
                .toList();
    }

    @Transactional
    public CollaborationSpaceResponse createSpace(Long studentId, CreateCollaborationSpaceRequest request) {
        String name = normalizeText(request.getName());
        if (name.isBlank()) {
            throw new BusinessException("BAD_REQUEST", "协作空间名称不能为空", HttpStatus.BAD_REQUEST);
        }

        CollaborationSpace space = new CollaborationSpace();
        space.setName(name);
        space.setDescription(normalizeOptionalText(request.getDescription()));
        space.setCreatorId(studentId);
        space.setStatus(CollaborationSpace.STATUS_ACTIVE);
        space.setIsDeleted(NOT_DELETED);
        CollaborationSpace savedSpace = spaceRepository.save(space);

        CollaborationSpaceMember member = new CollaborationSpaceMember();
        member.setSpaceId(savedSpace.getId());
        member.setStudentId(studentId);
        member.setRole(CollaborationSpaceMember.ROLE_OWNER);
        member.setIsDeleted(NOT_DELETED);
        member.setJoinedAt(AppTime.now());
        CollaborationSpaceMember savedMember = memberRepository.save(member);

        logActivity(savedSpace.getId(), null, null, studentId, "SPACE_CREATED", "创建了协作空间：" + name, null);
        return toSpaceResponse(savedSpace, savedMember, Map.of(savedSpace.getId(), 1L), true);
    }

    @Transactional(readOnly = true)
    public CollaborationSpaceResponse getSpace(Long studentId, Long spaceId) {
        CollaborationSpace space = requireActiveSpace(spaceId);
        CollaborationSpaceMember membership = requireActiveMember(spaceId, studentId);
        long memberCount = memberRepository.countBySpaceIdAndIsDeleted(spaceId, NOT_DELETED);
        return toSpaceResponse(space, membership, Map.of(spaceId, memberCount), true);
    }

    @Transactional
    public CollaborationSpaceInviteCodeResponse generateInviteCode(Long studentId, Long spaceId) {
        CollaborationSpace space = requireActiveSpace(spaceId);
        requireOwner(space, studentId);

        inviteCodeRepository.bulkUpdateStatusBySpaceIdAndStatus(
                spaceId,
                CollaborationSpaceInviteCode.STATUS_ACTIVE,
                CollaborationSpaceInviteCode.STATUS_REVOKED);

        LocalDateTime expiresAt = AppTime.now().plusHours(INVITE_CODE_HOURS);
        CollaborationSpaceInviteCode saved = insertInviteCodeWithRetry(spaceId, studentId, expiresAt);
        logActivity(spaceId, null, null, studentId, "INVITE_CODE_CREATED", "生成了新的空间邀请码", null);
        notifySpaceMembersExcept(
                spaceId,
                studentId,
                Notification.TYPE_COLLABORATION_SPACE,
                "自由协作空间邀请已更新",
                "空间「" + space.getName() + "」生成了新的邀请码。",
                spaceId);
        return CollaborationSpaceInviteCodeResponse.builder()
                .spaceId(spaceId)
                .code(saved.getCode())
                .expiresAt(saved.getExpiresAt())
                .build();
    }

    @Transactional
    public CollaborationSpaceResponse joinSpace(Long studentId, String inviteCode) {
        String code = normalizeText(inviteCode);
        if (code.isBlank()) {
            throw new BusinessException("BAD_REQUEST", "邀请码不能为空", HttpStatus.BAD_REQUEST);
        }

        CollaborationSpaceInviteCode invite = inviteCodeRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException("BAD_REQUEST", "邀请码无效", HttpStatus.BAD_REQUEST));
        if (!Objects.equals(invite.getStatus(), CollaborationSpaceInviteCode.STATUS_ACTIVE)) {
            throw new BusinessException("BAD_REQUEST", "邀请码已失效", HttpStatus.BAD_REQUEST);
        }
        if (invite.getExpiresAt() == null || AppTime.now().isAfter(invite.getExpiresAt())) {
            throw new BusinessException("BAD_REQUEST", "邀请码已过期", HttpStatus.BAD_REQUEST);
        }

        CollaborationSpace space = requireActiveSpace(invite.getSpaceId());
        CollaborationSpaceMember membership = memberRepository.findBySpaceIdAndStudentId(space.getId(), studentId)
                .map(existing -> reactivateMember(existing, AppTime.now()))
                .orElseGet(() -> createMember(space.getId(), studentId, AppTime.now()));

        String name = userRepository.findById(studentId).map(User::getName).orElse("新成员");
        logActivity(space.getId(), null, null, studentId, "SPACE_JOINED", name + " 加入了协作空间", null);
        notifyUserIfDifferent(
                space.getCreatorId(),
                studentId,
                Notification.TYPE_COLLABORATION_SPACE,
                "自由协作空间有新成员",
                name + " 加入了空间「" + space.getName() + "」。",
                space.getId());

        long memberCount = memberRepository.countBySpaceIdAndIsDeleted(space.getId(), NOT_DELETED);
        return toSpaceResponse(space, membership, Map.of(space.getId(), memberCount), true);
    }

    @Transactional
    public CollaborationSpaceResponse leaveSpace(Long studentId, Long spaceId) {
        CollaborationSpace space = requireActiveSpace(spaceId);
        CollaborationSpaceMember membership = requireActiveMember(spaceId, studentId);
        long memberCount = memberRepository.countBySpaceIdAndIsDeleted(spaceId, NOT_DELETED);

        if (Objects.equals(membership.getRole(), CollaborationSpaceMember.ROLE_OWNER) && memberCount > 1) {
            throw new BusinessException("BUSINESS_CONFLICT", "发起人需要先转让空间，再退出", HttpStatus.CONFLICT);
        }
        assertMemberHasNoOpenTasks(spaceId, studentId, "该成员还有未完成的负责或接收任务，暂不能退出");

        membership.setIsDeleted(1);
        memberRepository.save(membership);

        String actorName = userName(studentId);
        if (Objects.equals(membership.getRole(), CollaborationSpaceMember.ROLE_OWNER)) {
            space.setStatus(CollaborationSpace.STATUS_ARCHIVED);
            spaceRepository.save(space);
            logActivity(spaceId, null, null, studentId, "SPACE_ARCHIVED", actorName + " 退出并归档了协作空间", null);
            return toSpaceResponse(space, membership, Map.of(spaceId, 0L), false);
        }

        logActivity(spaceId, null, null, studentId, "SPACE_LEFT", actorName + " 退出了协作空间", null);
        notifySpaceMembersExcept(
                spaceId,
                studentId,
                Notification.TYPE_COLLABORATION_SPACE,
                "自由协作成员已退出",
                actorName + " 退出了空间「" + space.getName() + "」。",
                spaceId);
        return toSpaceResponse(space, membership, Map.of(spaceId, memberCount - 1), false);
    }

    @Transactional
    public CollaborationSpaceResponse removeMember(Long actorId, Long spaceId, Long targetStudentId) {
        CollaborationSpace space = requireActiveSpace(spaceId);
        requireOwner(space, actorId);
        if (Objects.equals(actorId, targetStudentId)) {
            throw new BusinessException("BAD_REQUEST", "不能移除自己，请使用退出或转让空间", HttpStatus.BAD_REQUEST);
        }

        CollaborationSpaceMember target = requireActiveMember(spaceId, targetStudentId);
        if (Objects.equals(target.getRole(), CollaborationSpaceMember.ROLE_OWNER)) {
            throw new BusinessException("BUSINESS_CONFLICT", "不能移除空间发起人", HttpStatus.CONFLICT);
        }
        assertMemberHasNoOpenTasks(spaceId, targetStudentId, "该成员还有未完成的负责或接收任务，暂不能移除");

        target.setIsDeleted(1);
        memberRepository.save(target);

        String targetName = userName(targetStudentId);
        logActivity(spaceId, null, null, actorId, "MEMBER_REMOVED", "移除了成员：" + targetName, null);
        notifyUserIfDifferent(
                targetStudentId,
                actorId,
                Notification.TYPE_COLLABORATION_SPACE,
                "你已被移出自由协作空间",
                "你已被移出空间「" + space.getName() + "」。",
                spaceId);
        notifySpaceMembersExcept(
                spaceId,
                actorId,
                Notification.TYPE_COLLABORATION_SPACE,
                "自由协作成员已调整",
                targetName + " 已离开空间「" + space.getName() + "」。",
                spaceId);

        CollaborationSpaceMember actorMembership = requireActiveMember(spaceId, actorId);
        long memberCount = memberRepository.countBySpaceIdAndIsDeleted(spaceId, NOT_DELETED);
        return toSpaceResponse(space, actorMembership, Map.of(spaceId, memberCount), true);
    }

    @Transactional
    public CollaborationSpaceResponse transferOwner(Long actorId, Long spaceId, Long newOwnerId) {
        CollaborationSpace space = requireActiveSpace(spaceId);
        CollaborationSpaceMember currentOwner = requireActiveMember(spaceId, actorId);
        if (!Objects.equals(currentOwner.getRole(), CollaborationSpaceMember.ROLE_OWNER)) {
            throw new BusinessException("FORBIDDEN", "只有发起人可以转让空间", HttpStatus.FORBIDDEN);
        }
        if (Objects.equals(actorId, newOwnerId)) {
            throw new BusinessException("BAD_REQUEST", "新发起人不能是自己", HttpStatus.BAD_REQUEST);
        }

        CollaborationSpaceMember newOwner = requireActiveMember(spaceId, newOwnerId);
        currentOwner.setRole(CollaborationSpaceMember.ROLE_MEMBER);
        newOwner.setRole(CollaborationSpaceMember.ROLE_OWNER);
        memberRepository.save(currentOwner);
        memberRepository.save(newOwner);

        String newOwnerName = userName(newOwnerId);
        logActivity(spaceId, null, null, actorId, "OWNER_TRANSFERRED", "转让发起人给：" + newOwnerName, null);
        notifyUserIfDifferent(
                newOwnerId,
                actorId,
                Notification.TYPE_COLLABORATION_SPACE,
                "你已成为自由协作空间发起人",
                "你已成为空间「" + space.getName() + "」的新发起人。",
                spaceId);
        notifySpaceMembersExcept(
                spaceId,
                actorId,
                Notification.TYPE_COLLABORATION_SPACE,
                "自由协作空间发起人已变更",
                "空间「" + space.getName() + "」的新发起人是 " + newOwnerName + "。",
                spaceId);

        long memberCount = memberRepository.countBySpaceIdAndIsDeleted(spaceId, NOT_DELETED);
        return toSpaceResponse(space, currentOwner, Map.of(spaceId, memberCount), true);
    }

    @Transactional(readOnly = true)
    public CollaborationDashboardResponse getDashboard(Long studentId) {
        List<CollaborationSpaceMember> memberships =
                memberRepository.findByStudentIdAndIsDeletedOrderByIdDesc(studentId, NOT_DELETED);
        if (memberships.isEmpty()) {
            return CollaborationDashboardResponse.builder()
                    .spaceCount(0)
                    .ownedSpaceCount(0)
                    .activeProjectCount(0)
                    .myActiveTaskCount(0)
                    .waitingForMeCount(0)
                    .waitingForOthersCount(0)
                    .dueSoonCount(0)
                    .myTasks(List.of())
                    .waitingForMe(List.of())
                    .waitingForOthers(List.of())
                    .dueSoonTasks(List.of())
                    .recentActivities(List.of())
                    .build();
        }

        List<Long> spaceIds = memberships.stream().map(CollaborationSpaceMember::getSpaceId).toList();
        List<CollaborationProject> projects = spaceIds.stream()
                .flatMap(spaceId -> projectRepository.findBySpaceIdOrderByIdDesc(spaceId).stream())
                .toList();
        List<Long> projectIds = projects.stream().map(CollaborationProject::getId).toList();
        List<CollaborationTask> tasks = projectIds.isEmpty()
                ? List.of()
                : activeTasks(taskRepository.findByProjectIdInOrderByIdAsc(projectIds));

        List<CollaborationTaskResponse> taskResponses = toTaskResponses(tasks, true);
        LocalDateTime now = AppTime.now();
        LocalDateTime dueSoon = now.plusHours(DUE_SOON_HOURS);
        List<CollaborationTaskResponse> myTasks = taskResponses.stream()
                .filter(task -> Objects.equals(task.getAssigneeId(), studentId))
                .filter(task -> isActiveTaskStatus(task.getStatus()))
                .limit(8)
                .toList();
        List<CollaborationTaskResponse> waitingForMe = taskResponses.stream()
                .filter(task -> Objects.equals(task.getReceiverId(), studentId))
                .filter(task -> Objects.equals(task.getStatus(), CollaborationTask.STATUS_WAITING_RECEIVE))
                .limit(8)
                .toList();
        List<CollaborationTaskResponse> waitingForOthers = taskResponses.stream()
                .filter(task -> Objects.equals(task.getAssigneeId(), studentId))
                .filter(task -> Objects.equals(task.getStatus(), CollaborationTask.STATUS_WAITING_RECEIVE))
                .limit(8)
                .toList();
        List<CollaborationTaskResponse> dueSoonTasks = taskResponses.stream()
                .filter(task -> task.getDueAt() != null)
                .filter(task -> !isDoneTaskStatus(task.getStatus()))
                .filter(task -> !task.getDueAt().isBefore(now) && !task.getDueAt().isAfter(dueSoon))
                .limit(8)
                .toList();

        List<CollaborationActivityLogResponse> recentActivities = spaceIds.stream()
                .flatMap(spaceId -> activityLogRepository.findTop80BySpaceIdOrderByIdDesc(spaceId).stream())
                .sorted(Comparator.comparing(CollaborationActivityLog::getId).reversed())
                .limit(12)
                .map(this::toActivityResponse)
                .toList();

        int ownedCount = (int) memberships.stream()
                .filter(item -> Objects.equals(item.getRole(), CollaborationSpaceMember.ROLE_OWNER))
                .count();
        int activeProjectCount = (int) projects.stream()
                .filter(project -> Objects.equals(project.getStatus(), CollaborationProject.STATUS_ACTIVE))
                .count();

        return CollaborationDashboardResponse.builder()
                .spaceCount(memberships.size())
                .ownedSpaceCount(ownedCount)
                .activeProjectCount(activeProjectCount)
                .myActiveTaskCount(myTasks.size())
                .waitingForMeCount(waitingForMe.size())
                .waitingForOthersCount(waitingForOthers.size())
                .dueSoonCount(dueSoonTasks.size())
                .myTasks(myTasks)
                .waitingForMe(waitingForMe)
                .waitingForOthers(waitingForOthers)
                .dueSoonTasks(dueSoonTasks)
                .recentActivities(recentActivities)
                .build();
    }

    @Transactional(readOnly = true)
    public List<CollaborationProjectResponse> listProjects(Long studentId, Long spaceId) {
        requireActiveSpace(spaceId);
        requireActiveMember(spaceId, studentId);
        return toProjectResponses(projectRepository.findBySpaceIdOrderByIdDesc(spaceId).stream()
                .filter(project -> !Objects.equals(project.getStatus(), CollaborationProject.STATUS_ARCHIVED))
                .toList(), false);
    }

    @Transactional
    public CollaborationProjectResponse createProject(
            Long studentId,
            Long spaceId,
            CreateCollaborationProjectRequest request) {
        CollaborationSpace space = requireActiveSpace(spaceId);
        requireOwner(space, studentId);
        String title = normalizeText(request.getTitle());
        if (title.isBlank()) {
            throw new BusinessException("BAD_REQUEST", "项目名称不能为空", HttpStatus.BAD_REQUEST);
        }

        CollaborationProject project = new CollaborationProject();
        project.setSpaceId(spaceId);
        project.setTitle(title);
        project.setDescription(normalizeOptionalText(request.getDescription()));
        project.setStartAt(request.getStartAt());
        project.setDueAt(request.getDueAt());
        project.setStatus(CollaborationProject.STATUS_ACTIVE);
        project.setCreatedBy(studentId);
        CollaborationProject saved = projectRepository.save(project);
        attachmentService.createForProject(spaceId, saved.getId(), request.getAttachments());
        logActivity(spaceId, saved.getId(), null, studentId, "PROJECT_CREATED", "创建了项目：" + title, null);
        return toProjectResponse(saved, List.of(), false);
    }

    @Transactional
    public CollaborationProjectResponse createProjectWithTasks(
            Long studentId,
            Long spaceId,
            CreateCollaborationProjectWithTasksRequest request) {
        CollaborationSpace space = requireActiveSpace(spaceId);
        requireOwner(space, studentId);

        CreateCollaborationProjectWithTasksRequest.ProjectPayload projectPayload = request.getProject();
        String title = normalizeText(projectPayload.getTitle());
        if (title.isBlank()) {
            throw new BusinessException("BAD_REQUEST", "项目名称不能为空", HttpStatus.BAD_REQUEST);
        }

        List<CreateCollaborationProjectWithTasksRequest.TaskPayload> taskPayloads =
                request.getTasks() == null ? List.of() : request.getTasks();
        if (taskPayloads.isEmpty()) {
            throw new BusinessException("BAD_REQUEST", "至少需要创建一个任务", HttpStatus.BAD_REQUEST);
        }

        Map<String, CreateCollaborationProjectWithTasksRequest.TaskPayload> tasksByLocalId =
                normalizeTaskPayloads(spaceId, taskPayloads);

        CollaborationProject project = new CollaborationProject();
        project.setSpaceId(spaceId);
        project.setTitle(title);
        project.setDescription(normalizeOptionalText(projectPayload.getDescription()));
        project.setStartAt(projectPayload.getStartAt());
        project.setDueAt(projectPayload.getDueAt());
        project.setStatus(CollaborationProject.STATUS_ACTIVE);
        project.setCreatedBy(studentId);
        CollaborationProject savedProject = projectRepository.save(project);
        attachmentService.createForProject(spaceId, savedProject.getId(), projectPayload.getAttachments());

        Map<String, CollaborationTask> savedTasksByLocalId = new LinkedHashMap<>();
        for (Map.Entry<String, CreateCollaborationProjectWithTasksRequest.TaskPayload> entry : tasksByLocalId.entrySet()) {
            CreateCollaborationProjectWithTasksRequest.TaskPayload taskPayload = entry.getValue();
            CollaborationTask savedTask = taskRepository.save(buildTask(
                    spaceId,
                    savedProject.getId(),
                    studentId,
                    taskPayload));
            attachmentService.createForTask(spaceId, savedProject.getId(), savedTask.getId(), taskPayload.getAttachments());
            saveFlowNodes(savedTask, taskPayload.getFlowNodes());
            savedTask = syncTaskWithCurrentFlowNode(savedTask);
            savedTasksByLocalId.put(entry.getKey(), savedTask);
            logActivity(
                    spaceId,
                    savedProject.getId(),
                    savedTask.getId(),
                    studentId,
                    "TASK_CREATED",
                    "创建了任务：" + savedTask.getTitle(),
                    null);
        }

        linkParentTasks(savedTasksByLocalId, tasksByLocalId);
        saveTaskDependencies(savedTasksByLocalId, tasksByLocalId);
        logActivity(spaceId, savedProject.getId(), null, studentId, "PROJECT_CREATED", "创建了项目：" + title, null);

        return toProjectResponse(savedProject, new ArrayList<>(savedTasksByLocalId.values()), true);
    }

    @Transactional(readOnly = true)
    public CollaborationProjectResponse getProject(Long studentId, Long spaceId, Long projectId) {
        requireActiveSpace(spaceId);
        requireActiveMember(spaceId, studentId);
        CollaborationProject project = requireProject(spaceId, projectId);
        List<CollaborationTask> tasks = activeTasks(taskRepository.findByProjectIdOrderByIdAsc(projectId));
        return toProjectResponse(project, tasks, true);
    }

    @Transactional
    public CollaborationProjectResponse updateProject(
            Long studentId,
            Long spaceId,
            Long projectId,
            UpdateCollaborationProjectRequest request) {
        CollaborationSpace space = requireActiveSpace(spaceId);
        requireOwner(space, studentId);
        CollaborationProject project = requireProject(spaceId, projectId);

        String title = normalizeOptionalText(request.getTitle());
        if (title != null) {
            project.setTitle(title);
        }
        project.setDescription(request.getDescription() == null
                ? project.getDescription()
                : normalizeOptionalText(request.getDescription()));
        if (request.getStartAt() != null) {
            project.setStartAt(request.getStartAt());
        }
        if (request.getDueAt() != null) {
            project.setDueAt(request.getDueAt());
        }
        if (isValidProjectStatus(request.getStatus())) {
            project.setStatus(request.getStatus());
        }

        CollaborationProject saved = projectRepository.save(project);
        if (request.getAttachments() != null) {
            attachmentService.replaceForProject(spaceId, projectId, request.getAttachments());
        }
        logActivity(spaceId, projectId, null, studentId, "PROJECT_UPDATED", "更新了项目：" + saved.getTitle(), null);
        return toProjectResponse(saved, activeTasks(taskRepository.findByProjectIdOrderByIdAsc(projectId)), true);
    }

    @Transactional
    public CollaborationProjectResponse archiveProject(Long studentId, Long spaceId, Long projectId) {
        CollaborationSpace space = requireActiveSpace(spaceId);
        requireOwner(space, studentId);
        CollaborationProject project = requireProject(spaceId, projectId);
        if (Objects.equals(project.getStatus(), CollaborationProject.STATUS_ARCHIVED)) {
            return toProjectResponse(project, List.of(), true);
        }

        project.setStatus(CollaborationProject.STATUS_ARCHIVED);
        CollaborationProject saved = projectRepository.save(project);
        List<CollaborationTask> tasks = taskRepository.findByProjectIdOrderByIdAsc(projectId);
        tasks.stream()
                .filter(task -> !Objects.equals(task.getStatus(), CollaborationTask.STATUS_COMPLETED))
                .filter(task -> !Objects.equals(task.getStatus(), CollaborationTask.STATUS_ARCHIVED))
                .forEach(task -> task.setStatus(CollaborationTask.STATUS_ARCHIVED));
        if (!tasks.isEmpty()) {
            taskRepository.saveAll(tasks);
        }

        logActivity(spaceId, projectId, null, studentId, "PROJECT_ARCHIVED", "归档了项目：" + saved.getTitle(), null);
        notifySpaceMembersExcept(
                spaceId,
                studentId,
                Notification.TYPE_COLLABORATION_SPACE,
                "自由协作项目已归档",
                "项目「" + saved.getTitle() + "」已归档。",
                projectId);
        return toProjectResponse(saved, activeTasks(tasks), true);
    }

    @Transactional(readOnly = true)
    public List<CollaborationTaskResponse> listTasks(Long studentId, Long spaceId, Long projectId) {
        requireActiveSpace(spaceId);
        requireActiveMember(spaceId, studentId);
        requireProject(spaceId, projectId);
        return toTaskResponses(activeTasks(taskRepository.findByProjectIdOrderByIdAsc(projectId)), true);
    }

    @Transactional
    public CollaborationTaskResponse createTask(
            Long studentId,
            Long spaceId,
            Long projectId,
            CreateCollaborationTaskRequest request) {
        CollaborationSpace space = requireActiveSpace(spaceId);
        requireOwner(space, studentId);
        CollaborationProject project = requireProject(spaceId, projectId);
        if (Objects.equals(project.getStatus(), CollaborationProject.STATUS_ARCHIVED)) {
            throw new BusinessException("BUSINESS_CONFLICT", "已归档项目不能追加任务", HttpStatus.CONFLICT);
        }

        String title = normalizeText(request.getTitle());
        if (title.isBlank()) {
            throw new BusinessException("BAD_REQUEST", "任务名称不能为空", HttpStatus.BAD_REQUEST);
        }
        validateMemberIfPresent(spaceId, request.getAssigneeId(), "负责人不在该协作空间内");
        validateMemberIfPresent(spaceId, request.getReceiverId(), "接收人不在该协作空间内");

        CollaborationTask task = new CollaborationTask();
        task.setSpaceId(spaceId);
        task.setProjectId(projectId);
        task.setParentTaskId(request.getParentTaskId());
        task.setTitle(title);
        task.setDescription(normalizeOptionalText(request.getDescription()));
        task.setDeliverableRequirements(normalizeOptionalText(request.getDeliverableRequirements()));
        task.setAssigneeId(request.getAssigneeId());
        task.setReceiverId(request.getReceiverId());
        task.setCreatedBy(studentId);
        task.setClaimMode(request.getAssigneeId() == null
                ? CollaborationTask.CLAIM_MODE_OPEN
                : CollaborationTask.CLAIM_MODE_ASSIGNED);
        task.setStatus(request.getAssigneeId() == null
                ? CollaborationTask.STATUS_UNCLAIMED
                : CollaborationTask.STATUS_CLAIMED);
        task.setStartAt(request.getStartAt());
        task.setDueAt(request.getDueAt());
        CollaborationTask saved = taskRepository.save(task);
        attachmentService.createForTask(spaceId, projectId, saved.getId(), request.getAttachments());
        saveFlowNodes(saved, request.getFlowNodes());
        saved = syncTaskWithCurrentFlowNode(saved);
        replaceDependencies(saved, request.getDependsOnTaskIds());
        logActivity(spaceId, projectId, saved.getId(), studentId, "TASK_CREATED", "创建了任务：" + title, null);
        notifyTaskAssignee(saved, studentId, "自由协作任务已分配", "你被分配了任务「" + saved.getTitle() + "」。");
        notifyTaskReceiver(saved, studentId, "自由协作任务需要接收", "任务「" + saved.getTitle() + "」指定你作为接收人。");
        return toTaskResponse(saved, true);
    }

    @Transactional
    public CollaborationTaskResponse archiveTask(Long studentId, Long spaceId, Long projectId, Long taskId) {
        CollaborationSpace space = requireActiveSpace(spaceId);
        requireOwner(space, studentId);
        requireProject(spaceId, projectId);
        CollaborationTask task = requireTask(spaceId, projectId, taskId);
        if (Objects.equals(task.getStatus(), CollaborationTask.STATUS_ARCHIVED)) {
            return toTaskResponse(task, true);
        }

        task.setStatus(CollaborationTask.STATUS_ARCHIVED);
        CollaborationTask saved = taskRepository.save(task);
        logActivity(spaceId, projectId, taskId, studentId, "TASK_ARCHIVED", "归档了任务：" + saved.getTitle(), null);
        notifyTaskAssignee(saved, studentId, "自由协作任务已归档", "任务「" + saved.getTitle() + "」已归档。");
        notifyTaskReceiver(saved, studentId, "自由协作任务已归档", "任务「" + saved.getTitle() + "」已归档。");
        return toTaskResponse(saved, true);
    }

    @Transactional
    public CollaborationTaskResponse updateTask(
            Long studentId,
            Long spaceId,
            Long projectId,
            Long taskId,
            UpdateCollaborationTaskRequest request) {
        CollaborationSpace space = requireActiveSpace(spaceId);
        requireProject(spaceId, projectId);
        CollaborationTask task = requireTask(spaceId, projectId, taskId);
        assertTaskNotArchived(task);

        boolean isOwner = isOwner(space, studentId);
        boolean isAssignee = Objects.equals(task.getAssigneeId(), studentId);
        boolean isAllowedToStart = isAssignee && isStartProgressRequest(request) && canStartProgress(task);

        if (!isOwner && !isAllowedToStart) {
            throw new BusinessException("FORBIDDEN", "只有发起人或负责人可以更新任务", HttpStatus.FORBIDDEN);
        }

        validateMemberIfPresent(spaceId, request.getAssigneeId(), "负责人不在该协作空间内");
        validateMemberIfPresent(spaceId, request.getReceiverId(), "接收人不在该协作空间内");

        String title = normalizeOptionalText(request.getTitle());
        if (title != null && isOwner) {
            task.setTitle(title);
        }
        if (request.getDescription() != null && isOwner) {
            task.setDescription(normalizeOptionalText(request.getDescription()));
        }
        if (request.getDeliverableRequirements() != null && isOwner) {
            task.setDeliverableRequirements(normalizeOptionalText(request.getDeliverableRequirements()));
        }
        if (request.getAssigneeId() != null && isOwner) {
            task.setAssigneeId(request.getAssigneeId());
            task.setClaimMode(CollaborationTask.CLAIM_MODE_ASSIGNED);
            if (Objects.equals(task.getStatus(), CollaborationTask.STATUS_UNCLAIMED)) {
                task.setStatus(CollaborationTask.STATUS_CLAIMED);
            }
        }
        if (request.getReceiverId() != null && isOwner) {
            task.setReceiverId(request.getReceiverId());
        }
        if (request.getStartAt() != null && isOwner) {
            task.setStartAt(request.getStartAt());
        }
        if (request.getDueAt() != null && isOwner) {
            task.setDueAt(request.getDueAt());
        }
        if (isOwner && isValidTaskStatus(request.getStatus())) {
            task.setStatus(request.getStatus());
        }
        if (isAllowedToStart) {
            task.setStatus(CollaborationTask.STATUS_IN_PROGRESS);
        }
        CollaborationTask saved = taskRepository.save(task);
        if (request.getDependsOnTaskIds() != null && isOwner) {
            replaceDependencies(saved, request.getDependsOnTaskIds());
        }
        if (request.getAttachments() != null && isOwner) {
            attachmentService.replaceForTask(spaceId, projectId, taskId, request.getAttachments());
        }
        if (request.getFlowNodes() != null && isOwner) {
            replaceFlowNodes(saved, request.getFlowNodes());
            saved = syncTaskWithCurrentFlowNode(saved);
        }
        logActivity(spaceId, projectId, taskId, studentId, "TASK_UPDATED", "更新了任务：" + saved.getTitle(), null);
        if (isOwner) {
            notifyTaskAssignee(saved, studentId, "自由协作任务已更新", "任务「" + saved.getTitle() + "」有新的安排。");
            notifyTaskReceiver(saved, studentId, "自由协作接收安排已更新", "任务「" + saved.getTitle() + "」的接收安排有更新。");
        }
        return toTaskResponse(saved, true);
    }

    @Transactional
    public CollaborationTaskResponse claimTask(Long studentId, Long spaceId, Long projectId, Long taskId) {
        requireActiveSpace(spaceId);
        requireActiveMember(spaceId, studentId);
        requireProject(spaceId, projectId);
        CollaborationTask task = requireTask(spaceId, projectId, taskId);
        assertTaskNotArchived(task);
        if (!Objects.equals(task.getStatus(), CollaborationTask.STATUS_UNCLAIMED) || task.getAssigneeId() != null) {
            throw new BusinessException("BUSINESS_CONFLICT", "该任务已被认领或已开始", HttpStatus.CONFLICT);
        }
        task.setAssigneeId(studentId);
        task.setStatus(CollaborationTask.STATUS_CLAIMED);
        CollaborationTask saved = taskRepository.save(task);
        logActivity(spaceId, projectId, taskId, studentId, "TASK_CLAIMED", "认领了任务：" + saved.getTitle(), null);
        notifyUserIfDifferent(
                saved.getCreatedBy(),
                studentId,
                Notification.TYPE_COLLABORATION_TASK,
                "自由协作任务已被认领",
                userName(studentId) + " 认领了任务「" + saved.getTitle() + "」。",
                saved.getId());
        return toTaskResponse(saved, true);
    }

    @Transactional
    public CollaborationTaskResponse claimFlowNode(Long studentId, Long spaceId, Long projectId, Long taskId, Long nodeId) {
        requireActiveSpace(spaceId);
        requireActiveMember(spaceId, studentId);
        requireProject(spaceId, projectId);
        CollaborationTask task = requireTask(spaceId, projectId, taskId);
        assertTaskNotArchived(task);
        CollaborationTaskFlowNode node = flowNodeRepository.findByIdAndTaskId(nodeId, taskId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "流程节点不存在", HttpStatus.NOT_FOUND));
        if (!Boolean.TRUE.equals(node.getClaimable())
                || !Objects.equals(node.getStatus(), CollaborationTaskFlowNode.STATUS_UNCLAIMED)
                || node.getAssigneeId() != null) {
            throw new BusinessException("BUSINESS_CONFLICT", "该流程节点不可认领", HttpStatus.CONFLICT);
        }
        node.setAssigneeId(studentId);
        node.setStatus(CollaborationTaskFlowNode.STATUS_CLAIMED);
        node.setStartedAt(AppTime.now());
        flowNodeRepository.save(node);
        CollaborationTask saved = syncTaskWithCurrentFlowNode(task);
        logActivity(spaceId, projectId, taskId, studentId, "FLOW_NODE_CLAIMED", "认领了协作环节：" + node.getTitle(), null);
        return toTaskResponse(saved, true);
    }

    @Transactional
    public CollaborationTaskResponse submitTask(
            Long studentId,
            Long spaceId,
            Long projectId,
            Long taskId,
            SubmitCollaborationTaskRequest request) {
        requireActiveSpace(spaceId);
        requireActiveMember(spaceId, studentId);
        requireProject(spaceId, projectId);
        CollaborationTask task = requireTask(spaceId, projectId, taskId);
        assertTaskNotArchived(task);
        CollaborationTaskFlowNode currentNode = findCurrentFlowNode(task).orElse(null);
        if (currentNode != null) {
            if (request.getFlowNodeId() != null && !Objects.equals(request.getFlowNodeId(), currentNode.getId())) {
                throw new BusinessException("BUSINESS_CONFLICT", "提交节点不是当前处理节点", HttpStatus.CONFLICT);
            }
            if (!Objects.equals(currentNode.getAssigneeId(), studentId)) {
                throw new BusinessException("FORBIDDEN", "只有当前节点负责人可以提交结果", HttpStatus.FORBIDDEN);
            }
        } else if (!Objects.equals(task.getAssigneeId(), studentId)) {
            throw new BusinessException("FORBIDDEN", "只有任务负责人可以提交结果", HttpStatus.FORBIDDEN);
        }
        if (Objects.equals(task.getStatus(), CollaborationTask.STATUS_COMPLETED)) {
            throw new BusinessException("BUSINESS_CONFLICT", "已完成的任务不能再次提交", HttpStatus.CONFLICT);
        }
        assertDependenciesCompleted(task);

        CollaborationTaskSubmission submission = new CollaborationTaskSubmission();
        submission.setTaskId(taskId);
        submission.setFlowNodeId(currentNode == null ? null : currentNode.getId());
        submission.setSubmittedBy(studentId);
        submission.setContent(normalizeOptionalText(request.getContent()));
        submission.setAttachmentsJson(normalizeOptionalText(request.getAttachmentsJson()));
        submission.setLinksJson(normalizeOptionalText(request.getLinksJson()));
        submission.setVersionNo((int) submissionRepository.countByTaskId(taskId) + 1);
        submission.setStatus(CollaborationTaskSubmission.STATUS_SUBMITTED);
        submissionRepository.save(submission);

        LocalDateTime now = AppTime.now();
        if (currentNode == null) {
            task.setStatus(CollaborationTask.STATUS_WAITING_RECEIVE);
            task.setSubmittedAt(now);
        } else {
            currentNode.setStatus(CollaborationTaskFlowNode.STATUS_COMPLETED);
            currentNode.setSubmittedAt(now);
            currentNode.setCompletedAt(now);
            flowNodeRepository.save(currentNode);
            task = moveToNextFlowNodeOrReview(task, currentNode, now);
        }
        CollaborationTask saved = taskRepository.save(task);
        logActivity(spaceId, projectId, taskId, studentId, "TASK_SUBMITTED", "提交了任务结果：" + saved.getTitle(), null);
        if (Objects.equals(saved.getStatus(), CollaborationTask.STATUS_WAITING_RECEIVE)) {
            notifyTaskReceiver(
                    saved,
                    studentId,
                    "自由协作任务待接收",
                    "任务「" + saved.getTitle() + "」已提交，请确认接收或打回。");
        } else {
            notifyTaskAssignee(saved, studentId, "自由协作任务已流转", "任务「" + saved.getTitle() + "」已流转到你处理。");
        }
        return toTaskResponse(saved, true);
    }

    @Transactional
    public CollaborationTaskResponse acceptTask(
            Long studentId,
            Long spaceId,
            Long projectId,
            Long taskId,
            ReviewCollaborationTaskRequest request) {
        return reviewTask(studentId, spaceId, projectId, taskId, request, true);
    }

    @Transactional
    public CollaborationTaskResponse returnTask(
            Long studentId,
            Long spaceId,
            Long projectId,
            Long taskId,
            ReviewCollaborationTaskRequest request) {
        return reviewTask(studentId, spaceId, projectId, taskId, request, false);
    }

    @Transactional(readOnly = true)
    public CollaborationProgressResponse getProgress(Long studentId, Long spaceId, Long projectId) {
        requireActiveSpace(spaceId);
        requireActiveMember(spaceId, studentId);
        CollaborationProject project = requireProject(spaceId, projectId);
        List<CollaborationTask> tasks = activeTasks(taskRepository.findByProjectIdOrderByIdAsc(projectId));
        List<CollaborationTaskResponse> taskResponses = toTaskResponses(tasks, true);
        List<Long> taskIds = tasks.stream().map(CollaborationTask::getId).toList();
        Set<Long> activeTaskIds = new HashSet<>(taskIds);
        List<CollaborationTaskDependencyResponse> dependencies = dependencyRepository.findByTaskIdIn(taskIds).stream()
                .filter(dep -> activeTaskIds.contains(dep.getTaskId()))
                .filter(dep -> activeTaskIds.contains(dep.getDependsOnTaskId()))
                .map(dep -> CollaborationTaskDependencyResponse.builder()
                        .id(dep.getId())
                        .taskId(dep.getTaskId())
                        .dependsOnTaskId(dep.getDependsOnTaskId())
                        .build())
                .toList();
        int completed = countCompleted(tasks);
        int waiting = countWaitingReceive(tasks);
        int overdue = countOverdue(tasks);
        double completionRate = tasks.isEmpty() ? 0.0 : Math.round((completed * 10000.0) / tasks.size()) / 100.0;

        return CollaborationProgressResponse.builder()
                .project(toProjectResponse(project, tasks, false))
                .taskCount(tasks.size())
                .completedTaskCount(completed)
                .waitingReceiveCount(waiting)
                .overdueTaskCount(overdue)
                .completionRate(completionRate)
                .tasks(taskResponses)
                .dependencies(dependencies)
                .build();
    }

    @Transactional(readOnly = true)
    public List<CollaborationActivityLogResponse> listActivityLogs(Long studentId, Long spaceId, Long projectId) {
        requireActiveSpace(spaceId);
        requireActiveMember(spaceId, studentId);
        List<CollaborationActivityLog> logs = projectId == null
                ? activityLogRepository.findTop80BySpaceIdOrderByIdDesc(spaceId)
                : activityLogRepository.findTop80BySpaceIdAndProjectIdOrderByIdDesc(spaceId, projectId);
        return logs.stream().map(this::toActivityResponse).toList();
    }

    private CollaborationTaskResponse reviewTask(
            Long studentId,
            Long spaceId,
            Long projectId,
            Long taskId,
            ReviewCollaborationTaskRequest request,
            boolean accepted) {
        requireActiveSpace(spaceId);
        requireActiveMember(spaceId, studentId);
        requireProject(spaceId, projectId);
        CollaborationTask task = requireTask(spaceId, projectId, taskId);
        assertTaskNotArchived(task);
        if (!Objects.equals(task.getStatus(), CollaborationTask.STATUS_WAITING_RECEIVE)) {
            throw new BusinessException("BUSINESS_CONFLICT", "该任务当前不在待接收状态", HttpStatus.CONFLICT);
        }
        if (task.getReceiverId() != null && !Objects.equals(task.getReceiverId(), studentId)) {
            throw new BusinessException("FORBIDDEN", "只有任务接收人可以处理该提交", HttpStatus.FORBIDDEN);
        }

        CollaborationTaskSubmission latest = submissionRepository.findFirstByTaskIdOrderByVersionNoDesc(taskId)
                .orElseThrow(() -> new BusinessException("BUSINESS_CONFLICT", "该任务还没有提交记录", HttpStatus.CONFLICT));
        latest.setStatus(accepted
                ? CollaborationTaskSubmission.STATUS_ACCEPTED
                : CollaborationTaskSubmission.STATUS_RETURNED);
        submissionRepository.save(latest);

        CollaborationTaskReview review = new CollaborationTaskReview();
        review.setTaskId(taskId);
        review.setSubmissionId(latest.getId());
        review.setReviewerId(studentId);
        review.setResult(accepted
                ? CollaborationTaskReview.RESULT_ACCEPTED
                : CollaborationTaskReview.RESULT_RETURNED);
        review.setComment(normalizeOptionalText(request.getComment()));
        reviewRepository.save(review);

        if (accepted) {
            LocalDateTime now = AppTime.now();
            task.setStatus(CollaborationTask.STATUS_COMPLETED);
            task.setAcceptedAt(now);
            task.setCompletedAt(now);
        } else {
            task.setStatus(CollaborationTask.STATUS_RETURNED);
        }
        CollaborationTask saved = taskRepository.save(task);
        logActivity(
                spaceId,
                projectId,
                taskId,
                studentId,
                accepted ? "TASK_ACCEPTED" : "TASK_RETURNED",
                (accepted ? "接收了任务：" : "打回了任务：") + saved.getTitle(),
                null);
        notifyUserIfDifferent(
                saved.getAssigneeId(),
                studentId,
                Notification.TYPE_COLLABORATION_REVIEW,
                accepted ? "自由协作任务已接收" : "自由协作任务被打回",
                accepted
                        ? "任务「" + saved.getTitle() + "」已被接收。"
                        : "任务「" + saved.getTitle() + "」被打回，请重新处理。",
                saved.getId());
        return toTaskResponse(saved, true);
    }

    private CollaborationSpace requireActiveSpace(Long spaceId) {
        CollaborationSpace space = spaceRepository.findByIdAndIsDeleted(spaceId, NOT_DELETED)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "协作空间不存在", HttpStatus.NOT_FOUND));
        if (!Objects.equals(space.getStatus(), CollaborationSpace.STATUS_ACTIVE)) {
            throw new BusinessException("BAD_REQUEST", "协作空间已归档", HttpStatus.BAD_REQUEST);
        }
        return space;
    }

    private CollaborationSpaceMember requireActiveMember(Long spaceId, Long studentId) {
        return memberRepository.findBySpaceIdAndStudentIdAndIsDeleted(spaceId, studentId, NOT_DELETED)
                .orElseThrow(() -> new BusinessException("FORBIDDEN", "你尚未加入该协作空间", HttpStatus.FORBIDDEN));
    }

    private void requireOwner(CollaborationSpace space, Long studentId) {
        CollaborationSpaceMember member = requireActiveMember(space.getId(), studentId);
        if (!Objects.equals(member.getRole(), CollaborationSpaceMember.ROLE_OWNER)) {
            throw new BusinessException("FORBIDDEN", "只有发起人可以执行该操作", HttpStatus.FORBIDDEN);
        }
    }

    private void assertMemberHasNoOpenTasks(Long spaceId, Long studentId, String message) {
        long openTaskCount = taskRepository.countOpenTasksForMember(
                spaceId,
                studentId,
                CollaborationTask.STATUS_COMPLETED);
        if (openTaskCount > 0) {
            throw new BusinessException("BUSINESS_CONFLICT", message, HttpStatus.CONFLICT);
        }
    }

    private CollaborationProject requireProject(Long spaceId, Long projectId) {
        return projectRepository.findByIdAndSpaceId(projectId, spaceId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND));
    }

    private CollaborationTask requireTask(Long spaceId, Long projectId, Long taskId) {
        return taskRepository.findByIdAndSpaceIdAndProjectId(taskId, spaceId, projectId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND));
    }

    private void validateMemberIfPresent(Long spaceId, Long userId, String message) {
        if (userId == null) {
            return;
        }
        memberRepository.findBySpaceIdAndStudentIdAndIsDeleted(spaceId, userId, NOT_DELETED)
                .orElseThrow(() -> new BusinessException("BAD_REQUEST", message, HttpStatus.BAD_REQUEST));
    }

    private boolean isOwner(CollaborationSpace space, Long studentId) {
        CollaborationSpaceMember member = requireActiveMember(space.getId(), studentId);
        return Objects.equals(member.getRole(), CollaborationSpaceMember.ROLE_OWNER);
    }

    private boolean isStartProgressRequest(UpdateCollaborationTaskRequest request) {
        return request.getStatus() != null && Objects.equals(request.getStatus(), CollaborationTask.STATUS_IN_PROGRESS)
                && request.getTitle() == null
                && request.getDescription() == null
                && request.getDeliverableRequirements() == null
                && request.getAssigneeId() == null
                && request.getReceiverId() == null
                && request.getStartAt() == null
                && request.getDueAt() == null
                && request.getDependsOnTaskIds() == null;
    }

    private boolean canStartProgress(CollaborationTask task) {
        return Objects.equals(task.getStatus(), CollaborationTask.STATUS_CLAIMED)
                || Objects.equals(task.getStatus(), CollaborationTask.STATUS_RETURNED);
    }

    private void assertTaskNotArchived(CollaborationTask task) {
        if (Objects.equals(task.getStatus(), CollaborationTask.STATUS_ARCHIVED)) {
            throw new BusinessException("BUSINESS_CONFLICT", "已归档任务不能继续流转", HttpStatus.CONFLICT);
        }
    }

    private List<CollaborationTask> activeTasks(List<CollaborationTask> tasks) {
        return tasks.stream()
                .filter(task -> !Objects.equals(task.getStatus(), CollaborationTask.STATUS_ARCHIVED))
                .toList();
    }

    private void assertDependenciesCompleted(CollaborationTask task) {
        List<Long> dependencyIds = dependencyRepository.findByTaskIdIn(List.of(task.getId())).stream()
                .map(CollaborationTaskDependency::getDependsOnTaskId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (dependencyIds.isEmpty()) {
            return;
        }
        Map<Long, String> statusByTaskId = taskRepository.findAllById(dependencyIds).stream()
                .filter(dependency -> Objects.equals(dependency.getProjectId(), task.getProjectId()))
                .collect(Collectors.toMap(
                        CollaborationTask::getId,
                        CollaborationTask::getStatus,
                        (left, right) -> left));
        boolean hasUnfinishedDependency = dependencyIds.stream()
                .anyMatch(id -> !Objects.equals(statusByTaskId.get(id), CollaborationTask.STATUS_COMPLETED));
        if (hasUnfinishedDependency) {
            throw new BusinessException("BUSINESS_CONFLICT", "前置任务未完成，暂不能提交该任务", HttpStatus.CONFLICT);
        }
    }

    private CollaborationSpaceMember reactivateMember(CollaborationSpaceMember existing, LocalDateTime now) {
        if (Objects.equals(existing.getIsDeleted(), NOT_DELETED)) {
            throw new BusinessException("BUSINESS_CONFLICT", "你已加入该协作空间", HttpStatus.CONFLICT);
        }
        existing.setIsDeleted(NOT_DELETED);
        existing.setJoinedAt(now);
        return memberRepository.save(existing);
    }

    private CollaborationSpaceMember createMember(Long spaceId, Long studentId, LocalDateTime now) {
        CollaborationSpaceMember member = new CollaborationSpaceMember();
        member.setSpaceId(spaceId);
        member.setStudentId(studentId);
        member.setRole(CollaborationSpaceMember.ROLE_MEMBER);
        member.setIsDeleted(NOT_DELETED);
        member.setJoinedAt(now);
        return memberRepository.save(member);
    }

    private CollaborationSpaceInviteCode insertInviteCodeWithRetry(Long spaceId, Long studentId, LocalDateTime expiresAt) {
        for (int i = 0; i < MAX_CODE_ATTEMPTS; i++) {
            CollaborationSpaceInviteCode invite = new CollaborationSpaceInviteCode();
            invite.setSpaceId(spaceId);
            invite.setCreatedBy(studentId);
            invite.setCode(generateCode());
            invite.setStatus(CollaborationSpaceInviteCode.STATUS_ACTIVE);
            invite.setExpiresAt(expiresAt);
            try {
                return inviteCodeRepository.save(invite);
            } catch (DataIntegrityViolationException ex) {
                // 唯一码冲突，重新生成。
            }
        }
        throw new BusinessException("INTERNAL_ERROR", "生成邀请码失败，请重试", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private void replaceDependencies(CollaborationTask task, List<Long> dependsOnTaskIds) {
        List<Long> normalized = dependsOnTaskIds == null
                ? List.of()
                : dependsOnTaskIds.stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        validateTaskDependencies(task, normalized);
        dependencyRepository.deleteByTaskId(task.getId());
        if (normalized.isEmpty()) {
            return;
        }
        for (Long dependsOnTaskId : normalized) {
            CollaborationTaskDependency dependency = new CollaborationTaskDependency();
            dependency.setTaskId(task.getId());
            dependency.setDependsOnTaskId(dependsOnTaskId);
            dependencyRepository.save(dependency);
        }
    }

    private void replaceFlowNodes(CollaborationTask task, List<CollaborationTaskFlowNodeRequest> requests) {
        flowNodeRepository.deleteByTaskId(task.getId());
        saveFlowNodes(task, requests);
    }

    private void saveFlowNodes(CollaborationTask task, List<CollaborationTaskFlowNodeRequest> requests) {
        List<CollaborationTaskFlowNodeRequest> normalized = requests == null
                ? List.of()
                : requests.stream()
                        .filter(Objects::nonNull)
                        .filter(request -> !normalizeText(request.getTitle()).isBlank())
                        .toList();
        if (normalized.isEmpty()) {
            return;
        }
        int order = 1;
        for (CollaborationTaskFlowNodeRequest request : normalized) {
            validateMemberIfPresent(task.getSpaceId(), request.getAssigneeId(), "流程节点负责人不在该协作空间内");
            boolean claimable = Boolean.TRUE.equals(request.getClaimable());
            if (request.getAssigneeId() == null && !claimable) {
                throw new BusinessException("BAD_REQUEST", "未指定负责人的流程节点必须允许认领", HttpStatus.BAD_REQUEST);
            }
            CollaborationTaskFlowNode node = new CollaborationTaskFlowNode();
            node.setSpaceId(task.getSpaceId());
            node.setProjectId(task.getProjectId());
            node.setTaskId(task.getId());
            node.setStepOrder(order);
            node.setTitle(normalizeText(request.getTitle()));
            node.setDescription(normalizeOptionalText(request.getDescription()));
            node.setAssigneeId(request.getAssigneeId());
            node.setClaimable(claimable);
            node.setStatus(initialFlowNodeStatus(order, request.getAssigneeId(), claimable));
            if (order == 1 && request.getAssigneeId() != null) {
                node.setStartedAt(AppTime.now());
            }
            flowNodeRepository.save(node);
            order += 1;
        }
    }

    private String initialFlowNodeStatus(int order, Long assigneeId, boolean claimable) {
        if (order > 1) {
            return CollaborationTaskFlowNode.STATUS_PENDING;
        }
        if (assigneeId != null) {
            return CollaborationTaskFlowNode.STATUS_CLAIMED;
        }
        return claimable ? CollaborationTaskFlowNode.STATUS_UNCLAIMED : CollaborationTaskFlowNode.STATUS_PENDING;
    }

    private CollaborationTask syncTaskWithCurrentFlowNode(CollaborationTask task) {
        List<CollaborationTaskFlowNode> nodes = flowNodeRepository.findByTaskIdOrderByStepOrderAsc(task.getId());
        if (nodes.isEmpty()) {
            return taskRepository.save(task);
        }
        CollaborationTaskFlowNode current = nodes.stream()
                .filter(node -> !Objects.equals(node.getStatus(), CollaborationTaskFlowNode.STATUS_COMPLETED))
                .findFirst()
                .orElse(null);
        if (current == null) {
            task.setStatus(CollaborationTask.STATUS_WAITING_RECEIVE);
            task.setAssigneeId(null);
            return taskRepository.save(task);
        }
        task.setAssigneeId(current.getAssigneeId());
        task.setClaimMode(current.getAssigneeId() == null
                ? CollaborationTask.CLAIM_MODE_OPEN
                : CollaborationTask.CLAIM_MODE_ASSIGNED);
        if (Objects.equals(current.getStatus(), CollaborationTaskFlowNode.STATUS_UNCLAIMED)) {
            task.setStatus(CollaborationTask.STATUS_UNCLAIMED);
        } else if (Objects.equals(current.getStatus(), CollaborationTaskFlowNode.STATUS_IN_PROGRESS)) {
            task.setStatus(CollaborationTask.STATUS_IN_PROGRESS);
        } else if (Objects.equals(current.getStatus(), CollaborationTaskFlowNode.STATUS_RETURNED)) {
            task.setStatus(CollaborationTask.STATUS_RETURNED);
        } else {
            task.setStatus(current.getAssigneeId() == null
                    ? CollaborationTask.STATUS_UNCLAIMED
                    : CollaborationTask.STATUS_CLAIMED);
        }
        return taskRepository.save(task);
    }

    private CollaborationTask moveToNextFlowNodeOrReview(
            CollaborationTask task,
            CollaborationTaskFlowNode completedNode,
            LocalDateTime now) {
        List<CollaborationTaskFlowNode> nodes = flowNodeRepository.findByTaskIdOrderByStepOrderAsc(task.getId());
        CollaborationTaskFlowNode next = nodes.stream()
                .filter(node -> node.getStepOrder() > completedNode.getStepOrder())
                .filter(node -> !Objects.equals(node.getStatus(), CollaborationTaskFlowNode.STATUS_COMPLETED))
                .findFirst()
                .orElse(null);
        if (next == null) {
            task.setAssigneeId(null);
            task.setStatus(CollaborationTask.STATUS_WAITING_RECEIVE);
            task.setSubmittedAt(now);
            return task;
        }
        if (next.getAssigneeId() == null) {
            next.setStatus(CollaborationTaskFlowNode.STATUS_UNCLAIMED);
            task.setAssigneeId(null);
            task.setClaimMode(CollaborationTask.CLAIM_MODE_OPEN);
            task.setStatus(CollaborationTask.STATUS_UNCLAIMED);
        } else {
            next.setStatus(CollaborationTaskFlowNode.STATUS_CLAIMED);
            next.setStartedAt(now);
            task.setAssigneeId(next.getAssigneeId());
            task.setClaimMode(CollaborationTask.CLAIM_MODE_ASSIGNED);
            task.setStatus(CollaborationTask.STATUS_CLAIMED);
        }
        flowNodeRepository.save(next);
        return task;
    }

    private java.util.Optional<CollaborationTaskFlowNode> findCurrentFlowNode(CollaborationTask task) {
        return flowNodeRepository.findByTaskIdOrderByStepOrderAsc(task.getId()).stream()
                .filter(node -> !Objects.equals(node.getStatus(), CollaborationTaskFlowNode.STATUS_COMPLETED))
                .findFirst();
    }

    private void validateTaskDependencies(CollaborationTask task, List<Long> dependsOnTaskIds) {
        if (dependsOnTaskIds.stream().anyMatch(id -> Objects.equals(id, task.getId()))) {
            throw new BusinessException("BAD_REQUEST", "前置任务不能形成循环依赖", HttpStatus.BAD_REQUEST);
        }
        List<CollaborationTask> projectTasks = activeTasks(taskRepository.findByProjectIdOrderByIdAsc(task.getProjectId()));
        Set<Long> validIds = projectTasks.stream()
                .map(CollaborationTask::getId)
                .collect(Collectors.toSet());
        for (Long dependsOnTaskId : dependsOnTaskIds) {
            if (!validIds.contains(dependsOnTaskId)) {
                throw new BusinessException("BAD_REQUEST", "前置任务必须属于同一个项目", HttpStatus.BAD_REQUEST);
            }
        }

        List<Long> projectTaskIds = projectTasks.stream()
                .map(CollaborationTask::getId)
                .toList();
        List<CollaborationTaskDependencyCycleValidator.Edge> edges = dependencyRepository
                .findByTaskIdIn(projectTaskIds)
                .stream()
                .filter(dependency -> !Objects.equals(dependency.getTaskId(), task.getId()))
                .map(dependency -> new CollaborationTaskDependencyCycleValidator.Edge(
                        dependency.getTaskId(),
                        dependency.getDependsOnTaskId()))
                .collect(Collectors.toCollection(ArrayList::new));
        dependsOnTaskIds.forEach(dependsOnTaskId ->
                edges.add(new CollaborationTaskDependencyCycleValidator.Edge(task.getId(), dependsOnTaskId)));
        dependencyCycleValidator.validateAcyclic(edges);
    }

    private Map<String, CreateCollaborationProjectWithTasksRequest.TaskPayload> normalizeTaskPayloads(
            Long spaceId,
            List<CreateCollaborationProjectWithTasksRequest.TaskPayload> taskPayloads) {
        Map<String, CreateCollaborationProjectWithTasksRequest.TaskPayload> tasksByLocalId = new LinkedHashMap<>();
        for (CreateCollaborationProjectWithTasksRequest.TaskPayload taskPayload : taskPayloads) {
            String localId = normalizeText(taskPayload.getLocalId());
            if (localId.isBlank()) {
                throw new BusinessException("BAD_REQUEST", "任务本地编号不能为空", HttpStatus.BAD_REQUEST);
            }
            if (tasksByLocalId.containsKey(localId)) {
                throw new BusinessException("BAD_REQUEST", "任务本地编号不能重复：" + localId, HttpStatus.BAD_REQUEST);
            }
            String title = normalizeText(taskPayload.getTitle());
            if (title.isBlank()) {
                throw new BusinessException("BAD_REQUEST", "任务名称不能为空", HttpStatus.BAD_REQUEST);
            }
            validateMemberIfPresent(spaceId, taskPayload.getAssigneeId(), "负责人不在该协作空间内");
            validateMemberIfPresent(spaceId, taskPayload.getReceiverId(), "接收人不在该协作空间内");
            tasksByLocalId.put(localId, taskPayload);
        }

        for (Map.Entry<String, CreateCollaborationProjectWithTasksRequest.TaskPayload> entry : tasksByLocalId.entrySet()) {
            String localId = entry.getKey();
            CreateCollaborationProjectWithTasksRequest.TaskPayload taskPayload = entry.getValue();
            String parentLocalId = normalizeOptionalText(taskPayload.getParentLocalId());
            if (parentLocalId != null) {
                validateReferencedLocalId(localId, parentLocalId, tasksByLocalId, "父级任务不存在：");
            }
            for (String dependsOnLocalId : normalizeLocalIds(taskPayload.getDependsOnLocalIds())) {
                validateReferencedLocalId(localId, dependsOnLocalId, tasksByLocalId, "前置任务不存在：");
            }
        }
        validateLocalDependencyGraph(tasksByLocalId);
        return tasksByLocalId;
    }

    private void validateLocalDependencyGraph(
            Map<String, CreateCollaborationProjectWithTasksRequest.TaskPayload> tasksByLocalId) {
        Map<String, Long> idByLocalId = new HashMap<>();
        long syntheticId = 1L;
        for (String localId : tasksByLocalId.keySet()) {
            idByLocalId.put(localId, syntheticId++);
        }
        List<CollaborationTaskDependencyCycleValidator.Edge> edges = new ArrayList<>();
        for (Map.Entry<String, CreateCollaborationProjectWithTasksRequest.TaskPayload> entry : tasksByLocalId.entrySet()) {
            Long taskId = idByLocalId.get(entry.getKey());
            for (String dependsOnLocalId : normalizeLocalIds(entry.getValue().getDependsOnLocalIds())) {
                edges.add(new CollaborationTaskDependencyCycleValidator.Edge(taskId, idByLocalId.get(dependsOnLocalId)));
            }
        }
        dependencyCycleValidator.validateAcyclic(edges);
    }

    private CollaborationTask buildTask(
            Long spaceId,
            Long projectId,
            Long studentId,
            CreateCollaborationProjectWithTasksRequest.TaskPayload request) {
        CollaborationTask task = new CollaborationTask();
        task.setSpaceId(spaceId);
        task.setProjectId(projectId);
        task.setTitle(normalizeText(request.getTitle()));
        task.setDescription(normalizeOptionalText(request.getDescription()));
        task.setDeliverableRequirements(normalizeOptionalText(request.getDeliverableRequirements()));
        task.setAssigneeId(request.getAssigneeId());
        task.setReceiverId(request.getReceiverId());
        task.setCreatedBy(studentId);
        task.setClaimMode(request.getAssigneeId() == null
                ? CollaborationTask.CLAIM_MODE_OPEN
                : CollaborationTask.CLAIM_MODE_ASSIGNED);
        task.setStatus(request.getAssigneeId() == null
                ? CollaborationTask.STATUS_UNCLAIMED
                : CollaborationTask.STATUS_CLAIMED);
        task.setStartAt(request.getStartAt());
        task.setDueAt(request.getDueAt());
        return task;
    }

    private void linkParentTasks(
            Map<String, CollaborationTask> savedTasksByLocalId,
            Map<String, CreateCollaborationProjectWithTasksRequest.TaskPayload> tasksByLocalId) {
        for (Map.Entry<String, CreateCollaborationProjectWithTasksRequest.TaskPayload> entry : tasksByLocalId.entrySet()) {
            String parentLocalId = normalizeOptionalText(entry.getValue().getParentLocalId());
            if (parentLocalId == null) {
                continue;
            }
            CollaborationTask task = savedTasksByLocalId.get(entry.getKey());
            CollaborationTask parentTask = savedTasksByLocalId.get(parentLocalId);
            task.setParentTaskId(parentTask.getId());
            taskRepository.save(task);
        }
    }

    private void saveTaskDependencies(
            Map<String, CollaborationTask> savedTasksByLocalId,
            Map<String, CreateCollaborationProjectWithTasksRequest.TaskPayload> tasksByLocalId) {
        for (Map.Entry<String, CreateCollaborationProjectWithTasksRequest.TaskPayload> entry : tasksByLocalId.entrySet()) {
            CollaborationTask task = savedTasksByLocalId.get(entry.getKey());
            for (String dependsOnLocalId : normalizeLocalIds(entry.getValue().getDependsOnLocalIds())) {
                CollaborationTask dependencyTask = savedTasksByLocalId.get(dependsOnLocalId);
                CollaborationTaskDependency dependency = new CollaborationTaskDependency();
                dependency.setTaskId(task.getId());
                dependency.setDependsOnTaskId(dependencyTask.getId());
                dependencyRepository.save(dependency);
            }
        }
    }

    private List<String> normalizeLocalIds(List<String> localIds) {
        if (localIds == null) {
            return List.of();
        }
        return localIds.stream()
                .map(this::normalizeText)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private void validateReferencedLocalId(
            String localId,
            String referencedLocalId,
            Map<String, CreateCollaborationProjectWithTasksRequest.TaskPayload> tasksByLocalId,
            String missingMessagePrefix) {
        if (Objects.equals(localId, referencedLocalId)) {
            throw new BusinessException("BAD_REQUEST", "任务不能引用自己：" + localId, HttpStatus.BAD_REQUEST);
        }
        if (!tasksByLocalId.containsKey(referencedLocalId)) {
            throw new BusinessException("BAD_REQUEST", missingMessagePrefix + referencedLocalId, HttpStatus.BAD_REQUEST);
        }
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(10);
        sb.append("FC");
        for (int i = 0; i < 8; i++) {
            sb.append(CODE_ALPHABET.charAt(secureRandom.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private Map<Long, Long> loadMemberCounts(List<Long> spaceIds) {
        if (spaceIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (CollaborationSpaceMember member : memberRepository.findBySpaceIdInAndIsDeleted(spaceIds, NOT_DELETED)) {
            counts.merge(member.getSpaceId(), 1L, Long::sum);
        }
        return counts;
    }

    private CollaborationSpaceResponse toSpaceResponse(
            CollaborationSpace space,
            CollaborationSpaceMember myMembership,
            Map<Long, Long> memberCounts,
            boolean includeMembers) {
        Long creatorId = space.getCreatorId();
        String creatorName = userRepository.findById(creatorId)
                .map(User::getName)
                .orElse("发起人");

        LocalDateTime now = AppTime.now();
        CollaborationSpaceInviteCode activeInvite = null;
        if (Objects.equals(myMembership.getRole(), CollaborationSpaceMember.ROLE_OWNER)) {
            activeInvite = inviteCodeRepository
                    .findFirstBySpaceIdAndStatusAndExpiresAtAfterOrderByIdDesc(
                            space.getId(),
                            CollaborationSpaceInviteCode.STATUS_ACTIVE,
                            now)
                    .orElse(null);
        }

        List<CollaborationSpaceMemberResponse> members = includeMembers
                ? loadMembers(space.getId())
                : List.of();

        return CollaborationSpaceResponse.builder()
                .id(space.getId())
                .name(space.getName())
                .description(space.getDescription())
                .creatorId(creatorId)
                .creatorName(creatorName)
                .myRole(myMembership.getRole())
                .memberCount(Math.toIntExact(memberCounts.getOrDefault(space.getId(), 0L)))
                .activeInviteCode(activeInvite == null ? null : activeInvite.getCode())
                .inviteCodeExpiresAt(activeInvite == null ? null : activeInvite.getExpiresAt())
                .members(members)
                .createdAt(space.getCreatedAt())
                .build();
    }

    private List<CollaborationSpaceMemberResponse> loadMembers(Long spaceId) {
        List<CollaborationSpaceMember> members = memberRepository.findBySpaceIdAndIsDeletedOrderByIdAsc(spaceId, NOT_DELETED);
        if (members.isEmpty()) {
            return List.of();
        }
        Map<Long, User> usersById = loadUsers(
                members.stream().map(CollaborationSpaceMember::getStudentId).collect(Collectors.toSet()));

        return members.stream()
                .map(member -> {
                    User user = usersById.get(member.getStudentId());
                    return CollaborationSpaceMemberResponse.builder()
                            .studentId(member.getStudentId())
                            .name(user == null ? "同伴 " + member.getStudentId() : user.getName())
                            .avatarUrl(user == null ? null : user.getAvatarUrl())
                            .role(member.getRole())
                            .joinedAt(member.getJoinedAt())
                            .build();
                })
                .toList();
    }

    private List<CollaborationProjectResponse> toProjectResponses(List<CollaborationProject> projects, boolean includeTasks) {
        if (projects.isEmpty()) {
            return List.of();
        }
        Map<Long, List<CollaborationTask>> tasksByProjectId = includeTasks
                ? taskRepository.findByProjectIdInOrderByIdAsc(projects.stream().map(CollaborationProject::getId).toList())
                        .stream()
                        .filter(task -> !Objects.equals(task.getStatus(), CollaborationTask.STATUS_ARCHIVED))
                        .collect(Collectors.groupingBy(CollaborationTask::getProjectId))
                : Map.of();
        Map<Long, List<CollaborationAttachmentResponse>> attachmentsByProjectId =
                attachmentService.listProjectResponsesByTargetId(projects.stream().map(CollaborationProject::getId).toList());
        return projects.stream()
                .map(project -> toProjectResponse(
                        project,
                        tasksByProjectId.getOrDefault(project.getId(), List.of()),
                        includeTasks,
                        attachmentsByProjectId.getOrDefault(project.getId(), List.of())))
                .toList();
    }

    private CollaborationProjectResponse toProjectResponse(
            CollaborationProject project,
            List<CollaborationTask> tasks,
            boolean includeTasks) {
        return toProjectResponse(project, tasks, includeTasks, attachmentService.listProjectResponses(project.getId()));
    }

    private CollaborationProjectResponse toProjectResponse(
            CollaborationProject project,
            List<CollaborationTask> tasks,
            boolean includeTasks,
            List<CollaborationAttachmentResponse> attachments) {
        List<CollaborationTaskResponse> taskResponses = includeTasks ? toTaskResponses(tasks, true) : List.of();
        return CollaborationProjectResponse.builder()
                .id(project.getId())
                .spaceId(project.getSpaceId())
                .title(project.getTitle())
                .description(project.getDescription())
                .status(project.getStatus())
                .startAt(project.getStartAt())
                .dueAt(project.getDueAt())
                .createdBy(project.getCreatedBy())
                .createdByName(userName(project.getCreatedBy()))
                .taskCount(tasks.size())
                .completedTaskCount(countCompleted(tasks))
                .waitingReceiveCount(countWaitingReceive(tasks))
                .overdueTaskCount(countOverdue(tasks))
                .attachments(attachments)
                .tasks(taskResponses)
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    private CollaborationTaskResponse toTaskResponse(CollaborationTask task, boolean includeSubmissions) {
        return toTaskResponses(List.of(task), includeSubmissions).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("INTERNAL_ERROR", "任务响应生成失败", HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private List<CollaborationTaskResponse> toTaskResponses(List<CollaborationTask> tasks, boolean includeSubmissions) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new HashSet<>();
        for (CollaborationTask task : tasks) {
            addIfPresent(userIds, task.getAssigneeId());
            addIfPresent(userIds, task.getReceiverId());
            addIfPresent(userIds, task.getCreatedBy());
        }
        List<Long> taskIds = tasks.stream().map(CollaborationTask::getId).toList();
        Map<Long, List<CollaborationTaskFlowNode>> flowNodesByTaskId = flowNodeRepository
                .findByTaskIdInOrderByTaskIdAscStepOrderAsc(taskIds)
                .stream()
                .peek(node -> addIfPresent(userIds, node.getAssigneeId()))
                .collect(Collectors.groupingBy(
                        CollaborationTaskFlowNode::getTaskId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        Map<Long, User> usersById = loadUsers(userIds);
        Map<Long, List<Long>> dependenciesByTaskId = dependencyRepository.findByTaskIdIn(taskIds).stream()
                .collect(Collectors.groupingBy(
                        CollaborationTaskDependency::getTaskId,
                        Collectors.mapping(CollaborationTaskDependency::getDependsOnTaskId, Collectors.toList())));
        Map<Long, List<CollaborationTaskSubmissionResponse>> submissionsByTaskId = includeSubmissions
                ? loadSubmissionsByTaskId(taskIds)
                : Map.of();
        Map<Long, List<CollaborationAttachmentResponse>> attachmentsByTaskId =
                attachmentService.listTaskResponsesByTargetId(taskIds);

        return tasks.stream()
                .map(task -> {
                    User assignee = usersById.get(task.getAssigneeId());
                    User receiver = usersById.get(task.getReceiverId());
                    User creator = usersById.get(task.getCreatedBy());
                    List<CollaborationTaskSubmissionResponse> submissions =
                            submissionsByTaskId.getOrDefault(task.getId(), List.of());
                    List<CollaborationTaskFlowNodeResponse> flowNodes = toFlowNodeResponses(
                            flowNodesByTaskId.getOrDefault(task.getId(), List.of()),
                            usersById);
                    CollaborationTaskFlowNodeResponse currentFlowNode = flowNodes.stream()
                            .filter(CollaborationTaskFlowNodeResponse::getCurrent)
                            .findFirst()
                            .orElse(null);
                    return CollaborationTaskResponse.builder()
                            .id(task.getId())
                            .projectId(task.getProjectId())
                            .spaceId(task.getSpaceId())
                            .parentTaskId(task.getParentTaskId())
                            .title(task.getTitle())
                            .description(task.getDescription())
                            .deliverableRequirements(task.getDeliverableRequirements())
                            .assigneeId(task.getAssigneeId())
                            .assigneeName(assignee == null ? null : assignee.getName())
                            .assigneeAvatarUrl(assignee == null ? null : assignee.getAvatarUrl())
                            .receiverId(task.getReceiverId())
                            .receiverName(receiver == null ? null : receiver.getName())
                            .createdBy(task.getCreatedBy())
                            .createdByName(creator == null ? "成员" : creator.getName())
                            .claimMode(task.getClaimMode())
                            .status(task.getStatus())
                            .startAt(task.getStartAt())
                            .dueAt(task.getDueAt())
                            .submittedAt(task.getSubmittedAt())
                            .acceptedAt(task.getAcceptedAt())
                            .completedAt(task.getCompletedAt())
                            .overdue(isOverdue(task))
                            .attachments(attachmentsByTaskId.getOrDefault(task.getId(), List.of()))
                            .flowNodes(flowNodes)
                            .currentFlowNode(currentFlowNode)
                            .latestSubmission(submissions.isEmpty() ? null : submissions.get(0))
                            .submissions(submissions)
                            .dependsOnTaskIds(dependenciesByTaskId.getOrDefault(task.getId(), List.of()))
                            .createdAt(task.getCreatedAt())
                            .updatedAt(task.getUpdatedAt())
                            .build();
                })
                .toList();
    }

    private Map<Long, List<CollaborationTaskSubmissionResponse>> loadSubmissionsByTaskId(List<Long> taskIds) {
        List<CollaborationTaskSubmission> submissions = submissionRepository.findByTaskIdInOrderByTaskIdAscVersionNoDesc(taskIds);
        Set<Long> userIds = submissions.stream().map(CollaborationTaskSubmission::getSubmittedBy).collect(Collectors.toSet());
        Map<Long, User> usersById = loadUsers(userIds);
        return submissions.stream()
                .map(submission -> toSubmissionResponse(submission, usersById.get(submission.getSubmittedBy())))
                .collect(Collectors.groupingBy(
                        CollaborationTaskSubmissionResponse::getTaskId,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    private List<CollaborationTaskFlowNodeResponse> toFlowNodeResponses(
            List<CollaborationTaskFlowNode> nodes,
            Map<Long, User> usersById) {
        if (nodes.isEmpty()) {
            return List.of();
        }
        Long currentId = nodes.stream()
                .filter(node -> !Objects.equals(node.getStatus(), CollaborationTaskFlowNode.STATUS_COMPLETED))
                .map(CollaborationTaskFlowNode::getId)
                .findFirst()
                .orElse(null);
        return nodes.stream()
                .map(node -> {
                    User assignee = usersById.get(node.getAssigneeId());
                    return CollaborationTaskFlowNodeResponse.builder()
                            .id(node.getId())
                            .taskId(node.getTaskId())
                            .stepOrder(node.getStepOrder())
                            .title(node.getTitle())
                            .description(node.getDescription())
                            .assigneeId(node.getAssigneeId())
                            .assigneeName(assignee == null ? null : assignee.getName())
                            .assigneeAvatarUrl(assignee == null ? null : assignee.getAvatarUrl())
                            .claimable(node.getClaimable())
                            .status(node.getStatus())
                            .current(Objects.equals(node.getId(), currentId))
                            .startedAt(node.getStartedAt())
                            .submittedAt(node.getSubmittedAt())
                            .completedAt(node.getCompletedAt())
                            .createdAt(node.getCreatedAt())
                            .updatedAt(node.getUpdatedAt())
                            .build();
                })
                .toList();
    }

    private CollaborationTaskSubmissionResponse toSubmissionResponse(CollaborationTaskSubmission submission, User user) {
        return CollaborationTaskSubmissionResponse.builder()
                .id(submission.getId())
                .taskId(submission.getTaskId())
                .flowNodeId(submission.getFlowNodeId())
                .submittedBy(submission.getSubmittedBy())
                .submittedByName(user == null ? "成员" : user.getName())
                .content(submission.getContent())
                .attachmentsJson(submission.getAttachmentsJson())
                .linksJson(submission.getLinksJson())
                .versionNo(submission.getVersionNo())
                .status(submission.getStatus())
                .createdAt(submission.getCreatedAt())
                .build();
    }

    private CollaborationActivityLogResponse toActivityResponse(CollaborationActivityLog log) {
        return CollaborationActivityLogResponse.builder()
                .id(log.getId())
                .spaceId(log.getSpaceId())
                .projectId(log.getProjectId())
                .taskId(log.getTaskId())
                .actorId(log.getActorId())
                .actorName(userName(log.getActorId()))
                .action(log.getAction())
                .summary(log.getSummary())
                .detailJson(log.getDetailJson())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private void logActivity(
            Long spaceId,
            Long projectId,
            Long taskId,
            Long actorId,
            String action,
            String summary,
            String detailJson) {
        CollaborationActivityLog log = new CollaborationActivityLog();
        log.setSpaceId(spaceId);
        log.setProjectId(projectId);
        log.setTaskId(taskId);
        log.setActorId(actorId);
        log.setAction(action);
        log.setSummary(summary);
        log.setDetailJson(detailJson);
        activityLogRepository.save(log);
    }

    private void notifyUserIfDifferent(
            Long userId,
            Long actorId,
            String type,
            String title,
            String content,
            Long relatedId) {
        if (userId == null || Objects.equals(userId, actorId)) {
            return;
        }
        notificationService.notifyUser(userId, type, title, content, relatedId);
    }

    private void notifyTaskAssignee(CollaborationTask task, Long actorId, String title, String content) {
        notifyUserIfDifferent(
                task.getAssigneeId(),
                actorId,
                Notification.TYPE_COLLABORATION_TASK,
                title,
                content,
                task.getId());
    }

    private void notifyTaskReceiver(CollaborationTask task, Long actorId, String title, String content) {
        notifyUserIfDifferent(
                task.getReceiverId(),
                actorId,
                Notification.TYPE_COLLABORATION_REVIEW,
                title,
                content,
                task.getId());
    }

    private void notifySpaceMembersExcept(
            Long spaceId,
            Long actorId,
            String type,
            String title,
            String content,
            Long relatedId) {
        memberRepository.findBySpaceIdAndIsDeletedOrderByIdAsc(spaceId, NOT_DELETED).stream()
                .map(CollaborationSpaceMember::getStudentId)
                .filter(userId -> !Objects.equals(userId, actorId))
                .distinct()
                .forEach(userId -> notificationService.notifyUser(userId, type, title, content, relatedId));
    }

    private Map<Long, User> loadUsers(Set<Long> userIds) {
        Set<Long> normalized = userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (normalized.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(normalized).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
    }

    private String userName(Long userId) {
        if (userId == null) {
            return "成员";
        }
        return userRepository.findById(userId).map(User::getName).orElse("成员 " + userId);
    }

    private void addIfPresent(Set<Long> set, Long value) {
        if (value != null) {
            set.add(value);
        }
    }

    private int countCompleted(List<CollaborationTask> tasks) {
        return (int) tasks.stream().filter(task -> isDoneTaskStatus(task.getStatus())).count();
    }

    private int countWaitingReceive(List<CollaborationTask> tasks) {
        return (int) tasks.stream()
                .filter(task -> Objects.equals(task.getStatus(), CollaborationTask.STATUS_WAITING_RECEIVE))
                .count();
    }

    private int countOverdue(List<CollaborationTask> tasks) {
        return (int) tasks.stream().filter(this::isOverdue).count();
    }

    private boolean isOverdue(CollaborationTask task) {
        return task.getDueAt() != null
                && !isDoneTaskStatus(task.getStatus())
                && task.getDueAt().isBefore(AppTime.now());
    }

    private boolean isActiveTaskStatus(String status) {
        return Objects.equals(status, CollaborationTask.STATUS_CLAIMED)
                || Objects.equals(status, CollaborationTask.STATUS_IN_PROGRESS)
                || Objects.equals(status, CollaborationTask.STATUS_RETURNED)
                || Objects.equals(status, CollaborationTask.STATUS_WAITING_RECEIVE);
    }

    private boolean isDoneTaskStatus(String status) {
        return Objects.equals(status, CollaborationTask.STATUS_COMPLETED);
    }

    private boolean isValidProjectStatus(String status) {
        return Objects.equals(status, CollaborationProject.STATUS_DRAFT)
                || Objects.equals(status, CollaborationProject.STATUS_ACTIVE)
                || Objects.equals(status, CollaborationProject.STATUS_COMPLETED)
                || Objects.equals(status, CollaborationProject.STATUS_ARCHIVED);
    }

    private boolean isValidTaskStatus(String status) {
        return Objects.equals(status, CollaborationTask.STATUS_UNCLAIMED)
                || Objects.equals(status, CollaborationTask.STATUS_CLAIMED)
                || Objects.equals(status, CollaborationTask.STATUS_IN_PROGRESS)
                || Objects.equals(status, CollaborationTask.STATUS_WAITING_RECEIVE)
                || Objects.equals(status, CollaborationTask.STATUS_RETURNED)
                || Objects.equals(status, CollaborationTask.STATUS_COMPLETED)
                || Objects.equals(status, CollaborationTask.STATUS_ARCHIVED);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeOptionalText(String value) {
        String text = normalizeText(value);
        return text.isBlank() ? null : text;
    }
}
