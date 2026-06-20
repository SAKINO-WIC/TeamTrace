<script setup>
import { computed, inject, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { loadHtml2Pdf } from '../utils/lazyPdf'
import { readSessionCache, writeSessionCache } from '../utils/sessionCache'
import { TEACHER_CLASSES_INJECT_KEY } from '../utils/teacherWorkspace'
import { useRoute, useRouter } from 'vue-router'
import TeacherSubviewShell from '../components/teacher/TeacherSubviewShell.vue'
import { useTeacherLocale } from '../composables/useTeacherLocale'
import {
  fetchCourseGroupScores,
  fetchCourseScores,
  fetchTeacherClasses,
  fetchTeacherClassGroups,
  fetchTeacherClassStudents,
  fetchTeacherClassTasks,
  fetchTeacherGroupScoreSummaries,
  fetchTeacherTaskScoreSummaries,
  fetchTeacherGroupSubtasks,
  fetchTeacherTaskGroupReport,
  fetchTeacherScores,
  saveCourseGroupScore,
  saveCourseScore,
  saveTeacherScore,
} from '../services/teacher'
import { buildPendingAppealsByTaskId, loadTeacherAppealsWorkspace } from '../services/teacherAppeals'
import { formatAppealStatus, formatDateTime, formatTaskStatus, normalizePagedPayload } from '../utils/teacher'
import { mapWithConcurrency } from '../utils/fetchCache'
import { parseSubmissionContent } from '../utils/submissionAttachments'

const STORAGE_KEY = 'teamtrace_teacher_scores_workspace_v2'

const route = useRoute()
const router = useRouter()
const { t, tm } = useTeacherLocale()
const teacherClassesCtx = inject(TEACHER_CLASSES_INJECT_KEY, null)

const loading = ref(false)
const loadingClasses = ref(false)
const workspaceReady = ref(false)
const scoreReloadToken = ref(0)
const pdfExportBusy = ref(false)
const courseReportRows = ref([])
const message = ref('')
const messageType = ref('info')
const classes = ref([])
const tasks = ref([])
const groups = ref([])
const classStudents = ref([])
const taskScoreBundles = ref([])
const courseScoreBundles = ref([])
const SCORE_DECIMAL_PLACES = 1
const MAX_SCORE_INPUT_LENGTH = 5
const MAX_SCORE_VALUE = 100

const filters = reactive({
  classId: String(route.query.classId || ''),
  scoreScope: route.query.scoreScope === 'course' ? 'course' : 'task',
  targetType: route.query.targetType === 'student' ? 'student' : 'group',
  taskId: String(route.query.taskId || ''),
  groupId: String(route.query.groupId || ''),
  termMode: route.query.termMode === 'manual' ? 'manual' : 'auto',
})

const reportType = ref(filters.scoreScope === 'course' ? 'course' : 'task')
const reportFormat = ref('pdf')
const reportGeneratedAt = ref('')
const REPORT_FORMAT_OPTIONS = [
  { value: 'pdf', label: 'PDF' },
  { value: 'csv', label: 'CSV' },
]

const draftState = reactive({
  taskStudentScores: {},
  taskStudentNotes: {},
  taskGroupScores: {},
  taskGroupNotes: {},
  courseStudentScores: {},
  courseStudentNotes: {},
  courseRegularScores: {},
  courseGroupScores: {},
  courseGroupNotes: {},
})

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

function formatApiError(error, fallback) {
  const raw = error?.message || fallback || tm('scores.requestFailed')
  if (raw === '无权限') {
    return tm('scores.permissionDenied')
  }
  if (raw === '无权限操作该班级') {
    return tm('scores.permissionClassDenied')
  }
  return raw
}

function toNumber(value, fallback = 0) {
  const parsed = Number(value)
  return Number.isNaN(parsed) ? fallback : parsed
}

function average(values, fallback = null) {
  const numeric = values
    .filter((item) => item !== null && item !== undefined && item !== '')
    .map((item) => Number(item))
    .filter((item) => !Number.isNaN(item))
  if (!numeric.length) {
    return fallback
  }
  return numeric.reduce((sum, item) => sum + item, 0) / numeric.length
}

function resolveModalInitialScore(row) {
  const candidate = filters.targetType === 'group' ? row?.teacherAverage : row?.teacherScore
  if (candidate === null || candidate === undefined || candidate === '') {
    return ''
  }
  return String(candidate)
}

function formatNumber(value, digits = 1) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  const parsed = Number(value)
  if (Number.isNaN(parsed)) {
    return '-'
  }
  return parsed.toFixed(digits).replace(/\.0+$/, '').replace(/(\.\d*[1-9])0+$/, '$1')
}

function formatScoreDifference(value) {
  const formatted = formatNumber(value)
  if (formatted === '-') {
    return formatted
  }
  return Number(value) > 0 ? `+${formatted}` : formatted
}

function normalizeScoreInput(value) {
  const raw = String(value ?? '').trim()
  if (!raw) {
    return ''
  }

  const compact = raw.replace(/[^\d.]/g, '')
  if (!compact) {
    return ''
  }

  const [integerRaw = '', ...decimalParts] = compact.split('.')
  const integerPart = integerRaw.replace(/^0+(?=\d)/, '')
  const hasDecimal = compact.includes('.')
  const decimalPart = decimalParts.join('').slice(0, SCORE_DECIMAL_PLACES)
  const normalized = hasDecimal ? `${integerPart || '0'}.${decimalPart}` : integerPart

  if (!normalized || normalized === '.') {
    return ''
  }

  const parsed = Number(normalized)
  if (Number.isNaN(parsed)) {
    return ''
  }

  if (parsed > MAX_SCORE_VALUE) {
    return String(MAX_SCORE_VALUE)
  }

  return normalized.slice(0, MAX_SCORE_INPUT_LENGTH)
}

function readScoreInput(event) {
  const normalizedValue = normalizeScoreInput(event?.target?.value)
  if (event?.target && event.target.value !== normalizedValue) {
    event.target.value = normalizedValue
  }
  return normalizedValue
}

function compareText(left, right) {
  return String(left || '').localeCompare(String(right || ''))
}

function normalizeClass(raw) {
  return {
    classId: String(raw?.classId ?? raw?.id ?? ''),
    name: raw?.name ?? raw?.className ?? t('未命名班级', 'Unnamed class'),
  }
}

function normalizeTask(raw) {
  return {
    taskId: String(raw?.taskId ?? raw?.id ?? ''),
    name: raw?.name ?? raw?.taskName ?? '未命名任务',
    status: formatTaskStatus(raw?.taskStatus ?? raw?.status),
    deadlineText: formatDateTime(raw?.deadline),
    enablePeerReview: Boolean(raw?.enablePeerReview),
  }
}

function normalizeGroup(raw) {
  const memberIds = Array.isArray(raw?.memberStudentIds) ? raw.memberStudentIds : []
  const memberNames =
    raw?.memberNames && typeof raw.memberNames === 'object'
      ? Object.fromEntries(Object.entries(raw.memberNames).map(([id, name]) => [String(id), name]))
      : {}
  return {
    groupId: String(raw?.groupId ?? raw?.id ?? ''),
    name: raw?.name ?? raw?.groupName ?? t('未命名小组', 'Unnamed group'),
    memberCount: raw?.memberCount ?? memberIds.length,
    memberNames,
  }
}

function normalizeClassStudent(raw) {
  const studentId = String(raw?.studentId ?? raw?.id ?? '')
  return {
    studentId,
    name: raw?.name ?? raw?.studentName ?? '',
  }
}

function resolveStudentDisplayName(studentId, fallback = '') {
  const normalizedId = String(studentId ?? '')
  const student = classStudents.value.find((item) => item.studentId === normalizedId)
  if (student?.name) {
    return student.name
  }
  if (fallback && fallback !== normalizedId && !String(fallback).startsWith('学生 ')) {
    return fallback
  }
  return normalizedId ? `${t('学生', 'Student')} ${normalizedId}` : fallback || '-'
}

function normalizeSummaryRow(raw, bundle) {
  const studentId = String(raw?.studentId ?? '-')
  return {
    classId: bundle.classId,
    taskId: bundle.taskId,
    taskName: bundle.taskName || t('未命名任务', 'Unnamed task'),
    taskStatus: bundle.taskStatus || '-',
    taskDeadlineText: bundle.taskDeadlineText || '-',
    groupId: bundle.groupId,
    groupName: bundle.groupName || t('未命名小组', 'Unnamed group'),
    studentId,
    studentName: resolveStudentDisplayName(studentId, raw?.studentName),
    peerReviewApplicable: Boolean(raw?.peerReviewApplicable),
    peerAverageOn100: raw?.peerAverageOn100 ?? null,
    teacherScore: raw?.teacherScore ?? null,
  }
}

function buildTaskStudentDraftKey(taskId, studentId) {
  return `${filters.classId}_${taskId}_${studentId}`
}

function buildTaskGroupDraftKey(taskId, groupId) {
  return `${filters.classId}_${taskId}_${groupId}`
}

function buildTaskWeightKey(taskId) {
  return `${filters.classId}_${taskId}`
}

function buildCourseStudentDraftKey(studentId) {
  return `${filters.classId}_${studentId}`
}

function buildCourseGroupDraftKey(groupId) {
  return `${filters.classId}_${groupId}`
}

function loadWorkspace() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : {}
    draftState.taskStudentScores = parsed?.taskStudentScores && typeof parsed.taskStudentScores === 'object' ? parsed.taskStudentScores : {}
    draftState.taskStudentNotes = parsed?.taskStudentNotes && typeof parsed.taskStudentNotes === 'object' ? parsed.taskStudentNotes : {}
    draftState.taskGroupScores = parsed?.taskGroupScores && typeof parsed.taskGroupScores === 'object' ? parsed.taskGroupScores : {}
    draftState.taskGroupNotes = parsed?.taskGroupNotes && typeof parsed.taskGroupNotes === 'object' ? parsed.taskGroupNotes : {}
    draftState.courseStudentScores = parsed?.courseStudentScores && typeof parsed.courseStudentScores === 'object' ? parsed.courseStudentScores : {}
    draftState.courseStudentNotes = parsed?.courseStudentNotes && typeof parsed.courseStudentNotes === 'object' ? parsed.courseStudentNotes : {}
    draftState.courseRegularScores = parsed?.courseRegularScores && typeof parsed.courseRegularScores === 'object' ? parsed.courseRegularScores : {}
    draftState.courseGroupScores = parsed?.courseGroupScores && typeof parsed.courseGroupScores === 'object' ? parsed.courseGroupScores : {}
    draftState.courseGroupNotes = parsed?.courseGroupNotes && typeof parsed.courseGroupNotes === 'object' ? parsed.courseGroupNotes : {}
  } catch {
    draftState.taskStudentScores = {}
    draftState.taskStudentNotes = {}
    draftState.taskGroupScores = {}
    draftState.taskGroupNotes = {}
    draftState.courseStudentScores = {}
    draftState.courseStudentNotes = {}
    draftState.courseRegularScores = {}
    draftState.courseGroupScores = {}
    draftState.courseGroupNotes = {}
  }
}

function saveWorkspace() {
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      taskStudentScores: draftState.taskStudentScores,
      taskStudentNotes: draftState.taskStudentNotes,
      taskGroupScores: draftState.taskGroupScores,
      taskGroupNotes: draftState.taskGroupNotes,
      courseStudentScores: draftState.courseStudentScores,
      courseStudentNotes: draftState.courseStudentNotes,
      courseRegularScores: draftState.courseRegularScores,
      courseGroupScores: draftState.courseGroupScores,
      courseGroupNotes: draftState.courseGroupNotes,
    }),
  )
}

async function loadClasses() {
  const shellList = teacherClassesCtx?.list?.value
  if (shellList?.length && !classes.value.length) {
    classes.value = shellList.map((item) => normalizeClass({
      classId: item.classId,
      name: item.name,
      status: item.statusLabel === '已归档' ? 0 : 1,
    }))
  }

  loadingClasses.value = !classes.value.length
  try {
    const { data } = await fetchTeacherClasses()
    classes.value = Array.isArray(data?.data) ? data.data.map(normalizeClass) : []
    const hasCurrent = classes.value.some((item) => item.classId === filters.classId)
    if (filters.classId && !hasCurrent) {
      filters.classId = classes.value[0]?.classId || ''
      filters.taskId = ''
      filters.groupId = ''
      if (route.query.classId) {
        setMessage(
          tm('scores.classSwitched'),
          'warn',
        )
      }
    } else if (!filters.classId && classes.value.length) {
      filters.classId = classes.value[0].classId
    }
  } catch (error) {
    classes.value = []
    setMessage(formatApiError(error, tm('scores.loadClassFailed')), 'error')
  } finally {
    loadingClasses.value = false
  }
}

async function loadClassContext() {
  if (!filters.classId) {
    tasks.value = []
    groups.value = []
    classStudents.value = []
    taskScoreBundles.value = []
    courseScoreBundles.value = []
    return
  }

  loading.value = true
  try {
    const [tasksResult, groupsResult, studentsResult] = await Promise.allSettled([
      fetchTeacherClassTasks(filters.classId),
      fetchTeacherClassGroups(filters.classId),
      fetchTeacherClassStudents(filters.classId, { page: 1, size: 500 }),
    ])

    if (tasksResult.status === 'rejected') {
      throw tasksResult.reason
    }
    if (groupsResult.status === 'rejected') {
      throw groupsResult.reason
    }

    tasks.value = Array.isArray(tasksResult.value?.data?.data) ? tasksResult.value.data.data.map(normalizeTask) : []
    groups.value = Array.isArray(groupsResult.value?.data?.data) ? groupsResult.value.data.data.map(normalizeGroup) : []

    if (studentsResult.status === 'fulfilled') {
      const payload = normalizePagedPayload(studentsResult.value?.data?.data || {})
      classStudents.value = payload.list.map(normalizeClassStudent)
    } else {
      classStudents.value = []
    }

    if (filters.taskId && !tasks.value.some((item) => item.taskId === filters.taskId)) {
      filters.taskId = ''
    }
    if (filters.groupId && !groups.value.some((item) => item.groupId === filters.groupId)) {
      filters.groupId = ''
    }
  } catch (error) {
    tasks.value = []
    groups.value = []
    classStudents.value = []
    filters.taskId = ''
    filters.groupId = ''
    taskScoreBundles.value = []
    courseScoreBundles.value = []
    setMessage(formatApiError(error, tm('scores.loadTaskFailed')), 'error')
  } finally {
    loading.value = false
  }
}

async function fetchScoreBundle(classId, task, group) {
  const bundle = {
    classId,
    taskId: task.taskId,
    taskName: task.name || t('未命名任务', 'Unnamed task'),
    taskStatus: task.status || '-',
    taskDeadlineText: task.deadlineText || '-',
    groupId: group.groupId,
    groupName: group.name,
    memberCount: group.memberCount,
  }

  try {
    const { data } = await fetchTeacherGroupScoreSummaries(classId, task.taskId, group.groupId)
    const rows = Array.isArray(data?.data) ? data.data : []
    return {
      ...bundle,
      rows: rows.map((item) => normalizeSummaryRow(item, bundle)),
    }
  } catch {
    return {
      ...bundle,
      rows: [],
    }
  }
}

function resolveScoreTargetGroups() {
  if (filters.groupId) {
    return groups.value.filter((item) => item.groupId === filters.groupId)
  }
  return groups.value
}

function taskWorkspaceCacheKey() {
  return `teacher:scores:task:${filters.classId}:${filters.taskId || 'all'}:${filters.groupId || 'all'}`
}

function resolveScoreTargetTasks() {
  if (filters.taskId) {
    return tasks.value.filter((item) => item.taskId === filters.taskId)
  }
  return tasks.value
}

async function loadTaskWorkspace(options = {}) {
  const { silent = false, force = false } = options
  const targetGroups = resolveScoreTargetGroups()
  const targetTasks = resolveScoreTargetTasks()
  if (!filters.classId || !targetTasks.length || !targetGroups.length) {
    taskScoreBundles.value = []
    return
  }

  const cached = force ? null : readSessionCache(taskWorkspaceCacheKey(), 120000)
  if (cached && Array.isArray(cached)) {
    taskScoreBundles.value = cached
    if (silent) {
      return
    }
    loading.value = false
  } else if (!silent) {
    loading.value = true
  }

  try {
    const taskBundles = await mapWithConcurrency(
      targetTasks,
      async (currentTask) => {
        const { data } = await fetchTeacherTaskScoreSummaries(filters.classId, currentTask.taskId)
        const groupSummariesMap = data?.data || {}
        const groupScoreMap = await loadGroupTeacherScoreMap(filters.classId, currentTask.taskId)

        return targetGroups.map((group) => {
          const rows = Array.isArray(groupSummariesMap[group.groupId])
            ? groupSummariesMap[group.groupId]
            : []
          return {
            classId: filters.classId,
            taskId: currentTask.taskId,
            taskName: currentTask.name,
            taskStatus: currentTask.status,
            taskDeadlineText: currentTask.deadlineText,
            groupId: group.groupId,
            groupName: group.name,
            memberCount: group.memberCount,
            rows: rows.map((item) =>
              normalizeSummaryRow(item, {
                classId: filters.classId,
                taskId: currentTask.taskId,
                taskName: currentTask.name,
                taskStatus: currentTask.status,
                taskDeadlineText: currentTask.deadlineText,
                groupId: group.groupId,
                groupName: group.name,
              }),
            ),
            groupTeacherScore: groupScoreMap.get(String(group.groupId)) ?? null,
          }
        })
      },
      3,
    )

    taskScoreBundles.value = taskBundles.flat()
    writeSessionCache(taskWorkspaceCacheKey(), taskScoreBundles.value)
    if (!silent) {
      setMessage('')
    }
  } catch (error) {
    if (!cached?.length) {
      taskScoreBundles.value = []
      setMessage(formatApiError(error, tm('scores.loadTaskWorkspaceFailed')), 'error')
    }
  } finally {
    loading.value = false
  }
}


