const STUDENT_CLASSES_KEY = 'teamtrace_student_classes'
const STUDENT_APPEALS_KEY = 'teamtrace_student_appeals'
const STUDENT_TASK_WORK_KEY = 'teamtrace_student_task_work'

export function loadStudentClasses() {
  try {
    const raw = localStorage.getItem(STUDENT_CLASSES_KEY)
    const parsed = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export function saveStudentClasses(classes) {
  localStorage.setItem(STUDENT_CLASSES_KEY, JSON.stringify(classes))
}

export function upsertStudentClass(item) {
  const current = loadStudentClasses()
  const normalized = {
    classId: item?.classId ?? item?.id ?? item?.classID ?? null,
    classCode: item?.classCode ?? item?.code ?? '',
    name: item?.name ?? item?.className ?? '',
    joinedAt: Date.now(),
  }

  const next = [
    normalized,
    ...current.filter((c) => String(c.classId) !== String(normalized.classId)),
  ].slice(0, 20)
  saveStudentClasses(next)
  return next
}

export function removeStudentClass(classId) {
  const current = loadStudentClasses()
  const next = current.filter((c) => String(c.classId) !== String(classId))
  saveStudentClasses(next)
  return next
}

export function loadStudentAppeals() {
  try {
    const raw = localStorage.getItem(STUDENT_APPEALS_KEY)
    const parsed = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export function saveStudentAppeals(items) {
  localStorage.setItem(STUDENT_APPEALS_KEY, JSON.stringify(items))
}

export function appendStudentAppeal(item) {
  const current = loadStudentAppeals()
  const normalized = {
    appealId: item?.appealId ?? `local-${Date.now()}`,
    classId: item?.classId ?? null,
    taskId: item?.taskId ?? null,
    type: item?.type ?? 'teacher_score',
    subtaskId: item?.subtaskId ?? null,
    reason: item?.reason ?? '',
    status: item?.status ?? '待处理',
    createdAt: item?.createdAt ?? new Date().toISOString(),
  }
  const next = [normalized, ...current].slice(0, 50)
  saveStudentAppeals(next)
  return next
}

export function formatStudentTaskStatus(value) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }

  const raw = String(value).trim()
  const parsed = Number(raw)
  if (!Number.isNaN(parsed)) {
    const numericMap = {
      0: '未开始',
      1: '进行中',
      2: '已截止',
    }
    if (numericMap[parsed]) {
      return numericMap[parsed]
    }
  }

  const normalized = raw.toLowerCase()
  const map = {
    open: '未开始',
    pending: '待开始',
    not_started: '未开始',
    in_progress: '进行中',
    active: '进行中',
    running: '进行中',
    closed: '已截止',
    overdue: '已截止',
    finished: '已完成',
    done: '已完成',
  }
  return map[normalized] || raw
}

export function formatStudentAppealStatus(value) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }

  const raw = String(value).trim()
  const normalized = raw.toLowerCase()
  const map = {
    0: '待处理',
    1: '处理中',
    2: '已驳回',
    3: '已通过',
    pending: '待处理',
    processing: '处理中',
    rejected: '已驳回',
    approved: '已通过',
    resolved: '已处理',
  }
  return map[normalized] || raw
}

export function buildStudentGroups(classId) {
  const local = loadStudentClasses().find((item) => String(item.classId) === String(classId))
  const label = local?.name || `班级 ${classId}`
  return [
    { groupId: `${classId}-g1`, name: `${label} · 第1组` },
    { groupId: `${classId}-g2`, name: `${label} · 第2组` },
  ]
}

export function buildStudentPeerReviewPreview(taskId, groupId) {
  return [
    {
      id: `${taskId}-${groupId}-r1`,
      reviewerAlias: '组员A',
      revieweeAlias: '组员B',
      score: 92,
      comment: '按时完成，提交内容完整。',
    },
    {
      id: `${taskId}-${groupId}-r2`,
      reviewerAlias: '组员C',
      revieweeAlias: '组员A',
      score: 88,
      comment: '推进稳定，但可以再补充细节说明。',
    },
  ]
}

export function buildStudentScoreSummaryPreview(taskId, groupId) {
  const peerAverage = 90
  const teacherScore = 93
  return {
    taskId,
    groupId,
    peerAverageOn100: peerAverage,
    teacherScore,
    weightedTotal100: ((peerAverage * 0.4) + (teacherScore * 0.6)).toFixed(1),
  }
}

export function loadStudentTaskWork() {
  try {
    const raw = localStorage.getItem(STUDENT_TASK_WORK_KEY)
    const parsed = raw ? JSON.parse(raw) : {}
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

export function saveStudentTaskWork(work) {
  localStorage.setItem(STUDENT_TASK_WORK_KEY, JSON.stringify(work))
}

export function getStudentTaskWork(taskId) {
  const current = loadStudentTaskWork()
  return current[String(taskId)] || null
}

export function upsertStudentTaskWork(taskId, payload) {
  const current = loadStudentTaskWork()
  const next = {
    ...current,
    [String(taskId)]: {
      ...(current[String(taskId)] || {}),
      ...payload,
      updatedAt: new Date().toISOString(),
    },
  }
  saveStudentTaskWork(next)
  return next[String(taskId)]
}

export function buildStudentTaskPreview(detail) {
  const baseName = detail?.name || '当前任务'
  return [
    {
      subtaskId: `${detail?.taskId || 'task'}-s1`,
      name: `${baseName} · 资料整理`,
      status: '待认领',
      assigneeName: '-',
      deadline: detail?.deadline || '-',
    },
    {
      subtaskId: `${detail?.taskId || 'task'}-s2`,
      name: `${baseName} · 页面实现`,
      status: '进行中',
      assigneeName: '我',
      deadline: detail?.deadline || '-',
    },
    {
      subtaskId: `${detail?.taskId || 'task'}-s3`,
      name: `${baseName} · 联调说明`,
      status: '已完成',
      assigneeName: '组员A',
      deadline: detail?.deadline || '-',
    },
  ]
}

