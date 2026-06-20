import { getCurrentUserId, getRole } from '../utils/auth'

const STORAGE_KEY = 'teamtrace_mock_state_v1'

function encodeBase64Url(value) {
  return btoa(unescape(encodeURIComponent(value)))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '')
}

function createMockToken(user) {
  const header = encodeBase64Url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const payload = encodeBase64Url(
    JSON.stringify({
      sub: String(user.id),
      role: user.role,
      name: user.name,
      email: user.email,
      phone: user.phone,
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + (7 * 24 * 60 * 60),
    }),
  )

  return `${header}.${payload}.teamtrace-mock`
}

function deepClone(value) {
  return JSON.parse(JSON.stringify(value))
}

function nowIso(offsetMinutes = 0) {
  return new Date(Date.now() + (offsetMinutes * 60 * 1000)).toISOString()
}

function buildPagedResult(list, page = 1, size = 10) {
  const safePage = Math.max(Number(page) || 1, 1)
  const safeSize = Math.max(Number(size) || 10, 1)
  const total = list.length
  const pages = total ? Math.ceil(total / safeSize) : 1
  const start = (safePage - 1) * safeSize
  const sliced = list.slice(start, start + safeSize)

  return {
    list: deepClone(sliced),
    page: safePage,
    size: safeSize,
    total,
    pages,
    hasNext: safePage < pages,
  }
}

function getDefaultState() {
  return {
    users: [
      {
        id: 1001,
        role: 'admin',
        phone: '13800000001',
        name: '系统管理员',
        status: 1,
        isDeleted: false,
        createdAt: '2026-04-01T08:00:00+08:00',
        password: 'Admin@123',
      },
      {
        id: 2001,
        role: 'teacher',
        phone: '13800000002',
        name: '李老师',
        status: 1,
        isDeleted: false,
        createdAt: '2026-04-02T09:00:00+08:00',
        password: 'Teacher@123',
      },
      {
        id: 2002,
        role: 'teacher',
        phone: '13800000005',
        name: '王老师',
        status: 1,
        isDeleted: false,
        createdAt: '2026-04-04T10:00:00+08:00',
        password: 'Teacher@123',
      },
      {
        id: 3001,
        role: 'student',
        phone: '13800000003',
        name: '张三',
        status: 1,
        isDeleted: false,
        createdAt: '2026-04-03T10:00:00+08:00',
        password: 'Student@123',
      },
      {
        id: 3002,
        role: 'student',
        phone: '13800000004',
        name: '李四',
        status: 1,
        isDeleted: false,
        createdAt: '2026-04-03T11:00:00+08:00',
        password: 'Student@123',
      },
      {
        id: 3003,
        role: 'student',
        phone: '13800000006',
        name: '王五',
        status: 1,
        isDeleted: false,
        createdAt: '2026-04-05T09:30:00+08:00',
        password: 'Student@123',
      },
      {
        id: 3004,
        role: 'student',
        phone: '13800000007',
        name: '赵六',
        status: 0,
        isDeleted: false,
        createdAt: '2026-04-06T09:30:00+08:00',
        password: 'Student@123',
      },
    ],
    classes: [
      {
        classId: 101,
        classCode: 'SE2026-A',
        name: '软件工程 2026 春 A班',
        semester: '2026-Spring',
        status: 1,
        teacherId: 2001,
        teacherName: '李老师',
        groupSizeMin: 1,
        groupSizeMax: 10,
        classInviteCode: 'CLASS-SE2026A',
        groupingLocked: false,
        inviteExpireAt: '2026-05-20T23:59:59+08:00',
      },
      {
        classId: 102,
        classCode: 'DB2026-B',
        name: '数据库原理 2026 春 B班',
        semester: '2026-Spring',
        status: 0,
        teacherId: 2002,
        teacherName: '王老师',
        groupSizeMin: 1,
        groupSizeMax: 10,
        classInviteCode: 'CLASS-DB2026B',
        groupingLocked: true,
        inviteExpireAt: '2026-05-18T23:59:59+08:00',
      },
    ],
    memberships: [
      {
        classId: 101,
        studentId: 3001,
        groupId: '1011',
        groupJoinStatus: '已加入',
        studentStatus: '在班',
        joinedAt: '2026-04-10T10:30:00+08:00',
      },
      {
        classId: 101,
        studentId: 3002,
        groupId: '1011',
        groupJoinStatus: '已加入',
        studentStatus: '在班',
        joinedAt: '2026-04-10T10:31:00+08:00',
      },
      {
        classId: 101,
        studentId: 3003,
        groupId: '1012',
        groupJoinStatus: '已加入',
        studentStatus: '在班',
        joinedAt: '2026-04-10T10:32:00+08:00',
      },
      {
        classId: 101,
        studentId: 3004,
        groupId: '1012',
        groupJoinStatus: '待审批',
        studentStatus: '在班',
        joinedAt: '2026-04-12T10:32:00+08:00',
      },
    ],
    groups: [
      {
        groupId: '1011',
        classId: 101,
        name: '第1组',
        leaderId: 3001,
        leaderName: '张三',
        memberStudentIds: [3001, 3002],
        joinMode: '审批',
      },
      {
        groupId: '1012',
        classId: 101,
        name: '第2组',
        leaderId: 3003,
        leaderName: '王五',
        memberStudentIds: [3003, 3004],
        joinMode: '直通',
      },
      {
        groupId: '1021',
        classId: 102,
        name: '第1组',
        leaderId: 3003,
        leaderName: '王五',
        memberStudentIds: [3003],
        joinMode: '审批',
      },
    ],
    tasks: [
      {
        taskId: 5001,
        classId: 101,
        name: '需求分析与原型设计',
        description: '完成需求梳理、流程图和原型页面说明，准备进入前后端联调。',
        taskStatus: 1,
        deadline: '2026-05-10T18:00:00+08:00',
        enablePeerReview: true,
        canPeerReviewNow: true,
        canSubmitAppeal: true,
        peerReviewPhase: 'open',
        peerReviewWeight: 0.4,
        teacherScoreWeight: 0.6,
      },
      {
        taskId: 5002,
        classId: 101,
        name: '系统功能开发排期',
        description: '拆分前端、后端和测试排期，跟踪实际进度。',
        taskStatus: 0,
        deadline: '2026-05-22T18:00:00+08:00',
        enablePeerReview: false,
        canPeerReviewNow: false,
        canSubmitAppeal: false,
        peerReviewPhase: 'not_started',
        peerReviewWeight: 0.3,
        teacherScoreWeight: 0.7,
      },
      {
        taskId: 5003,
        classId: 102,
        name: '数据库设计复盘',
        description: '围绕数据库建模、索引和查询性能进行复盘。',
        taskStatus: 2,
        deadline: '2026-04-18T18:00:00+08:00',
        enablePeerReview: true,
        canPeerReviewNow: false,
        canSubmitAppeal: true,
        peerReviewPhase: 'closed',
        peerReviewWeight: 0.5,
        teacherScoreWeight: 0.5,
      },
    ],
    subtasks: [
      {
        subtaskId: '9001',
        classId: 101,
        taskId: 5001,
        groupId: '1011',
        name: '整理需求文档',
        description: '补齐需求边界、角色和页面清单。',
        qualityRequirement: '必须对齐 PRD 和功能设计文档。',
        assigneeId: null,
        status: 1,
        deadline: '2026-05-05T20:00:00+08:00',
        submissionContent: '',
        submittedAt: '',
        reviewedAt: '',
        reviewComment: '',
      },
      {
        subtaskId: '9002',
        classId: 101,
        taskId: 5001,
        groupId: '1011',
        name: '实现核心页面原型',
        description: '完成学生端和教师端的核心页面原型。',
        qualityRequirement: '需要兼顾移动端和桌面端布局。',
        assigneeId: 3001,
        status: 2,
        deadline: '2026-05-06T18:00:00+08:00',
        submissionContent: '',
        submittedAt: '',
        reviewedAt: '',
        reviewComment: '',
      },
      {
        subtaskId: '9003',
        classId: 101,
        taskId: 5001,
        groupId: '1011',
        name: '补充接口联调说明',
        description: '整理接口字段、联调状态和异常提示。',
        qualityRequirement: '字段说明要和当前接口实现一致。',
        assigneeId: 3002,
        status: 3,
        deadline: '2026-05-07T12:00:00+08:00',
        submissionContent: JSON.stringify({
          text: '已整理接口字段和异常提示说明文档。',
          attachments: [{ type: 'link', value: 'https://example.com/api-note' }],
        }),
        submittedAt: '2026-04-24T10:30:00+08:00',
        reviewedAt: '',
        reviewComment: '',
      },
      {
        subtaskId: '9004',
        classId: 101,
        taskId: 5001,
        groupId: '1011',
        name: '验收交互稿',
        description: '核查页面结构、信息层级和交互提示。',
        qualityRequirement: '要覆盖空状态、失败态和分页。',
        assigneeId: 3001,
        status: 4,
        deadline: '2026-05-04T18:00:00+08:00',
        submissionContent: JSON.stringify({
          text: '已完成交互稿验收并补充说明。',
          attachments: [{ type: 'link', value: 'https://example.com/review-note' }],
        }),
        submittedAt: '2026-04-22T15:00:00+08:00',
        reviewedAt: '2026-04-23T09:00:00+08:00',
        reviewComment: '交互结构清晰，可以继续开发。',
      },
      {
        subtaskId: '9011',
        classId: 101,
        taskId: 5001,
        groupId: '1012',
        name: '分组任务拆解',
        description: '拆解工作包并分配优先级。',
        qualityRequirement: '说明负责人和验收标准。',
        assigneeId: 3003,
        status: 2,
        deadline: '2026-05-06T20:00:00+08:00',
        submissionContent: '',
        submittedAt: '',
        reviewedAt: '',
        reviewComment: '',
      },
    ],
    peerReviews: [
      {
        classId: 101,
        taskId: 5001,
        groupId: '1011',
        items: [
          {
            id: 'pr-1011-1',
            reviewerId: 3001,
            revieweeId: 3002,
            reviewerAlias: '组员A',
            revieweeAlias: '组员B',
            score: 92,
            comment: '推进稳定，文档说明完整。',
            submittedAt: '2026-04-24T08:00:00+08:00',
          },
          {
            id: 'pr-1011-2',
            reviewerId: 3002,
            revieweeId: 3001,
            reviewerAlias: '组员B',
            revieweeAlias: '组员A',
            score: 89,
            comment: '原型完成度较高，建议补一版异常态说明。',
            submittedAt: '2026-04-24T08:30:00+08:00',
          },
        ],
      },
    ],
    scoreSummaries: [
      {
        classId: 101,
        taskId: 5001,
        groupId: '1011',
        items: [
          {
            studentId: 3001,
            peerReviewApplicable: true,
            peerReviewWeight: 0.4,
            teacherScoreWeight: 0.6,
            peerReviewMaxScore: 100,
            peerAverageReceived: 89,
            peerAverageOn100: 89,
            teacherScore: 94,
            weightedTotal100: 92,
          },
          {
            studentId: 3002,
            peerReviewApplicable: true,
            peerReviewWeight: 0.4,
            teacherScoreWeight: 0.6,
            peerReviewMaxScore: 100,
            peerAverageReceived: 92,
            peerAverageOn100: 92,
            teacherScore: 90,
            weightedTotal100: 90.8,
          },
        ],
      },
    ],
    teacherScores: [
      {
        classId: 101,
        taskId: 5001,
        studentId: 3001,
        score: 94,
        scoredBy: 2001,
        scoredAt: '2026-04-25T10:00:00+08:00',
      },
      {
        classId: 101,
        taskId: 5001,
        studentId: 3002,
        score: 90,
        scoredBy: 2001,
        scoredAt: '2026-04-25T10:00:00+08:00',
      },
    ],
    pendingMembers: [],
    classStudents: [
      { classId: 101, studentId: 3001, isDeleted: false },
      { classId: 101, studentId: 3002, isDeleted: false },
      { classId: 101, studentId: 3003, isDeleted: false },
      { classId: 101, studentId: 3004, isDeleted: false },
      { classId: 102, studentId: 3003, isDeleted: false },
    ],
    courseScores: [],
    appeals: [
      {
        appealId: 'AP-5001-01',
        classId: 101,
        taskId: 5001,
        studentId: 3002,
        status: 0,
        reason: '教师分录入时遗漏了提交说明中的加分项，希望复核。',
        createdAt: '2026-04-24T11:20:00+08:00',
        teacherResponse: '',
        adjustedTeacherScore: null,
      },
    ],
    notifications: [
      {
        id: 'N-1001',
        userId: 3001,
        title: '子任务已通过',
        content: '你负责的“验收交互稿”已被组长审批通过。',
        createdAt: '2026-04-23T09:05:00+08:00',
        isRead: 0,
      },
      {
        id: 'N-1002',
        userId: 3001,
        title: '互评已开启',
        content: '任务“需求分析与原型设计”已进入互评阶段。',
        createdAt: '2026-04-24T08:45:00+08:00',
        isRead: 0,
      },
      {
        id: 'N-1003',
        userId: 3001,
        title: '班级公告',
        content: '请在本周内完成分组任务拆解。',
        createdAt: '2026-04-22T10:00:00+08:00',
        isRead: 1,
      },
    ],
    invites: [
      {
        code: 'TT-TEACH-2026-01',
        expireAt: '2026-05-20T23:59:59+08:00',
        status: 0,
        createdAt: '2026-04-20T10:00:00+08:00',
      },
      {
        code: 'TT-TEACH-2026-00',
        expireAt: '2026-05-01T23:59:59+08:00',
        status: 2,
        createdAt: '2026-04-15T10:00:00+08:00',
      },
    ],
    logs: [
      {
        id: 'LOG-1001',
        userId: 1001,
        role: 'admin',
        action: '生成邀请码',
        httpMethod: 'POST',
        path: '/api/admin/teacher-invite-codes',
        queryString: 'expireDays=30',
        durationMs: 83,
        httpStatus: 200,
        success: true,
        createdAt: '2026-04-20T10:00:00+08:00',
      },
      {
        id: 'LOG-1002',
        userId: 2001,
        role: 'teacher',
        action: '查看任务详情',
        httpMethod: 'GET',
        path: '/api/teacher/classes/101/tasks/5001',
        queryString: '',
        durationMs: 42,
        httpStatus: 200,
        success: true,
        createdAt: '2026-04-24T09:40:00+08:00',
      },
      {
        id: 'LOG-1003',
        userId: 3001,
        role: 'student',
        action: '查看任务中心',
        httpMethod: 'GET',
        path: '/api/student/tasks',
        queryString: '',
        durationMs: 36,
        httpStatus: 200,
        success: true,
        createdAt: '2026-04-24T09:48:00+08:00',
      },
      {
        id: 'LOG-1004',
        userId: 3001,
        role: 'student',
        action: '提交子任务',
        httpMethod: 'PUT',
        path: '/api/student/classes/101/tasks/5001/groups/9001/subtasks/7002/submit',
        queryString: '',
        durationMs: 91,
        httpStatus: 200,
        success: true,
        createdAt: '2026-04-24T10:12:00+08:00',
      },
    ],
  }
}