async function loadGroupTeacherScoreMap(classId, taskId) {
  try {
    const { data } = await fetchTeacherScores(classId, taskId)
    const list = Array.isArray(data?.data) ? data.data : []
    const map = new Map()
    list.forEach((item) => {
      if (item?.groupId !== null && item?.groupId !== undefined && item?.groupId !== '') {
        map.set(String(item.groupId), Number(item.score))
      }
    })
    return map
  } catch {
    return new Map()
  }
}

async function loadCourseWorkspace(options = {}) {
  const { silent = false } = options
  const targetGroups = resolveScoreTargetGroups()
  if (!filters.classId || !tasks.value.length || !targetGroups.length) {
    courseScoreBundles.value = []
    return
  }

  loading.value = true
  try {
    const taskBundles = await mapWithConcurrency(
      tasks.value,
      async (task) => {
        try {
          const { data } = await fetchTeacherTaskScoreSummaries(filters.classId, task.taskId)
          const groupSummariesMap = data?.data || {}
          const groupScoreMap = await loadGroupTeacherScoreMap(filters.classId, task.taskId)
          return targetGroups.map((group) => {
            const rows = Array.isArray(groupSummariesMap[group.groupId])
              ? groupSummariesMap[group.groupId]
              : []
            return {
              classId: filters.classId,
              taskId: task.taskId,
              taskName: task.name || t('未命名任务', 'Unnamed task'),
              taskStatus: task.status || '-',
              taskDeadlineText: task.deadlineText || '-',
              groupId: group.groupId,
              groupName: group.name,
              memberCount: group.memberCount,
              groupTeacherScore: groupScoreMap.get(String(group.groupId)) ?? null,
              rows: rows.map((item) =>
                normalizeSummaryRow(item, {
                  classId: filters.classId,
                  taskId: task.taskId,
                  taskName: task.name || t('未命名任务', 'Unnamed task'),
                  taskStatus: task.status || '-',
                  taskDeadlineText: task.deadlineText || '-',
                  groupId: group.groupId,
                  groupName: group.name,
                }),
              ),
            }
          })
        } catch {
          return targetGroups.map((group) => ({
            classId: filters.classId,
            taskId: task.taskId,
            taskName: task.name || t('未命名任务', 'Unnamed task'),
            taskStatus: task.status || '-',
            taskDeadlineText: task.deadlineText || '-',
            groupId: group.groupId,
            groupName: group.name,
            memberCount: group.memberCount,
            groupTeacherScore: null,
            rows: [],
          }))
        }
      },
      4,
    )

    courseScoreBundles.value = taskBundles.flat()
    await loadCourseScoresFromBackend()
    if (!silent) {
      setMessage('')
    }
  } catch (error) {
    courseScoreBundles.value = []
    setMessage(error.message || '加载课程评分记录工作区失败', 'error')
  } finally {
    loading.value = false
  }
}

async function reloadScoreWorkspace(options = {}) {
  if (!filters.classId) {
    taskScoreBundles.value = []
    courseScoreBundles.value = []
    return
  }

  const token = scoreReloadToken.value + 1
  scoreReloadToken.value = token

  if (filters.scoreScope === 'task') {
    await loadTaskWorkspace(options)
  } else {
    await loadCourseWorkspace(options)
  }

  if (token !== scoreReloadToken.value) {
    return
  }
}

async function refreshScoreCenter() {
  if (!filters.classId) {
    await loadClasses()
    return
  }
  await loadClassContext()
  await reloadScoreWorkspace({ force: true })
}

const fromClassTaskPage = computed(() => route.query.from === 'class-task' && filters.classId)
const classTaskReturnPath = computed(() => {
  if (!fromClassTaskPage.value) {
    return ''
  }
  return `/teacher/classes/${filters.classId}/tasks`
})

const selectedClass = computed(() => classes.value.find((item) => item.classId === filters.classId) || null)
const selectedTask = computed(() => tasks.value.find((item) => item.taskId === filters.taskId) || null)

const visibleTaskBundles = computed(() => {
  return filters.groupId
    ? taskScoreBundles.value.filter((item) => item.groupId === filters.groupId)
    : taskScoreBundles.value
})

const visibleCourseBundles = computed(() => {
  return filters.groupId
    ? courseScoreBundles.value.filter((item) => item.groupId === filters.groupId)
    : courseScoreBundles.value
})

function calculateScoreDifference(peerScore, teacherScore) {
  if (
    peerScore === null ||
    peerScore === undefined ||
    peerScore === '' ||
    teacherScore === null ||
    teacherScore === undefined ||
    teacherScore === ''
  ) {
    return null
  }

  const parsedPeer = Number(peerScore)
  const parsedTeacher = Number(teacherScore)
  if (Number.isNaN(parsedPeer) || Number.isNaN(parsedTeacher)) {
    return null
  }

  return Number((parsedTeacher - parsedPeer).toFixed(1))
}

const taskStudentRows = computed(() => {
  return visibleTaskBundles.value
    .flatMap((bundle) =>
      bundle.rows.map((row) => {
        const scoreKey = buildTaskStudentDraftKey(bundle.taskId, row.studentId)
        const draftTeacherScore = draftState.taskStudentScores[scoreKey]
        const adjustedTeacherScore =
          draftTeacherScore === undefined || draftTeacherScore === '' ? null : Number(draftTeacherScore)
        const effectiveTeacherScore = adjustedTeacherScore === null ? row.teacherScore : adjustedTeacherScore

        return {
          ...row,
          adjustedTeacherScore,
          scoreDifference: calculateScoreDifference(row.peerAverageOn100, effectiveTeacherScore),
          note: draftState.taskStudentNotes[scoreKey] || '',
          changed: adjustedTeacherScore !== null && Number(adjustedTeacherScore) !== Number(row.teacherScore),
        }
      }),
    )
    .sort((left, right) => compareText(left.groupName, right.groupName) || compareText(left.studentName, right.studentName))
})

const taskGroupRows = computed(() => {
  return visibleTaskBundles.value.map((bundle) => {
    const peerAverage = average(bundle.rows.map((item) => item.peerAverageOn100), null)
    const memberTeacherAverage = average(bundle.rows.map((item) => item.teacherScore), null)
    const teacherAverage =
      bundle.groupTeacherScore === null || bundle.groupTeacherScore === undefined
        ? memberTeacherAverage
        : Number(bundle.groupTeacherScore)
    const scoreKey = buildTaskGroupDraftKey(bundle.taskId, bundle.groupId)
    const draftTeacherScore = draftState.taskGroupScores[scoreKey]
    const adjustedTeacherScore =
      draftTeacherScore === undefined || draftTeacherScore === '' ? null : Number(draftTeacherScore)
    const effectiveTeacherScore = adjustedTeacherScore === null ? teacherAverage : adjustedTeacherScore

    return {
      taskId: bundle.taskId,
      taskName: bundle.taskName,
      taskStatus: bundle.taskStatus,
      groupId: bundle.groupId,
      groupName: bundle.groupName,
      memberCount: bundle.memberCount,
      peerAverage,
      teacherAverage,
      adjustedTeacherScore,
      scoreDifference: calculateScoreDifference(peerAverage, effectiveTeacherScore),
      note: draftState.taskGroupNotes[scoreKey] || '',
      changed: adjustedTeacherScore !== null && Number(adjustedTeacherScore) !== Number(teacherAverage),
    }
  })
})

const courseStudentRows = computed(() => {
  const map = new Map()

  visibleCourseBundles.value.forEach((bundle) => {
    bundle.rows.forEach((row) => {
      const key = row.studentId
      const current = map.get(key) || {
        studentId: row.studentId,
        studentName: row.studentName,
        taskCount: 0,
        groupNames: new Set(),
        taskRows: [],
      }

      current.taskCount += 1
      current.groupNames.add(row.groupName)
      current.taskRows.push(row)
      map.set(key, current)
    })
  })

  return Array.from(map.values())
    .map((item) => {
      const teacherAverage = average(item.taskRows.map((row) => row.teacherScore), null)
      const peerReference = average(item.taskRows.map((row) => row.peerAverageOn100), null)
      const scoreKey = buildCourseStudentDraftKey(item.studentId)
      const manualScore =
        draftState.courseStudentScores[scoreKey] === undefined || draftState.courseStudentScores[scoreKey] === ''
          ? null
          : Number(draftState.courseStudentScores[scoreKey])
      const note = draftState.courseStudentNotes[scoreKey] || ''

      return {
        studentId: item.studentId,
        studentName: item.studentName,
        taskCount: item.taskCount,
        groupLabel: Array.from(item.groupNames).join(' / ') || '-',
        teacherAverage,
        peerReference,
        manualScore,
        note,
        changed: manualScore !== null,
      }
    })
    .sort((left, right) => compareText(left.groupLabel, right.groupLabel) || compareText(left.studentName, right.studentName))
})

const courseGroupRows = computed(() => {
  const map = new Map()

  visibleCourseBundles.value.forEach((bundle) => {
    const key = bundle.groupId
    const memberTeacherAverage = average(bundle.rows.map((row) => row.teacherScore), null)
    const teacherScore =
      bundle.groupTeacherScore === null || bundle.groupTeacherScore === undefined
        ? memberTeacherAverage
        : Number(bundle.groupTeacherScore)
    const current = map.get(key) || {
      groupId: bundle.groupId,
      groupName: bundle.groupName,
      memberCount: bundle.memberCount,
      taskCount: 0,
      taskRows: [],
    }

    current.taskCount += 1
    current.taskRows.push({
      taskId: bundle.taskId,
      taskName: bundle.taskName,
      taskStatus: bundle.taskStatus,
      teacherAverage: teacherScore,
    })
    map.set(key, current)
  })

  return Array.from(map.values())
    .map((item) => {
      const teacherAverage = average(item.taskRows.map((row) => row.teacherAverage), null)
      const scoreKey = buildCourseGroupDraftKey(item.groupId)
      const manualScore =
        draftState.courseGroupScores[scoreKey] === undefined || draftState.courseGroupScores[scoreKey] === ''
          ? null
          : Number(draftState.courseGroupScores[scoreKey])
      const note = draftState.courseGroupNotes[scoreKey] || ''

      return {
        groupId: item.groupId,
        groupName: item.groupName,
        memberCount: item.memberCount,
        taskCount: item.taskCount,
        teacherAverage,
        manualScore,
        note,
        changed: manualScore !== null,
        taskRows: item.taskRows,
      }
    })
    .sort((left, right) => compareText(left.groupName, right.groupName))
})

const TABLE_PAGE_SIZE_OPTIONS = [10, 20, 50]
const tablePage = ref(1)
const tablePageSize = ref(10)

const activeTableRows = computed(() => {
  if (filters.scoreScope === 'task' && filters.targetType === 'student') {
    return taskStudentRows.value
  }
  if (filters.scoreScope === 'task' && filters.targetType === 'group') {
    return taskGroupRows.value
  }
  if (filters.scoreScope === 'course' && filters.targetType === 'student') {
    return courseStudentRows.value
  }
  return courseGroupRows.value
})

const tablePageCount = computed(() => Math.max(1, Math.ceil(activeTableRows.value.length / tablePageSize.value)))

const tablePageSafe = computed(() => Math.min(Math.max(1, tablePage.value), tablePageCount.value))

const pagedTableRows = computed(() => {
  const start = (tablePageSafe.value - 1) * tablePageSize.value
  return activeTableRows.value.slice(start, start + tablePageSize.value)
})

const tableRowOffset = computed(() => (tablePageSafe.value - 1) * tablePageSize.value)

function prevTablePage() {
  tablePage.value = Math.max(1, tablePageSafe.value - 1)
}

function nextTablePage() {
  tablePage.value = Math.min(tablePageCount.value, tablePageSafe.value + 1)
}

function setTablePageSize(value) {
  const nextSize = Number(value)
  tablePageSize.value = TABLE_PAGE_SIZE_OPTIONS.includes(nextSize) ? nextSize : TABLE_PAGE_SIZE_OPTIONS[0]
  tablePage.value = 1
}

function buildCourseRowId(row) {
  if (!row) {
    return ''
  }
  if (filters.targetType === 'student') {
    return `course:student:${row.studentId}`
  }
  return `course:group:${row.groupId}`
}

function countPendingAppeals(list) {
  return list.filter((item) => {
    const label = formatAppealStatus(item?.status)
    return label === '待处理' || label === '处理中'
  }).length
}

