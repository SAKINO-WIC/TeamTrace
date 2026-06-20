import { fetchTeacherClassTasks, fetchTeacherClasses } from '../services/teacher'

/** 无任务时任务详情占位路由段，避免与真实 taskId 冲突 */
export const TEACHER_TASK_DETAIL_EMPTY_SLUG = 'empty'

export function isTeacherTaskDetailEmptyTaskId(taskId = '') {
  return String(taskId || '').trim() === TEACHER_TASK_DETAIL_EMPTY_SLUG
}

export function buildTeacherTaskSourceQuery(routeQuery = {}, source = '') {
  const query = {}

  if (routeQuery?.classId) {
    query.classId = String(routeQuery.classId)
  }
  if (routeQuery?.status) {
    query.status = String(routeQuery.status)
  }
  if (routeQuery?.peerReview) {
    query.peerReview = String(routeQuery.peerReview)
  }
  if (source) {
    query.from = source
  }

  return query
}

export function buildTeacherTaskDetailLocation(classId, taskId, routeQuery = {}, source = '') {
  return {
    path: `/teacher/classes/${classId}/tasks/${taskId}`,
    query: buildTeacherTaskSourceQuery(routeQuery, source),
  }
}

export function buildTeacherTaskSubviewLocation(classId, taskId, suffix, routeQuery = {}) {
  return {
    path: `/teacher/classes/${classId}/tasks/${taskId}/${suffix}`,
    query: buildTeacherTaskSourceQuery(routeQuery, routeQuery?.from ? String(routeQuery.from) : ''),
  }
}

/** 解析任务详情入口：优先班级内路径 /teacher/classes/:classId/tasks/:taskId */
export async function resolveTeacherTaskDetailEntry(classId = '', preferredTaskId = '') {
  let resolvedClassId = String(classId || '').trim()
  let resolvedTaskId = String(preferredTaskId || '').trim()

  if (!resolvedClassId) {
    try {
      const response = await fetchTeacherClasses()
      const payload = response?.data?.data || []
      const firstClass = Array.isArray(payload) ? payload[0] : null
      resolvedClassId = String(firstClass?.classId ?? firstClass?.id ?? '').trim()
    } catch {
      resolvedClassId = ''
    }
  }

  if (!resolvedClassId) {
    return { path: '/teacher/classes' }
  }

  if (!resolvedTaskId) {
    try {
      const response = await fetchTeacherClassTasks(resolvedClassId)
      const payload = response?.data?.data || []
      const firstTask = Array.isArray(payload) ? payload[0] : null
      resolvedTaskId = String(firstTask?.taskId ?? firstTask?.id ?? '').trim()
    } catch {
      resolvedTaskId = ''
    }
  }

  if (resolvedTaskId) {
    return { path: `/teacher/classes/${resolvedClassId}/tasks/${resolvedTaskId}` }
  }

  return { path: `/teacher/classes/${resolvedClassId}/tasks/${TEACHER_TASK_DETAIL_EMPTY_SLUG}` }
}

export function isTeacherClassTaskDetailPath(path, classId = '') {
  if (!classId) {
    return /^\/teacher\/classes\/[^/]+\/tasks\/[^/]+/.test(path)
  }
  const prefix = `/teacher/classes/${classId}/tasks/`
  return path.startsWith(prefix) && path !== `/teacher/classes/${classId}/tasks`
}

/** 当前路由是否为教师任务详情（含独立入口）；keep-alive 缓存页应用此判断再响应全局 route 变化。 */
export function isTeacherTaskDetailRoute(currentRoute) {
  if (!currentRoute) {
    return false
  }
  const name = String(currentRoute.name || '')
  if (name === 'teacher-task-detail' || name === 'teacher-task-detail-standalone') {
    return true
  }
  const classId = String(currentRoute.params?.classId || '')
  const taskId = String(currentRoute.params?.taskId || '')
  if (classId && isTeacherTaskDetailEmptyTaskId(taskId)) {
    return true
  }
  return Boolean(classId && isTeacherClassTaskDetailPath(String(currentRoute.path || ''), classId))
}