function readState() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      const initialState = getDefaultState()
      localStorage.setItem(STORAGE_KEY, JSON.stringify(initialState))
      return initialState
    }

    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object') {
      throw new Error('invalid mock state')
    }
    return parsed
  } catch {
    const initialState = getDefaultState()
    localStorage.setItem(STORAGE_KEY, JSON.stringify(initialState))
    return initialState
  }
}

function writeState(state) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
}

function getCurrentUser(state, fallbackRole = '') {
  const currentUserId = getCurrentUserId()
  const currentRole = getRole()

  if (currentUserId) {
    const current = state.users.find((item) => Number(item.id) === Number(currentUserId))
    if (current) {
      return current
    }
  }

  if (currentRole) {
    const current = state.users.find((item) => item.role === currentRole)
    if (current) {
      return current
    }
  }

  if (fallbackRole) {
    const current = state.users.find((item) => item.role === fallbackRole)
    if (current) {
      return current
    }
  }

  return state.users[0]
}

function nextNumericId(list, field) {
  return list.reduce((max, item) => Math.max(max, Number(item?.[field]) || 0), 0) + 1
}

function appendLog(state, payload) {
  state.logs.unshift({
    id: `LOG-${Date.now()}`,
    createdAt: nowIso(),
    success: true,
    durationMs: payload.durationMs ?? 60,
    httpStatus: payload.httpStatus ?? 200,
    queryString: payload.queryString ?? '',
    ...payload,
  })
  state.logs = state.logs.slice(0, 100)
}

function buildCsv(rows) {
  return rows.map((row) => row.map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(',')).join('\n')
}

function getStudentOperationLogPage(state, user, params = {}) {
  let list = state.logs.filter((item) => Number(item.userId) === Number(user.id) && item.role === 'student')

  if (params.classId !== undefined && params.classId !== null && String(params.classId).trim()) {
    list = list.filter((item) => String(item.path || '').includes(`/api/student/classes/${params.classId}/`))
  }
  if (params.pathContains) {
    list = list.filter((item) => String(item.path || '').includes(params.pathContains))
  }

  list.sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime())
  return buildPagedResult(list, params.page, params.size)
}

function getClassById(state, classId) {
  return state.classes.find((item) => String(item.classId) === String(classId)) || null
}

function getTaskById(state, classId, taskId) {
  return state.tasks.find(
    (item) => String(item.classId) === String(classId) && String(item.taskId) === String(taskId),
  ) || null
}

function getGroupById(state, groupId) {
  return state.groups.find((item) => String(item.groupId) === String(groupId)) || null
}

function getGroupMembership(state, classId, studentId) {
  return state.memberships.find(
    (item) => String(item.classId) === String(classId) && Number(item.studentId) === Number(studentId),
  ) || null
}

function getStudentName(state, studentId) {
  return state.users.find((item) => Number(item.id) === Number(studentId))?.name || `成员 ${studentId}`
}

function buildStudentClassDetail(state, classId, studentId) {
  const classInfo = getClassById(state, classId)
  if (!classInfo) {
    throw new Error('班级不存在')
  }

  const membership = getGroupMembership(state, classId, studentId)
  const group = membership?.groupId ? getGroupById(state, membership.groupId) : null

  const classStudentCount = state.memberships.filter(
    (item) => String(item.classId) === String(classId),
  ).length

  return {
    classId: classInfo.classId,
    classCode: classInfo.classCode,
    name: classInfo.name,
    semester: classInfo.semester,
    teacherId: classInfo.teacherId || '',
    teacherName: classInfo.teacherName || '',
    studentCount: classStudentCount,
    groupId: membership?.groupId || '',
    groupName: group?.name || '',
    groupJoinStatus: membership?.groupJoinStatus || '',
    studentStatus: membership?.studentStatus || '未加入',
    groupingLocked: false,
  }
}

function buildStudentClassmates(state, classId) {
  return state.memberships
    .filter((item) => String(item.classId) === String(classId))
    .map((membership) => {
      const user = state.users.find((item) => Number(item.id) === Number(membership.studentId))
      const group = membership.groupId ? getGroupById(state, membership.groupId) : null
      const studentId = user?.id || membership.studentId
      return {
        studentId,
        name: user?.name || `学生 ${studentId}`,
        groupName: group?.name || '',
        isLeader: group ? Number(group.leaderId) === Number(studentId) : false,
      }
    })
}

function buildGroupProgress(state, classId, taskId, groupId) {
  const group = getGroupById(state, groupId)
  const groupSubtasks = state.subtasks.filter(
    (item) =>
      String(item.classId) === String(classId) &&
      String(item.taskId) === String(taskId) &&
      String(item.groupId) === String(groupId),
  )

  const claimed = groupSubtasks.filter((item) => Number(item.status) >= 2).length
  const completed = groupSubtasks.filter((item) => Number(item.status) === 4).length
  const total = groupSubtasks.length
  const progressPercent = groupSubtasks.length ? (completed / groupSubtasks.length) * 100 : 0

  const members = (group?.memberStudentIds || []).map((studentId) => {
    const mine = groupSubtasks.filter((item) => Number(item.assigneeId) === Number(studentId))
    const mineCompleted = mine.filter((item) => Number(item.status) === 4).length
    return {
      studentId,
      studentName: getStudentName(state, studentId),
      claimedSubtasks: mine.length,
      completedSubtasks: mineCompleted,
      progressPercent: mine.length ? (mineCompleted / mine.length) * 100 : 0,
    }
  })

  return {
    groupId: String(groupId),
    groupTotalSubtasks: total,
    groupClaimedSubtasks: claimed,
    groupCompletedSubtasks: completed,
    groupPendingSubtasks: Math.max(total - completed, 0),
    groupProgressPercent: progressPercent,
    members,
  }
}

function buildScoreSummaryForStudent(state, taskId, groupId, studentId) {
  const summaryGroup = state.scoreSummaries.find(
    (item) => String(item.taskId) === String(taskId) && String(item.groupId) === String(groupId),
  )

  return summaryGroup?.items.find((item) => Number(item.studentId) === Number(studentId)) || {
    studentId,
    peerReviewApplicable: true,
    peerReviewWeight: 0.4,
    teacherScoreWeight: 0.6,
    peerReviewMaxScore: 100,
    peerAverageReceived: 88,
    peerAverageOn100: 88,
    teacherScore: 92,
    weightedTotal100: 90.4,
  }
}