function downloadReportCsv(filename, headers, rows, metaRows = []) {
  const csvRows = [
    ...metaRows.map((row) =>
      row
        .map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`)
        .join(','),
    ),
    ...(metaRows.length ? [''] : []),
    headers.join(','),
    ...rows.map((row) =>
      row
        .map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`)
        .join(','),
    ),
  ]

  const blob = new Blob([`\uFEFF${csvRows.join('\n')}`], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

const reportExportConfig = computed(() => {
  const className = selectedClass.value?.name || '-'
  const taskName = selectedTask.value?.name || '全部任务'

  if (reportType.value === 'course' && filters.targetType === 'student') {
    return {
      title: `${className} 课程个人评分报告`,
      filename: `teacher-course-student-report-${filters.classId}.csv`,
      headers: ['班级', '学生', '学号', '小组轨迹', '覆盖任务数', '系统参考分', '互评参考', '教师最终评分', '备注'],
      rows: courseStudentRows.value.map((item) => [
        className,
        item.studentName,
        item.studentId,
        item.groupLabel,
        item.taskCount,
        formatNumber(item.teacherAverage),
        formatNumber(item.peerReference),
        formatNumber(item.manualScore),
        item.note || '',
      ]),
    }
  }

  if (reportType.value === 'course' && filters.targetType === 'group') {
    return {
      title: `${className} 课程小组评分报告`,
      filename: `teacher-course-group-report-${filters.classId}.csv`,
      headers: ['班级', '小组', '成员数', '覆盖任务数', '系统参考分', '教师最终评分', '备注'],
      rows: courseGroupRows.value.map((item) => [
        className,
        item.groupName,
        item.memberCount,
        item.taskCount,
        formatNumber(item.teacherAverage),
        formatNumber(item.manualScore),
        item.note || '',
      ]),
    }
  }

  if (filters.targetType === 'group') {
    return {
      title: `${taskName} 任务小组评分报告`,
      filename: `teacher-task-group-report-${filters.classId}-${filters.taskId || 'all'}.csv`,
      headers: ['任务', '小组', '成员数', '互评均分', '教师评分', '评分差值', '状态', '备注'],
      rows: taskGroupRows.value.map((item) => [
        item.taskName || taskName,
        item.groupName,
        item.memberCount,
        formatNumber(item.peerAverage),
        formatNumber(item.adjustedTeacherScore ?? item.teacherAverage),
        formatScoreDifference(item.scoreDifference),
        item.changed ? tm('scores.statusAdjusted') : tm('scores.statusOriginal'),
        item.note || '',
      ]),
    }
  }

  return {
    title: `${taskName} 任务个人评分报告`,
    filename: `teacher-task-student-report-${filters.classId}-${filters.taskId || 'all'}.csv`,
    headers: ['任务', '学生', '学号', '小组', '互评均分', '教师评分', '评分差值', '状态', '备注'],
    rows: taskStudentRows.value.map((item) => [
      item.taskName || taskName,
      item.studentName,
      item.studentId,
      item.groupName,
      formatNumber(item.peerAverageOn100),
      formatNumber(item.adjustedTeacherScore ?? item.teacherScore),
      formatScoreDifference(item.scoreDifference),
      item.changed ? tm('scores.statusAdjusted') : tm('scores.statusOriginal'),
      item.note || '',
    ]),
  }
})

const reportExportRows = computed(() => reportExportConfig.value.rows)
const reportRowCount = computed(() => reportExportRows.value.length)
const reportScopeLabel = computed(() => (reportType.value === 'course' ? '课程评分' : '任务评分'))
const reportTargetLabel = computed(() => (filters.targetType === 'student' ? '个人评分' : '小组评分'))
const reportTaskLabel = computed(() => {
  if (reportType.value === 'course') {
    return '全部任务'
  }
  return selectedTask.value?.name || '全部任务'
})
const reportFormatLabel = computed(
  () => REPORT_FORMAT_OPTIONS.find((item) => item.value === reportFormat.value)?.label || 'PDF',
)
const reportGeneratedAtText = computed(() => reportGeneratedAt.value || '生成时自动写入')
const reportSummaryItems = computed(() => [
  { label: '班级', value: selectedClass.value?.name || '-' },
  { label: '报告范围', value: reportScopeLabel.value },
  { label: '评分对象', value: reportTargetLabel.value },
  { label: '任务', value: reportTaskLabel.value },
  { label: '明细记录', value: `${reportRowCount.value} 条` },
  { label: '生成时间', value: reportGeneratedAtText.value },
])

function buildReportMetaRows() {
  return [
    [reportExportConfig.value.title],
    [],
    ['报告信息', '班级', '报告范围', '评分对象', '任务', '明细记录', '生成时间'],
    [
      '',
      selectedClass.value?.name || '-',
      reportScopeLabel.value,
      reportTargetLabel.value,
      reportTaskLabel.value,
      `${reportRowCount.value} 条`,
      reportGeneratedAt.value,
    ],
    [],
    ['评分明细'],
  ]
}

function escapeReportHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function buildReportHtml() {
  const summaryHtml = reportSummaryItems.value
    .map(
      (item) => `
        <div class="summary-item">
          <span>${escapeReportHtml(item.label)}</span>
          <strong>${escapeReportHtml(item.value)}</strong>
        </div>
      `,
    )
    .join('')
  const headerHtml = reportExportConfig.value.headers
    .map((header) => `<th>${escapeReportHtml(header)}</th>`)
    .join('')
  const rowsHtml = reportExportRows.value
    .map(
      (row) => `
        <tr>
          ${row.map((cell) => `<td>${escapeReportHtml(cell)}</td>`).join('')}
        </tr>
      `,
    )
    .join('')

  return `
    <div style="width: 760px; padding: 18px; background: #ffffff; color: #111827; font-family: Arial, 'Microsoft YaHei', sans-serif;">
      <style>
        .report-title-wrap { margin-bottom: 14px; padding-bottom: 12px; border-bottom: 2px solid #111827; }
        .report-kicker { margin: 0 0 6px; color: #64748b; font-size: 12px; }
        .report-title { margin: 0; color: #111827; font-size: 20px; line-height: 1.35; }
        .summary-grid { margin-bottom: 14px; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
        .summary-item { padding: 8px; border: 1px solid #d9e1ee; background: #f8fafc; }
        .summary-item span { display: block; margin-bottom: 4px; color: #64748b; font-size: 11px; }
        .summary-item strong { display: block; color: #111827; font-size: 12px; line-height: 1.35; word-break: break-word; }
        table { width: 100%; border-collapse: collapse; table-layout: fixed; }
        th, td { border: 1px solid #d9e1ee; padding: 7px 8px; font-size: 11px; line-height: 1.45; text-align: left; word-break: break-word; vertical-align: top; }
        th { background: #eef4ff; color: #1e3a8a; font-weight: 700; }
        td { color: #111827; }
      </style>
      <div class="report-title-wrap">
        <p class="report-kicker">TeamTrace 评分报告</p>
        <h1 class="report-title">${escapeReportHtml(reportExportConfig.value.title)}</h1>
      </div>
      <div class="summary-grid">${summaryHtml}</div>
      <table>
        <thead><tr>${headerHtml}</tr></thead>
        <tbody>${rowsHtml}</tbody>
      </table>
    </div>
  `
}

function createReportPdfElement() {
  const wrapper = document.createElement('div')
  wrapper.className = 'report-pdf-runtime'
  wrapper.style.cssText = [
    'position: fixed',
    'left: 0',
    'top: 0',
    'z-index: 2147483647',
    'width: 800px',
    'background: #ffffff',
    'pointer-events: none',
  ].join(';')
  wrapper.innerHTML = buildReportHtml()
  document.body.appendChild(wrapper)
  return wrapper
}

async function refreshCourseReportRows() {
  if (!filters.classId || !tasks.value.length) {
    courseReportRows.value = []
    return
  }

  try {
    const workspaceAppeals = await loadTeacherAppealsWorkspace()
    const classAppeals = workspaceAppeals.filter((item) => String(item.classId) === String(filters.classId))
    const pendingByTaskId = buildPendingAppealsByTaskId(classAppeals)

    courseReportRows.value = tasks.value.map((item) => {
      const pendingAppeals = pendingByTaskId.get(String(item.taskId)) ?? 0
      return {
        task: item.name,
        status: item.status,
        deadline: item.deadlineText,
        pendingAppeals,
        peerReview: item.enablePeerReview ? '开启' : '关闭',
      }
    })
  } catch {
    courseReportRows.value = []
  }
}

function syncReportScope() {
  filters.scoreScope = reportType.value
}

async function exportCurrentReport() {
  if (!reportExportRows.value.length) {
    setMessage(tm('reports.noExportData'), 'error')
    return
  }

  reportGeneratedAt.value = new Date().toLocaleString('zh-CN', { hour12: false })
  downloadReportCsv(
    reportExportConfig.value.filename,
    reportExportConfig.value.headers,
    reportExportRows.value,
    buildReportMetaRows(),
  )

  setMessage(tm('reports.csvExported'), 'success')
}

async function exportCurrentReportPdf() {
  if (!reportExportRows.value.length) {
    setMessage(tm('reports.noExportData'), 'error')
    return
  }

  const filename =
    reportExportConfig.value.filename.replace(/\.csv$/i, '.pdf')

  pdfExportBusy.value = true
  let reportElement = null
  try {
    reportGeneratedAt.value = new Date().toLocaleString('zh-CN', { hour12: false })
    await nextTick()
    reportElement = createReportPdfElement()
    const html2pdf = await loadHtml2Pdf()
    await html2pdf()
      .set({
        margin: [10, 10, 10, 10],
        filename,
        image: { type: 'jpeg', quality: 0.92 },
        html2canvas: { scale: 2, useCORS: true, logging: false, backgroundColor: '#ffffff' },
        jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' },
        pagebreak: { mode: ['avoid-all', 'css', 'legacy'] },
      })
      .from(reportElement.firstElementChild || reportElement)
      .save()
    setMessage(tm('reports.pdfExported'), 'success')
  } catch (error) {
    setMessage(error?.message || tm('reports.pdfFailed'), 'error')
  } finally {
    reportElement?.remove()
    pdfExportBusy.value = false
  }
}

async function generateCurrentReport() {
  if (reportFormat.value === 'csv') {
    await exportCurrentReport()
    return
  }
  await exportCurrentReportPdf()
}

const activeTaskPolicy = computed(() => {
  return {
    scoringApproach: '互评与教师评分独立记录，评分差值仅用于发现分歧',
    peerEditable: '互评仅在申诉成立后才通过申诉流程调整',
    teacherEditable: '调整教师分为教师主动输入的独立字段，不会回写到原始教师分',
  }
})

const courseRuleSummary = computed(() => {
  const sampleCount = filters.targetType === 'student' ? courseStudentRows.value.length : courseGroupRows.value.length
  return {
    taskCount: tasks.value.length,
    sampleCount,
    sampleLabel: filters.targetType === 'student' ? '学生样本' : '小组样本',
  }
})

const overviewCards = computed(() => {
  if (filters.scoreScope === 'task') {
    const rowCount = filters.targetType === 'student' ? taskStudentRows.value.length : taskGroupRows.value.length
    const scoredCount =
      filters.targetType === 'student'
        ? taskStudentRows.value.filter((item) => item.adjustedTeacherScore !== null || item.teacherScore !== null).length
        : taskGroupRows.value.filter((item) => item.adjustedTeacherScore !== null || item.teacherAverage !== null).length
    const differenceAverage =
      filters.targetType === 'student'
        ? average(taskStudentRows.value.map((item) => item.scoreDifference), null)
        : average(taskGroupRows.value.map((item) => item.scoreDifference), null)
    const changedCount =
      filters.targetType === 'student'
        ? taskStudentRows.value.filter((item) => item.changed).length
        : taskGroupRows.value.filter((item) => item.changed).length

    return [
      {
        label: '评分层级',
        value: '任务评分',
        note: selectedTask.value?.name || '全部任务',
      },
      {
        label: filters.targetType === 'student' ? '学生样本' : '小组样本',
        value: String(rowCount),
        note: filters.groupId ? '已按小组范围筛选' : '当前任务全部样本',
      },
      {
        label: '已评分对象',
        value: `${scoredCount}/${rowCount}`,
        note: '教师对每个评分对象只记录一次任务评分',
      },
      {
        label: '平均差值',
        value: formatScoreDifference(differenceAverage),
        note: `教师评分 - 互评均分；已调整 ${changedCount} ${filters.targetType === 'student' ? '人' : '组'}`,
      },
    ]
  }

  const rowCount = filters.targetType === 'student' ? courseStudentRows.value.length : courseGroupRows.value.length
  const teacherAverage =
    filters.targetType === 'student'
      ? average(courseStudentRows.value.map((item) => item.teacherAverage), null)
      : average(courseGroupRows.value.map((item) => item.teacherAverage), null)
  const courseScoreAverage =
    filters.targetType === 'student'
      ? average(courseStudentRows.value.map((item) => item.manualScore), null)
      : average(courseGroupRows.value.map((item) => item.manualScore), null)
  const changedCount =
    filters.targetType === 'student'
      ? courseStudentRows.value.filter((item) => item.changed).length
      : courseGroupRows.value.filter((item) => item.changed).length

  return [
    {
      label: '评分层级',
      value: '课程评分记录',
      note: selectedClass.value?.name || '请选择班级',
    },
    {
      label: filters.targetType === 'student' ? '学生记录数' : '小组记录数',
      value: String(rowCount),
      note: filters.groupId ? '当前已按小组范围筛选' : '当前班级全部样本',
    },
    {
      label: '系统参考分',
      value: formatNumber(teacherAverage),
      note: '由已评分任务汇总生成，仅供教师参考',
    },
    {
      label: '教师最终评分',
      value: formatNumber(courseScoreAverage),
      note: `已记录 ${changedCount} 项`,
    },
  ]
})

const workspaceEmptyState = computed(() => {
  if (loading.value || loadingClasses.value) {
    return null
  }
  if (!classes.value.length) {
    return {
      title: '暂无可评分班级',
      description: '创建班级并发布任务后，评分中心会在这里显示评分记录。',
      action: '',
    }
  }
  if (!filters.classId) {
    return {
      title: '请选择班级',
      description: '先选择一个班级，再查看任务评分或课程评分记录。',
      action: '',
    }
  }
  if (filters.scoreScope === 'task' && !tasks.value.length) {
    return {
      title: '当前班级暂无任务',
      description: '发布任务后，教师评分、互评均分和评分差值会显示在这里。',
      action: '',
    }
  }
  if (!groups.value.length) {
    return {
      title: '当前班级暂无小组',
      description: '学生加入小组后，评分中心才能按小组和成员生成记录。',
      action: '',
    }
  }
  if (!activeTableRows.value.length) {
    return {
      title: '当前筛选暂无评分记录',
      description:
        filters.scoreScope === 'task'
          ? '如果学生尚未完成互评，或教师还没有评分，这里会暂时为空。可以切换小组、任务或点击刷新重试。'
          : '课程评分记录会基于已发布任务和教师评分记录汇总生成。',
      action: '刷新评分中心',
    }
  }
  return null
})

function resolveRowTaskId(row) {
  return row?.taskId || filters.taskId
}

function updateTaskStudentScore(rowOrStudentId, value) {
  const normalizedValue = normalizeScoreInput(value)
  const studentId = rowOrStudentId?.studentId ?? rowOrStudentId
  const key = buildTaskStudentDraftKey(resolveRowTaskId(rowOrStudentId), studentId)
  draftState.taskStudentScores[key] = normalizedValue
  if (isSelectedScoreRow(rowOrStudentId)) {
    scoreWorkbenchDraft.value = normalizedValue
    scoreDetailRow.value = findActiveScoreRow(rowOrStudentId)
  }
}

function updateTaskStudentNote(rowOrStudentId, value) {
  const studentId = rowOrStudentId?.studentId ?? rowOrStudentId
  const key = buildTaskStudentDraftKey(resolveRowTaskId(rowOrStudentId), studentId)
  draftState.taskStudentNotes[key] = value
  if (isSelectedScoreRow(rowOrStudentId)) {
    scoreWorkbenchNote.value = value
    scoreDetailRow.value = findActiveScoreRow(rowOrStudentId)
  }
}

function updateTaskGroupScore(rowOrGroupId, value) {
  const normalizedValue = normalizeScoreInput(value)
  const groupId = rowOrGroupId?.groupId ?? rowOrGroupId
  const key = buildTaskGroupDraftKey(resolveRowTaskId(rowOrGroupId), groupId)
  draftState.taskGroupScores[key] = normalizedValue
  if (isSelectedScoreRow(rowOrGroupId)) {
    scoreWorkbenchDraft.value = normalizedValue
    scoreDetailRow.value = findActiveScoreRow(rowOrGroupId)
  }
}

function updateTaskGroupNote(rowOrGroupId, value) {
  const groupId = rowOrGroupId?.groupId ?? rowOrGroupId
  const key = buildTaskGroupDraftKey(resolveRowTaskId(rowOrGroupId), groupId)
  draftState.taskGroupNotes[key] = value
  if (isSelectedScoreRow(rowOrGroupId)) {
    scoreWorkbenchNote.value = value
    scoreDetailRow.value = findActiveScoreRow(rowOrGroupId)
  }
}

function updateSavedTaskScoreLocally(row, score) {
  const taskId = row?.taskId || filters.taskId
  if (!taskId || !row) {
    return
  }

  taskScoreBundles.value = taskScoreBundles.value.map((bundle) => {
    const isSameTask = String(bundle.taskId) === String(taskId)
    const isSameGroup = String(bundle.groupId) === String(row.groupId)
    if (!isSameTask || !isSameGroup) {
      return bundle
    }

    if (filters.targetType === 'group') {
      return {
        ...bundle,
        groupTeacherScore: score,
      }
    }

    return {
      ...bundle,
      rows: bundle.rows.map((member) => {
        if (String(member.studentId) !== String(row.studentId)) {
          return member
        }
        return {
          ...member,
          teacherScore: score,
        }
      }),
    }
  })

  writeSessionCache(taskWorkspaceCacheKey(), taskScoreBundles.value)
  syncScoreDetailRowFromActiveRows()
}

function updateCourseStudentScore(studentId, value) {
  const key = buildCourseStudentDraftKey(studentId)
  draftState.courseStudentScores[key] = normalizeScoreInput(value)
}

function updateCourseStudentNote(studentId, value) {
  const key = buildCourseStudentDraftKey(studentId)
  draftState.courseStudentNotes[key] = value
}

function updateCourseRegularScore(studentId, value) {
  const key = buildCourseStudentDraftKey(studentId)
  draftState.courseRegularScores[key] = normalizeScoreInput(value)
}

function updateCourseGroupScore(groupId, value) {
  const key = buildCourseGroupDraftKey(groupId)
  draftState.courseGroupScores[key] = normalizeScoreInput(value)
}

function updateCourseGroupNote(groupId, value) {
  const key = buildCourseGroupDraftKey(groupId)
  draftState.courseGroupNotes[key] = value
}

function resolveCourseScoreValue(row) {
  if (!row) {
    return ''
  }
  if (filters.targetType === 'student') {
    return draftState.courseStudentScores[buildCourseStudentDraftKey(row.studentId)] ?? ''
  }
  return draftState.courseGroupScores[buildCourseGroupDraftKey(row.groupId)] ?? ''
}

async function focusCourseScoreInput(row) {
  await nextTick()
  const rowId = buildCourseRowId(row)
  const input = Array.from(document.querySelectorAll('.course-score-input')).find((item) => item.dataset.courseRowId === rowId)
  input?.focus()
  input?.select?.()
}

async function focusNextCourseScoreInput(row) {
  const currentId = buildCourseRowId(row)
  const currentIndex = activeTableRows.value.findIndex((item) => buildCourseRowId(item) === currentId)
  if (currentIndex < 0 || !activeTableRows.value.length) {
    return
  }

  const nextIndex = Math.min(currentIndex + 1, activeTableRows.value.length - 1)
  const nextRow = activeTableRows.value[nextIndex]
  tablePage.value = Math.floor(nextIndex / tablePageSize.value) + 1
  await focusCourseScoreInput(nextRow)
}

const scoreDetailOpen = ref(false)
const scoreDetailLoading = ref(false)
const scoreDetailSaving = ref(false)
const scoreDetailRow = ref(null)
const scoreDetailContext = reactive({
  taskName: '',
  targetType: 'student',
  submissionStatus: 'none',
  submissionHint: '',
  submittedAtText: '',
  submissionLink: '',
  submissionText: '',
  files: [],
  subtasks: [],
  reportHistories: [],
  initialScore: '',
})
const scoreWorkbenchTab = ref('overview')
const scoreWorkbenchDraft = ref('')
const scoreWorkbenchNote = ref('')
const selectedScoreRowId = ref('')
const selectedTraceSubtaskId = ref('')
const traceDetailOpen = ref(false)

const selectedTraceSubtask = computed(() =>
  scoreDetailContext.subtasks.find((item) => item.id === selectedTraceSubtaskId.value) || null,
)

const scoreEvidenceFileCount = computed(() => {
  if (scoreDetailContext.targetType === 'group' && scoreDetailContext.reportHistories.length) {
    return scoreDetailContext.reportHistories.reduce((total, history) => total + (history.files?.length || 0), 0)
  }
  return scoreDetailContext.files.length
})

const scoreDetailTargetLabel = computed(() => {
  const row = scoreDetailRow.value
  if (!row) {
    return ''
  }
  if (filters.targetType === 'student') {
    return row.studentName || `${t('学生', 'Student')} ${row.studentId}`
  }
  return row.groupName || t('未命名小组', 'Unnamed group')
})

function isInteractiveScoreTarget(event) {
  const tag = String(event?.target?.tagName || '').toLowerCase()
  return tag === 'input' || tag === 'textarea' || tag === 'button' || tag === 'select' || tag === 'a'
}

function buildScoreRowId(row) {
  if (!row) {
    return ''
  }
  if (filters.targetType === 'student') {
    return `${row.taskId || filters.taskId}:${row.groupId}:${row.studentId}`
  }
  return `${row.taskId || filters.taskId}:${row.groupId}`
}

function isSelectedScoreRow(row) {
  return buildScoreRowId(row) === selectedScoreRowId.value
}

function findActiveScoreRow(row) {
  const rowId = buildScoreRowId(row)
  return activeTableRows.value.find((item) => buildScoreRowId(item) === rowId) || row || null
}

function syncScoreDetailRowFromActiveRows() {
  if (!scoreDetailRow.value) {
    return
  }

  const activeRow = findActiveScoreRow(scoreDetailRow.value)
  if (!activeRow) {
    return
  }

  scoreDetailRow.value = activeRow
  scoreWorkbenchDraft.value = resolveWorkbenchScore(activeRow)
  scoreWorkbenchNote.value = activeRow.note || ''
}

function resolveScoreDetailDifference() {
  const row = scoreDetailRow.value
  if (!row) {
    return null
  }

  const teacherScore = resolveWorkbenchScore(row)
  const peerScore = filters.targetType === 'student' ? row.peerAverageOn100 : row.peerAverage
  return calculateScoreDifference(peerScore, teacherScore)
}

function resolveSubtaskAssigneeName(subtask, groupRows, group) {
  const assigneeId = subtask?.assigneeId ?? subtask?.studentId
  const groupMemberName = group?.memberNames?.[String(assigneeId)]
  if (groupMemberName) {
    return groupMemberName
  }

  const directName = subtask?.assigneeName || subtask?.studentName
  if (directName && String(directName) !== String(assigneeId) && !String(directName).startsWith('学生 ')) {
    return directName
  }

  const member = groupRows.find((item) => String(item.studentId) === String(assigneeId))
  if (member?.studentName && String(member.studentName) !== String(assigneeId) && !String(member.studentName).startsWith('学生 ')) {
    return member.studentName
  }

  return t('未分配', 'Unassigned')
}

function resolveWorkbenchScore(row) {
  if (!row) {
    return ''
  }
  const taskId = row.taskId || filters.taskId
  if (filters.targetType === 'student') {
    const draft = draftState.taskStudentScores[buildTaskStudentDraftKey(taskId, row.studentId)]
    if (draft !== undefined) {
      return draft
    }
    return row.adjustedTeacherScore ?? row.teacherScore ?? ''
  }

  const draft = draftState.taskGroupScores[buildTaskGroupDraftKey(taskId, row.groupId)]
  if (draft !== undefined) {
    return draft
  }
  return row.adjustedTeacherScore ?? row.teacherAverage ?? ''
}

function resolveTaskScoreInputValue(row) {
  return resolveWorkbenchScore(row)
}

function updateWorkbenchDraft(value) {
  const normalizedValue = normalizeScoreInput(value)
  scoreWorkbenchDraft.value = normalizedValue
  const row = scoreDetailRow.value
  if (!row) {
    return
  }
  if (filters.targetType === 'student') {
    updateTaskStudentScore(row, normalizedValue)
    return
  }
  updateTaskGroupScore(row, normalizedValue)
}

function updateWorkbenchNote(value) {
  scoreWorkbenchNote.value = value
  const row = scoreDetailRow.value
  if (!row) {
    return
  }
  row.note = value
  if (filters.targetType === 'student') {
    updateTaskStudentNote(row, value)
    return
  }
  updateTaskGroupNote(row, value)
}

function selectedScoreRowIndex() {
  return activeTableRows.value.findIndex((item) => buildScoreRowId(item) === selectedScoreRowId.value)
}

async function selectAdjacentScoreRow(step) {
  if (!activeTableRows.value.length) {
    return
  }
  const currentIndex = selectedScoreRowIndex()
  const baseIndex = currentIndex >= 0 ? currentIndex : 0
  const nextIndex = Math.min(Math.max(baseIndex + step, 0), activeTableRows.value.length - 1)
  await selectScoreRow(activeTableRows.value[nextIndex])
}

async function focusSelectedScoreInput(source = 'list') {
  await nextTick()
  if (source === 'workbench') {
    document.querySelector('.workbench-score-input')?.focus()
    return
  }

  const selectedId = selectedScoreRowId.value
  const input = Array.from(document.querySelectorAll('.target-score-input'))
    .find((item) => item.dataset.scoreRowId === selectedId)
  input?.focus()
}

function formatSubtaskStatusLabel(status) {
  const normalized = Number(status)
  if (normalized === 4) return t('已完成', 'Completed')
  if (normalized === 3) return t('待审批', 'Pending review')
  if (normalized === 2) return t('已提交', 'Submitted')
  if (normalized === 1) return t('进行中', 'In progress')
  return t('未开始', 'Not started')
}

function buildSubtaskEvidence(subtask) {
  const parsed = parseSubmissionContent(subtask?.submissionContent)
  const label = subtask?.name || t('\u5b50\u4efb\u52a1', 'Subtask')
  const files = parsed.files.map((item, index) => ({
    id: `${subtask?.subtaskId || label}-file-${index}`,
    name: item.name || t('\u9644\u4ef6', 'Attachment'),
    url: item.value,
    size: item.size,
    subtaskName: label,
    kind: 'file',
  }))

  if (parsed.link) {
    files.push({
      id: `${subtask?.subtaskId || label}-link`,
      name: t('\u63d0\u4ea4\u94fe\u63a5', 'Submission link'),
      url: parsed.link,
      subtaskName: label,
      kind: 'link',
    })
  }

  return {
    text: parsed.text || '',
    link: parsed.link || parsed.attachment || '',
    files,
  }
}

function buildSubmissionFiles(subtasks) {
  const files = []

  subtasks.forEach((subtask) => {
    const parsed = parseSubmissionContent(subtask?.submissionContent)
    const label = subtask?.name || t('子任务', 'Subtask')

    parsed.files.forEach((item, index) => {
      files.push({
        id: `${subtask?.subtaskId || label}-file-${index}`,
        name: `${label} · ${item.name || t('附件', 'Attachment')}`,
        url: item.value,
        size: item.size,
        subtaskName: label,
        kind: 'file',
      })
    })

    if (parsed.link) {
      files.push({
        id: `${subtask?.subtaskId || label}-link`,
        name: `${label} · ${t('外链', 'Link')}`,
        url: parsed.link,
        subtaskName: label,
        kind: 'link',
      })
    }
  })

  return files
}

function buildGroupReportFiles(report) {
  const parsed = parseSubmissionContent(report?.reportContent)
  const files = []

  parsed.files.forEach((item, index) => {
    files.push({
      id: `${report?.reportId || 'group-report'}-file-${index}`,
      name: `小组总报告 · ${item.name || t('附件', 'Attachment')}`,
      url: item.value,
      size: item.size,
      subtaskName: t('小组总报告', 'Group report'),
      kind: 'file',
    })
  })

  if (parsed.link) {
    files.push({
      id: `${report?.reportId || 'group-report'}-link`,
      name: `小组总报告 · ${t('外链', 'Link')}`,
      url: parsed.link,
      subtaskName: t('小组总报告', 'Group report'),
      kind: 'link',
    })
  }

  return files
}

function buildGroupReportHistoryFiles(history, report) {
  const parsed = parseSubmissionContent(history?.reportContent)
  const versionNo = history?.versionNo || history?.version || ''
  const baseId = history?.id || `${report?.reportId || 'group-report'}-${versionNo}`
  const files = parsed.files.map((item, index) => ({
    id: `${baseId}-file-${index}`,
    name: item.name || t('附件', 'Attachment'),
    url: item.value,
    size: item.size,
    kind: 'file',
  }))

  if (parsed.link) {
    files.push({
      id: `${baseId}-link`,
      name: t('外链', 'Link'),
      url: parsed.link,
      kind: 'link',
    })
  }

  return files
}

function resolveSubmissionStatus(subtasks) {
  if (!subtasks.length) {
    return {
      status: 'none',
      hint: t('未检测到提交记录，评分前请与学生确认。', 'No submission found. Confirm with the student before scoring.'),
      submittedAtText: '',
    }
  }

  const submitted = subtasks.filter((item) => item?.submissionContent)
  if (!submitted.length) {
    return {
      status: 'none',
      hint: t('未检测到提交记录，评分前请与学生确认。', 'No submission found. Confirm with the student before scoring.'),
      submittedAtText: '',
    }
  }

  const completedCount = subtasks.filter((item) => Number(item?.status) === 4).length
  const latestSubmittedAt = submitted
    .map((item) => item?.submittedAt || item?.updatedAt || item?.handledAt || '')
    .filter(Boolean)
    .sort((left, right) => new Date(right).getTime() - new Date(left).getTime())[0]

  if (completedCount > 0 && completedCount < subtasks.length) {
    return {
      status: 'partial',
      hint: t('部分子任务已提交，仍有未完成项。', 'Some subtasks were submitted; others are still pending.'),
      submittedAtText: latestSubmittedAt ? formatDateTime(latestSubmittedAt) : '',
    }
  }

  const hasLate = submitted.some((item) => Number(item?.isLate) === 1 || item?.late === true)
  return {
    status: hasLate ? 'late' : 'onTime',
    hint: hasLate
      ? t('已超过截止时间，请结合迟交情况评分。', 'Submitted after the deadline. Consider lateness when scoring.')
      : t('作业已在截止前提交。', 'Submitted before the deadline.'),
    submittedAtText: latestSubmittedAt ? formatDateTime(latestSubmittedAt) : '',
  }
}

function resetScoreDetailContext(row) {
  const currentTask = tasks.value.find((item) => item.taskId === (row?.taskId || filters.taskId))
  scoreDetailContext.taskName = row?.taskName || currentTask?.name || t('未命名任务', 'Unnamed task')
  scoreDetailContext.targetType = filters.targetType
  scoreDetailContext.submissionStatus = 'none'
  scoreDetailContext.submissionHint = ''
  scoreDetailContext.submittedAtText = ''
  scoreDetailContext.submissionLink = ''
  scoreDetailContext.submissionText = ''
  scoreDetailContext.files = []
  scoreDetailContext.subtasks = []
  scoreDetailContext.reportHistories = []
  scoreDetailContext.initialScore = resolveModalInitialScore(row)
  scoreWorkbenchDraft.value = resolveWorkbenchScore(row)
  scoreWorkbenchNote.value = row?.note || ''
  selectedTraceSubtaskId.value = ''
  traceDetailOpen.value = false
}

async function loadScoreDetailSubmission(row) {
  const classId = filters.classId
  const taskId = row?.taskId || filters.taskId
  const groupId = row?.groupId

  if (!classId || !taskId || !groupId) {
    return
  }

  scoreDetailLoading.value = true
  try {
    const { data } = await fetchTeacherGroupSubtasks(classId, taskId, groupId)
    const subtasks = Array.isArray(data?.data) ? data.data : []
    const groupBundle = taskScoreBundles.value.find(
      (item) => String(item.taskId) === String(taskId) && String(item.groupId) === String(groupId),
    )
    const groupRows = Array.isArray(groupBundle?.rows) ? groupBundle.rows : []
    const currentGroup = groups.value.find((item) => String(item.groupId) === String(groupId)) || null
    const relevant =
      filters.targetType === 'student'
        ? subtasks.filter((item) => String(item?.assigneeId ?? '') === String(row.studentId))
        : subtasks

    scoreDetailContext.subtasks = relevant.map((item) => {
      const evidence = buildSubtaskEvidence(item)
      return {
        id: item?.subtaskId || item?.id || `${item?.name || 'subtask'}-${item?.assigneeId || 'unknown'}`,
        name: item?.name || t('\u672a\u547d\u540d\u5b50\u4efb\u52a1', 'Unnamed subtask'),
        assigneeName: resolveSubtaskAssigneeName(item, groupRows, currentGroup),
        statusLabel: formatSubtaskStatusLabel(item?.status),
        submittedAtText: item?.submittedAt ? formatDateTime(item.submittedAt) : '',
        updatedAtText: item?.updatedAt ? formatDateTime(item.updatedAt) : '',
        hasSubmission: Boolean(item?.submissionContent),
        submissionText: evidence.text,
        submissionLink: evidence.link,
        files: evidence.files,
      }
    })
    if (!scoreDetailContext.subtasks.some((item) => item.id === selectedTraceSubtaskId.value)) {
      selectedTraceSubtaskId.value = ''
    }

    if (filters.targetType === 'group') {
      const { data: groupReportResponse } = await fetchTeacherTaskGroupReport(classId, taskId, groupId).catch(() => ({ data: null }))
      const report = groupReportResponse?.data || null
      if (!report) {
        scoreDetailContext.submissionStatus = 'none'
        scoreDetailContext.submissionHint = t(
          '\u8be5\u5c0f\u7ec4\u8fd8\u6ca1\u6709\u63d0\u4ea4\u5c0f\u7ec4\u603b\u62a5\u544a\u3002\u5c0f\u7ec4\u8bc4\u5206\u7684\u63d0\u4ea4\u6750\u6599\u53ea\u663e\u793a\u5c0f\u7ec4\u603b\u62a5\u544a\uff0c\u6210\u5458\u5b50\u4efb\u52a1\u8bb0\u5f55\u8bf7\u5728\u8d21\u732e\u75d5\u8ff9\u4e2d\u67e5\u770b\u3002',
          'This group has not submitted a group report. Group evidence only shows the group report; member subtasks are available under traces.',
        )
        scoreDetailContext.submittedAtText = ''
        scoreDetailContext.submissionText = ''
        scoreDetailContext.submissionLink = ''
        scoreDetailContext.files = []
        scoreDetailContext.reportHistories = []
        return
      }

      scoreDetailContext.submissionStatus = 'onTime'
      scoreDetailContext.submissionHint = t(
        '\u5c0f\u7ec4\u8bc4\u5206\u4f9d\u636e\u4f7f\u7528\u7ec4\u957f\u63d0\u4ea4\u7684\u5c0f\u7ec4\u603b\u62a5\u544a\uff0c\u5b50\u4efb\u52a1\u75d5\u8ff9\u53ef\u5728\u8d21\u732e\u75d5\u8ff9\u4e2d\u67e5\u770b\u3002',
        'Group scoring uses the leader-submitted group report. Subtask traces remain available.',
      )
      scoreDetailContext.submittedAtText = report.submittedAt ? formatDateTime(report.submittedAt) : ''
      scoreDetailContext.submissionText = ''
      scoreDetailContext.submissionLink = ''
      scoreDetailContext.files = []
      const reportHistories = Array.isArray(report.histories) && report.histories.length ? report.histories : [report]
      scoreDetailContext.reportHistories = reportHistories.map((history) => {
        const parsed = parseSubmissionContent(history?.reportContent)
        return {
          id: history?.id || `${report.reportId}-${history?.versionNo || history?.version || report.versionNo || 'current'}`,
          versionNo: history?.versionNo || history?.version || report.versionNo,
          submittedAtText: history?.submittedAt || history?.createdAt ? formatDateTime(history.submittedAt || history.createdAt) : '',
          current: Boolean(history?.current) || String(history?.versionNo || history?.version || '') === String(report.versionNo || ''),
          text: parsed.text || '',
          link: parsed.link || parsed.attachment || '',
          files: buildGroupReportHistoryFiles(history, report),
        }
      })
      return
    }

    const submittedRows = relevant.filter((item) => item?.submissionContent)
    const statusMeta = resolveSubmissionStatus(relevant)
    const firstSubmission = submittedRows[0]
    const firstParsed = parseSubmissionContent(firstSubmission?.submissionContent)

    scoreDetailContext.submissionStatus = statusMeta.status
    scoreDetailContext.submissionHint = statusMeta.hint
    scoreDetailContext.submittedAtText = statusMeta.submittedAtText
    scoreDetailContext.submissionText = submittedRows
      .map((item) => {
        const parsed = parseSubmissionContent(item?.submissionContent)
        const prefix = item?.name ? `${item.name}: ` : ''
        return parsed.text ? `${prefix}${parsed.text}` : ''
      })
      .filter(Boolean)
      .join('\n\n')
    scoreDetailContext.submissionLink = firstParsed.link || firstParsed.attachment
    scoreDetailContext.files = buildSubmissionFiles(submittedRows)
    scoreDetailContext.reportHistories = []
  } catch {
    scoreDetailContext.submissionStatus = 'none'
    scoreDetailContext.submissionHint = t('\u63d0\u4ea4\u5185\u5bb9\u6682\u65f6\u52a0\u8f7d\u5931\u8d25\uff0c\u4ecd\u53ef\u7ee7\u7eed\u8bc4\u5206\u3002', 'Failed to load submission details. You can still score.')
    scoreDetailContext.submittedAtText = ''
    scoreDetailContext.submissionText = ''
    scoreDetailContext.submissionLink = ''
    scoreDetailContext.files = []
    scoreDetailContext.reportHistories = []
  } finally {
    scoreDetailLoading.value = false
  }
}

async function selectScoreRow(row) {
  if (!row || filters.scoreScope !== 'task') {
    return
  }
  scoreDetailRow.value = row
  selectedScoreRowId.value = buildScoreRowId(row)
  resetScoreDetailContext(row)
  scoreDetailOpen.value = true
  scoreWorkbenchTab.value = 'overview'
  await loadScoreDetailSubmission(row)
}

async function ensureSelectedScoreRow() {
  if (filters.scoreScope !== 'task' || !activeTableRows.value.length) {
    closeScoreDetail()
    return
  }
  if (activeTableRows.value.some((item) => buildScoreRowId(item) === selectedScoreRowId.value)) {
    return
  }
  await selectScoreRow(activeTableRows.value[0])
}

async function handleScoreRowClick(row, event) {
  if (filters.scoreScope !== 'task' || isInteractiveScoreTarget(event)) {
    return
  }

  await selectScoreRow(row)
}

function closeScoreDetail() {
  scoreDetailOpen.value = false
  scoreDetailRow.value = null
  selectedScoreRowId.value = ''
  selectedTraceSubtaskId.value = ''
  traceDetailOpen.value = false
}

function selectTraceSubtask(item) {
  selectedTraceSubtaskId.value = item?.id || ''
  traceDetailOpen.value = Boolean(item)
}

function closeTraceDetail() {
  traceDetailOpen.value = false
}

async function confirmScoreDetail(score, options = {}) {
  const row = scoreDetailRow.value
  if (!row || score === null || score === undefined || score === '') {
    setMessage(t('请先输入教师评分。', 'Enter a teacher score first.'), 'error')
    if (options.close !== false) {
      closeScoreDetail()
    }
    return false
  }

  const classId = filters.classId
  const taskId = row?.taskId || filters.taskId
  if (!classId || !taskId) {
    setMessage(t('请先选择班级和任务。', 'Select a class and task first.'), 'error')
    return false
  }

  const parsed = Number(score)
  if (Number.isNaN(parsed) || parsed < 0 || parsed > 100) {
    setMessage(t('教师评分须在 0–100 之间。', 'Teacher score must be between 0 and 100.'), 'error')
    return false
  }

  scoreDetailSaving.value = true
  try {
    if (filters.targetType === 'student') {
      await saveTeacherScore(classId, taskId, {
        targetType: 'student',
        studentId: Number(row.studentId),
        score: parsed,
      })
      const draftKey = buildTaskStudentDraftKey(taskId, row.studentId)
      delete draftState.taskStudentScores[draftKey]
      updateSavedTaskScoreLocally(row, parsed)
    } else {
      const bundle = taskScoreBundles.value.find(
        (item) => String(item.taskId) === String(taskId) && String(item.groupId) === String(row.groupId),
      )
      const memberStudentId = bundle?.rows?.[0]?.studentId
      if (!memberStudentId) {
        setMessage(t('该小组暂无成员，无法保存小组评分。', 'Cannot save a group score without members.'), 'error')
        return false
      }
      await saveTeacherScore(classId, taskId, {
        targetType: 'group',
        groupId: Number(row.groupId),
        studentId: Number(memberStudentId),
        score: parsed,
      })
      const draftKey = buildTaskGroupDraftKey(taskId, row.groupId)
      delete draftState.taskGroupScores[draftKey]
      updateSavedTaskScoreLocally(row, parsed)
    }

    if (options.close !== false) {
      closeScoreDetail()
    }
    setMessage(t('教师评分已保存。', 'Teacher score saved.'), 'success')
    await loadTaskWorkspace({ silent: true, force: true })
    syncScoreDetailRowFromActiveRows()
    return true
  } catch (error) {
    setMessage(error?.response?.data?.message || error?.message || t('保存教师评分失败。', 'Failed to save teacher score.'), 'error')
    return false
  } finally {
    scoreDetailSaving.value = false
  }
}

async function saveWorkbenchScore(options = {}) {
  const saved = await confirmScoreDetail(scoreWorkbenchDraft.value, { close: false })
  if (saved && options.next) {
    await selectAdjacentScoreRow(1)
    await focusSelectedScoreInput(options.focusSource || 'list')
  }
}

async function handleScoreEnter(row, event) {
  event?.preventDefault?.()
  const focusSource = event?.target?.classList?.contains('workbench-score-input') ? 'workbench' : 'list'
  if (row && !isSelectedScoreRow(row)) {
    await selectScoreRow(row)
  }
  await saveWorkbenchScore({ next: true, focusSource })
}

const courseScoresFromBackend = ref([])
const courseGroupScoresFromBackend = ref([])

async function loadCourseScoresFromBackend() {
  if (!filters.classId) return
  try {
    const [studentResponse, groupResponse] = await Promise.all([
      fetchCourseScores(filters.classId),
      fetchCourseGroupScores(filters.classId),
    ])
    const list = Array.isArray(studentResponse?.data?.data) ? studentResponse.data.data : []
    const groupList = Array.isArray(groupResponse?.data?.data) ? groupResponse.data.data : []
    courseScoresFromBackend.value = list
    courseGroupScoresFromBackend.value = groupList
    // 将后端已保存的分数回填到草稿
    list.forEach((item) => {
      if (item.totalScore !== null && item.totalScore !== undefined) {
        const key = buildCourseStudentDraftKey(item.studentId)
        if (draftState.courseStudentScores[key] === undefined || draftState.courseStudentScores[key] === '') {
          draftState.courseStudentScores[key] = String(item.totalScore)
        }
      }
    })
    groupList.forEach((item) => {
      if (item.totalScore !== null && item.totalScore !== undefined) {
        const key = buildCourseGroupDraftKey(item.groupId)
        if (draftState.courseGroupScores[key] === undefined || draftState.courseGroupScores[key] === '') {
          draftState.courseGroupScores[key] = String(item.totalScore)
        }
      }
    })
  } catch {
    courseScoresFromBackend.value = []
    courseGroupScoresFromBackend.value = []
  }
}

const savingCourseScores = ref(false)

const courseSaveButtonText = computed(() =>
  filters.targetType === 'group' ? '保存当前小组课程评分' : '保存当前课程评分',
)

async function saveCourseScoreRow(row, options = {}) {
  if (!row) {
    return false
  }

  const scoreValue = resolveCourseScoreValue(row)
  if (scoreValue === undefined || scoreValue === '') {
    if (!options.silent) {
      setMessage('请先填写教师最终评分。', 'error')
    }
    return false
  }

  const parsed = Number(scoreValue)
  if (Number.isNaN(parsed) || parsed < 0 || parsed > MAX_SCORE_VALUE) {
    setMessage('课程评分必须在 0-100 范围内。', 'error')
    return false
  }

  if (filters.targetType === 'group') {
    if (!filters.classId) {
      setMessage('请先选择班级。', 'error')
      return false
    }

    try {
      await saveCourseGroupScore(filters.classId, {
        groupId: Number(row.groupId),
        totalScore: parsed,
      })
    } catch (error) {
      setMessage(error?.response?.data?.message || error?.message || '保存小组课程评分失败。', 'error')
      return false
    }

    saveWorkspace()
    if (!options.silent) {
      setMessage(`${row.groupName || '当前小组'} 的课程评分已保存。`, 'success')
    }
    return true
  }

  if (!filters.classId) {
    setMessage('请先选择班级。', 'error')
    return false
  }

  try {
    await saveCourseScore(filters.classId, {
      studentId: Number(row.studentId),
      totalScore: parsed,
    })
    saveWorkspace()
    if (!options.silent) {
      setMessage(`${row.studentName || '当前学生'} 的课程评分已保存。`, 'success')
    }
    return true
  } catch (error) {
    setMessage(error?.response?.data?.message || error?.message || '保存课程评分失败。', 'error')
    return false
  }
}

async function handleCourseScoreEnter(row, event) {
  event?.preventDefault?.()
  const saved = await saveCourseScoreRow(row, { silent: true })
  if (saved) {
    await focusNextCourseScoreInput(row)
  }
}

async function submitCourseScoresToBackend() {
  if (!filters.classId) {
    setMessage('请先选择班级。', 'error')
    return
  }

  if (filters.targetType === 'group') {
    const rows = courseGroupRows.value
    if (!rows.length) {
      setMessage('当前没有可提交的小组课程评分记录。', 'error')
      return
    }

    savingCourseScores.value = true
    const tasks = []
    for (const row of rows) {
      const key = buildCourseGroupDraftKey(row.groupId)
      const scoreValue = draftState.courseGroupScores[key]
      if (scoreValue === undefined || scoreValue === '') continue
      const parsed = Number(scoreValue)
      if (Number.isNaN(parsed) || parsed < 0 || parsed > 100) continue
      tasks.push({ groupId: Number(row.groupId), totalScore: parsed })
    }
    const results = await Promise.allSettled(
      tasks.map((item) => saveCourseGroupScore(filters.classId, item))
    )
    const saved = results.filter((r) => r.status === 'fulfilled').length
    const failed = results.filter((r) => r.status === 'rejected').length
    savingCourseScores.value = false
    if (failed > 0) {
      setMessage(`小组课程评分提交完成：成功 ${saved} 条，失败 ${failed} 条。`, 'warn')
    } else if (saved > 0) {
      setMessage(`小组课程评分已全部提交到后端，共 ${saved} 条。`, 'success')
    } else {
      setMessage('没有需要提交的小组课程评分记录。', 'info')
    }
    saveWorkspace()
    await loadCourseScoresFromBackend()
    return
  }

  const rows = courseStudentRows.value
  if (!rows.length) {
    setMessage('当前没有可提交的教师评分记录。', 'error')
    return
  }

  savingCourseScores.value = true
  const tasks = []
  for (const row of rows) {
    const key = buildCourseStudentDraftKey(row.studentId)
    const scoreValue = draftState.courseStudentScores[key]
    if (scoreValue === undefined || scoreValue === '') continue
    const parsed = Number(scoreValue)
    if (Number.isNaN(parsed) || parsed < 0 || parsed > 100) continue
    tasks.push({ studentId: Number(row.studentId), totalScore: parsed })
  }
  const results = await Promise.allSettled(
    tasks.map((item) => saveCourseScore(filters.classId, item))
  )
  const saved = results.filter((r) => r.status === 'fulfilled').length
  const failed = results.filter((r) => r.status === 'rejected').length
  savingCourseScores.value = false
  if (failed > 0) {
    setMessage(`教师评分记录提交完成：成功 ${saved} 条，失败 ${failed} 条。`, 'warn')
  } else if (saved > 0) {
    setMessage(`教师评分记录已全部提交到后端，共 ${saved} 条。`, 'success')
  } else {
    setMessage('没有需要提交的评分记录。', 'info')
  }
  saveWorkspace()
  await loadCourseScoresFromBackend()
}

function currentEditableRows() {
  if (filters.scoreScope === 'task' && filters.targetType === 'student') {
    return taskStudentRows.value.map((item) => Number(item.adjustedTeacherScore))
  }
  if (filters.scoreScope === 'task' && filters.targetType === 'group') {
    return taskGroupRows.value.map((item) => Number(item.adjustedTeacherScore))
  }
  if (filters.scoreScope === 'course' && filters.targetType === 'student') {
    return courseStudentRows.value
      .filter((item) => item.manualScore !== null)
      .map((item) => Number(item.manualScore))
  }
  if (filters.scoreScope === 'course' && filters.targetType === 'group') {
    return courseGroupRows.value
      .filter((item) => item.manualScore !== null)
      .map((item) => Number(item.manualScore))
  }
  return []
}

function saveDrafts() {
  const invalid = currentEditableRows().some((item) => Number.isNaN(item) || item < 0 || item > 100)
  if (invalid) {
    setMessage('存在无效评分，请确保所有调整分数都在 0-100 范围内。', 'error')
    return
  }
  saveWorkspace()
  setMessage('当前评分工作区已保存到本地浏览器，暂不回写后端。', 'success')
}

function resetCurrentWorkspace() {
  if (filters.scoreScope === 'task' && filters.targetType === 'student') {
    taskStudentRows.value.forEach((item) => {
      const key = buildTaskStudentDraftKey(filters.taskId, item.studentId)
      delete draftState.taskStudentScores[key]
      delete draftState.taskStudentNotes[key]
    })
  } else if (filters.scoreScope === 'task' && filters.targetType === 'group') {
    taskGroupRows.value.forEach((item) => {
      const key = buildTaskGroupDraftKey(filters.taskId, item.groupId)
      delete draftState.taskGroupScores[key]
      delete draftState.taskGroupNotes[key]
    })
  } else if (filters.scoreScope === 'course' && filters.targetType === 'student') {
    courseStudentRows.value.forEach((item) => {
      const key = buildCourseStudentDraftKey(item.studentId)
      delete draftState.courseStudentScores[key]
      delete draftState.courseStudentNotes[key]
    })
  } else if (filters.scoreScope === 'course' && filters.targetType === 'group') {
    courseGroupRows.value.forEach((item) => {
      const key = buildCourseGroupDraftKey(item.groupId)
      delete draftState.courseGroupScores[key]
      delete draftState.courseGroupNotes[key]
    })
  }

  saveWorkspace()
  setMessage('当前工作区草稿已重置。', 'success')
}

function resetTaskWeights() {
  setMessage('当前任务评分差值视图已恢复为默认状态。', 'success')
}

function syncQuery() {
  const query = { ...route.query }

  if (filters.classId) {
    query.classId = filters.classId
  } else {
    delete query.classId
  }

  if (filters.taskId && filters.scoreScope === 'task') {
    query.taskId = filters.taskId
  } else {
    delete query.taskId
  }

  if (filters.groupId) {
    query.groupId = filters.groupId
  } else {
    delete query.groupId
  }

  query.scoreScope = filters.scoreScope
  query.targetType = filters.targetType

  delete query.termMode

  router.replace({ path: route.path, query })
}

watch(
  [() => filters.classId, () => filters.scoreScope, () => filters.targetType, () => filters.groupId, () => filters.taskId],
  () => {
    syncQuery()
    tablePage.value = 1
  },
)

watch(
  () => filters.classId,
  async (nextClassId, previousClassId) => {
    if (!workspaceReady.value || nextClassId === previousClassId) {
      return
    }
    closeScoreDetail()
    filters.taskId = ''
    filters.groupId = ''
    await loadClassContext()
    await reloadScoreWorkspace({ force: true })
    await ensureSelectedScoreRow()
  },
)

watch(
  () => [filters.scoreScope, filters.taskId, filters.groupId, filters.targetType],
  async () => {
    if (!workspaceReady.value || !filters.classId) {
      return
    }
    closeScoreDetail()
    await reloadScoreWorkspace({ force: true })
    await ensureSelectedScoreRow()
  },
)

watch(
  activeTableRows,
  async () => {
    if (!workspaceReady.value || loading.value || loadingClasses.value) {
      return
    }
    await ensureSelectedScoreRow()
  },
  { flush: 'post' },
)

watch(
  () => filters.scoreScope,
  (scope) => {
    reportType.value = scope === 'course' ? 'course' : 'task'
  },
)

watch(
  () => [filters.classId, reportType.value],
  async () => {
    if (!workspaceReady.value || reportType.value !== 'course' || !filters.classId) {
      return
    }
    await refreshCourseReportRows()
  },
)

onMounted(async () => {
  loadWorkspace()
  workspaceReady.value = true
  await loadClasses()
  if (filters.classId) {
    await loadClassContext()
    await reloadScoreWorkspace()
  }
})
</script>

<template>
  <TeacherSubviewShell :title="tm('scores.title')" :message="message" :message-type="messageType">
    <template #actions>
      <button v-if="fromClassTaskPage" class="back-btn" type="button" @click="router.push(classTaskReturnPath)">
        {{ tm('scores.backToTaskPage') }}
      </button>
    </template>

    <div class="scores-center-shell">
    <section class="card panel mode-panel">
      <div class="scope-tabs">
        <button
          class="scope-tab"
          :class="{ active: filters.scoreScope === 'task' }"
          type="button"
          @click="filters.scoreScope = 'task'"
        >
          <div class="scope-tab-top">
            <strong>{{ tm('scores.taskScore') }}</strong>
            <span v-if="filters.scoreScope === 'task'" class="scope-state">{{ tm('scores.current') }}</span>
          </div>
          <span>{{ tm('scores.forSingleTask') }}</span>
        </button>
        <button
          class="scope-tab"
          :class="{ active: filters.scoreScope === 'course' }"
          type="button"
          @click="filters.scoreScope = 'course'"
        >
          <div class="scope-tab-top">
            <strong>{{ tm('scores.courseScore') }}</strong>
            <span v-if="filters.scoreScope === 'course'" class="scope-state">{{ tm('scores.current') }}</span>
          </div>
          <span>{{ tm('scores.aggregatesTerm') }}</span>
        </button>
      </div>

      <div class="filters">
        <label>
          <span>{{ tm('scores.classLabel') }}</span>
          <select v-model="filters.classId" :disabled="loadingClasses || !classes.length">
            <option value="" disabled>{{ tm('common.selectClass') }}</option>
            <option v-for="item in classes" :key="item.classId" :value="item.classId">{{ item.name }}</option>
          </select>
        </label>

        <label v-if="filters.scoreScope === 'task'">
          <span>{{ tm('scores.taskLabel') }}</span>
          <select v-model="filters.taskId" :disabled="loading || !tasks.length">
            <option value="">{{ t('全部任务', 'All tasks') }}</option>
            <option v-for="item in tasks" :key="item.taskId" :value="item.taskId">
              {{ item.name }}（{{ item.status }}）
            </option>
          </select>
        </label>

        <label>
          <span>{{ tm('scores.groupScope') }}</span>
          <select v-model="filters.groupId" :disabled="loading || !groups.length">
            <option value="">{{ tm('scores.allGroups') }}</option>
            <option v-for="item in groups" :key="item.groupId" :value="item.groupId">
              {{ item.name }}（{{ tm('scores.memberCount', { count: item.memberCount }) }}）
            </option>
          </select>
        </label>

        <div class="filter-toggle">
          <span>{{ tm('scores.scoreTarget') }}</span>
          <div class="toggle-row">
            <button
              class="toggle-btn"
              :class="{ active: filters.targetType === 'group' }"
              type="button"
              @click="filters.targetType = 'group'"
            >
              {{ tm('scores.groupScore') }}
            </button>
            <button
              class="toggle-btn"
              :class="{ active: filters.targetType === 'student' }"
              type="button"
              @click="filters.targetType = 'student'"
            >
              {{ tm('scores.studentScore') }}
            </button>
          </div>
        </div>

      </div>
    </section>

    <section class="workspace-grid">
      <article class="card panel workspace-panel">
        <div class="panel-head">
          <div>
            <h3>
              {{
                filters.scoreScope === 'task'
                  ? filters.targetType === 'student'
                    ? tm('scores.taskStudentWorkspace')
                    : tm('scores.taskGroupWorkspace')
                  : tm('scores.courseWorkspace')
              }}
            </h3>
          </div>
          <div class="panel-head-actions">
            <div class="report-generator">
              <span class="report-inline-title">报告</span>
              <span class="report-count-pill" :class="{ empty: !reportRowCount }">
                {{ reportRowCount ? `${reportRowCount} 条` : '暂无数据' }}
              </span>
              <label class="report-type-field">
                <span>范围</span>
                <select v-model="reportType" @change="syncReportScope">
                  <option value="task">{{ tm('reports.taskReport') }}</option>
                  <option value="course">{{ tm('reports.courseReport') }}</option>
                </select>
              </label>
              <label class="report-type-field report-type-field--compact">
                <span>格式</span>
                <select v-model="reportFormat">
                  <option
                    v-for="option in REPORT_FORMAT_OPTIONS"
                    :key="option.value"
                    :value="option.value"
                  >
                    {{ option.label }}
                  </option>
                </select>
              </label>
              <button
                class="ghost-btn report-generate-btn"
                type="button"
                :disabled="pdfExportBusy || loading || !reportRowCount"
                @click="generateCurrentReport"
              >
                {{ pdfExportBusy ? tm('reports.generatingPdf') : `导出${reportFormatLabel}` }}
              </button>
            </div>
            <div v-if="filters.scoreScope === 'course'" class="export-actions score-save-actions">
              <button
                class="primary-btn"
                type="button"
                :disabled="savingCourseScores"
                @click="submitCourseScoresToBackend"
              >
                {{ savingCourseScores ? tm('scores.submitting') : courseSaveButtonText }}
              </button>
            </div>
          </div>
        </div>

        <div class="workspace-table-stage">
        <div v-if="loading || loadingClasses" class="score-empty-state">
          <strong>{{ t('正在加载评分记录', 'Loading score records') }}</strong>
          <p>{{ t('正在同步班级、任务、小组与评分数据，请稍候。', 'Syncing class, task, group, and score data. Please wait.') }}</p>
        </div>

        <div v-else-if="workspaceEmptyState" class="score-empty-state">
          <strong>{{ workspaceEmptyState.title }}</strong>
          <p>{{ workspaceEmptyState.description }}</p>
          <button
            v-if="workspaceEmptyState.action"
            class="secondary-btn"
            type="button"
            @click="refreshScoreCenter"
          >
            {{ workspaceEmptyState.action }}
          </button>
        </div>

        <div v-else-if="filters.scoreScope === 'task'" class="score-workbench-layout">
          <aside class="score-target-list">
            <div class="score-target-list-head">
              <div>
                <strong>{{ filters.targetType === 'student' ? tm('scores.studentScore') : tm('scores.groupScore') }}</strong>
                <p class="meta">{{ activeTableRows.length }} 个评分对象</p>
              </div>
              <button class="secondary-btn small" type="button" @click="refreshScoreCenter">刷新</button>
            </div>

            <button
              v-for="item in activeTableRows"
              :key="buildScoreRowId(item)"
              class="score-target-card"
              :class="{ active: isSelectedScoreRow(item) }"
              type="button"
              @click="selectScoreRow(item)"
            >
              <span class="target-title">
                {{ filters.targetType === 'student' ? item.studentName : item.groupName }}
              </span>
              <span v-if="filters.targetType === 'student'" class="meta">
                {{ item.groupName }} · ID {{ item.studentId }}
              </span>
              <span v-else class="meta">
                {{ item.memberCount }} 人 · {{ item.taskName || selectedTask?.name || '-' }}
              </span>
              <span class="target-metrics">
                <span>互评 {{ formatNumber(filters.targetType === 'student' ? item.peerAverageOn100 : item.peerAverage) }}</span>
                <span>教师 {{ formatNumber(filters.targetType === 'student' ? (item.adjustedTeacherScore ?? item.teacherScore) : (item.adjustedTeacherScore ?? item.teacherAverage)) }}</span>
                <span>{{ item.changed ? tm('scores.statusAdjusted') : tm('scores.statusOriginal') }}</span>
              </span>
              <label class="target-score-field" @click.stop>
                <span>教师评分</span>
                <input
                  class="target-score-input"
                  :data-score-row-id="buildScoreRowId(item)"
                  :value="resolveTaskScoreInputValue(item)"
                  type="text"
                  inputmode="decimal"
                  :maxlength="MAX_SCORE_INPUT_LENGTH"
                  autocomplete="off"
                  placeholder="0-100"
                  @input="filters.targetType === 'student' ? updateTaskStudentScore(item, readScoreInput($event)) : updateTaskGroupScore(item, readScoreInput($event))"
                  @keydown.enter.stop="handleScoreEnter(item, $event)"
                />
              </label>
            </button>
          </aside>

          <section class="score-workbench-detail">
            <div v-if="!scoreDetailRow" class="score-empty-state compact-empty">
              <strong>请选择评分对象</strong>
              <p>从左侧选择一个小组或学生后，可以查看提交材料、贡献痕迹，并连续保存评分。</p>
            </div>

            <template v-else>
              <div class="workbench-detail-head">
                <div>
                  <p class="meta">{{ scoreDetailContext.taskName }}</p>
                  <h3>{{ scoreDetailTargetLabel }}</h3>
                </div>
                <div class="workbench-top-actions">
                  <button class="secondary-btn" type="button" :disabled="selectedScoreRowIndex() <= 0" @click="selectAdjacentScoreRow(-1)">
                    上一个
                  </button>
                  <button
                    class="secondary-btn"
                    type="button"
                    :disabled="selectedScoreRowIndex() >= activeTableRows.length - 1"
                    @click="selectAdjacentScoreRow(1)"
                  >
                    下一个
                  </button>
                  <button class="secondary-btn" type="button" @click="saveDrafts">保存草稿</button>
                  <button class="primary-btn" type="button" :disabled="scoreDetailSaving" @click="saveWorkbenchScore()">
                    {{ scoreDetailSaving ? '保存中...' : '保存评分' }}
                  </button>
                  <button class="primary-btn" type="button" :disabled="scoreDetailSaving" @click="saveWorkbenchScore({ next: true })">
                    保存并下一组
                  </button>
                </div>
              </div>

              <div class="workbench-kpis">
                <div>
                  <span class="kpi-help">
                    互评均分
                    <span class="info-dot" tabindex="0" role="button" aria-label="互评均分说明">
                      !
                      <span class="info-tooltip">来自组内成员互评的平均分，仅反映组内成员对贡献的评价，不是组间互评，也不自动计入最终成绩。</span>
                    </span>
                  </span>
                  <strong>{{ formatNumber(filters.targetType === 'student' ? scoreDetailRow.peerAverageOn100 : scoreDetailRow.peerAverage) }}</strong>
                </div>
                <div>
                  <span class="kpi-help">
                    教师评分
                    <span class="info-dot" tabindex="0" role="button" aria-label="教师评分说明">
                      !
                      <span class="info-tooltip">教师对当前任务中该学生或小组录入的一次评分。单次任务只记录一次教师评分，不称为教师均分。</span>
                    </span>
                  </span>
                  <strong>{{ formatNumber(resolveWorkbenchScore(scoreDetailRow)) }}</strong>
                </div>
                <div>
                  <span class="kpi-help">
                    评分差值
                    <span class="info-dot" tabindex="0" role="button" aria-label="评分差值说明">
                      !
                      <span class="info-tooltip">计算公式：教师评分 - 互评均分。正数表示教师评分高于组内互评，负数表示教师评分低于组内互评，仅用于发现评价分歧。</span>
                    </span>
                  </span>
                  <strong>{{ formatScoreDifference(resolveScoreDetailDifference()) }}</strong>
                </div>
                <div>
                  <span>提交状态</span>
                  <strong>{{ scoreDetailContext.submissionStatus === 'none' ? '无提交' : '有记录' }}</strong>
                </div>
              </div>

              <div class="workbench-tabs">
                <button type="button" :class="{ active: scoreWorkbenchTab === 'overview' }" @click="scoreWorkbenchTab = 'overview'">概览</button>
                <button type="button" :class="{ active: scoreWorkbenchTab === 'evidence' }" @click="scoreWorkbenchTab = 'evidence'">提交材料</button>
                <button type="button" :class="{ active: scoreWorkbenchTab === 'trace' }" @click="scoreWorkbenchTab = 'trace'">贡献痕迹</button>
                <button type="button" :class="{ active: scoreWorkbenchTab === 'score' }" @click="scoreWorkbenchTab = 'score'">教师评分</button>
              </div>

              <div class="workbench-tab-body">
                <section v-if="scoreWorkbenchTab === 'overview'" class="workbench-section">
                  <h4>评分提醒</h4>
                  <p>{{ scoreDetailContext.submissionHint || '当前对象暂无异常提示。' }}</p>
                  <div class="evidence-summary-grid">
                    <span>提交时间：{{ scoreDetailContext.submittedAtText || '-' }}</span>
                    <span>附件数量：{{ scoreEvidenceFileCount }}</span>
                    <span>子任务数：{{ scoreDetailContext.subtasks.length }}</span>
                    <span>当前状态：{{ scoreDetailRow.changed ? tm('scores.statusAdjusted') : tm('scores.statusOriginal') }}</span>
                  </div>
                </section>

                <section v-else-if="scoreWorkbenchTab === 'evidence'" class="workbench-section">
                  <div class="section-title-row">
                    <h4>提交材料</h4>
                    <span v-if="scoreDetailLoading" class="meta">加载中...</span>
                  </div>
                  <div v-if="scoreDetailContext.reportHistories.length" class="report-history-list">
                    <article v-for="history in scoreDetailContext.reportHistories" :key="history.id" class="report-history-card">
                      <div class="section-title-row">
                        <div>
                          <strong>{{ t('\u5c0f\u7ec4\u603b\u62a5\u544a', 'Group report') }} V{{ history.versionNo || '-' }}</strong>
                          <p class="meta">{{ history.submittedAtText || t('\u6682\u65e0\u65f6\u95f4\u8bb0\u5f55', 'No time record') }}</p>
                        </div>
                        <span class="tag">{{ history.current ? t('\u5f53\u524d\u7248\u672c', 'Current') : t('\u5386\u53f2\u7248\u672c', 'History') }}</span>
                      </div>
                      <div v-if="history.files.length" class="file-list">
                        <a
                          v-for="file in history.files"
                          :key="file.id"
                          class="file-row"
                          :href="file.url"
                          target="_blank"
                          rel="noopener"
                        >
                          <span>{{ file.name }}</span>
                          <strong>{{ file.kind === 'link' ? t('\u6253\u5f00', 'Open') : t('\u4e0b\u8f7d/\u67e5\u770b', 'Download/View') }}</strong>
                        </a>
                      </div>
                    </article>
                  </div>
                  <template v-else>
                    <div v-if="scoreDetailContext.files.length" class="file-list">
                      <a
                        v-for="file in scoreDetailContext.files"
                        :key="file.id"
                        class="file-row"
                        :href="file.url"
                        target="_blank"
                        rel="noopener"
                      >
                        <span>{{ file.name }}</span>
                        <strong>{{ file.kind === 'link' ? '打开' : '下载/查看' }}</strong>
                      </a>
                    </div>
                    <p v-else class="empty-inline">暂无提交材料。</p>
                  </template>
                </section>

                <section v-else-if="scoreWorkbenchTab === 'trace'" class="workbench-section">
                  <h4>{{ t('\u8d21\u732e\u75d5\u8ff9', 'Contribution traces') }}</h4>
                  <div v-if="scoreDetailContext.subtasks.length" class="trace-list">
                    <button
                      v-for="item in scoreDetailContext.subtasks"
                      :key="item.id"
                      class="trace-row trace-row-button"
                      :class="{ active: selectedTraceSubtaskId === item.id }"
                      type="button"
                      @click="selectTraceSubtask(item)"
                    >
                      <div>
                        <strong>{{ item.name }}</strong>
                        <p class="meta">{{ t('\u8d1f\u8d23\u4eba', 'Owner') }}: {{ item.assigneeName }} · {{ item.submittedAtText || item.updatedAtText || t('\u6682\u65e0\u65f6\u95f4\u8bb0\u5f55', 'No time record') }}</p>
                      </div>
                      <span class="tag">{{ item.statusLabel }}</span>
                    </button>
                  </div>
                  <p v-else class="empty-inline">{{ t('\u6682\u65e0\u53ef\u5c55\u793a\u7684\u5b50\u4efb\u52a1\u75d5\u8ff9\u3002', 'No subtask traces to show.') }}</p>
                </section>

                <section v-else class="workbench-section score-editor-section">
                  <h4>教师评分</h4>
                  <label>
                    <span>评分</span>
                    <input
                      class="workbench-score-input"
                      :value="scoreWorkbenchDraft"
                      type="text"
                      inputmode="decimal"
                      :maxlength="MAX_SCORE_INPUT_LENGTH"
                      autocomplete="off"
                      placeholder="0-100"
                      @input="updateWorkbenchDraft(readScoreInput($event))"
                      @keydown.enter.stop="handleScoreEnter(scoreDetailRow, $event)"
                    />
                  </label>
                  <label>
                    <span>备注</span>
                    <textarea
                      :value="scoreWorkbenchNote"
                      rows="4"
                      placeholder="记录评分依据或课堂观察，不会替代教师判断。"
                      @input="updateWorkbenchNote($event.target.value)"
                    ></textarea>
                  </label>
                </section>
              </div>
            </template>
          </section>
        </div>

        <div v-else-if="false && filters.scoreScope === 'task' && filters.targetType === 'student'" class="table-wrap dense-table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ tm('scores.colStudent') }}</th>
                <th>{{ tm('scores.colPeerScore') }}</th>
                <th>{{ tm('scores.colTeacherScore') }}</th>
                <th>评分差值</th>
                <th>{{ tm('scores.colStatus') }}</th>
                <th>{{ tm('scores.colRemarks') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in pagedTableRows"
                :key="`${item.groupId}-${item.studentId}`"
                class="score-row-clickable"
                @click="handleScoreRowClick(item, $event)"
              >
                <td>
                  <strong>{{ item.studentName }}</strong>
                  <p class="meta">{{ tm('scores.studentIdMeta', { id: item.studentId }) }}</p>
                </td>
                <td>{{ formatNumber(item.peerAverageOn100) }}</td>
                <td>
                  <input
                    :value="resolveTaskScoreInputValue(item)"
                    type="text"
                    inputmode="decimal"
                    :maxlength="MAX_SCORE_INPUT_LENGTH"
                    autocomplete="off"
                    @input="updateTaskStudentScore(item, readScoreInput($event))"
                  />
                </td>
                <td>{{ formatScoreDifference(item.scoreDifference) }}</td>
                <td>
                  <span class="tag">{{ item.changed ? tm('scores.statusAdjusted') : tm('scores.statusOriginal') }}</span>
                </td>
                <td class="remark-cell">
                  <textarea
                    :value="item.note"
                    rows="2"
                    :placeholder="tm('scores.noteTaskStudent')"
                    @input="updateTaskStudentNote(item, $event.target.value)"
                  ></textarea>
                </td>
              </tr>
              <tr v-if="!activeTableRows.length">
                <td colspan="6" class="empty">{{ tm('scores.emptyTaskStudent') }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-else-if="filters.scoreScope === 'task' && filters.targetType === 'group'" class="table-wrap dense-table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ tm('scores.colGroup') }}</th>
                <th>{{ tm('scores.colPeerScore') }}</th>
                <th>{{ tm('scores.colTeacherScore') }}</th>
                <th>评分差值</th>
                <th>{{ tm('scores.colStatus') }}</th>
                <th>{{ tm('scores.colRemarks') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in pagedTableRows"
                :key="item.groupId"
                class="score-row-clickable"
                @click="handleScoreRowClick(item, $event)"
              >
                <td>{{ item.groupName }}</td>
                <td>{{ formatNumber(item.peerAverage) }}</td>
                <td>
                  <input
                    :value="resolveTaskScoreInputValue(item)"
                    type="text"
                    inputmode="decimal"
                    :maxlength="MAX_SCORE_INPUT_LENGTH"
                    autocomplete="off"
                    @input="updateTaskGroupScore(item, readScoreInput($event))"
                  />
                </td>
                <td>{{ formatScoreDifference(item.scoreDifference) }}</td>
                <td>
                  <span class="tag">{{ item.changed ? tm('scores.statusAdjusted') : tm('scores.statusOriginal') }}</span>
                </td>
                <td class="remark-cell">
                  <textarea
                    :value="item.note"
                    rows="2"
                    :placeholder="tm('scores.noteTaskGroup')"
                    @input="updateTaskGroupNote(item, $event.target.value)"
                  ></textarea>
                </td>
              </tr>
              <tr v-if="!activeTableRows.length">
                <td colspan="6" class="empty">{{ tm('scores.emptyTaskGroup') }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-else-if="filters.scoreScope === 'course' && filters.targetType === 'student'" class="table-wrap course-table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ tm('scores.colStudent') }}</th>
                <th>{{ tm('scores.colGroupTrack') }}</th>
                <th>{{ tm('scores.colTaskCoverage') }}</th>
                <th>
                  <span class="th-help">
                    系统参考分
                    <span class="info-dot" tabindex="0" role="button" aria-label="系统参考分说明">
                      !
                      <span class="info-tooltip">该学生本学期已评分任务的教师评分平均分。计算公式：有效教师评分总和 ÷ 有效任务数；空值不参与计算，仅作参考。</span>
                    </span>
                  </span>
                </th>
                <th>
                  <span class="th-help">
                    互评参考
                    <span class="info-dot" tabindex="0" role="button" aria-label="互评参考说明">
                      !
                      <span class="info-tooltip">该学生本学期组内互评结果的平均分。计算公式：有效互评均分总和 ÷ 有效任务数；空值不参与计算，不自动计入课程总分。</span>
                    </span>
                  </span>
                </th>
                <th>
                  <span class="th-help">
                    教师最终评分
                    <span class="info-dot" tabindex="0" role="button" aria-label="教师最终评分说明">
                      !
                      <span class="info-tooltip">教师结合系统参考分、互评参考、提交材料和课堂表现后录入的课程总评结果。</span>
                    </span>
                  </span>
                </th>
                <th>{{ tm('scores.colRemarks') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in pagedTableRows" :key="item.studentId">
                <td>
                  <strong>{{ item.studentName }}</strong>
                  <p class="meta">{{ tm('scores.studentIdMeta', { id: item.studentId }) }}</p>
                </td>
                <td>{{ item.groupLabel }}</td>
                <td>{{ item.taskCount }}</td>
                <td>{{ formatNumber(item.teacherAverage) }}</td>
                <td>{{ formatNumber(item.peerReference) }}</td>
                <td>
                  <div class="course-score-cell">
                    <input
                      class="course-score-input"
                      :data-course-row-id="buildCourseRowId(item)"
                      :value="item.manualScore"
                      type="text"
                      inputmode="decimal"
                      :maxlength="MAX_SCORE_INPUT_LENGTH"
                      autocomplete="off"
                      placeholder="0-100"
                      @input="updateCourseStudentScore(item.studentId, readScoreInput($event))"
                      @keydown.enter.stop="handleCourseScoreEnter(item, $event)"
                    />
                  </div>
                </td>
                <td class="remark-cell">
                  <textarea
                    :value="item.note"
                    rows="2"
                    :placeholder="tm('scores.noteCourseStudent')"
                    @input="updateCourseStudentNote(item.studentId, $event.target.value)"
                  ></textarea>
                </td>
              </tr>
              <tr v-if="!activeTableRows.length">
                <td colspan="7" class="empty">{{ tm('scores.emptyCourseStudent') }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-else-if="filters.scoreScope === 'course' && filters.targetType === 'group'" class="table-wrap dense-table-wrap course-table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ tm('scores.colGroup') }}</th>
                <th>{{ tm('scores.colMemberCount') }}</th>
                <th>{{ tm('scores.colTaskCoverage') }}</th>
                <th>
                  <span class="th-help">
                    系统参考分
                    <span class="info-dot" tabindex="0" role="button" aria-label="系统参考分说明">
                      !
                      <span class="info-tooltip">该小组本学期已评分任务的教师评分平均分。计算公式：有效小组教师评分总和 ÷ 有效任务数；空值不参与计算，仅作参考。</span>
                    </span>
                  </span>
                </th>
                <th>
                  <span class="th-help">
                    教师最终评分
                    <span class="info-dot" tabindex="0" role="button" aria-label="教师最终评分说明">
                      !
                      <span class="info-tooltip">教师结合小组任务表现、总报告、贡献痕迹和其他依据后录入的课程总评结果。</span>
                    </span>
                  </span>
                </th>
                <th>{{ tm('scores.colRemarks') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in pagedTableRows" :key="item.groupId">
                <td>{{ item.groupName }}</td>
                <td>{{ item.memberCount }}</td>
                <td>{{ item.taskCount }}</td>
                <td>{{ formatNumber(item.teacherAverage) }}</td>
                <td>
                  <div class="course-score-cell">
                    <input
                      class="course-score-input"
                      :data-course-row-id="buildCourseRowId(item)"
                      :value="item.manualScore"
                      type="text"
                      inputmode="decimal"
                      :maxlength="MAX_SCORE_INPUT_LENGTH"
                      autocomplete="off"
                      placeholder="0-100"
                      @input="updateCourseGroupScore(item.groupId, readScoreInput($event))"
                      @keydown.enter.stop="handleCourseScoreEnter(item, $event)"
                    />
                  </div>
                </td>
                <td class="remark-cell">
                  <textarea
                    :value="item.note"
                    rows="2"
                    :placeholder="tm('scores.noteCourseGroup')"
                    @input="updateCourseGroupNote(item.groupId, $event.target.value)"
                  ></textarea>
                </td>
              </tr>
              <tr v-if="!activeTableRows.length">
                <td colspan="6" class="empty">{{ tm('scores.emptyCourseGroup') }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        </div>

        <div v-if="!loading && !loadingClasses && !workspaceEmptyState" class="table-pager">
          <label class="page-size-field">
            <span>每页显示</span>
            <select :value="tablePageSize" @change="setTablePageSize($event.target.value)">
              <option v-for="size in TABLE_PAGE_SIZE_OPTIONS" :key="size" :value="size">
                {{ size }} 条
              </option>
            </select>
          </label>
          <button class="secondary-btn" type="button" :disabled="tablePageSafe <= 1 || !activeTableRows.length" @click="prevTablePage">
            {{ tm('scores.prevPage') }}
          </button>
          <span class="meta">
            {{ tm('scores.pageInfo', { page: tablePageSafe, pages: tablePageCount, total: activeTableRows.length }) }}
          </span>
          <button
            class="secondary-btn"
            type="button"
            :disabled="tablePageSafe >= tablePageCount || !activeTableRows.length"
            @click="nextTablePage"
          >
            {{ tm('scores.nextPage') }}
          </button>
        </div>

      </article>
    </section>
    </div>

    <Teleport to="body">
      <div v-if="traceDetailOpen && selectedTraceSubtask" class="score-trace-modal-mask" @click.self="closeTraceDetail">
        <section class="score-trace-modal" role="dialog" aria-modal="true">
          <header class="score-trace-modal-head">
            <div>
              <p class="meta">{{ t('\u8d21\u732e\u75d5\u8ff9', 'Contribution traces') }}</p>
              <h3>{{ selectedTraceSubtask.name }}</h3>
            </div>
            <button class="icon-close-btn" type="button" :aria-label="t('\u5173\u95ed', 'Close')" @click="closeTraceDetail">×</button>
          </header>

          <div class="score-trace-modal-body">
            <div class="evidence-summary-grid">
              <span>{{ t('\u8d1f\u8d23\u4eba', 'Owner') }}: {{ selectedTraceSubtask.assigneeName }}</span>
              <span>{{ t('\u72b6\u6001', 'Status') }}: {{ selectedTraceSubtask.statusLabel }}</span>
              <span>{{ t('\u63d0\u4ea4\u65f6\u95f4', 'Submitted at') }}: {{ selectedTraceSubtask.submittedAtText || '-' }}</span>
              <span>{{ t('\u66f4\u65b0\u65f6\u95f4', 'Updated at') }}: {{ selectedTraceSubtask.updatedAtText || '-' }}</span>
            </div>

            <section class="trace-modal-section">
              <h4>{{ t('\u6587\u672c\u63d0\u4ea4', 'Text submission') }}</h4>
              <p v-if="selectedTraceSubtask.submissionText" class="submission-text">{{ selectedTraceSubtask.submissionText }}</p>
              <p v-else class="empty-inline">{{ t('\u6682\u65e0\u53ef\u76f4\u63a5\u663e\u793a\u7684\u6587\u672c\u63d0\u4ea4\u3002', 'No text submission to display.') }}</p>
            </section>

            <section v-if="selectedTraceSubtask.submissionLink" class="trace-modal-section">
              <h4>{{ t('\u63d0\u4ea4\u94fe\u63a5', 'Submission link') }}</h4>
              <div class="file-action-row">
                <a class="secondary-btn" :href="selectedTraceSubtask.submissionLink" target="_blank" rel="noopener">{{ t('\u6253\u5f00\u94fe\u63a5', 'Open link') }}</a>
              </div>
            </section>

            <section v-if="selectedTraceSubtask.files.length" class="trace-modal-section">
              <h4>{{ t('\u9644\u4ef6', 'Attachments') }}</h4>
              <div class="file-list">
                <a
                  v-for="file in selectedTraceSubtask.files"
                  :key="file.id"
                  class="file-row"
                  :href="file.url"
                  target="_blank"
                  rel="noopener"
                >
                  <span>{{ file.name }}</span>
                  <strong>{{ file.kind === 'link' ? t('\u6253\u5f00', 'Open') : t('\u4e0b\u8f7d/\u67e5\u770b', 'Download/View') }}</strong>
                </a>
              </div>
            </section>
          </div>
        </section>
      </div>
    </Teleport>
  </TeacherSubviewShell>
</template>

<style scoped>
.card {
  background: var(--teacher-surface);
  border-radius: var(--teacher-radius-card);
  box-shadow: var(--teacher-shadow);
}

.panel {
  margin-top: 12px;
  padding: 14px;
}

.mode-panel,
.scope-tabs,
.filters,
.stats-grid,
.workspace-grid,
.group-card-grid,
.mini-stats,
.note-stack {
  display: grid;
  gap: 14px;
}

.scope-tabs {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.filters {
  margin-top: 12px;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.filters > * {
  min-width: 0;
}

.stats-grid {
  margin-top: 18px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.scores-center-shell {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  gap: 12px;
}

.scores-center-shell .mode-panel {
  flex-shrink: 0;
  margin-top: 0;
}

.workspace-grid {
  flex: 1;
  min-height: 0;
  margin-top: 0;
  grid-template-columns: minmax(0, 1fr);
  align-items: stretch;
}

.rules-banner {
  margin-top: 18px;
}

.workspace-panel {
  min-height: 0;
  height: 100%;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  overflow: hidden;
}

.workspace-table-stage {
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.score-workbench-layout {
  min-height: 0;
  flex: 1;
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: 14px;
  overflow: hidden;
}

.score-target-list,
.score-workbench-detail {
  min-height: 0;
  border: 1px solid var(--teacher-divider);
  border-radius: 14px;
  background: var(--teacher-surface);
}

.score-target-list {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.score-target-list-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 14px;
  border-bottom: 1px solid var(--teacher-divider);
  flex-shrink: 0;
}

.score-target-list-head strong,
.target-title,
.workbench-detail-head h3,
.workbench-section h4 {
  color: var(--teacher-text-primary);
}

.score-target-list > .score-target-card {
  margin: 0;
}

.score-target-card {
  width: 100%;
  min-height: 112px;
  padding: 12px 14px;
  border: 0;
  border-bottom: 1px solid var(--teacher-divider);
  background: transparent;
  color: inherit;
  text-align: left;
  display: grid;
  gap: 7px;
  cursor: pointer;
  transition:
    background 0.18s ease,
    box-shadow 0.18s ease;
}

.score-target-card:hover,
.score-target-card.active {
  background: color-mix(in srgb, var(--tt-accent) 8%, var(--teacher-surface));
}

.score-target-card.active {
  box-shadow: inset 3px 0 0 var(--teacher-accent);
}

.target-title {
  font-size: 14px;
  font-weight: 800;
  line-height: 1.3;
}

.target-metrics {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.target-metrics span {
  min-height: 24px;
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--teacher-surface-muted);
  color: var(--teacher-text-secondary);
  font-size: 11px;
  font-weight: 700;
}

.target-score-field {
  width: min(240px, 100%);
  display: grid;
  gap: 6px;
  justify-self: start;
  margin-top: 2px;
}

.target-score-field span {
  color: var(--teacher-text-tertiary);
  font-size: 12px;
  font-weight: 700;
}

.target-score-input {
  width: 100%;
}

.score-workbench-detail {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.workbench-detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 16px;
  border-bottom: 1px solid var(--teacher-divider);
  flex-shrink: 0;
}

.workbench-detail-head h3 {
  margin-top: 4px;
  font-size: 20px;
}

.workbench-top-actions,
.file-action-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.workbench-top-actions {
  justify-content: flex-end;
  max-width: 760px;
}

.workbench-top-actions .secondary-btn,
.workbench-top-actions .primary-btn {
  min-height: 38px;
  white-space: nowrap;
}

.workbench-kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--teacher-divider);
  flex-shrink: 0;
}

.workbench-kpis > div {
  min-height: 72px;
  padding: 10px;
  border-radius: 12px;
  background: var(--teacher-surface-muted);
  display: grid;
  align-content: center;
  gap: 4px;
}

.workbench-kpis span {
  color: var(--teacher-text-tertiary);
  font-size: 12px;
}

.workbench-kpis .kpi-help {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  justify-content: center;
  width: fit-content;
}

.workbench-kpis strong {
  color: var(--teacher-text-primary);
  font-size: 18px;
}

.workbench-tabs {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 16px 0;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.workbench-tabs button {
  height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid var(--teacher-border);
  background: var(--teacher-surface);
  color: var(--teacher-text-secondary);
  cursor: pointer;
  font-weight: 700;
}

.workbench-tabs button.active {
  border-color: var(--tt-accent-border);
  background: var(--tt-accent-soft);
  color: var(--teacher-accent);
}

.workbench-tab-body {
  min-height: 0;
  flex: 1;
  overflow: auto;
  padding: 14px 16px 16px;
}

.workbench-section {
  display: grid;
  gap: 12px;
}

.workbench-section p {
  margin: 0;
  color: var(--teacher-text-secondary);
  line-height: 1.7;
}

.evidence-summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.evidence-summary-grid span,
.empty-inline {
  min-height: 42px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--teacher-surface-muted);
  color: var(--teacher-text-secondary);
  font-size: 13px;
}

.section-title-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.submission-text {
  white-space: pre-wrap;
  padding: 12px;
  border-radius: 12px;
  background: var(--teacher-surface-muted);
}

.file-list,
.trace-list {
  display: grid;
  gap: 10px;
}

.file-row,
.trace-row {
  min-height: 56px;
  padding: 10px 12px;
  border: 1px solid var(--teacher-divider);
  border-radius: 12px;
  background: var(--teacher-surface);
}

.file-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--teacher-text-primary);
  text-decoration: none;
}

.file-row strong {
  color: var(--teacher-accent);
}

.trace-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.trace-row-button {
  width: 100%;
  text-align: left;
  color: var(--teacher-text-primary);
  cursor: pointer;
}

.trace-row-button:hover,
.trace-row-button.active {
  border-color: color-mix(in srgb, var(--teacher-accent) 55%, var(--teacher-divider));
  background: color-mix(in srgb, var(--teacher-accent) 8%, var(--teacher-surface));
}

.report-history-list,
.report-history-card,
.trace-modal-section {
  display: grid;
  gap: 12px;
}

.report-history-card {
  padding: 14px;
  border: 1px solid var(--teacher-divider);
  border-radius: 12px;
  background: var(--teacher-surface);
}

.score-trace-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgb(15 23 42 / 0.42);
}

.score-trace-modal {
  width: min(760px, calc(100vw - 32px));
  max-height: min(720px, calc(100vh - 48px));
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  overflow: hidden;
  border: 1px solid var(--teacher-divider);
  border-radius: 16px;
  background: var(--teacher-surface);
  box-shadow: 0 24px 80px rgb(15 23 42 / 0.24);
}

.score-trace-modal-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--teacher-divider);
}

.score-trace-modal-head h3 {
  margin: 2px 0 0;
  font-size: 20px;
}

.score-trace-modal-body {
  min-height: 0;
  overflow: auto;
  display: grid;
  gap: 16px;
  padding: 18px 20px 20px;
}

.icon-close-btn {
  width: 34px;
  height: 34px;
  border: 1px solid var(--teacher-divider);
  border-radius: 999px;
  background: var(--teacher-surface-muted);
  color: var(--teacher-text-primary);
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
}

.icon-close-btn:hover {
  border-color: color-mix(in srgb, var(--teacher-accent) 55%, var(--teacher-divider));
  color: var(--teacher-accent);
}

.trace-detail-panel {
  display: grid;
  gap: 12px;
  margin-top: 12px;
  padding: 14px;
  border: 1px solid var(--teacher-divider);
  border-radius: 12px;
  background: color-mix(in srgb, var(--teacher-surface-muted) 64%, var(--teacher-surface));
}

.score-editor-section label {
  max-width: 520px;
}

.score-editor-section input {
  width: 180px;
}

.score-editor-section textarea {
  min-height: 120px;
}

.compact-empty {
  min-height: 100%;
  border: 0;
  border-radius: 14px;
}

.score-empty-state {
  flex: 1;
  min-height: 320px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 28px;
  border: 1px dashed var(--teacher-divider);
  border-radius: 16px;
  background: var(--teacher-surface-muted);
  text-align: center;
  color: var(--teacher-text-secondary);
}

.score-empty-state strong {
  color: var(--teacher-text-primary);
  font-size: 18px;
}

.score-empty-state p {
  max-width: 520px;
  margin: 0;
  line-height: 1.7;
}

.table-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  flex-wrap: wrap;
  padding: 12px 4px 4px;
  border-top: 1px solid var(--teacher-divider);
  flex-shrink: 0;
}

.page-size-field {
  display: inline-flex;
  grid-template-columns: none;
  align-items: center;
  gap: 8px;
  color: var(--teacher-text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.page-size-field select {
  width: 96px;
  height: 36px;
}

.group-card-grid {
  margin-top: 14px;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
}

.mini-stats {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.scope-tab,
.stat-card,
.group-score-card,
.rule-pill,
.note-card {
  border: 1px solid var(--teacher-divider);
  border-radius: 16px;
  background: var(--tt-surface);
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease,
    border-color 0.22s ease;
}

.scope-tab {
  padding: 14px 16px;
  display: grid;
  gap: 5px;
  text-align: left;
  cursor: pointer;
  position: relative;
  overflow: hidden;
}

.scope-tab::after {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: transparent;
  transition: background 0.22s ease;
}

.scope-tab.active {
  border-color: var(--tt-accent-border);
  background: color-mix(in srgb, var(--tt-accent) 12%, var(--tt-surface-muted));
  box-shadow:
    0 0 0 2px color-mix(in srgb, var(--tt-accent) 22%, transparent),
    0 18px 34px color-mix(in srgb, var(--tt-accent) 18%, transparent);
  transform: translateY(-2px);
}

.scope-tab.active::after {
  background: var(--teacher-accent);
}

.scope-tab:hover,
.stat-card:hover,
.group-score-card:hover,
.rule-pill:hover,
.note-card:hover {
  transform: translateY(-2px);
  border-color: var(--tt-accent-border);
  box-shadow: var(--tt-shadow-md);
}

.stat-card {
  min-height: 108px;
  padding: 14px;
}

.scope-tab strong,
.value,
.mini-stat strong,
.rule-pill strong {
  color: var(--teacher-text-primary);
}

.scope-tab span,
.desc,
.meta,
.label,
.note,
.empty {
  margin: 0;
  color: var(--teacher-text-tertiary);
  font-size: 12px;
  line-height: 1.5;
}

.scope-tab-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.scope-state {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 26px;
  padding: 0 12px;
  border-radius: 999px;
  background: var(--tt-accent-soft);
  color: var(--teacher-accent);
  border: 1px solid var(--tt-accent-border);
  box-shadow: none;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.panel-head,
.group-card-head,
.section-head,
.rules-banner-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.panel-head {
  padding-bottom: 16px;
  border-bottom: 1px solid var(--teacher-divider);
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 20px;
}

.panel-head .desc {
  margin: 8px 0 0;
  max-width: 52ch;
  line-height: 1.6;
}

.panel-head-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  justify-content: flex-end;
  margin-left: auto;
}

.report-generator {
  min-height: 38px;
  padding: 3px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: 1px solid var(--teacher-border);
  border-radius: 14px;
  background: color-mix(in srgb, var(--teacher-surface-muted) 58%, var(--teacher-surface));
}

.report-inline-title {
  padding: 0 4px 0 8px;
  color: var(--teacher-text-primary);
  font-size: 13px;
  font-weight: 800;
  white-space: nowrap;
}

.report-count-pill {
  min-height: 26px;
  padding: 0 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  border: 1px solid var(--tt-accent-border);
  background: var(--tt-accent-soft);
  color: var(--teacher-accent);
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.report-count-pill.empty {
  border-color: var(--teacher-border);
  background: var(--teacher-surface);
  color: var(--teacher-text-tertiary);
}

.report-type-field {
  min-width: 108px;
  gap: 2px;
}

.report-generator .report-type-field {
  display: block;
}

.report-generator .report-type-field span {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
}

.report-type-field--compact {
  min-width: 84px;
}

.report-type-field select {
  height: 34px;
  border: 1px solid var(--teacher-border);
  border-radius: 10px;
  background: var(--teacher-surface);
  color: var(--teacher-text-primary);
  padding: 0 8px;
  font-size: 13px;
}

.report-generate-btn {
  min-width: 86px;
  height: 34px;
  border-radius: 10px;
  padding: 0 13px;
  font-weight: 800;
}

.export-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.score-save-actions {
  padding-left: 14px;
  border-left: 1px solid var(--teacher-divider);
}

.score-row-clickable .tag,
.scope-tab.active .scope-state,
.toggle-btn.active {
  font-weight: 900;
}

label,
.field {
  display: grid;
  gap: 6px;
  color: var(--teacher-text-secondary);
  font-size: 12px;
}

.filter-toggle {
  display: grid;
  gap: 8px;
  color: var(--teacher-text-secondary);
  font-size: 12px;
  padding: 0;
  margin: 0;
  min-width: 0;
}

.toggle-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  width: 100%;
  padding: 4px;
  border-radius: 12px;
  border: 1px solid var(--teacher-border);
  background: color-mix(in srgb, var(--teacher-border) 28%, var(--teacher-surface));
}

.toggle-btn {
  height: 36px;
  min-width: 0;
  width: 100%;
  padding: 0 10px;
  border-radius: 8px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--teacher-text-secondary);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
  transition:
    border-color 0.22s ease,
    box-shadow 0.22s ease,
    background 0.22s ease,
    color 0.22s ease;
}

.toggle-btn.active {
  border-color: color-mix(in srgb, var(--tt-accent) 35%, transparent);
  background: color-mix(in srgb, var(--tt-accent) 14%, var(--tt-surface));
  color: var(--tt-accent);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--tt-accent) 22%, transparent);
}

.toggle-btn:hover:not(.active) {
  border-color: color-mix(in srgb, var(--tt-accent) 18%, transparent);
  background: color-mix(in srgb, var(--tt-accent) 6%, var(--tt-surface));
}

.toggle-btn:focus-visible,
.scope-tab:focus-visible,
.primary-btn:focus-visible,
.secondary-btn:focus-visible,
.back-btn:focus-visible,
.ghost-btn:focus-visible {
  outline: none;
  box-shadow:
    0 0 0 2px rgba(36, 86, 173, 0.14),
    0 12px 24px rgba(36, 86, 173, 0.08);
}

select,
input,
textarea {
  border: 1px solid var(--teacher-border);
  border-radius: var(--teacher-radius-control);
  background: var(--teacher-surface);
  color: var(--teacher-text-primary);
  padding: 7px 10px;
  font-family: inherit;
  font-size: 13px;
}

select,
input {
  height: 36px;
}

textarea {
  resize: vertical;
}

h3,
h4 {
  margin: 0;
}

.group-score-card,
.rule-pill,
.note-card {
  padding: 12px;
}

.value {
  margin: 6px 0 0;
  font-size: 22px;
  font-weight: 800;
  line-height: 1.1;
}

.table-wrap {
  margin-top: 14px;
  overflow-x: auto;
  overflow-y: auto;
  min-height: 0;
}

.course-table-wrap {
  height: min(58vh, 620px);
  min-height: 380px;
  border: 1px solid var(--teacher-divider);
  border-radius: 14px;
  background: var(--teacher-surface);
}

.course-score-cell {
  max-width: 160px;
  margin: 0 auto;
}

.course-score-input {
  width: 100%;
  min-width: 0;
  text-align: center;
}

.dense-table-wrap {
  flex: 1;
  min-height: 0;
  height: auto;
  max-height: none;
  margin-top: 0;
  padding-right: 4px;
}

table {
  width: 100%;
  border-collapse: collapse;
  min-width: 980px;
}

th,
td {
  border-bottom: 1px solid var(--teacher-divider);
  text-align: center;
  padding: 10px 8px;
  font-size: 13px;
  vertical-align: middle;
}

th {
  color: var(--teacher-text-tertiary);
  font-weight: 600;
}

.course-table-wrap thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  background: var(--teacher-surface);
}

.th-help {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  white-space: nowrap;
}

.info-dot {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--teacher-text-tertiary) 44%, transparent);
  color: var(--teacher-text-tertiary);
  background: var(--teacher-surface);
  font-size: 11px;
  font-weight: 800;
  line-height: 1;
  cursor: help;
  outline: none;
}

.info-dot:focus-visible {
  border-color: var(--tt-accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--tt-accent) 18%, transparent);
}

.info-tooltip {
  position: absolute;
  left: 50%;
  top: calc(100% + 10px);
  z-index: 20;
  width: max-content;
  max-width: 260px;
  padding: 9px 11px;
  border-radius: 8px;
  border: 1px solid var(--teacher-divider);
  background: var(--teacher-surface);
  color: var(--teacher-text);
  box-shadow: 0 14px 36px color-mix(in srgb, var(--teacher-text) 14%, transparent);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.55;
  text-align: left;
  white-space: normal;
  opacity: 0;
  pointer-events: none;
  transform: translate(-50%, -4px);
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.info-tooltip::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 100%;
  width: 8px;
  height: 8px;
  border-left: 1px solid var(--teacher-divider);
  border-top: 1px solid var(--teacher-divider);
  background: var(--teacher-surface);
  transform: translate(-50%, 4px) rotate(45deg);
}

.info-dot:hover .info-tooltip,
.info-dot:focus-visible .info-tooltip {
  opacity: 1;
  transform: translate(-50%, 0);
}

.field.compact {
  align-content: start;
}

.score-note-field textarea {
  min-height: 66px;
}

.tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--tt-accent) 18%, var(--tt-surface-muted));
  color: var(--tt-accent);
  border: 1px solid color-mix(in srgb, var(--tt-accent) 30%, var(--tt-border));
  box-shadow: 0 8px 18px color-mix(in srgb, var(--tt-accent) 10%, transparent);
  font-size: 12px;
  font-weight: 800;
}

.mini-stat {
  min-height: 72px;
  padding: 10px;
  border-radius: 14px;
  background: var(--tt-accent-soft);
  display: grid;
  gap: 4px;
}

.mini-stat.accent {
  background: var(--tt-success-soft);
}

.mini-stat span,
.rule-pill span {
  color: var(--teacher-text-tertiary);
  font-size: 12px;
  font-weight: 700;
}

.mini-stat strong,
.rule-pill strong {
  font-size: 17px;
  font-weight: 800;
}

.rule-strip,
.note-stack {
  margin-top: 10px;
}

.rule-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
  padding: 14px;
  border: 1px solid var(--teacher-divider);
  border-radius: 16px;
  background: var(--tt-surface);
  box-shadow: var(--teacher-shadow);
}

.rule-pill {
  min-height: 64px;
  padding: 8px 10px;
  display: grid;
  gap: 3px;
  align-content: center;
  border: 0;
  background: color-mix(in srgb, var(--tt-accent) 8%, var(--tt-surface-muted));
  box-shadow: none;
}

.rule-input-card {
  min-height: 64px;
  padding: 8px 10px;
  display: grid;
  gap: 4px;
  align-content: center;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 18%, var(--teacher-divider));
  border-radius: 14px;
  background: color-mix(in srgb, var(--tt-accent) 4%, var(--teacher-surface));
}

