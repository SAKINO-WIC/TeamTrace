import http from './http'
import { fetchTeacherClassStudents, fetchTeacherClassTasks, fetchTeacherClasses, fetchTeacherTaskAppeals } from './teacher'
import { fetchWithCache, invalidateCache, mapWithConcurrency } from '../utils/fetchCache'
import { formatAppealStatus } from '../utils/teacher'

const APPEALS_WORKSPACE_CACHE_KEY = 'teacher:appeals-workspace:v2'

export function invalidateTeacherAppealsWorkspaceCache() {
  invalidateCache(APPEALS_WORKSPACE_CACHE_KEY)
}

function normalizeWorkspaceRow(raw) {
  return {
    appealId: String(raw?.appealId ?? raw?.id ?? ''),
    classId: String(raw?.classId ?? ''),
    className: raw?.className ?? '',
    taskId: String(raw?.taskId ?? ''),
    taskName: raw?.taskName ?? '',
    studentId: String(raw?.studentId ?? '-'),
    studentName: raw?.studentName ?? raw?.name ?? '',
    subtaskId: raw?.subtaskId ? String(raw.subtaskId) : '',
    type: raw?.type ?? '',
    attachments: raw?.attachments ?? '',
    status: raw?.status,
    reason: raw?.reason ?? '',
    createdAt: raw?.createdAt ?? '',
    handledAt: raw?.handledAt ?? '',
    teacherResponse: raw?.teacherResponse ?? '',
    adjustedTeacherScore: raw?.adjustedTeacherScore ?? null,
  }
}

function normalizePagedList(payload) {
  if (Array.isArray(payload)) return payload
  if (!payload || typeof payload !== 'object') return []
  if (Array.isArray(payload.list)) return payload.list
  if (Array.isArray(payload.items)) return payload.items
  if (Array.isArray(payload.records)) return payload.records
  if (Array.isArray(payload.content)) return payload.content
  return []
}

function normalizeClassStudent(raw) {
  const studentId = String(raw?.studentId ?? raw?.id ?? '')
  return {
    studentId,
    name: raw?.name ?? raw?.studentName ?? '',
  }
}

async function enrichAppealsWithStudentNames(rows) {
  if (!Array.isArray(rows) || rows.length === 0) {
    return []
  }

  const classIds = Array.from(new Set(rows.map((row) => String(row?.classId ?? '')).filter(Boolean)))
  const settled = await mapWithConcurrency(
    classIds,
    async (classId) => {
      try {
        const response = await fetchTeacherClassStudents(classId, { page: 1, size: 500 })
        const students = normalizePagedList(response?.data?.data).map(normalizeClassStudent)
        return students.map((student) => ({
          key: `${classId}:${student.studentId}`,
          name: student.name,
        }))
      } catch {
        return []
      }
    },
    4,
  )

  const nameMap = new Map(settled.flat().filter((item) => item.key && item.name).map((item) => [item.key, item.name]))
  return rows.map((row) => {
    const classId = String(row?.classId ?? '')
    const studentId = String(row?.studentId ?? '')
    const studentName = row?.studentName || nameMap.get(`${classId}:${studentId}`) || ''
    return {
      ...row,
      studentName,
    }
  })
}

export function isPendingAppealStatus(status) {
  const label = formatAppealStatus(status)
  return label === '待处理' || label === '处理中'
}

export function countPendingAppeals(appeals) {
  if (!Array.isArray(appeals)) {
    return 0
  }
  return appeals.filter((item) => isPendingAppealStatus(item?.status)).length
}

/** 按 taskId 统计待处理申诉数（用于课程报告等）。 */
export function buildPendingAppealsByTaskId(appeals) {
  const map = new Map()
  if (!Array.isArray(appeals)) {
    return map
  }
  appeals.forEach((item) => {
    if (!isPendingAppealStatus(item?.status)) {
      return
    }
    const taskId = String(item?.taskId ?? '')
    if (!taskId) {
      return
    }
    map.set(taskId, (map.get(taskId) || 0) + 1)
  })
  return map
}

async function fetchAppealsWorkspaceFromApi() {
  const { data } = await http.get('/teacher/analytics/appeals-workspace', { timeout: 60000 })
  const rows = data?.data
  return Array.isArray(rows) ? rows.map(normalizeWorkspaceRow) : null
}

async function fetchAppealsWorkspaceClientSide() {
  const { data } = await fetchTeacherClasses()
  const classes = Array.isArray(data?.data) ? data.data : []

  const aggregated = await mapWithConcurrency(
    classes,
    async (classItem) => {
      try {
        const classId = String(classItem?.classId ?? classItem?.id ?? '')
        const className = classItem?.name ?? classItem?.className ?? '未命名班级'
        const { data: taskResponse } = await fetchTeacherClassTasks(classId)
        const tasks = Array.isArray(taskResponse?.data) ? taskResponse.data : []

        const taskAppeals = await mapWithConcurrency(
          tasks,
          async (task) => {
            try {
              const taskId = String(task?.taskId ?? task?.id ?? '')
              const taskName = task?.name ?? task?.taskName ?? '未命名任务'
              const { data: appealsResponse } = await fetchTeacherTaskAppeals(classId, taskId)
              const rows = Array.isArray(appealsResponse?.data) ? appealsResponse.data : []

              return rows.map((appeal) =>
                normalizeWorkspaceRow({
                  appealId: appeal?.appealId ?? appeal?.id,
                  classId,
                  className,
                  taskId,
                  taskName,
                  studentId: appeal?.studentId,
                  studentName: appeal?.studentName,
                  subtaskId: appeal?.subtaskId,
                  type: appeal?.type,
                  attachments: appeal?.attachments,
                  status: appeal?.status,
                  reason: appeal?.reason,
                  createdAt: appeal?.createdAt,
                  handledAt: appeal?.handledAt,
                  teacherResponse: appeal?.teacherResponse,
                  adjustedTeacherScore: appeal?.adjustedTeacherScore,
                }),
              )
            } catch {
              return []
            }
          },
          4,
        )

        return taskAppeals.flat()
      } catch {
        return []
      }
    },
    3,
  )

  return aggregated.flat()
}

/**
 * 加载教师申诉中心工作台数据：优先后端聚合接口，失败时有限并发回退。
 */
export async function loadTeacherAppealsWorkspace(options = {}) {
  const { force = false } = options
  if (force) {
    invalidateTeacherAppealsWorkspaceCache()
  }

  try {
    const rows = await fetchWithCache(
      APPEALS_WORKSPACE_CACHE_KEY,
      fetchAppealsWorkspaceFromApi,
      45000,
    )
    if (Array.isArray(rows)) {
      return enrichAppealsWithStudentNames(rows)
    }
  } catch {
    /* fall through to client-side aggregation */
  }

  const fallbackRows = await fetchAppealsWorkspaceClientSide()
  return enrichAppealsWithStudentNames(fallbackRows)
}