function buildTeacherClassList(state, teacherId) {
  return state.classes
    .filter((item) => Number(item.teacherId) === Number(teacherId))
    .map((item) => {
      const studentCount = state.memberships.filter((member) => String(member.classId) === String(item.classId)).length
      const groupCount = state.groups.filter(
        (group) => String(group.classId) === String(item.classId) && !group.taskId,
      ).length
      return {
        ...item,
        studentCount,
        groupCount,
        activeInviteCode: item.classInviteCode || item.activeInviteCode || '',
        inviteExpireAt: item.inviteExpireAt || '',
      }
    })
}

function createInviteCode() {
  return `TT-TEACH-${Date.now().toString().slice(-8)}`
}

function assertInviteAvailable(state, code) {
  const invite = state.invites.find((item) => item.code === code && Number(item.status) === 0)
  if (!invite) {
    throw new Error('邀请码无效、已失效或已被撤销')
  }
  return invite
}

export const mockAuthApi = {
  sendEmailCode(payload) {
    const email = String(payload?.email || '').trim().toLowerCase()
    if (!/^[\w.+-]+@(qq\.com|foxmail\.com|163\.com|126\.com|yeah\.net|188\.com|sina\.com|sina\.cn|sohu\.com|139\.com|gmail\.com|outlook\.com|hotmail\.com|live\.cn|live\.com|icloud\.com|([\w-]+\.)+edu\.cn)$/i.test(email)) {
      throw new Error('请使用支持的邮箱（QQ/163/126 等）')
    }
    return { message: '验证码已发送（演示环境不会真实发信，注册时可填任意 6 位数字）' }
  },

  login(payload) {
    const state = readState()
    const account = String(payload?.email || payload?.phone || '').trim()
    const password = String(payload?.password || '')

    const user = state.users.find((item) => {
      const matchAccount = item.email === account || item.phone === account
      return matchAccount && item.password === password
    })
    if (!user) {
      throw new Error('账号或密码错误，请检查后重试。')
    }
    if (Number(user.isDeleted) === 1) {
      throw new Error('该账号已注销，无法登录。')
    }
    if (Number(user.status) !== 1) {
      throw new Error('当前账号已被禁用，无法登录。')
    }

    return {
      token: createMockToken(user),
      role: user.role,
      user: { id: user.id, name: user.name, email: user.email, phone: user.phone },
    }
  },

  registerStudent(payload) {
    const state = readState()
    const email = String(payload?.email || '').trim().toLowerCase()
    if (!email) {
      throw new Error('邮箱不能为空')
    }
    if (state.users.some((item) => item.email === email)) {
      throw new Error('该邮箱已存在')
    }

    const user = {
      id: nextNumericId(state.users, 'id'),
      role: 'student',
      email,
      phone: null,
      name: String(payload?.name || '').trim() || '新学生',
      status: 1,
      isDeleted: false,
      createdAt: nowIso(),
      password: String(payload?.password || ''),
    }

    state.users.push(user)
    state.notifications.unshift({
      id: `N-${Date.now()}`,
      userId: user.id,
      title: '欢迎加入 TeamTrace',
      content: '当前正在使用本地演示环境，可继续浏览学生端页面。',
      createdAt: nowIso(),
      isRead: 0,
    })
    writeState(state)

    return {
      token: createMockToken(user),
      role: user.role,
      user: { id: user.id, name: user.name, email: user.email, phone: user.phone },
    }
  },

  registerTeacher(payload) {
    const state = readState()
    const email = String(payload?.email || '').trim().toLowerCase()
    if (!email) {
      throw new Error('邮箱不能为空')
    }
    if (state.users.some((item) => item.email === email)) {
      throw new Error('该邮箱已存在')
    }

    const invite = assertInviteAvailable(state, String(payload?.inviteCode || '').trim())

    const user = {
      id: nextNumericId(state.users, 'id'),
      role: 'teacher',
      email,
      phone: null,
      name: String(payload?.name || '').trim() || '新教师',
      status: 1,
      isDeleted: false,
      createdAt: nowIso(),
      password: String(payload?.password || ''),
    }

    state.users.push(user)
    invite.status = 1
    invite.usedBy = user.id
    invite.usedAt = nowIso()
    writeState(state)

    return {
      token: createMockToken(user),
      role: user.role,
      user: { id: user.id, name: user.name, email: user.email, phone: user.phone },
    }
  },

  resetPassword(payload) {
    const state = readState()
    const email = String(payload?.email || '').trim().toLowerCase()
    const user = state.users.find((item) => item.email === email && !item.isDeleted)
    if (!user) {
      throw new Error('该邮箱尚未注册')
    }
    user.password = String(payload?.newPassword || '')
    writeState(state)
    return { message: '密码已重置' }
  },
}

function buildInviteUsedByUser(state, usedBy) {
  const user = state.users.find((item) => Number(item.id) === Number(usedBy) && !item.isDeleted)
  if (!user) {
    return null
  }
  return {
    id: user.id,
    name: user.name,
    phone: user.phone,
    email: user.email,
  }
}