.rule-input-card input {
  width: 100%;
}

.remark-cell {
  min-width: 280px;
  width: 320px;
}

.remark-cell textarea {
  width: 100%;
  min-width: 260px;
  text-align: center;
}

.score-row-clickable {
  cursor: pointer;
  transition: background 0.18s ease;
}

.score-row-clickable:hover {
  background: color-mix(in srgb, var(--tt-accent) 8%, var(--tt-surface-hover));
}

.score-row-clickable:focus-within {
  background: color-mix(in srgb, var(--tt-accent) 6%, var(--tt-surface-hover));
}

.primary-btn,
.secondary-btn,
.back-btn,
.ghost-btn {
  height: 36px;
  border-radius: 10px;
  padding: 0 12px;
  border: 0;
  cursor: pointer;
  font-weight: 600;
  font-size: 13px;
}

.primary-btn {
  background: var(--teacher-accent);
  color: #fff;
}

.primary-btn:hover {
  filter: brightness(1.02);
  box-shadow: 0 14px 26px rgba(36, 86, 173, 0.18);
}

.secondary-btn {
  background: var(--teacher-surface-muted);
  color: var(--teacher-text-primary);
}

.secondary-btn:hover,
.back-btn:hover,
.ghost-btn:hover {
  border-color: var(--tt-accent-border);
  background: var(--tt-surface-hover);
}

.back-btn,
.ghost-btn {
  background: var(--teacher-surface);
  border: 1px solid var(--teacher-border);
  color: var(--teacher-text-primary);
}

.back-btn.small {
  height: 34px;
  padding: 0 10px;
  font-size: 12px;
}

@media (max-width: 1100px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .rule-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 860px) {
  .scope-tabs,
  .mini-stats {
    grid-template-columns: 1fr;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  .rule-strip {
    grid-template-columns: 1fr;
  }

  .toggle-row {
    grid-template-columns: 1fr;
  }

  .remark-cell {
    min-width: 220px;
    width: 220px;
  }

  .dense-table-wrap {
    min-height: 460px;
    height: min(68vh, 760px);
  }
}

@media (max-width: 760px) {
  .panel-head,
  .group-card-head,
  .section-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .panel-head-actions {
    width: 100%;
    flex-direction: column;
    align-items: stretch;
  }

  .report-generator {
    width: 100%;
    flex-wrap: wrap;
  }

  .export-actions {
    justify-content: flex-start;
  }
}
</style>
