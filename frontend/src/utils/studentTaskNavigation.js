function normalizeSourceQuery(routeQuery = {}, source = '') {
  const query = {}
  const resolvedSource = source || routeQuery?.from

  if (resolvedSource) {
    query.from = String(resolvedSource)
  }
  if (routeQuery?.fromClassId) {
    query.fromClassId = String(routeQuery.fromClassId)
  }
  if (routeQuery?.fromTab) {
    query.fromTab = String(routeQuery.fromTab)
  }
  if (routeQuery?.focusTaskId) {
    query.focusTaskId = String(routeQuery.focusTaskId)
  }
  if (routeQuery?.classFilterId) {
    query.classFilterId = String(routeQuery.classFilterId)
  }
  if (routeQuery?.parentFrom) {
    query.parentFrom = String(routeQuery.parentFrom)
  }
  if (routeQuery?.parentFromClassId) {
    query.parentFromClassId = String(routeQuery.parentFromClassId)
  }
  if (routeQuery?.parentFromTab) {
    query.parentFromTab = String(routeQuery.parentFromTab)
  }
  if (routeQuery?.tab) {
    query.tab = String(routeQuery.tab)
  }

  return query
}

function buildStudentTaskCenterQuery(routeQuery = {}) {
  const query = {}
  const source = String(routeQuery?.parentFrom || routeQuery?.from || '').trim()
  const classId = String(routeQuery?.parentFromClassId || routeQuery?.fromClassId || '').trim()
  const tab = String(routeQuery?.parentFromTab || routeQuery?.fromTab || '').trim()

  if (source && source !== 'task-center') {
    query.from = source
  }
  if (classId) {
    query.fromClassId = classId
  }
  if (tab) {
    query.fromTab = tab
  }
  if (routeQuery?.classFilterId) {
    query.classFilterId = String(routeQuery.classFilterId)
  }
  if (routeQuery?.focusTaskId) {
    query.focusTaskId = String(routeQuery.focusTaskId)
  }

  return query
}

export function buildStudentTaskCenterLocation({ classId = '', taskId = '', source = '', tab = '', classFilterId = '' } = {}) {
  const query = {}

  if (source) {
    query.from = String(source)
  }
  if (classId !== '' && classId !== null && classId !== undefined) {
    query.fromClassId = String(classId)
  }
  if (tab) {
    query.fromTab = String(tab)
  }
  if (taskId !== '' && taskId !== null && taskId !== undefined) {
    query.focusTaskId = String(taskId)
  }
  if (classFilterId !== '' && classFilterId !== null && classFilterId !== undefined) {
    query.classFilterId = String(classFilterId)
  } else if (classId !== '' && classId !== null && classId !== undefined) {
    query.classFilterId = String(classId)
  }

  return {
    path: '/student/tasks',
    query,
  }
}

export function buildStudentTaskDetailLocation(classId, taskId, routeQuery = {}) {
  return {
    path: `/student/classes/${classId}/tasks/${taskId}`,
    query: normalizeSourceQuery(routeQuery),
  }
}

export function buildStudentTaskSubviewLocation(classId, taskId, suffix, routeQuery = {}) {
  return {
    path: `/student/classes/${classId}/tasks/${taskId}/${suffix}`,
    query: normalizeSourceQuery(routeQuery),
  }
}

export function resolveStudentTaskCenterBackLocation(routeQuery = {}) {
  const source = String(routeQuery?.from || '')
  const classId = routeQuery?.fromClassId ? String(routeQuery.fromClassId) : ''
  const tab = routeQuery?.fromTab ? String(routeQuery.fromTab) : 'tasks'

  if (!classId) {
    return null
  }

  if (source === 'class-groups') {
    return {
      path: `/student/classes/${classId}/groups`,
    }
  }

  if (source === 'class-tasks') {
    return {
      path: `/student/classes/${classId}`,
      query: { tab },
    }
  }

  return null
}

export function getStudentTaskCenterBackLabel(routeQuery = {}) {
  const source = String(routeQuery?.from || '')

  if (source === 'class-groups') {
    return '返回我的小组'
  }

  if (source === 'class-tasks') {
    return '返回班级任务'
  }

  return ''
}

export function resolveStudentTaskDetailBackLocation(classId, routeQuery = {}) {
  const source = String(routeQuery?.from || '')

  if (source === 'task-center') {
    return {
      path: '/student/tasks',
      query: buildStudentTaskCenterQuery(routeQuery),
    }
  }

  const sourceQuery = normalizeSourceQuery(routeQuery)

  if (sourceQuery.from) {
    return {
      path: '/student/tasks',
      query: sourceQuery,
    }
  }

  return {
    path: `/student/classes/${classId}`,
    query: { tab: 'tasks' },
  }
}

export function getStudentTaskDetailBackLabel(routeQuery = {}) {
  return routeQuery?.from ? '返回任务中心' : '返回班级任务'
}

export function buildStudentTaskCenterCrumbLocation(routeQuery = {}) {
  return {
    path: '/student/tasks',
    query: buildStudentTaskCenterQuery(routeQuery),
  }
}

export function resolveStudentTaskKind(task, fallback = 'group') {
  if (typeof task?.isGroupTask === 'boolean') {
    return task.isGroupTask ? 'group' : 'personal'
  }

  if (typeof task?.groupTask === 'boolean') {
    return task.groupTask ? 'group' : 'personal'
  }

  const rawType = task?.taskType ?? task?.type ?? task?.taskKind ?? task?.scopeType ?? task?.assignmentType ?? ''
  const normalized = String(rawType || '')
    .trim()
    .toLowerCase()

  if (['personal', 'individual', 'self', 'solo'].includes(normalized)) {
    return 'personal'
  }

  if (['group', 'team', 'cooperate', 'collaboration'].includes(normalized)) {
    return 'group'
  }

  return fallback === 'personal' ? 'personal' : 'group'
}

export function getStudentTaskKindLabel(task, fallback = 'group') {
  return resolveStudentTaskKind(task, fallback) === 'personal' ? '个人任务' : '小组任务'
}

/** 当前路由是否为学生任务详情；keep-alive 缓存页应用此判断再响应全局 route 变化。 */
export function isStudentTaskDetailRoute(currentRoute) {
  return String(currentRoute?.name || '') === 'student-task-detail'
}