export const mockStudentApi = {
  joinStudentClass(inviteCode) {
    const state = readState()
    const user = getCurrentUser(state, 'student')
    const classInfo =
      state.classes.find((item) => item.classInviteCode === inviteCode) ||
      state.classes.find((item) => item.classCode === inviteCode) ||
      state.classes.find((item) => String(item.classId) === String(inviteCode))

    if (!classInfo) {
      throw new Error('未找到匹配的班级邀请码')
    }

    let membership = getGroupMembership(state, classInfo.classId, user.id)
    if (!membership) {
      const defaultGroup = state.groups.find((item) => String(item.classId) === String(classInfo.classId))
      membership = {
        classId: classInfo.classId,
        studentId: user.id,
        groupId: defaultGroup?.groupId || '',
        groupJoinStatus: defaultGroup ? '已加入' : '待审批',
        studentStatus: '在班',
        joinedAt: nowIso(),
      }
      state.memberships.push(membership)
      if (defaultGroup && !defaultGroup.memberStudentIds.includes(user.id)) {
        defaultGroup.memberStudentIds.push(user.id)
      }
      writeState(state)
    }

    return {
      classId: classInfo.classId,
      classCode: classInfo.classCode,
      className: classInfo.name,
      groupId: membership.groupId,
    }
  },

  fetchStudentClassTasks(classId) {
    const state = readState()
    return state.tasks.filter((item) => String(item.classId) === String(classId))
  },

  fetchStudentClassDetail(classId) {
    const state = readState()
    const user = getCurrentUser(state, 'student')
    return buildStudentClassDetail(state, classId, user.id)
  },

  fetchStudentClassmates(classId) {
    const state = readState()
    return buildStudentClassmates(state, classId)
  },

  fetchStudentTaskDetail(classId, taskId) {
    const state = readState()
    const task = getTaskById(state, classId, taskId)
    if (!task) {
      throw new Error('任务不存在')
    }
    return task
  },

  fetchStudentClassGroups(classId) {
    const state = readState()
    return state.groups.filter((item) => String(item.classId) === String(classId))
  },

  fetchStudentGroupSubtasks(classId, taskId, groupId) {
    const state = readState()
    return state.subtasks.filter(
      (item) =>
        String(item.classId) === String(classId) &&
        String(item.taskId) === String(taskId) &&
        String(item.groupId) === String(groupId),
    )
  },

  fetchStudentGroupSubtaskProgress(classId, taskId, groupId) {
    const state = readState()
    return buildGroupProgress(state, classId, taskId, groupId)
  },

  createStudentSubtask(classId, taskId, groupId, payload) {
    const state = readState()
    state.subtasks.push({
      subtaskId: `${nextNumericId(state.subtasks, 'subtaskId')}`,
      classId: Number(classId),
      taskId: Number(taskId),
      groupId: String(groupId),
      name: String(payload?.name || '').trim(),
      description: payload?.description || '',
      qualityRequirement: payload?.qualityRequirement || '',
      assigneeId: null,
      status: 1,
      deadline: payload?.deadline || nowIso(60 * 24),
      submissionContent: '',
      submittedAt: '',
      reviewedAt: '',
      reviewComment: '',
    })
    writeState(state)
    return { success: true }
  },

  claimStudentSubtask(classId, taskId, groupId, subtaskId) {
    const state = readState()
    const user = getCurrentUser(state, 'student')
    const subtask = state.subtasks.find(
      (item) =>
        String(item.classId) === String(classId) &&
        String(item.taskId) === String(taskId) &&
        String(item.groupId) === String(groupId) &&
        String(item.subtaskId) === String(subtaskId),
    )
    if (!subtask) {
      throw new Error('子任务不存在')
    }
    subtask.assigneeId = user.id
    subtask.status = 2
    writeState(state)
    return { success: true }
  },

  submitStudentSubtask(classId, taskId, groupId, subtaskId, payload) {
    const state = readState()
    const subtask = state.subtasks.find(
      (item) =>
        String(item.classId) === String(classId) &&
        String(item.taskId) === String(taskId) &&
        String(item.groupId) === String(groupId) &&
        String(item.subtaskId) === String(subtaskId),
    )
    if (!subtask) {
      throw new Error('子任务不存在')
    }
    subtask.submissionContent = payload?.submissionContent || ''
    subtask.status = 3
    subtask.submittedAt = nowIso()
    writeState(state)
    return { success: true }
  },

  reviewStudentSubtask(classId, taskId, groupId, subtaskId, payload) {
    const state = readState()
    const subtask = state.subtasks.find(
      (item) =>
        String(item.classId) === String(classId) &&
        String(item.taskId) === String(taskId) &&
        String(item.groupId) === String(groupId) &&
        String(item.subtaskId) === String(subtaskId),
    )
    if (!subtask) {
      throw new Error('子任务不存在')
    }
    subtask.status = payload?.approved ? 4 : 2
    subtask.reviewedAt = payload?.approved ? nowIso() : ''
    subtask.reviewComment = payload?.reviewComment || ''
    writeState(state)
    return { success: true }
  },

  sendBackStudentSubtask(classId, taskId, groupId, subtaskId, payload) {
    const state = readState()
    const subtask = state.subtasks.find(
      (item) =>
        String(item.classId) === String(classId) &&
        String(item.taskId) === String(taskId) &&
        String(item.groupId) === String(groupId) &&
        String(item.subtaskId) === String(subtaskId),
    )
    if (!subtask) {
      throw new Error('子任务不存在')
    }
    subtask.status = 2
    subtask.reviewComment = payload?.reviewComment || ''
    subtask.reviewedAt = ''
    writeState(state)
    return { success: true }
  },

  fetchStudentGroupPeerReviews(classId, taskId, groupId) {
    const state = readState()
    return (
      state.peerReviews.find(
        (item) =>
          String(item.classId) === String(classId) &&
          String(item.taskId) === String(taskId) &&
          String(item.groupId) === String(groupId),
      )?.items || []
    )
  },

  submitStudentPeerReview(classId, taskId, groupId, payload) {
    const state = readState()
    const user = getCurrentUser(state, 'student')
    const revieweeId = Number(payload?.revieweeId)
    const score = Number(payload?.score)
    const comment = String(payload?.comment || '').trim()

    if (!revieweeId) {
      throw new Error('请选择要评价的组员')
    }
    if (revieweeId === Number(user.id)) {
      throw new Error('不能评价自己')
    }
    if (Number.isNaN(score) || score < 0 || score > 100) {
      throw new Error('分数须在 0～100 之间')
    }

    let bucket = state.peerReviews.find(
      (item) =>
        String(item.classId) === String(classId) &&
        String(item.taskId) === String(taskId) &&
        String(item.groupId) === String(groupId),
    )
    if (!bucket) {
      bucket = { classId, taskId, groupId, items: [] }
      state.peerReviews.push(bucket)
    }

    if (
      bucket.items.some(
        (item) => Number(item.reviewerId) === Number(user.id) && Number(item.revieweeId) === revieweeId,
      )
    ) {
      throw new Error('互评已提交，不可修改')
    }

    const review = {
      id: `pr-${taskId}-${groupId}-${Date.now()}`,
      reviewerId: Number(user.id),
      revieweeId,
      reviewerAlias: '我',
      revieweeAlias: `成员 ${revieweeId}`,
      score,
      comment,
      submittedAt: nowIso(),
    }
    bucket.items.push(review)
    writeState(state)
    return review
  },

  fetchStudentGroupScoreSummary(classId, taskId, groupId) {
    const state = readState()
    const user = getCurrentUser(state, 'student')
    return buildScoreSummaryForStudent(state, taskId, groupId, user.id)
  },

  fetchStudentTaskAppeals(taskId) {
    const state = readState()
    const user = getCurrentUser(state, 'student')
    return state.appeals.filter(
      (item) => String(item.taskId) === String(taskId) && Number(item.studentId) === Number(user.id),
    )
  },

  createStudentAppeal(taskId, payload) {
    const state = readState()
    const user = getCurrentUser(state, 'student')
    const membership = state.memberships.find((item) => Number(item.studentId) === Number(user.id))
    const appeal = {
      appealId: `AP-${taskId}-${Date.now().toString().slice(-4)}`,
      id: `AP-${taskId}-${Date.now().toString().slice(-4)}`,
      classId: membership?.classId || null,
      taskId: Number(taskId),
      studentId: user.id,
      type: payload?.type || 'teacher_score',
      subtaskId: payload?.subtaskId ?? null,
      status: 0,
      reason: payload?.reason || '',
      createdAt: nowIso(),
      teacherResponse: '',
      adjustedTeacherScore: null,
    }
    state.appeals.unshift(appeal)
    writeState(state)
    return appeal
  },

  fetchStudentNotifications(params = {}) {
    const state = readState()
    const user = getCurrentUser(state, 'student')
    const list = state.notifications
      .filter((item) => Number(item.userId) === Number(user.id))
      .sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime())
    const paged = buildPagedResult(list, params.page, params.size)
    return {
      ...paged,
      unreadCount: list.filter((item) => Number(item.isRead) !== 1).length,
    }
  },

  markStudentNotificationRead(notificationId) {
    const state = readState()
    const notification = state.notifications.find((item) => String(item.id) === String(notificationId))
    if (notification) {
      notification.isRead = 1
      writeState(state)
    }
    return { success: true }
  },

  markAllStudentNotificationsRead() {
    const state = readState()
    const user = getCurrentUser(state, 'student')
    state.notifications.forEach((item) => {
      if (Number(item.userId) === Number(user.id)) {
        item.isRead = 1
      }
    })
    writeState(state)
    return { success: true }
  },

  deleteStudentNotification(notificationId) {
    const state = readState()
    state.notifications = state.notifications.filter(
      (item) => String(item.id) !== String(notificationId),
    )
    writeState(state)
    return { success: true }
  },

  deleteBatchStudentNotifications(ids) {
    const state = readState()
    const idSet = new Set(ids.map(String))
    state.notifications = state.notifications.filter(
      (item) => !idSet.has(String(item.id)),
    )
    writeState(state)
    return { success: true, deleted: ids.length }
  },

  fetchStudentClasses() {
    const state = readState()
    const user = getCurrentUser(state, 'student')
    return state.classes
      .filter((c) => {
        const membership = state.memberships.find(
          (m) => Number(m.studentId) === Number(user.id) && Number(m.classId) === Number(c.classId),
        )
        return !!membership
      })
      .map((c) => {
        const membership = state.memberships.find(
          (m) => Number(m.studentId) === Number(user.id) && Number(m.classId) === Number(c.classId),
        )
        const group = state.groups.find(
          (g) => String(g.classId) === String(c.classId) && g.memberStudentIds?.includes(user.id),
        )
        return {
          classId: c.classId,
          classCode: c.classCode,
          name: c.name,
          semester: c.semester,
          groupingLocked: c.groupingLocked || 0,
          studentStatus: membership?.studentStatus || '在班',
          groupId: group?.groupId || null,
          groupName: group?.name || null,
          groupJoinStatus: membership?.groupJoinStatus || null,
          teacherId: c.teacherId,
          teacherName: c.teacherName || null,
        }
      })
  },

  fetchStudentAllTasks(params = {}) {
    const state = readState()
    const user = getCurrentUser(state, 'student')
    const classIds = state.memberships
      .filter((m) => Number(m.studentId) === Number(user.id))
      .map((m) => String(m.classId))
    let tasks = state.tasks.filter((t) => classIds.includes(String(t.classId)))
    if (params.status != null) {
      tasks = tasks.filter((t) => Number(t.status) === Number(params.status))
    }
    return tasks.map((t) => {
      const cls = state.classes.find((c) => String(c.classId) === String(t.classId))
      return { ...t, className: cls?.name || null }
    })
  },

  fetchStudentAllAppeals() {
    const state = readState()
    const user = getCurrentUser(state, 'student')
    return state.appeals
      .filter((a) => Number(a.studentId) === Number(user.id))
      .map((a) => {
        const task = state.tasks.find((t) => String(t.taskId) === String(a.taskId))
        const cls = task ? state.classes.find((c) => String(c.classId) === String(task.classId)) : null
        return {
          ...a,
          taskName: task?.name || null,
          className: cls?.name || null,
        }
      })
  },

  fetchStudentProfile() {
    const state = readState()
    const user = getCurrentUser(state, 'student')
    return {
      userId: user.id,
      userUuid: user.userUuid || user.id,
      name: user.name,
      phone: user.phone,
      status: user.status ?? 1,
      createdAt: user.createdAt || nowIso(),
    }
  },

  updateStudentProfile(payload) {
    const state = readState()
    const user = getCurrentUser(state, 'student')
    if (payload?.name) {
      user.name = payload.name.trim()
    }
    writeState(state)
    return {
      userId: user.id,
      userUuid: user.userUuid || user.id,
      name: user.name,
      phone: user.phone,
      status: user.status ?? 1,
      createdAt: user.createdAt || nowIso(),
    }
  },

  deleteStudentAccount(password) {
    const state = readState()
    const user = getCurrentUser(state, 'student')
    if (String(password || '') !== String(user.password)) {
      throw new Error('密码不正确')
    }
    user.isDeleted = 1
    user.status = 0
    appendLog(state, {
      userId: user.id,
      role: user.role,
      action: '学生注销账号',
      path: '/api/student/account',
      httpMethod: 'DELETE',
      httpStatus: 200,
    })
    writeState(state)
    return { message: '账号已注销' }
  },

  fetchStudentOperationLogs(params = {}) {
    const state = readState()
    const user = getCurrentUser(state, 'student')
    return getStudentOperationLogPage(state, user, params)
  },

  exportStudentOperationLogs(params = {}) {
    const state = readState()
    const user = getCurrentUser(state, 'student')
    const page = getStudentOperationLogPage(state, user, {
      ...params,
      page: 1,
      size: Number(params.limit) || 500,
    })
    return buildCsv([
      ['操作时间', '操作类型', '请求方法', '目标路径', '查询参数', '状态码', '耗时(ms)'],
      ...page.list.map((item) => [
        item.createdAt,
        item.action,
        item.httpMethod,
        item.path,
        item.queryString || '',
        item.httpStatus,
        item.durationMs,
      ]),
    ])
  },

  reassignStudentSubtask(classId, taskId, groupId, subtaskId, payload) {
    const state = readState()
    const subtask = state.subtasks.find(
      (s) =>
        String(s.classId) === String(classId) &&
        String(s.taskId) === String(taskId) &&
        String(s.groupId) === String(groupId) &&
        String(s.subtaskId) === String(subtaskId),
    )
    if (subtask) {
      subtask.assigneeId = payload?.newAssigneeId || subtask.assigneeId
      if (subtask.status === 1) subtask.status = 2
      writeState(state)
    }
    return subtask || { success: true }
  },

  createStudentGroup(classId, payload) {
    const state = readState()
    const user = getCurrentUser(state, 'student')
    const groupId = `GRP-${classId}-${Date.now().toString().slice(-4)}`
    const inviteCode = Math.random().toString(36).substring(2, 10).toUpperCase()
    const group = {
      groupId,
      classId: Number(classId),
      name: payload?.name || '新小组',
      leaderId: user.id,
      leaderName: user.name,
      memberCount: 1,
      memberStudentIds: [user.id],
      joinMode: payload?.joinMode || 1,
      inviteCode,
      inviteCodeExpireMinutes: payload?.inviteCodeExpireMinutes || 1440,
      createdAt: nowIso(),
    }
    state.groups.push(group)
    const membership = state.memberships.find(
      (m) => Number(m.studentId) === Number(user.id) && Number(m.classId) === Number(classId),
    )
    if (membership) {
      membership.groupId = groupId
      membership.groupName = group.name
      membership.groupJoinStatus = '已加入'
    }
    writeState(state)
    return { group, inviteCode }
  },

  joinStudentGroup(classId, inviteCode) {
    const state = readState()
    const user = getCurrentUser(state, 'student')
    const group = state.groups.find(
      (g) => String(g.classId) === String(classId) && g.inviteCode === inviteCode,
    )
    if (!group) throw new Error('邀请码不存在，请核对后重新输入')
    if (group.memberStudentIds?.includes(user.id)) throw new Error('您已是该小组成员')
    if (group.joinMode === 2) {
      if (!state.pendingMembers) state.pendingMembers = []
      const alreadyPending = state.pendingMembers.some(
        (p) => String(p.groupId) === String(group.groupId) && Number(p.userId) === Number(user.id),
      )
      if (!alreadyPending) {
        state.pendingMembers.push({
          groupId: group.groupId,
          classId: Number(classId),
          userId: user.id,
          userName: user.name,
          requestedAt: nowIso(),
        })
      }
      writeState(state)
      return { status: 'pending', message: '申请已提交，等待组长审批' }
    }
    if (!group.memberStudentIds) group.memberStudentIds = []
    group.memberStudentIds.push(user.id)
    group.memberCount = group.memberStudentIds.length
    const membership = state.memberships.find(
      (m) => Number(m.studentId) === Number(user.id) && Number(m.classId) === Number(classId),
    )
    if (membership) {
      membership.groupId = group.groupId
      membership.groupName = group.name
      membership.groupJoinStatus = '已加入'
    }
    writeState(state)
    return { status: 'joined', group }
  },

  approveGroupMember(classId, groupId, userId) {
    const state = readState()
    const group = state.groups.find((g) => String(g.groupId) === String(groupId))
    if (group) {
      if (!group.memberStudentIds) group.memberStudentIds = []
      if (!group.memberStudentIds.includes(Number(userId))) {
        group.memberStudentIds.push(Number(userId))
        group.memberCount = group.memberStudentIds.length
      }
      if (state.pendingMembers) {
        state.pendingMembers = state.pendingMembers.filter(
          (p) => !(String(p.groupId) === String(groupId) && Number(p.userId) === Number(userId)),
        )
      }
      writeState(state)
    }
    return { success: true }
  },

  rejectGroupMember(classId, groupId, userId) {
    const state = readState()
    if (state.pendingMembers) {
      state.pendingMembers = state.pendingMembers.filter(
        (p) => !(String(p.groupId) === String(groupId) && Number(p.userId) === Number(userId)),
      )
      writeState(state)
    }
    return { success: true }
  },

  refreshGroupInviteCode(classId, groupId) {
    const state = readState()
    const group = state.groups.find((g) => String(g.groupId) === String(groupId))
    if (group) {
      group.inviteCode = Math.random().toString(36).substring(2, 10).toUpperCase()
      writeState(state)
      return { inviteCode: group.inviteCode }
    }
    return { inviteCode: '' }
  },

  removeGroupMember(classId, groupId, userId) {
    const state = readState()
    const group = state.groups.find((g) => String(g.groupId) === String(groupId))
    if (group && group.memberStudentIds) {
      group.memberStudentIds = group.memberStudentIds.filter((id) => Number(id) !== Number(userId))
      group.memberCount = group.memberStudentIds.length
      writeState(state)
    }
    return { success: true }
  },

  fetchGroupJoinPending(classId, groupId) {
    const state = readState()
    if (!state.pendingMembers) return []
    return state.pendingMembers
      .filter((p) => String(p.groupId) === String(groupId))
      .map((p) => ({
        userId: p.userId,
        userName: p.userName,
        requestedAt: p.requestedAt,
      }))
  },
}

export const mockTeacherApi = {
  fetchTeacherClasses() {
    const state = readState()
    const user = getCurrentUser(state, 'teacher')
    return buildTeacherClassList(state, user.id)
  },

  createTeacherClass(payload) {
    const state = readState()
    const user = getCurrentUser(state, 'teacher')
    const classId = nextNumericId(state.classes, 'classId')
    const item = {
      classId,
      classCode: `CLS-${classId}`,
      name: String(payload?.name || '').trim() || `新班级 ${classId}`,
      semester: String(payload?.semester || '').trim() || '2026-Spring',
      status: 1,
      teacherId: user.id,
      teacherName: user.name,
      groupSizeMin: Number(payload?.groupSizeMin) || 1,
      groupSizeMax: Number(payload?.groupSizeMax) || 10,
      classInviteCode: `CLASS-${classId}`,
      groupingLocked: false,
      inviteExpireAt: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString(),
    }
    state.classes.unshift(item)
    appendLog(state, {
      userId: user.id,
      role: user.role,
      action: '创建班级',
      httpMethod: 'POST',
      path: '/api/teacher/classes',
    })
    writeState(state)
    return item
  },

  fetchTeacherClassDetail(classId) {
    const state = readState()
    const classInfo = getClassById(state, classId)
    if (!classInfo) {
      throw new Error('班级不存在')
    }
    const studentCount = state.memberships.filter((item) => String(item.classId) === String(classId)).length
    return {
      ...classInfo,
      studentCount,
      activeInviteCode: classInfo.classInviteCode,
      inviteExpireAt: classInfo.inviteExpireAt || '',
      groupingLocked: classInfo.groupingLocked ? 1 : 0,
    }
  },

  deleteTeacherClass(classId) {
    const state = readState()
    const user = getCurrentUser(state, 'teacher')
    const classInfo = getClassById(state, classId)
    if (!classInfo) {
      throw new Error('班级不存在')
    }

    const taskIds = state.tasks
      .filter((item) => String(item.classId) === String(classId))
      .map((item) => Number(item.taskId))
    const groupIds = state.groups
      .filter((item) => String(item.classId) === String(classId))
      .map((item) => String(item.groupId))

    state.classes = state.classes.filter((item) => String(item.classId) !== String(classId))
    state.memberships = state.memberships.filter((item) => String(item.classId) !== String(classId))
    state.groups = state.groups.filter((item) => String(item.classId) !== String(classId))
    state.tasks = state.tasks.filter((item) => String(item.classId) !== String(classId))
    state.subtasks = state.subtasks.filter((item) => String(item.classId) !== String(classId))
    state.peerReviews = state.peerReviews.filter((item) => String(item.classId) !== String(classId))
    state.scoreSummaries = state.scoreSummaries.filter((item) => String(item.classId) !== String(classId))
    state.appeals = state.appeals.filter((item) => String(item.classId) !== String(classId))

    if (Array.isArray(state.notifications)) {
      state.notifications = state.notifications.filter((item) => {
        const content = String(item?.content || '')
        return !taskIds.some((taskId) => content.includes(String(taskId)))
      })
    }

    appendLog(state, {
      userId: user.id,
      role: user.role,
      action: '删除班级',
      httpMethod: 'DELETE',
      path: `/api/teacher/classes/${classId}`,
    })
    writeState(state)

    return {
      success: true,
      classId: Number(classId),
      removedTaskCount: taskIds.length,
      removedGroupCount: groupIds.length,
    }
  },

  restoreTeacherClass(classId) {
    const state = readState()
    const user = getCurrentUser(state, 'teacher')
    const classInfo = getClassById(state, classId)
    if (!classInfo) {
      throw new Error('班级不存在')
    }
    classInfo.status = 1
    appendLog(state, {
      userId: user.id,
      role: user.role,
      action: '恢复班级',
      httpMethod: 'POST',
      path: `/api/teacher/classes/${classId}/restore`,
    })
    writeState(state)
    return { success: true, classId: Number(classId) }
  },

  generateTeacherClassInviteCode(classId) {
    const state = readState()
    const user = getCurrentUser(state, 'teacher')
    const classInfo = getClassById(state, classId)
    if (!classInfo) {
      throw new Error('班级不存在')
    }

    classInfo.classInviteCode = `CLASS-${classId}-${String(Date.now()).slice(-4)}`
    classInfo.inviteExpireAt = new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString()

    appendLog(state, {
      userId: user.id,
      role: user.role,
      action: '刷新班级邀请码',
      httpMethod: 'POST',
      path: `/api/teacher/classes/${classId}/invite-codes`,
    })
    writeState(state)

    return {
      classId: Number(classId),
      inviteCode: classInfo.classInviteCode,
      activeInviteCode: classInfo.classInviteCode,
      expireAt: classInfo.inviteExpireAt,
      inviteExpireAt: classInfo.inviteExpireAt,
    }
  },

  setTeacherClassGroupingLock(classId, locked) {
    const state = readState()
    const user = getCurrentUser(state, 'teacher')
    const classInfo = getClassById(state, classId)
    if (!classInfo) {
      throw new Error('班级不存在')
    }

    classInfo.groupingLocked = Boolean(locked)
    appendLog(state, {
      userId: user.id,
      role: user.role,
      action: classInfo.groupingLocked ? '锁定班级分组' : '解锁班级分组',
      httpMethod: 'PUT',
      path: `/api/teacher/classes/${classId}/grouping-lock`,
    })
    writeState(state)

    return {
      classId: Number(classId),
      locked: classInfo.groupingLocked,
      groupingLocked: classInfo.groupingLocked,
    }
  },

  removeTeacherClassStudent(classId, studentId) {
    const state = readState()
    const user = getCurrentUser(state, 'teacher')
    const classInfo = getClassById(state, classId)
    if (!classInfo) {
      throw new Error('班级不存在')
    }

    const membershipIndex = state.memberships.findIndex(
      (item) => String(item.classId) === String(classId) && Number(item.studentId) === Number(studentId),
    )
    if (membershipIndex < 0) {
      throw new Error('学生不在当前班级中')
    }

    const membership = state.memberships[membershipIndex]
    if (membership.groupId) {
      const group = getGroupById(state, membership.groupId)
      if (group?.memberStudentIds) {
        group.memberStudentIds = group.memberStudentIds.filter((item) => Number(item) !== Number(studentId))
        group.memberCount = group.memberStudentIds.length
        if (Number(group.leaderId) === Number(studentId)) {
          const nextLeaderId = group.memberStudentIds[0]
          group.leaderId = nextLeaderId || null
          group.leaderName = nextLeaderId ? getStudentName(state, nextLeaderId) : '待设置'
        }
      }
    }

    state.memberships.splice(membershipIndex, 1)

    appendLog(state, {
      userId: user.id,
      role: user.role,
      action: '移除班级学生',
      httpMethod: 'DELETE',
      path: `/api/teacher/classes/${classId}/students/${studentId}`,
    })
    writeState(state)
    return { success: true }
  },

  fetchTeacherClassStudents(classId, params = {}) {
    const state = readState()
    const list = state.memberships
      .filter((item) => String(item.classId) === String(classId))
      .map((membership) => {
        const user = state.users.find((item) => Number(item.id) === Number(membership.studentId))
        const group = membership.groupId ? getGroupById(state, membership.groupId) : null
        return {
          studentId: user?.id || membership.studentId,
          name: user?.name || '未知学生',
          email: user?.email || '-',
          status: Number(user?.status) === 1 ? 1 : 0,
          statusLabel: Number(user?.status) === 1 ? '在班' : '非在班',
          groupName: group?.name || '未分组',
          joinedAt: membership.joinedAt,
        }
      })
    return buildPagedResult(list, params.page, params.size)
  },

  fetchTeacherClassTasks(classId) {
    const state = readState()
    return state.tasks.filter((item) => String(item.classId) === String(classId))
  },

  createTeacherClassTask(classId, payload) {
    const state = readState()
    const user = getCurrentUser(state, 'teacher')
    const classInfo = getClassById(state, classId)
    if (!classInfo) {
      throw new Error('班级不存在')
    }

    const taskId = nextNumericId(state.tasks, 'taskId')
    const task = {
      taskId,
      classId: Number(classId),
      name: String(payload?.name || '').trim() || `新任务 ${taskId}`,
      description: String(payload?.description || '').trim(),
      taskStatus: 0,
      deadline: payload?.deadline || new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
      enablePeerReview: Boolean(payload?.enablePeerReview),
      canPeerReviewNow: false,
      canSubmitAppeal: false,
      peerReviewPhase: 'not_started',
      peerReviewOffsetHours: Number(payload?.peerReviewOffsetHours || 1),
      peerReviewMaxScore: Number(payload?.peerReviewMaxScore || 100),
      peerReviewWeight: Number(payload?.peerReviewWeight ?? 0.4),
      teacherScoreWeight: Number(payload?.teacherScoreWeight ?? 0.6),
    }

    state.tasks.unshift(task)
    appendLog(state, {
      userId: user.id,
      role: user.role,
      action: '发布班级任务',
      httpMethod: 'POST',
      path: `/api/teacher/classes/${classId}/tasks`,
    })
    writeState(state)
    return task
  },

  fetchTeacherTaskDetail(classId, taskId) {
    const state = readState()
    const task = getTaskById(state, classId, taskId)
    if (!task) {
      throw new Error('任务不存在')
    }
    return task
  },

  updateTeacherClassTask(classId, taskId, payload) {
    const state = readState()
    const task = getTaskById(state, classId, taskId)
    if (!task) {
      throw new Error('任务不存在')
    }
    if (payload?.peerReviewDeadline) {
      task.peerReviewDeadline = payload.peerReviewDeadline
    }
    if (payload?.name != null) task.name = String(payload.name || '').trim() || task.name
    if (payload?.description != null) task.description = String(payload.description || '')
    if (payload?.deadline != null) task.deadline = payload.deadline
    if (payload?.enablePeerReview != null) task.enablePeerReview = Boolean(payload.enablePeerReview)
    writeState(state)
    return task
  },

  fetchTeacherClassGroups(classId) {
    const state = readState()
    return state.groups.filter((item) => String(item.classId) === String(classId))
  },

  createTeacherClassGroup(classId, payload) {
    const state = readState()
    const user = getCurrentUser(state, 'teacher')
    const classInfo = getClassById(state, classId)
    if (!classInfo) {
      throw new Error('班级不存在')
    }

    const groupId = String(nextNumericId(state.groups, 'groupId'))
    const memberStudentIds = Array.isArray(payload?.memberStudentIds)
      ? payload.memberStudentIds.map((item) => Number(item)).filter((item) => !Number.isNaN(item))
      : []
    const leaderId = Number(payload?.leaderId)

    if (!memberStudentIds.length) {
      throw new Error('请至少选择一名学生')
    }

    if (!memberStudentIds.includes(leaderId)) {
      memberStudentIds.unshift(leaderId)
    }

    const group = {
      groupId,
      classId: Number(classId),
      name: String(payload?.name || '').trim() || `第${state.groups.length + 1}组`,
      leaderId,
      leaderName: getStudentName(state, leaderId),
      memberStudentIds: Array.from(new Set(memberStudentIds)),
      joinMode: '教师创建',
      memberCount: Array.from(new Set(memberStudentIds)).length,
    }

    group.memberStudentIds.forEach((studentId) => {
      const membership = getGroupMembership(state, classId, studentId)
      if (membership) {
        membership.groupId = groupId
        membership.groupJoinStatus = '已加入'
      }
    })

    state.groups.unshift(group)
    appendLog(state, {
      userId: user.id,
      role: user.role,
      action: '创建班级小组',
      httpMethod: 'POST',
      path: `/api/teacher/classes/${classId}/groups`,
    })
    writeState(state)
    return group
  },

  moveTeacherClassGroupMember(classId, fromGroupId, studentId, targetGroupId) {
    const state = readState()
    const user = getCurrentUser(state, 'teacher')
    const fromGroup = getGroupById(state, fromGroupId)
    const targetGroup = getGroupById(state, targetGroupId)
    const membership = getGroupMembership(state, classId, studentId)
    const isUngroupedMove = String(fromGroupId) === 'ungrouped' || !membership?.groupId

    if (!targetGroup || !membership) {
      throw new Error('移动小组成员失败，请刷新后重试')
    }

    if (!isUngroupedMove) {
      if (!fromGroup) {
        throw new Error('移动小组成员失败，请刷新后重试')
      }

      fromGroup.memberStudentIds = (fromGroup.memberStudentIds || []).filter((item) => Number(item) !== Number(studentId))
      fromGroup.memberCount = fromGroup.memberStudentIds.length
      if (Number(fromGroup.leaderId) === Number(studentId)) {
        const nextLeaderId = fromGroup.memberStudentIds[0]
        fromGroup.leaderId = nextLeaderId || null
        fromGroup.leaderName = nextLeaderId ? getStudentName(state, nextLeaderId) : '待设置'
      }
    }

    targetGroup.memberStudentIds = Array.from(new Set([...(targetGroup.memberStudentIds || []), Number(studentId)]))
    targetGroup.memberCount = targetGroup.memberStudentIds.length
    membership.groupId = String(targetGroup.groupId)
    membership.groupJoinStatus = '已加入'

    appendLog(state, {
      userId: user.id,
      role: user.role,
      action: '跨组移动学生',
      httpMethod: 'PUT',
      path: `/api/teacher/classes/${classId}/groups/${fromGroupId}/members/${studentId}/move`,
    })
    writeState(state)
    return targetGroup
  },

  addStudentToGroup(classId, targetGroupId, studentId) {
    const state = readState()
    const user = getCurrentUser(state, 'teacher')
    const targetGroup = getGroupById(state, targetGroupId)
    const membership = getGroupMembership(state, classId, studentId)

    if (!targetGroup) {
      throw new Error('目标小组不存在')
    }

    if (membership?.groupId && membership.groupJoinStatus === '已加入') {
      throw new Error('该学生已在本班级的小组中')
    }

    targetGroup.memberStudentIds = Array.from(new Set([...(targetGroup.memberStudentIds || []), Number(studentId)]))
    targetGroup.memberCount = targetGroup.memberStudentIds.length
    if (membership) {
      membership.groupId = String(targetGroup.groupId)
      membership.groupJoinStatus = '已加入'
    }

    appendLog(state, {
      userId: user.id,
      role: user.role,
      action: '将未分组学生加入小组',
      httpMethod: 'POST',
      path: `/api/teacher/classes/${classId}/groups/${targetGroupId}/members/${studentId}/add`,
    })
    writeState(state)
    return targetGroup
  },

  fetchTeacherGroupSubtaskProgress(classId, taskId, groupId) {
    const state = readState()
    return buildGroupProgress(state, classId, taskId, groupId)
  },

  fetchTeacherGroupSubtasks(classId, taskId, groupId) {
    const state = readState()
    return state.subtasks.filter(
      (item) =>
        String(item.classId) === String(classId) &&
        String(item.taskId) === String(taskId) &&
        String(item.groupId) === String(groupId),
    )
  },

  fetchTeacherGroupPeerReviews(classId, taskId, groupId) {
    const state = readState()
    return (
      state.peerReviews.find(
        (item) =>
          String(item.classId) === String(classId) &&
          String(item.taskId) === String(taskId) &&
          String(item.groupId) === String(groupId),
      )?.items || []
    )
  },

  fetchTeacherGroupScoreSummaries(classId, taskId, groupId) {
    const state = readState()
    return (
      state.scoreSummaries.find(
        (item) =>
          String(item.classId) === String(classId) &&
          String(item.taskId) === String(taskId) &&
          String(item.groupId) === String(groupId),
      )?.items || []
    )
  },

  fetchTeacherTaskAppeals(classId, taskId) {
    const state = readState()
    return state.appeals.filter(
      (item) => String(item.classId) === String(classId) && String(item.taskId) === String(taskId),
    )
  },

  resolveTeacherTaskAppeal(classId, taskId, appealId, payload) {
    const state = readState()
    const user = getCurrentUser(state, 'teacher')
    const appeal = state.appeals.find(
      (item) =>
        String(item.classId) === String(classId) &&
        String(item.taskId) === String(taskId) &&
        String(item.appealId) === String(appealId),
    )
    if (!appeal) {
      throw new Error('申诉不存在')
    }

    appeal.status = Number(payload?.outcome) || 2
    appeal.teacherResponse = payload?.teacherResponse || ''
    appeal.adjustedTeacherScore =
      payload?.adjustedTeacherScore === undefined ? null : Number(payload.adjustedTeacherScore)

    if (appeal.adjustedTeacherScore !== null) {
      const summaryGroup = state.scoreSummaries.find(
        (item) =>
          String(item.classId) === String(classId) &&
          String(item.taskId) === String(taskId) &&
          item.items.some((summary) => Number(summary.studentId) === Number(appeal.studentId)),
      )
      const summary = summaryGroup?.items.find((item) => Number(item.studentId) === Number(appeal.studentId))
      if (summary) {
        summary.teacherScore = appeal.adjustedTeacherScore
        summary.weightedTotal100 = Number(
          ((Number(summary.peerAverageOn100 || 0) * Number(summary.peerReviewWeight || 0)) +
          (Number(summary.teacherScore || 0) * Number(summary.teacherScoreWeight || 0))).toFixed(1),
        )
      }
    }

    appendLog(state, {
      userId: user.id,
      role: user.role,
      action: '处理申诉',
      httpMethod: 'PUT',
      path: `/api/teacher/classes/${classId}/tasks/${taskId}/appeals/${appealId}`,
    })
    writeState(state)
    return { success: true }
  },

  fetchTeacherOperationLogs() {
    const state = readState()
    const user = getCurrentUser(state, 'teacher')
    return state.logs
      .filter((item) => Number(item.userId) === Number(user.id) || item.role === 'teacher')
      .sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime())
      .map((item) => ({
        id: item.id,
        action: item.action,
        httpMethod: item.httpMethod,
        path: item.path,
        queryString: item.queryString,
        durationMs: item.durationMs,
        httpStatus: item.httpStatus,
        success: item.success,
        createdAt: item.createdAt,
      }))
  },

  fetchTeacherProfile() {
    const state = readState()
    const user = getCurrentUser(state, 'teacher')
    return {
      userId: user.id,
      name: user.name,
      phone: user.phone,
      role: '教师',
      status: user.status ?? 1,
      createdAt: user.createdAt || nowIso(),
    }
  },

  updateTeacherProfile(payload) {
    const state = readState()
    const user = getCurrentUser(state, 'teacher')

    user.name = String(payload?.name || user.name || '').trim() || user.name || '教师账号'
    user.phone = String(payload?.phone || user.phone || '').trim() || user.phone || ''

    appendLog(state, {
      userId: user.id,
      role: user.role,
      action: '更新个人资料',
      httpMethod: 'PUT',
      path: '/local/teacher/profile',
    })
    writeState(state)

    return {
      userId: user.id,
      name: user.name,
      phone: user.phone,
      role: '教师',
      status: user.status ?? 1,
      createdAt: user.createdAt || nowIso(),
    }
  },

  changeTeacherOwnPassword(payload) {
    const state = readState()
    const user = getCurrentUser(state, 'teacher')
    const oldPassword = String(payload?.oldPassword || '')
    const newPassword = String(payload?.newPassword || '')

    if (!oldPassword) {
      throw new Error('请输入当前密码')
    }

    if (user.password !== oldPassword) {
      throw new Error('当前密码不正确')
    }

    if (newPassword.length < 6 || newPassword.length > 64) {
      throw new Error('新密码长度需在 6 到 64 个字符之间')
    }

    user.password = newPassword
    appendLog(state, {
      userId: user.id,
      role: user.role,
      action: '修改个人密码',
      httpMethod: 'PUT',
      path: '/local/teacher/password',
    })
    writeState(state)
    return { success: true }
  },

  fetchCourseScores(classId) {
    const state = readState()
    const classStudents = state.classStudents.filter(
      (item) => String(item.classId) === String(classId) && !item.isDeleted,
    )
    return classStudents.map((cs) => {
      const user = state.users.find((u) => Number(u.id) === Number(cs.studentId))
      const score = (state.courseScores || []).find(
        (s) => String(s.classId) === String(classId) && Number(s.studentId) === Number(cs.studentId),
      )
      return {
        studentId: cs.studentId,
        studentName: user?.name || '',
        totalScore: score?.totalScore ?? null,
        scoreType: score?.scoreType ?? null,
        calculatedAt: score?.calculatedAt || '',
      }
    })
  },

  saveCourseScore(classId, payload) {
    const state = readState()
    if (!state.courseScores) state.courseScores = []
    const existing = state.courseScores.find(
      (s) => String(s.classId) === String(classId) && Number(s.studentId) === Number(payload.studentId),
    )
    if (existing) {
      existing.totalScore = Number(payload.totalScore)
      existing.scoreType = 2
      existing.calculatedAt = nowIso()
    } else {
      state.courseScores.push({
        classId: String(classId),
        studentId: Number(payload.studentId),
        totalScore: Number(payload.totalScore),
        scoreType: 2,
        calculatedAt: nowIso(),
      })
    }
    const user = state.users.find((u) => Number(u.id) === Number(payload.studentId))
    writeState(state)
    return {
      studentId: payload.studentId,
      studentName: user?.name || '',
      totalScore: Number(payload.totalScore),
      scoreType: 2,
      calculatedAt: nowIso(),
    }
  },

  fetchTeacherScores(classId, taskId) {
    const state = readState()
    const scores = (state.teacherScores || []).filter(
      (s) => String(s.classId) === String(classId) && String(s.taskId) === String(taskId),
    )
    return scores.map((s) => {
      const isGroup = String(s.targetType || '').toLowerCase() === 'group' || s.groupId != null
      const user = state.users.find((u) => Number(u.id) === Number(s.studentId))
      return {
        studentId: isGroup ? null : s.studentId,
        groupId: isGroup ? (s.groupId ?? s.targetId ?? null) : null,
        studentName: isGroup ? '小组' : user?.name || '',
        score: s.score,
        scoredBy: s.scoredBy,
        scoredAt: s.scoredAt,
      }
    })
  },

  saveTeacherScore(classId, taskId, payload) {
    const state = readState()
    if (!state.teacherScores) state.teacherScores = []
    const isGroup = String(payload.targetType || '').toLowerCase() === 'group'
    const targetId = isGroup ? Number(payload.groupId) : Number(payload.studentId)
    const existing = state.teacherScores.find((s) => {
      if (String(s.classId) !== String(classId) || String(s.taskId) !== String(taskId)) {
        return false
      }
      if (isGroup) {
        return (
          String(s.targetType || '').toLowerCase() === 'group' &&
          Number(s.groupId ?? s.targetId) === targetId
        )
      }
      return String(s.targetType || 'student').toLowerCase() !== 'group' && Number(s.studentId) === targetId
    })
    if (existing) {
      existing.score = Number(payload.score)
      existing.scoredAt = nowIso()
    } else {
      state.teacherScores.push({
        classId: Number(classId),
        taskId: Number(taskId),
        targetType: isGroup ? 'group' : 'student',
        studentId: isGroup ? null : targetId,
        groupId: isGroup ? targetId : null,
        targetId: isGroup ? targetId : undefined,
        score: Number(payload.score),
        scoredBy: getCurrentUser(state, 'teacher').id,
        scoredAt: nowIso(),
      })
    }
    writeState(state)
    const user = state.users.find((u) => Number(u.id) === Number(payload.studentId))
    return {
      studentId: isGroup ? null : targetId,
      groupId: isGroup ? targetId : null,
      studentName: isGroup ? '小组' : user?.name || '',
      score: Number(payload.score),
      scoredAt: nowIso(),
    }
  },
}

export const mockAdminApi = {
  fetchAdminUsers(params = {}) {
    const state = readState()
    const deletedFilter = params?.isDeleted === undefined ? 0 : Number(params.isDeleted)
    const statusFilter = params?.status === '' || params?.status === undefined ? null : Number(params.status)
    let list = state.users
      .filter((item) => Number(Boolean(item.isDeleted)) === deletedFilter)
      .filter((item) => !params.role || item.role === params.role)
      .filter((item) => statusFilter === null || Number(item.status) === statusFilter)
      .sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime())
      .map((item) => ({
        id: item.id,
        role: item.role,
        phone: item.phone,
        email: item.email,
        name: item.name,
        status: item.status,
        isDeleted: item.isDeleted ? 1 : 0,
        createdAt: item.createdAt,
      }))

    return buildPagedResult(list, params.page, params.size)
  },

  createAdminUser(payload = {}) {
    const state = readState()
    const currentUser = getCurrentUser(state, 'admin')
    const role = String(payload.role || '').trim()
    const email = String(payload.email || '').trim().toLowerCase()
    const phone = String(payload.phone || '').trim()
    const name = String(payload.name || '').trim()
    if (role !== 'teacher' && role !== 'student') {
      throw new Error('管理员端仅支持新增教师或学生')
    }
    if (!name || !email || !payload.password) {
      throw new Error('姓名、邮箱和密码不能为空')
    }
    if (state.users.some((item) => String(item.email || '').toLowerCase() === email)) {
      throw new Error('该邮箱已注册')
    }
    if (phone && state.users.some((item) => String(item.phone || '') === phone)) {
      throw new Error('该手机号已被使用')
    }
    const user = {
      id: nextNumericId(state.users, 'id'),
      role,
      phone: phone || null,
      email,
      name,
      status: Number(payload.status) === 0 ? 0 : 1,
      isDeleted: false,
      createdAt: nowIso(),
      password: payload.password,
    }
    state.users.unshift(user)
    appendLog(state, {
      userId: currentUser.id,
      role: currentUser.role,
      action: '新增账户',
      httpMethod: 'POST',
      path: '/api/admin/users',
    })
    writeState(state)
    return { ...user, isDeleted: 0 }
  },

  updateAdminUser(userId, payload = {}) {
    const state = readState()
    const currentUser = getCurrentUser(state, 'admin')
    const target = state.users.find((item) => Number(item.id) === Number(userId))
    if (!target || target.isDeleted) {
      throw new Error('用户不存在')
    }
    if (target.role === 'admin') {
      throw new Error('管理员账户不能在此操作')
    }
    const email = String(payload.email || '').trim().toLowerCase()
    const phone = String(payload.phone || '').trim()
    const name = String(payload.name || '').trim()
    if (!name || !email) {
      throw new Error('姓名和邮箱不能为空')
    }
    if (state.users.some((item) => Number(item.id) !== Number(userId) && String(item.email || '').toLowerCase() === email)) {
      throw new Error('该邮箱已注册')
    }
    if (phone && state.users.some((item) => Number(item.id) !== Number(userId) && String(item.phone || '') === phone)) {
      throw new Error('该手机号已被使用')
    }
    target.name = name
    target.email = email
    target.phone = phone || null
    target.status = Number(payload.status) === 0 ? 0 : 1
    appendLog(state, {
      userId: currentUser.id,
      role: currentUser.role,
      action: '编辑账户',
      httpMethod: 'PUT',
      path: `/api/admin/users/${userId}`,
    })
    writeState(state)
    return { ...target, isDeleted: target.isDeleted ? 1 : 0 }
  },

  fetchTeacherInviteCodes(params = {}) {
    const state = readState()
    const page = Math.max(1, Number(params?.page) || 1)
    const size = Math.max(1, Number(params?.size) || 10)
    const codeFilter = String(params?.code || '').trim()
    const expiredFilter = params?.expired === '' || params?.expired === undefined ? null : params?.expired
    const expireFrom = params?.expireFrom ? new Date(params.expireFrom) : null
    const expireTo = params?.expireTo ? new Date(params.expireTo) : null
    const now = Date.now()

    let list = [...(state.invites || [])]

    if (codeFilter) {
      list = list.filter((item) => String(item.code || '') === codeFilter)
    }

    if (params?.status !== '' && params?.status !== undefined && params?.status !== null) {
      list = list.filter((item) => Number(item.status) === Number(params.status))
    }

    if (expiredFilter !== null) {
      const expected = String(expiredFilter) === 'true'
      list = list.filter((item) => {
        const expireAt = new Date(item.expireAt).getTime()
        return expected ? expireAt < now : expireAt >= now
      })
    }

    if (expireFrom && !Number.isNaN(expireFrom.getTime())) {
      list = list.filter((item) => new Date(item.expireAt).getTime() >= expireFrom.getTime())
    }

    if (expireTo && !Number.isNaN(expireTo.getTime())) {
      list = list.filter((item) => new Date(item.expireAt).getTime() <= expireTo.getTime())
    }

    list = list
      .sort((left, right) => new Date(right.createdAt || right.expireAt).getTime() - new Date(left.createdAt || left.expireAt).getTime())
        .map((item, index) => ({
          id: item.id || index + 1,
          code: item.code,
          status: Number(item.status) || 0,
          expireAt: item.expireAt,
          usedBy: item.usedBy || null,
          usedAt: item.usedAt || null,
          usedByUser: buildInviteUsedByUser(state, item.usedBy),
        }))

    return buildPagedResult(list, page, size)
  },

  updateAdminUserStatus(userId, status) {
    const state = readState()
    const currentUser = getCurrentUser(state, 'admin')
    const target = state.users.find((item) => Number(item.id) === Number(userId))
    if (!target) {
      throw new Error('用户不存在')
    }
    target.status = Number(status)
    appendLog(state, {
      userId: currentUser.id,
      role: currentUser.role,
      action: Number(status) === 1 ? '启用账户' : '禁用账户',
      httpMethod: 'PUT',
      path: `/api/admin/users/${userId}/status`,
    })
    writeState(state)
    return { success: true }
  },

  deleteAdminUser(userId) {
    const state = readState()
    const currentUser = getCurrentUser(state, 'admin')
    const target = state.users.find((item) => Number(item.id) === Number(userId))
    if (!target || target.isDeleted) {
      throw new Error('用户不存在')
    }
    if (target.role === 'admin') {
      throw new Error('管理员账户不能在此操作')
    }

    target.isDeleted = true
    target.status = 0
    target.deletedAt = nowIso()

    const invite = state.invites.find((item) => Number(item.usedBy) === Number(userId))
    if (invite) {
      invite.status = 0
      invite.usedBy = null
      invite.usedAt = null
    }

    appendLog(state, {
      userId: currentUser.id,
      role: currentUser.role,
      action: '删除账户',
      httpMethod: 'DELETE',
      path: `/api/admin/users/${userId}`,
    })
    writeState(state)
    return {
      message: '账户已删除',
      role: target.role,
      releasedInviteCode: invite?.code || null,
      releasedInviteStatus: invite ? 0 : null,
    }
  },

  deleteAdminTeacherUser(userId) {
    return this.deleteAdminUser(userId)
  },

  restoreAdminUser(userId) {
    const state = readState()
    const currentUser = getCurrentUser(state, 'admin')
    const target = state.users.find((item) => Number(item.id) === Number(userId))
    if (!target || !target.isDeleted) {
      throw new Error('用户不存在')
    }
    if (target.role === 'admin') {
      throw new Error('管理员账户不能在此操作')
    }
    target.isDeleted = false
    target.deletedAt = null
    target.status = 0
    appendLog(state, {
      userId: currentUser.id,
      role: currentUser.role,
      action: '恢复账户',
      httpMethod: 'POST',
      path: `/api/admin/users/${userId}/restore`,
    })
    writeState(state)
    return { ...target, isDeleted: 0 }
  },

  resetAdminUserPassword(userId, payload) {
    const state = readState()
    const currentUser = getCurrentUser(state, 'admin')
    const target = state.users.find((item) => Number(item.id) === Number(userId))
    if (!target) {
      throw new Error('用户不存在')
    }
    const autoGenerated = !payload?.newPassword
    const newPassword = payload?.newPassword || `TT${String(Date.now()).slice(-6)}`
    target.password = newPassword
    appendLog(state, {
      userId: currentUser.id,
      role: currentUser.role,
      action: '重置用户密码',
      httpMethod: 'POST',
      path: `/api/admin/users/${userId}/reset-password`,
    })
    writeState(state)
    return { message: '密码已重置', autoGenerated }
  },

  createTeacherInviteCode(expireDays = 30) {
    const state = readState()
    const currentUser = getCurrentUser(state, 'admin')
    const invite = {
      id: nextNumericId(state.invites, 'id'),
      code: createInviteCode(),
      expireAt: new Date(Date.now() + (Number(expireDays) || 30) * 24 * 60 * 60 * 1000).toISOString(),
      status: 0,
      createdAt: nowIso(),
      usedBy: null,
      usedAt: null,
      usedByUser: null,
    }
    state.invites.unshift(invite)
    appendLog(state, {
      userId: currentUser.id,
      role: currentUser.role,
      action: '生成邀请码',
      httpMethod: 'POST',
      path: '/api/admin/teacher-invite-codes',
      queryString: `expireDays=${expireDays}`,
    })
    writeState(state)
    return invite
  },

  batchCreateTeacherInviteCodes(count, expireDays = 30) {
    const total = Number(count)
    if (!Number.isInteger(total) || total <= 0) {
      throw new Error('count参数无效')
    }

    const succeeded = []
    for (let index = 0; index < total; index += 1) {
      succeeded.push(this.createTeacherInviteCode(expireDays))
    }

    return {
      succeeded,
      failed: [],
    }
  },

  revokeTeacherInviteCode(code) {
    const state = readState()
    const currentUser = getCurrentUser(state, 'admin')
    const invite = state.invites.find((item) => item.code === code)
    if (!invite) {
      throw new Error('邀请码不存在')
    }
    if (Number(invite.status) === 1) {
      throw new Error('邀请码已绑定教师，无法停止使用')
    }
    if (Number(invite.status) === 2) {
      throw new Error('邀请码当前不可停止使用')
    }
    invite.status = 2
    appendLog(state, {
      userId: currentUser.id,
      role: currentUser.role,
      action: '停止使用邀请码',
      httpMethod: 'POST',
      path: `/api/admin/teacher-invite-codes/${code}/revoke`,
    })
    writeState(state)
    return { success: true }
  },

  resumeTeacherInviteCode(code) {
    const state = readState()
    const currentUser = getCurrentUser(state, 'admin')
    const invite = state.invites.find((item) => item.code === code)
    if (!invite) {
      throw new Error('邀请码不存在')
    }
    if (Number(invite.status) === 1) {
      throw new Error('邀请码已绑定教师，无法恢复为未使用')
    }
    if (Number(invite.status) !== 2) {
      throw new Error('仅已停止使用的邀请码可以继续使用')
    }
    if (new Date(invite.expireAt).getTime() < Date.now()) {
      throw new Error('邀请码已过期，无法继续使用')
    }
    invite.status = 0
    appendLog(state, {
      userId: currentUser.id,
      role: currentUser.role,
      action: '恢复邀请码使用',
      httpMethod: 'POST',
      path: `/api/admin/teacher-invite-codes/${code}/resume`,
    })
    writeState(state)
    return { success: true }
  },

  deleteTeacherInviteCode(code) {
    const state = readState()
    const currentUser = getCurrentUser(state, 'admin')
    const inviteIndex = state.invites.findIndex((item) => item.code === code)
    if (inviteIndex < 0) {
      throw new Error('邀请码不存在')
    }
    if (Number(state.invites[inviteIndex].status) !== 0) {
      throw new Error('仅未使用的邀请码可以删除')
    }
    state.invites.splice(inviteIndex, 1)
    appendLog(state, {
      userId: currentUser.id,
      role: currentUser.role,
      action: '删除邀请码',
      httpMethod: 'DELETE',
      path: `/api/admin/teacher-invite-codes/${code}`,
    })
    writeState(state)
    return { deleted: true }
  },

  batchRevokeTeacherInviteCodes(codes = []) {
    const succeeded = []
    const failed = []
    const uniqueCodes = [...new Set((Array.isArray(codes) ? codes : []).map((item) => String(item || '').trim()).filter(Boolean))]

    uniqueCodes.forEach((code) => {
      try {
        this.revokeTeacherInviteCode(code)
        succeeded.push(code)
      } catch (error) {
        failed.push({
          code,
          errorCode: 'MOCK_ERROR',
          message: error.message || '撤销失败',
        })
      }
    })

    return { succeeded, failed }
  },

  revokeTeacherInviteCodesByQuery(payload = {}) {
    const state = readState()
    const now = Date.now()
    const status = payload?.status === '' || payload?.status === undefined ? null : payload?.status
    const expired = payload?.expired === '' || payload?.expired === undefined ? null : payload?.expired
    const expireFrom = payload?.expireFrom ? new Date(payload.expireFrom) : null
    const expireTo = payload?.expireTo ? new Date(payload.expireTo) : null
    const limit = Math.max(1, Number(payload?.limit) || 200)

    let candidates = [...(state.invites || [])]

    if (status !== null) {
      candidates = candidates.filter((item) => Number(item.status) === Number(status))
    }

    if (expired !== null) {
      const expected = String(expired) === 'true'
      candidates = candidates.filter((item) => {
        const expireAt = new Date(item.expireAt).getTime()
        return expected ? expireAt < now : expireAt >= now
      })
    }

    if (expireFrom && !Number.isNaN(expireFrom.getTime())) {
      candidates = candidates.filter((item) => new Date(item.expireAt).getTime() >= expireFrom.getTime())
    }

    if (expireTo && !Number.isNaN(expireTo.getTime())) {
      candidates = candidates.filter((item) => new Date(item.expireAt).getTime() <= expireTo.getTime())
    }

    const codes = candidates
      .sort((left, right) => new Date(right.createdAt || right.expireAt).getTime() - new Date(left.createdAt || left.expireAt).getTime())
      .slice(0, limit)
      .map((item) => item.code)

    return this.batchRevokeTeacherInviteCodes(codes)
  },

  changeAdminOwnPassword(oldPassword, newPassword) {
    const state = readState()
    const currentUser = getCurrentUser(state, 'admin')
    if (currentUser.password !== oldPassword) {
      throw new Error('当前密码不正确')
    }
    currentUser.password = newPassword
    appendLog(state, {
      userId: currentUser.id,
      role: currentUser.role,
      action: '修改管理员密码',
      httpMethod: 'PUT',
      path: '/api/admin/me/password',
    })
    writeState(state)
    return { success: true }
  },

  fetchAdminOperationLogs(params = {}) {
    const state = readState()
    let list = [...state.logs]

    if (params.userId !== undefined) {
      list = list.filter((item) => Number(item.userId) === Number(params.userId))
    }
    if (params.role) {
      list = list.filter((item) => item.role === params.role)
    }
    if (params.pathContains) {
      list = list.filter((item) => String(item.path || '').includes(params.pathContains))
    }

    list.sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime())
    return buildPagedResult(list, params.page, params.size)
  },

  fetchAdminMonitorOverview() {
    const state = readState()
    const classCount = state.classes.length
    const taskCount = state.tasks.length
    const groupCount = state.groups.length
    const activeClassCount = state.classes.filter((item) => Number(item.status) === 1).length
    return {
      classCount,
      taskCount,
      groupCount,
      activeClassCount,
    }
  },

  fetchAdminMonitorClasses(params = {}) {
    const state = readState()
    let list = state.classes.map((item) => ({
      id: item.classId,
      classId: item.classId,
      name: item.name,
      className: item.name,
      teacherName: item.teacherName,
      status: Number(item.status) === 1 ? 'active' : 'archived',
      memberCount: state.memberships.filter((member) => String(member.classId) === String(item.classId)).length,
    }))

    if (params.keyword) {
      const keyword = String(params.keyword).toLowerCase()
      list = list.filter((item) =>
        [item.name, item.teacherName].some((field) => String(field || '').toLowerCase().includes(keyword)),
      )
    }
    if (params.status) {
      list = list.filter((item) => item.status === params.status)
    }

    return buildPagedResult(list, params.page, params.size)
  },

  fetchAdminMonitorTasks(params = {}) {
    const state = readState()
    let list = state.tasks.map((item) => ({
      id: item.taskId,
      taskId: item.taskId,
      name: item.name,
      taskName: item.name,
      className: getClassById(state, item.classId)?.name || '-',
      status: Number(item.taskStatus) === 0 ? 'open' : Number(item.taskStatus) === 1 ? 'in_progress' : 'closed',
      deadline: item.deadline,
    }))

    if (params.keyword) {
      const keyword = String(params.keyword).toLowerCase()
      list = list.filter((item) =>
        [item.name, item.className].some((field) => String(field || '').toLowerCase().includes(keyword)),
      )
    }
    if (params.status) {
      list = list.filter((item) => item.status === params.status)
    }

    list.sort((left, right) => new Date(left.deadline).getTime() - new Date(right.deadline).getTime())
    return buildPagedResult(list, params.page, params.size)
  },
}
