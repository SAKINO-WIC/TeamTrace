<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTeacherLocale } from '../composables/useTeacherLocale'
import { mapWithConcurrency } from '../utils/fetchCache'
import {
  fetchTeacherClassDetail,
  fetchTeacherClassGroups,
  fetchTeacherClassTasks,
  fetchTeacherGroupPeerReviews,
  fetchTeacherGroupSubtasks,
  fetchTeacherTaskAppeals,
  fetchTeacherTaskDetail,
  fetchTeacherTaskGroupProgress,
  fetchTeacherTaskOverview,
  updateTeacherClassTask,
} from '../services/teacher'
import { formatBoolean, formatDateTime, formatTaskStatus } from '../utils/teacher'
import { canPreviewMedia, downloadMediaFile, resolveMediaUrl } from '../utils/mediaUrl'
import { buildTaskAttachments, normalizeTaskAttachments } from '../utils/taskAttachments'
import EmptyState from '../components/common/EmptyState.vue'
import FileUploadZone from '../components/common/FileUploadZone.vue'
import TaskStatusBadge from '../components/common/TaskStatusBadge.vue'
import WorkspaceDialogMask from '../components/common/WorkspaceDialogMask.vue'
import {
  buildTeacherTaskSubviewLocation,
  isTeacherTaskDetailEmptyTaskId,
  isTeacherTaskDetailRoute,
  resolveTeacherTaskDetailEntry,
} from '../utils/teacherTaskNavigation'

const route = useRoute()
const router = useRouter()
const { t, isEn } = useTeacherLocale()

const loading = ref(false)
const entryResolving = ref(false)
const progressLoading = ref(false)
const extendPeerBusy = ref(false)
const extendPeerLocal = ref('')
const editTaskOpen = ref(false)
const editTaskBusy = ref(false)
const message = ref('')
const messageType = ref('info')
const taskDetail = ref(null)
const groups = ref([])
const progressRows = ref([])
const progressLoaded = ref(false)
const progressLoadError = ref('')
const selectedGroupId = ref('')

const classId = computed(() => String(route.params.classId || route.query.classId || ''))
const taskId = computed(() => String(route.params.taskId || route.query.taskId || ''))
const isEmptyTaskEntry = computed(() => isTeacherTaskDetailEmptyTaskId(taskId.value))

const classTasks = ref([])
const classTasksLoading = ref(false)
const classNavMeta = ref({ studentCount: null, groupCount: null, taskCount: null })
const selectedMemberId = ref('')
const memberSubtasks = ref([])
const memberSubtasksLoading = ref(false)
const selectedSubtaskDetail = ref(null)
const peerReviewItems = ref([])
const peerDetailOpen = ref(false)
const peerDetailItem = ref(null)
const peerReviewLoading = ref(false)
const appealItems = ref([])
const appealLoading = ref(false)
const appealCount = ref(0)

const editTaskForm = reactive({
  name: '',
  description: '',
  deadline: '',
  enablePeerReview: true,
  peerReviewOffsetHours: 1,
  attachmentLink: '',
  uploadedFiles: [],
})

async function loadClassTasks() {
  if (!classId.value) {
    classTasks.value = []
    return
  }
  classTasksLoading.value = true
  try {
    const res = await fetchTeacherClassTasks(classId.value)
    const payload = res?.data?.data || []
    classTasks.value = Array.isArray(payload)
      ? payload.map((raw) => ({
          id: String(raw?.taskId ?? raw?.id ?? ''),
          name: raw?.name ?? raw?.taskName ?? '未命名任务',
        }))
      : []
  } catch {
    classTasks.value = []
  } finally {
    classTasksLoading.value = false
  }
}

async function loadClassNavMeta() {
  if (!classId.value) {
    classNavMeta.value = { studentCount: null, groupCount: null, taskCount: null }
    return
  }
  try {
    const [detailRes, tasksRes] = await Promise.allSettled([
      fetchTeacherClassDetail(classId.value),
      fetchTeacherClassTasks(classId.value),
    ])
    const detail = detailRes.status === 'fulfilled' ? detailRes.value?.data?.data || {} : {}
    const tasks = tasksRes.status === 'fulfilled' ? tasksRes.value?.data?.data || [] : []
    classNavMeta.value = {
      studentCount: Number(detail?.studentCount ?? detail?.memberCount ?? 0),
      groupCount: Number(detail?.groupCount ?? 0),
      taskCount: Array.isArray(tasks) ? tasks.length : null,
    }
  } catch {
    classNavMeta.value = { studentCount: null, groupCount: null, taskCount: null }
  }
}

function switchTask(newTaskId) {
  if (newTaskId && newTaskId !== taskId.value && classId.value) {
    router.push({
      path: `/teacher/classes/${classId.value}/tasks/${newTaskId}`,
      query: route.query,
    })
  }
}

function formatSubtaskStatus(value) {
  const map = { 1: '待认领', 2: '进行中', 3: '待审批', 4: '已完成' }
  return map[Number(value)] || '-'
}

function parseSubmissionContent(raw) {
  if (!raw) {
    return { text: '', attachment: '', files: [], link: '' }
  }
  try {
    const parsed = JSON.parse(raw)
    const attachments = Array.isArray(parsed?.attachments)
      ? parsed.attachments
          .map((item, index) => {
            const value = String(item?.value ?? item?.url ?? item?.href ?? item?.link ?? '').trim()
            if (!value) return null
            const typeRaw = String(item?.type || '').trim().toLowerCase()
            const isUpload = value.startsWith('/uploads/') || value.startsWith('uploads/')
            return {
              type: typeRaw || (isUpload ? 'file' : 'link'),
              name: String(item?.name || item?.title || `附件 ${index + 1}`).trim(),
              value,
              size: item?.size ?? null,
            }
          })
          .filter(Boolean)
      : []
    const firstAttachment = attachments[0]
    const attachment =
      parsed?.attachment ||
      (firstAttachment?.value ? String(firstAttachment.value) : '') ||
      (firstAttachment?.name ? String(firstAttachment.name) : '')
    const files = attachments.filter((item) => item.type === 'file' || item.value.startsWith('/uploads/'))
    const linkItem = attachments.find((item) => item.type === 'link' && !item.value.startsWith('/uploads/'))
    return {
      text: String(parsed?.text || parsed?.submissionContent || '').trim(),
      attachment: String(attachment || '').trim(),
      files,
      link: linkItem?.value || '',
    }
  } catch {
    return { text: String(raw).trim(), attachment: '', files: [], link: '' }
  }
}

function normalizeSubtaskHistory(raw, subtaskId) {
  return {
    id: String(raw?.id ?? `${subtaskId}-${raw?.versionNo ?? ''}`),
    versionNo: Number(raw?.versionNo ?? 0),
    submittedAtText: formatDateTime(raw?.submittedAt),
    parsedSubmission: parseSubmissionContent(raw?.submissionContent),
    current: Boolean(raw?.current),
  }
}

function normalizeSubtaskRow(raw) {
  const submission = parseSubmissionContent(raw?.submissionContent)
  const subtaskId = String(raw?.subtaskId ?? raw?.id ?? '')
  return {
    subtaskId,
    name: raw?.name ?? '未命名子任务',
    description: raw?.description ?? '',
    statusCode: Number(raw?.status ?? 0),
    statusLabel: formatSubtaskStatus(raw?.status),
    assigneeId: raw?.assigneeId != null ? String(raw.assigneeId) : '',
    deadline: formatDateTime(raw?.deadline),
    submittedAt: formatDateTime(raw?.submittedAt),
    reviewedAt: formatDateTime(raw?.reviewedAt),
    reviewComment: raw?.reviewComment || '',
    submissionText: submission.text,
    submissionAttachment: submission.attachment,
    parsedSubmission: submission,
    submissionHistories: Array.isArray(raw?.submissionHistories)
      ? raw.submissionHistories.map((history) => normalizeSubtaskHistory(history, subtaskId))
      : [],
  }
}

async function loadMemberSubtasks() {
  if (!selectedGroupId.value || !selectedMemberId.value || !classId.value || !taskId.value) {
    memberSubtasks.value = []
    return
  }
  memberSubtasksLoading.value = true
  try {
    const { data } = await fetchTeacherGroupSubtasks(classId.value, taskId.value, selectedGroupId.value)
    const payload = Array.isArray(data?.data) ? data.data : []
    memberSubtasks.value = payload
      .filter((item) => String(item?.assigneeId ?? '') === selectedMemberId.value)
      .map(normalizeSubtaskRow)
  } catch {
    memberSubtasks.value = []
  } finally {
    memberSubtasksLoading.value = false
  }
}

function selectMember(studentId) {
  selectedMemberId.value = String(studentId || '')
  loadMemberSubtasks()
}

function openSubtaskDetail(item) {
  selectedSubtaskDetail.value = item
}

function closeSubtaskDetail() {
  selectedSubtaskDetail.value = null
}

function displayLinkText(value) {
  return String(value || '').trim() || '链接'
}

function canPreviewSubmissionFile(file) {
  return canPreviewMedia(file?.value || file?.url)
}

function viewSubmissionFile(file) {
  const url = resolveMediaUrl(file?.value || file?.url)
  if (!url) return
  window.open(url, '_blank', 'noopener,noreferrer')
}

async function downloadSubmissionFile(file) {
  try {
    await downloadMediaFile(file?.value || file?.url, file?.name || '附件')
  } catch {
    setMessage(t('文件下载失败，请稍后重试。', 'File download failed. Please try again later.'), 'error')
  }
}

function openPeerDetail(item) {
  peerDetailItem.value = item
  peerDetailOpen.value = true
}

function closePeerDetail() {
  peerDetailOpen.value = false
  peerDetailItem.value = null
}

function openAppealRecord(item) {
  const appealId = String(item?.appealId ?? item?.id ?? '')
  router.push({
    path: '/teacher/appeals',
    query: {
      classId: classId.value,
      taskId: taskId.value,
      ...(appealId ? { appealId } : {}),
      from: 'class-task',
    },
  })
}

function goTaskProgress() {
  router.push(buildTeacherTaskSubviewLocation(classId.value, taskId.value, 'progress', route.query))
}

function goPeerReviews() {
  router.push(buildTeacherTaskSubviewLocation(classId.value, taskId.value, 'peer-reviews', route.query))
}

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

function handleEditTaskUploadError(text) {
  if (text) {
    setMessage(text, 'error')
  }
}

function toWeightPercent(value, fallback = 0) {
  const numeric = Number(value)
  if (Number.isNaN(numeric)) return fallback
  return Math.round(numeric <= 1 ? numeric * 100 : numeric)
}

function resetEditTaskForm() {
  const detail = taskDetail.value
  if (!detail) return
  editTaskForm.name = detail.name || ''
  editTaskForm.description = detail.descriptionValue || ''
  editTaskForm.deadline = detail.deadlineValue ? toDateTimeLocalFromDate(new Date(detail.deadlineValue)) : ''
  editTaskForm.enablePeerReview = Boolean(detail.enablePeerReview)
  editTaskForm.peerReviewOffsetHours = Number(detail.peerReviewOffsetHours || 1)
  editTaskForm.uploadedFiles = detail.attachments
    .filter((file) => file.isFile)
    .map((file) => ({
      id: file.id,
      name: file.name,
      url: file.url,
      size: file.size ?? null,
    }))
  editTaskForm.attachmentLink = detail.attachments.find((file) => file.isLink)?.url || ''
}

function openEditTaskDialog() {
  if (!taskDetail.value) return
  resetEditTaskForm()
  editTaskOpen.value = true
}

function closeEditTaskDialog() {
  if (editTaskBusy.value) return
  editTaskOpen.value = false
}

async function submitEditTask() {
  if (!editTaskForm.name.trim()) {
    setMessage('请输入任务名称。', 'error')
    return
  }
  if (!editTaskForm.deadline) {
    setMessage('请选择任务截止时间。', 'error')
    return
  }
  if (editTaskForm.enablePeerReview && Number(editTaskForm.peerReviewOffsetHours) <= 0) {
    setMessage('互评时长至少为 1 小时。', 'error')
    return
  }
  const attachmentLink = editTaskForm.attachmentLink.trim()
  if (attachmentLink && !/^https?:\/\//i.test(attachmentLink)) {
    setMessage('附件链接必须以 http:// 或 https:// 开头。', 'error')
    return
  }
  const deadlineIso = toIsoFromDateTimeLocal(editTaskForm.deadline)
  if (!deadlineIso) {
    setMessage('任务截止时间格式无效，请重新选择。', 'error')
    return
  }

  editTaskBusy.value = true
  try {
    await updateTeacherClassTask(classId.value, taskId.value, {
      name: editTaskForm.name.trim(),
      description: editTaskForm.description.trim(),
      deadline: deadlineIso,
      enablePeerReview: editTaskForm.enablePeerReview,
      peerReviewOffsetHours: editTaskForm.enablePeerReview ? Number(editTaskForm.peerReviewOffsetHours || 1) : null,
      peerReviewMaxScore: 100,
      peerReviewWeight: taskDetail.value?.peerReviewWeightValue ?? 0.4,
      teacherScoreWeight: taskDetail.value?.teacherScoreWeightValue ?? 0.6,
      attachments: buildTaskAttachments({
        files: editTaskForm.uploadedFiles,
        link: attachmentLink,
      }),
    })
    editTaskOpen.value = false
    await loadTaskWorkspace()
    if (activeTab.value === 'progress' || progressLoaded.value) {
      await loadProgress()
    }
    loadClassTasks().catch(() => {})
    setMessage('任务信息已更新；若已存在子任务，子任务截止时间已同步调整。', 'success')
  } catch (error) {
    setMessage(error.message || '保存任务修改失败，请稍后重试。', 'error')
  } finally {
    editTaskBusy.value = false
  }
}

function viewTaskAttachment(file) {
  const url = resolveMediaUrl(file?.url)
  if (!url) return
  window.open(url, '_blank', 'noopener,noreferrer')
}

async function downloadTaskAttachment(file) {
  try {
    await downloadMediaFile(file?.url, file?.name || '附件')
  } catch {
    setMessage(t('文件下载失败，请稍后重试。', 'File download failed. Please try again later.'), 'error')
  }
}

function canPreviewTaskAttachment(file) {
  return Boolean(file?.isFile && canPreviewMedia(file?.url))
}

function formatPercent(value) {
  const numeric = Number(value)
  return Number.isNaN(numeric) ? '0%' : `${Math.round(numeric)}%`
}

function normalizeWeight(value) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }

  const numeric = Number(value)
  if (Number.isNaN(numeric)) {
    return '-'
  }

  return `${Math.round(numeric <= 1 ? numeric * 100 : numeric)}%`
}

function normalizeTaskDetail(raw) {
  const rawStatus = raw?.taskStatus ?? raw?.status
  return {
    taskId: String(raw?.taskId ?? raw?.id ?? '-'),
    name: raw?.name ?? raw?.taskName ?? '-',
    descriptionValue: raw?.description ?? '',
    description: raw?.description ?? '暂无任务说明',
    statusKey: rawStatus,
    status: formatTaskStatus(rawStatus),
    deadlineValue: raw?.deadline ?? '',
    deadline: formatDateTime(raw?.deadline),
    enablePeerReview: Boolean(raw?.enablePeerReview),
    enablePeerReviewText: formatBoolean(raw?.enablePeerReview, {
      trueLabel: '开启',
      falseLabel: '关闭',
    }),
    peerReviewDeadlineValue: raw?.peerReviewDeadline ?? '',
    peerReviewDeadline: formatDateTime(raw?.peerReviewDeadline),
    peerReviewOffsetHours: raw?.peerReviewOffsetHours ?? 1,
    peerReviewMaxScore: raw?.peerReviewMaxScore ?? 100,
    peerReviewWeightValue: raw?.peerReviewWeight ?? 0.4,
    teacherScoreWeightValue: raw?.teacherScoreWeight ?? 0.6,
    peerReviewWeight: normalizeWeight(raw?.peerReviewWeight),
    teacherScoreWeight: normalizeWeight(raw?.teacherScoreWeight),
    attachments: normalizeTaskAttachments(raw?.attachments),
  }
}

function toDateTimeLocalFromDate(date) {
  if (!(date instanceof Date) || Number.isNaN(date.getTime())) {
    return ''
  }
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hour}:${minute}`
}

function syncExtendPeerLocalDefault() {
  const raw = taskDetail.value?.peerReviewDeadlineValue
  if (!raw || !taskDetail.value?.enablePeerReview) {
    extendPeerLocal.value = ''
    return
  }
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) {
    extendPeerLocal.value = ''
    return
  }
  d.setTime(d.getTime() + 60 * 60 * 1000)
  extendPeerLocal.value = toDateTimeLocalFromDate(d)
}

function toIsoFromDateTimeLocal(localStr) {
  if (!localStr) {
    return ''
  }
  const date = new Date(localStr)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return date.toISOString()
}

/** 与后端 applyExplicitPeerReviewDeadlineExtension 规则对齐，减少无效请求 */
const extendPeerClientError = computed(() => {
  if (!taskDetail.value?.enablePeerReview || !taskDetail.value?.peerReviewDeadlineValue) {
    return ''
  }
  const raw = extendPeerLocal.value?.trim()
  if (!raw) {
    return '请选择新的互评截止时间。'
  }
  const newTs = new Date(raw).getTime()
  if (!Number.isFinite(newTs)) {
    return '时间格式无效，请重新选择。'
  }
  const taskDl = taskDetail.value.deadlineValue
  if (taskDl) {
    const taskTs = new Date(taskDl).getTime()
    if (Number.isFinite(taskTs) && newTs <= taskTs) {
      return '互评截止时间须晚于任务截止时间。'
    }
  }
  if (newTs <= Date.now()) {
    return '互评截止时间须晚于当前时间。'
  }
  const peerBase = taskDetail.value.peerReviewDeadlineValue
  if (peerBase) {
    const peerTs = new Date(peerBase).getTime()
    if (Number.isFinite(peerTs) && newTs <= peerTs) {
      return '互评截止时间仅允许延后（须晚于当前互评截止时间）。'
    }
  }
  return ''
})

async function submitExtendPeerReview() {
  if (!taskDetail.value?.enablePeerReview) {
    return
  }
  if (extendPeerClientError.value) {
    setMessage(extendPeerClientError.value, 'error')
    return
  }
  const iso = toIsoFromDateTimeLocal(extendPeerLocal.value)
  if (!iso) {
    setMessage('请选择新的互评截止时间。', 'error')
    return
  }

  extendPeerBusy.value = true
  try {
    await updateTeacherClassTask(classId.value, taskId.value, { peerReviewDeadline: iso })
    setMessage('互评截止时间已更新（仅允许延后，可重新开放已结束的互评）。', 'success')
    await loadTaskWorkspace()
    if (activeTab.value === 'progress' || progressLoaded.value) {
      await loadProgress()
    }
  } catch (error) {
    setMessage(error.message || '更新互评截止时间失败', 'error')
  } finally {
    extendPeerBusy.value = false
  }
}

function normalizeGroup(raw) {
  const memberIds = Array.isArray(raw?.memberStudentIds) ? raw.memberStudentIds.map((item) => String(item)) : []
  return {
    groupId: String(raw?.groupId ?? raw?.id ?? ''),
    name: raw?.name ?? raw?.groupName ?? '未命名小组',
    leaderName: raw?.leaderName ?? '待设置',
    memberCount: Number(raw?.memberCount ?? memberIds.length ?? 0),
  }
}

function resolveProgressTone(percent) {
  if (percent >= 80) {
    return 'success'
  }
  if (percent >= 40) {
    return 'warning'
  }
  return 'danger'
}

function goTaskManagement() {
  if (!classId.value) {
    return
  }
  router.push(`/teacher/classes/${classId.value}/tasks`)
}

function goPublishTask() {
  if (!classId.value) {
    return
  }
  router.push({
    path: `/teacher/classes/${classId.value}/tasks`,
    query: { publish: '1' },
  })
}

async function ensureTaskDetailRoute() {
  if (!isTeacherTaskDetailRoute(route)) {
    return false
  }

  if (isEmptyTaskEntry.value && classId.value) {
    return true
  }

  if (classId.value && taskId.value) {
    return true
  }

  entryResolving.value = true
  try {
    const location = await resolveTeacherTaskDetailEntry(
      route.params.classId || route.query.classId,
      route.params.taskId || route.query.taskId,
    )
    if (location.path !== route.path) {
      await router.replace(location)
      return false
    }
  } finally {
    entryResolving.value = false
  }

  return Boolean(classId.value && taskId.value)
}

function applyOverviewPayload(d) {
  const taskRaw = d?.task || d?.taskDetail || {}
  taskDetail.value = normalizeTaskDetail(taskRaw)
  groups.value = Array.isArray(d?.groups) ? d.groups.map(normalizeGroup) : []

  const tasksPayload = d?.classTasks
  if (Array.isArray(tasksPayload)) {
    classTasks.value = tasksPayload.map((raw) => ({
      id: String(raw?.taskId ?? raw?.id ?? ''),
      name: raw?.name ?? raw?.taskName ?? '未命名任务',
    }))
  }

  const detail = d?.classDetail || {}
  classNavMeta.value = {
    studentCount: Number(detail?.studentCount ?? detail?.memberCount ?? 0),
    groupCount: Number(detail?.groupCount ?? groups.value.length),
    taskCount: classTasks.value.length || null,
  }

  const appeals = Array.isArray(d?.appeals) ? d.appeals : []
  appealCount.value = appeals.filter((item) => Number(item?.status) === 0).length

  if (!groups.value.some((item) => item.groupId === selectedGroupId.value)) {
    selectedGroupId.value = groups.value[0]?.groupId || ''
  }
  syncExtendPeerLocalDefault()
  setMessage('')
}

async function loadTaskWorkspace() {
  if (!classId.value || !taskId.value) {
    return
  }

  loading.value = true
  progressLoadError.value = ''
  try {
    try {
      const overviewRes = await fetchTeacherTaskOverview(classId.value, taskId.value)
      const envelope = overviewRes?.data
      if (envelope?.success !== false && envelope?.data) {
        applyOverviewPayload(envelope.data)
        return
      }
    } catch (_) {
      /* fall through */
    }

    // Fallback: 2 parallel API calls
    const [{ data: detailResponse }, { data: groupsResponse }] = await Promise.all([
      fetchTeacherTaskDetail(classId.value, taskId.value),
      fetchTeacherClassGroups(classId.value),
    ])

    taskDetail.value = normalizeTaskDetail(detailResponse?.data || {})
    groups.value = Array.isArray(groupsResponse?.data) ? groupsResponse.data.map(normalizeGroup) : []

    if (!groups.value.some((item) => item.groupId === selectedGroupId.value)) {
      selectedGroupId.value = groups.value[0]?.groupId || ''
    }

    syncExtendPeerLocalDefault()
    setMessage('')
    await Promise.all([loadClassTasks(), loadClassNavMeta()])
  } catch (error) {
    taskDetail.value = null
    groups.value = []
    progressRows.value = []
    progressLoaded.value = false
    progressLoadError.value = ''
    selectedGroupId.value = ''
    setMessage(error.message || '加载任务详情失败', 'error')
  } finally {
    loading.value = false
  }
}

async function loadProgress() {
  if (!classId.value || !taskId.value) {
    progressRows.value = []
    progressLoaded.value = true
    progressLoadError.value = '任务详情缺少参数，无法加载进度。'
    return
  }
  if (!groups.value.length) {
    progressRows.value = []
    progressLoaded.value = true
    progressLoadError.value = ''
    return
  }

  progressLoading.value = true
  progressLoadError.value = ''
  try {
    const { data: batchData } = await fetchTeacherTaskGroupProgress(classId.value, taskId.value)
    const batchMap = batchData?.data || {}
    const rows = groups.value.map((group) => {
      const payload = batchMap[group.groupId] || batchMap[group.id] || {}
      return {
        groupId: group.groupId,
        groupName: group.name,
        leaderName: group.leaderName,
        memberCount: group.memberCount,
        groupTotalSubtasks: Number(payload?.groupTotalSubtasks ?? 0),
        groupClaimedSubtasks: Number(payload?.groupClaimedSubtasks ?? 0),
        groupCompletedSubtasks: Number(payload?.groupCompletedSubtasks ?? 0),
        groupPendingSubtasks: Number(payload?.groupPendingSubtasks ?? 0),
        groupProgressPercent: Number(payload?.groupProgressPercent ?? 0),
        members: Array.isArray(payload?.members)
          ? payload.members.map((item) => ({
              studentId: String(item?.studentId ?? ''),
              studentName: item?.studentName ?? `学生 ${item?.studentId ?? '-'}`,
              claimedSubtasks: Number(item?.claimedSubtasks ?? 0),
              completedSubtasks: Number(item?.completedSubtasks ?? 0),
              pendingSubtasks: Math.max(
                Number(item?.claimedSubtasks ?? 0) - Number(item?.completedSubtasks ?? 0),
                0,
              ),
              progressPercent: Number(item?.progressPercent ?? 0),
            }))
          : [],
      }
    })
    progressRows.value = rows
    progressLoaded.value = true
    if (!rows.some((item) => item.groupId === selectedGroupId.value)) {
      selectedGroupId.value = rows[0]?.groupId || ''
    }
  } catch (error) {
    progressLoaded.value = true
    progressLoadError.value = error.message || '加载任务进度失败'
    if (!progressRows.value.length) {
      progressRows.value = []
    }
  } finally {
    progressLoading.value = false
  }
}

const selectedGroupProgress = computed(() => {
  return progressRows.value.find((item) => item.groupId === selectedGroupId.value) || null
})

async function loadPeerReviews() {
  peerReviewLoading.value = true
  try {
    if (!groups.value.length) {
      peerReviewItems.value = []
      return
    }

    const results = await mapWithConcurrency(
      groups.value,
      async (group) => {
        const res = await fetchTeacherGroupPeerReviews(classId.value, taskId.value, group.groupId)
        const items = Array.isArray(res?.data?.data) ? res.data.data : []
        return items.map((item) => ({
          ...item,
          groupId: group.groupId,
          groupName: group.name || `小组 ${group.groupId}`,
        }))
      },
      6,
    )

    peerReviewItems.value = results.flat()
  } catch {
    peerReviewItems.value = []
  } finally {
    peerReviewLoading.value = false
  }
}

async function loadAppeals() {
  if (!classId.value || !taskId.value) {
    appealItems.value = []
    appealCount.value = 0
    return
  }
  appealLoading.value = true
  try {
    const res = await fetchTeacherTaskAppeals(classId.value, taskId.value)
    appealItems.value = res?.data?.data || []
    appealCount.value = appealItems.value.filter((a) => a?.status === 0).length
  } catch {
    appealItems.value = []
  } finally {
    appealLoading.value = false
  }
}

const isDeadlinePassed = computed(() => {
  const deadlineValue = taskDetail.value?.deadlineValue
  if (!deadlineValue) {
    return false
  }

  const timestamp = new Date(deadlineValue).getTime()
  return Number.isFinite(timestamp) ? timestamp < Date.now() : false
})

const overallMetrics = computed(() => {
  const fallbackGroups = groups.value.length
  const fallbackMembers = groups.value.reduce((sum, item) => sum + Number(item.memberCount || 0), 0)
  const metrics = progressRows.value.reduce(
    (accumulator, item) => {
      accumulator.groups += 1
      accumulator.members += item.memberCount
      accumulator.total += item.groupTotalSubtasks
      accumulator.claimed += item.groupClaimedSubtasks
      accumulator.completed += item.groupCompletedSubtasks
      accumulator.pending += item.groupPendingSubtasks
      return accumulator
    },
    { groups: 0, members: 0, total: 0, claimed: 0, completed: 0, pending: 0 },
  )

  return {
    ...metrics,
    groups: metrics.groups || fallbackGroups,
    members: metrics.members || fallbackMembers,
    progressPercent: metrics.total ? Math.round((metrics.completed / metrics.total) * 100) : 0,
  }
})

const taskInfoRows = computed(() => {
  if (!taskDetail.value) {
    return []
  }

  const rows = [
    { label: '任务编号', value: taskDetail.value.taskId },
    {
      label: '任务状态',
      value: taskDetail.value.status,
      type: 'task-status',
      statusKey: taskDetail.value.statusKey,
    },
    { label: '截止时间', value: taskDetail.value.deadline },
    { label: '互评开关', value: taskDetail.value.enablePeerReviewText },
  ]
  rows.push({ label: '评分口径', value: '独立记录 / 参考汇总' })
  return rows
})

const taskOverviewMetrics = computed(() => {
  const total = overallMetrics.value.total
  const completed = overallMetrics.value.completed
  const active = isDeadlinePassed.value ? 0 : Math.max(total - completed, 0)
  const overdue = isDeadlinePassed.value ? Math.max(total - completed, 0) : 0
  const claimed = overallMetrics.value.claimed

  return {
    total,
    completed,
    active,
    overdue,
    claimed,
    unclaimed: Math.max(total - claimed, 0),
    completedPercent: total ? Math.round((completed / total) * 100) : 0,
    activePercent: total ? Math.round((active / total) * 100) : 0,
    overduePercent: total ? Math.round((overdue / total) * 100) : 0,
  }
})

const classOverviewCards = computed(() => {
  return [
    { label: '班级总人数', value: overallMetrics.value.members },
    { label: '总小组数', value: overallMetrics.value.groups },
    { label: '总子任务数', value: taskOverviewMetrics.value.total },
    { label: '已认领子任务', value: taskOverviewMetrics.value.claimed },
  ]
})

const groupProgressChart = computed(() => {
  return progressRows.value
    .slice()
    .sort((left, right) => right.groupProgressPercent - left.groupProgressPercent)
    .map((item) => ({
      ...item,
      activeCount: isDeadlinePassed.value ? 0 : Math.max(item.groupTotalSubtasks - item.groupCompletedSubtasks, 0),
      overdueCount: isDeadlinePassed.value ? Math.max(item.groupTotalSubtasks - item.groupCompletedSubtasks, 0) : 0,
      progressText: formatPercent(item.groupProgressPercent),
      width: `${Math.max(8, Math.round(item.groupProgressPercent))}%`,
      tone: resolveProgressTone(item.groupProgressPercent),
    }))
})

const groupSummary = computed(() => {
  const rows = groupProgressChart.value
  if (!rows.length) {
    return {
      count: 0,
      average: 0,
      bestName: '-',
      bestPercent: 0,
      slowName: '-',
      slowPercent: 0,
    }
  }

  const totalPercent = rows.reduce((sum, item) => sum + item.groupProgressPercent, 0)
  return {
    count: rows.length,
    average: Math.round(totalPercent / rows.length),
    bestName: rows[0]?.groupName || '-',
    bestPercent: Math.round(rows[0]?.groupProgressPercent || 0),
    slowName: rows[rows.length - 1]?.groupName || '-',
    slowPercent: Math.round(rows[rows.length - 1]?.groupProgressPercent || 0),
  }
})

const groupBuckets = computed(() => {
  const source = groupProgressChart.value
  return {
    high: source.filter((item) => item.groupProgressPercent >= 80).length,
    middle: source.filter((item) => item.groupProgressPercent >= 40 && item.groupProgressPercent < 80).length,
    low: source.filter((item) => item.groupProgressPercent < 40).length,
  }
})

const groupWorkQueue = computed(() => {
  const rank = { danger: 0, warning: 1, success: 2 }
  return groupProgressChart.value
    .slice()
    .sort((left, right) => {
      if (rank[left.tone] !== rank[right.tone]) {
        return rank[left.tone] - rank[right.tone]
      }
      return left.groupProgressPercent - right.groupProgressPercent
    })
    .map((item, index) => ({
      ...item,
      queueIndex: index + 1,
      statusLabel:
        item.groupProgressPercent >= 80
          ? '推进良好'
          : item.groupProgressPercent >= 40
            ? '正常推进'
            : '优先跟进',
    }))
})

const progressOverviewStats = computed(() => {
  return [
    { label: '优先跟进小组', value: groupBuckets.value.low, tone: 'danger' },
    { label: '正常推进小组', value: groupBuckets.value.middle, tone: 'warning' },
    { label: '推进良好小组', value: groupBuckets.value.high, tone: 'success' },
    { label: '全班平均进度', value: `${groupSummary.value.average}%`, tone: 'neutral' },
  ]
})

const peerReviewStats = computed(() => {
  const scores = peerReviewItems.value
    .map((item) => Number(item.score))
    .filter((item) => !Number.isNaN(item))
  const groupIds = new Set(peerReviewItems.value.map((item) => String(item.groupId || '')).filter(Boolean))
  const lowScoreCount = scores.filter((item) => item < 60).length
  return [
    { label: '互评记录', value: peerReviewItems.value.length },
    { label: '涉及小组', value: groupIds.size },
    {
      label: '平均互评分',
      value: scores.length
        ? (scores.reduce((sum, item) => sum + item, 0) / scores.length).toFixed(1).replace(/\.0$/, '')
        : '-',
    },
    { label: '低分记录', value: lowScoreCount },
  ]
})

const studentRows = computed(() => {
  return progressRows.value
    .flatMap((group) =>
      group.members.map((member) => ({
        ...member,
        groupId: group.groupId,
        groupName: group.groupName,
        width: `${Math.max(8, Math.round(member.progressPercent))}%`,
        progressText: formatPercent(member.progressPercent),
        tone: resolveProgressTone(member.progressPercent),
      })),
    )
    .sort((left, right) => right.progressPercent - left.progressPercent)
})

const selectedGroupMembers = computed(() => {
  return (selectedGroupProgress.value?.members || [])
    .slice()
    .sort((left, right) => right.progressPercent - left.progressPercent)
    .map((item) => ({
      ...item,
      progressText: formatPercent(item.progressPercent),
      width: `${Math.max(8, Math.round(item.progressPercent))}%`,
      tone: resolveProgressTone(item.progressPercent),
    }))
})

function buildStudentSummary(rows) {
  const count = rows.length
  const high = rows.filter((item) => item.progressPercent >= 80).length
  const middle = rows.filter((item) => item.progressPercent >= 40 && item.progressPercent < 80).length
  const low = rows.filter((item) => item.progressPercent < 40).length
  const average = count ? Math.round(rows.reduce((sum, item) => sum + item.progressPercent, 0) / count) : 0

  return { count, high, middle, low, average }
}

const classStudentSummary = computed(() => buildStudentSummary(studentRows.value))
const selectedStudentSummary = computed(() => buildStudentSummary(selectedGroupMembers.value))
const currentMemberSummary = computed(() => {
  return selectedGroupMembers.value.length ? selectedStudentSummary.value : classStudentSummary.value
})

const selectedGroupFocusMembers = computed(() => {
  return selectedGroupMembers.value
    .filter((item) => item.progressPercent < 80 && item.pendingSubtasks > 0)
    .sort((left, right) => {
      if (left.progressPercent !== right.progressPercent) {
        return left.progressPercent - right.progressPercent
      }
      return right.pendingSubtasks - left.pendingSubtasks
    })
})

const selectedGroupMetrics = computed(() => {
  const current = selectedGroupProgress.value
  if (!current) {
    return {
      progressPercent: 0,
      completed: 0,
      active: 0,
      overdue: 0,
      total: 0,
      claimed: 0,
      unclaimed: 0,
    }
  }

  return {
    progressPercent: Math.round(current.groupProgressPercent || 0),
    completed: current.groupCompletedSubtasks,
    active: isDeadlinePassed.value ? 0 : Math.max(current.groupTotalSubtasks - current.groupCompletedSubtasks, 0),
    overdue: isDeadlinePassed.value ? Math.max(current.groupTotalSubtasks - current.groupCompletedSubtasks, 0) : 0,
    total: current.groupTotalSubtasks,
    claimed: current.groupClaimedSubtasks,
    unclaimed: Math.max(current.groupTotalSubtasks - current.groupClaimedSubtasks, 0),
  }
})

const selectedGroupMeta = computed(() => {
  const current = selectedGroupProgress.value
  if (!current) {
    return '请选择小组查看组员数据'
  }

  return `${current.leaderName} · ${current.memberCount} 人 · 已认领 ${selectedGroupMetrics.value.claimed} / 未认领 ${selectedGroupMetrics.value.unclaimed}`
})

const selectedGroupInfoCards = computed(() => {
  const activeLabel = isDeadlinePassed.value ? '逾期未完成' : '进行中'
  const activeValue = isDeadlinePassed.value ? selectedGroupMetrics.value.overdue : selectedGroupMetrics.value.active

  return [
    {
      label: '当前进度',
      value: `${selectedGroupMetrics.value.progressPercent}%`,
    },
    {
      label: '总子任务数',
      value: selectedGroupMetrics.value.total,
    },
    {
      label: '已完成',
      value: selectedGroupMetrics.value.completed,
      tone: 'success',
    },
    {
      label: activeLabel,
      value: activeValue,
      tone: isDeadlinePassed.value ? 'warning' : '',
    },
  ]
})

const selectedGroupStatus = computed(() => {
  const progress = selectedGroupMetrics.value.progressPercent
  if (!selectedGroupProgress.value) {
    return { label: '等待选择', tone: 'neutral' }
  }
  if (progress >= 80) {
    return { label: '推进良好', tone: 'success' }
  }
  if (progress >= 40) {
    return { label: '正常推进', tone: 'warning' }
  }
  return { label: '优先跟进', tone: 'danger' }
})

const selectedGroupActionText = computed(() => {
  if (!selectedGroupProgress.value) {
    return '从左侧选择一个小组，查看组员认领、完成和待完成情况。'
  }
  if (selectedGroupMetrics.value.progressPercent >= 80) {
    return '当前小组接近完成，主要检查剩余待完成子任务和提交质量。'
  }
  if (selectedGroupMetrics.value.progressPercent >= 40) {
    return '当前小组仍在推进，建议确认待完成成员是否需要重新分工。'
  }
  return '当前小组进度偏低，建议先联系组长并核对未完成成员的任务认领情况。'
})

function selectGroup(groupId) {
  selectedGroupId.value = groupId
  selectedMemberId.value = ''
  memberSubtasks.value = []
}

const activeTab = ref('overview')
const activeTabUsesBoard = computed(() => activeTab.value === 'overview' || activeTab.value === 'progress')

// 进度页自动轮询刷新（每30秒）
let progressPollTimer = null
function startProgressPolling() {
  stopProgressPolling()
  progressPollTimer = setInterval(() => {
    if (activeTab.value === 'progress' && !progressLoading.value) {
      loadProgress()
    }
  }, 30000)
}
function stopProgressPolling() {
  if (progressPollTimer) {
    clearInterval(progressPollTimer)
    progressPollTimer = null
  }
}

function setTab(tab) {
  activeTab.value = tab
  if (tab === 'progress') {
    if (!progressLoaded.value && !progressLoading.value) loadProgress()
    startProgressPolling()
  } else {
    stopProgressPolling()
  }
  if (tab === 'peer-reviews' && peerReviewItems.value.length === 0) loadPeerReviews()
  if (tab === 'appeals' && appealItems.value.length === 0) loadAppeals()
}

async function refreshCurrentTaskView() {
  if (activeTab.value === 'progress') {
    await loadTaskWorkspace()
    await loadProgress()
    return
  }

  if (activeTab.value === 'appeals') {
    await Promise.all([loadTaskWorkspace(), loadAppealCount()])
    return
  }

  await loadTaskWorkspace()
}

const selectedMemberSummary = computed(() => {
  return selectedGroupMembers.value.find((item) => String(item.studentId) === selectedMemberId.value) || null
})

watch(
  () => [classId.value, taskId.value, route.path],
  async ([nextClassId, nextTaskId], previous) => {
    if (!isTeacherTaskDetailRoute(route)) {
      return
    }

    const ready = await ensureTaskDetailRoute()
    if (!ready) {
      return
    }

    if (isEmptyTaskEntry.value) {
      loading.value = false
      taskDetail.value = null
      await loadClassTasks()
      return
    }

    const previousClassId = previous?.[0]
    const classChanged = Boolean(previousClassId && previousClassId !== nextClassId)

    selectedGroupId.value = ''
    selectedMemberId.value = ''
    memberSubtasks.value = []
    progressRows.value = []
    progressLoaded.value = false
    progressLoadError.value = ''
    peerReviewItems.value = []
    appealItems.value = []

    if (classChanged || !previous) {
      activeTab.value = 'overview'
    }

    await loadTaskWorkspace()

    if (activeTab.value === 'progress') {
      await loadProgress()
    } else if (activeTab.value === 'peer-reviews') {
      await loadPeerReviews()
    } else if (activeTab.value === 'appeals') {
      await loadAppeals()
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => { stopProgressPolling() })
</script>

<template>
  <div class="teacher-page task-detail-page">
    <div v-if="entryResolving" class="card entry-loading">{{ t('正在进入任务详情…', 'Opening task details…') }}</div>

    <section v-else-if="isEmptyTaskEntry" class="card empty-guide">
      <EmptyState
        icon="task"
        :title="t('发布第一个任务', 'Publish your first task')"
        :description="
          t(
            '发布第一个任务后，可在此查看任务详情、进度与申诉。',
            'After publishing your first task, you can view details, progress, and appeals here.',
          )
        "
        :action-label="t('发布第一个任务', 'Publish first task')"
        :secondary-label="t('返回任务管理', 'Back to tasks')"
        @action="goPublishTask"
        @secondary="goTaskManagement"
      />
    </section>

    <template v-else>
    <header class="card topbar task-hero task-detail-head">
      <div class="title-block">
        <p class="eyebrow">{{ t('班级任务详情', 'Class task details') }}</p>
        <h2>{{ taskDetail?.name || t('任务详情', 'Task details') }}</h2>
      </div>
      <label class="task-switcher-wrap">
        <span class="switcher-label">{{ t('切换任务', 'Switch task') }}</span>
        <select
          class="task-switcher"
          :disabled="classTasksLoading || classTasks.length === 0"
          :value="taskId"
          @change="switchTask($event.target.value)"
        >
          <option v-if="classTasks.length === 0" value="" disabled>{{ t('暂无任务', 'No tasks') }}</option>
          <option v-for="taskItem in classTasks" :key="taskItem.id" :value="taskItem.id">{{ taskItem.name }}</option>
        </select>
      </label>
    </header>

    <!-- ═══ TAB BAR ═══ -->
    <nav class="task-tabs">
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'overview' }"
        type="button"
        @click="setTab('overview')"
      >{{ t('概览', 'Overview') }}</button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'progress' }"
        type="button"
        @click="setTab('progress')"
      >{{ t('进度', 'Progress') }}</button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'peer-reviews' }"
        type="button"
        @click="setTab('peer-reviews')"
      >{{ t('互评', 'Peer review') }}</button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'appeals' }"
        type="button"
        @click="setTab('appeals')"
      >
        {{ t('申诉', 'Appeals') }}
        <span v-if="appealCount > 0" class="tab-badge">{{ appealCount }}</span>
      </button>
    </nav>

    <p v-if="message" class="message task-detail-message" :class="messageType">{{ message }}</p>

    <div v-if="taskDetail" class="task-detail-stage">
      <section v-if="activeTabUsesBoard" class="board-grid" :class="{ 'board-grid-single': activeTab === 'overview' }">
        <article v-if="activeTab === 'overview'" class="card panel-card task-card">
          <div class="panel-headline">
            <div>
              <p class="stage-kicker">{{ t('任务详情', 'Task details') }}</p>
              <h3>{{ taskDetail.name }}</h3>
            </div>
            <div class="stage-badges">
              <button class="secondary-btn task-edit-btn" type="button" @click="openEditTaskDialog">
                {{ t('编辑任务', 'Edit task') }}
              </button>
              <TaskStatusBadge :status="taskDetail.statusKey" :label="taskDetail.status" />
              <span class="badge muted">{{ taskDetail.deadline }}</span>
            </div>
          </div>

          <div class="task-meta-grid">
            <article v-for="item in taskInfoRows" :key="item.label" class="meta-item">
              <span>{{ item.label }}</span>
              <TaskStatusBadge
                v-if="item.type === 'task-status'"
                :status="item.statusKey"
                :label="item.value"
                size="lg"
              />
              <strong v-else>{{ item.value }}</strong>
            </article>
          </div>

          <article class="task-desc">
            <span>任务说明</span>
            <p>{{ taskDetail.description || '暂无说明' }}</p>
          </article>

          <article v-if="taskDetail.attachments?.length" class="task-desc task-attachments">
            <span>{{ t('任务附件', 'Task attachments') }}</span>
            <div class="task-attachment-list">
              <div v-for="file in taskDetail.attachments" :key="file.id" class="task-attachment-row">
                <div>
                  <strong>{{ file.name }}</strong>
                  <small>{{ file.isFile ? t('文件资料', 'File') : t('外部链接', 'Link') }}</small>
                </div>
                <div class="task-attachment-actions">
                  <button
                    v-if="canPreviewTaskAttachment(file)"
                    class="secondary-btn"
                    type="button"
                    @click="viewTaskAttachment(file)"
                  >
                    {{ t('查看', 'View') }}
                  </button>
                  <button
                    v-else-if="file.isFile"
                    class="secondary-btn"
                    type="button"
                    @click="downloadTaskAttachment(file)"
                  >
                    {{ t('下载', 'Download') }}
                  </button>
                  <button
                    v-else
                    class="secondary-btn"
                    type="button"
                    @click="viewTaskAttachment(file)"
                  >
                    {{ t('打开', 'Open') }}
                  </button>
                </div>
              </div>
            </div>
          </article>

          <div
            v-if="taskDetail.enablePeerReview && taskDetail.peerReviewDeadlineValue"
            class="peer-extend-block"
          >
            <p class="peer-extend-title">延长互评截止</p>
            <div class="peer-extend-row">
              <label class="peer-extend-label" for="peer-extend-dt">新互评截止时间</label>
              <input
                id="peer-extend-dt"
                v-model="extendPeerLocal"
                class="peer-extend-input"
                type="datetime-local"
              />
              <button
                class="secondary-btn"
                type="button"
                :disabled="extendPeerBusy || Boolean(extendPeerClientError)"
                @click="submitExtendPeerReview"
              >
                {{ extendPeerBusy ? '提交中...' : '保存延后' }}
              </button>
            </div>
            <p v-if="extendPeerClientError" class="peer-extend-inline-error">{{ extendPeerClientError }}</p>
          </div>
        </article>


        <article v-if="activeTab === 'progress'" class="card panel-card progress-workspace-card">
          <p v-if="progressLoadError" class="inline-error">
            {{ progressLoadError }}
            <button class="text-btn" type="button" :disabled="progressLoading" @click="loadProgress">重试</button>
          </p>

          <div v-if="progressLoading && !progressLoaded" class="progress-loading-state">
            <span></span>
            <span></span>
            <span></span>
          </div>

          <div v-else class="progress-three-columns">
            <section class="progress-column queue-column">
              <div class="column-head">
                <h4>小组处理队列</h4>
                <span class="meta">{{ groupSummary.count }} 组</span>
              </div>
              <div class="column-body">
                <div class="rail-list">
                  <button
                    v-for="item in groupWorkQueue"
                    :key="item.groupId"
                    class="group-row"
                    :class="[item.tone, { active: item.groupId === selectedGroupId }]"
                    type="button"
                    @click="selectGroup(item.groupId)"
                  >
                    <div class="group-row-head">
                      <span class="queue-no">{{ item.queueIndex }}</span>
                      <div>
                        <p class="group-name">{{ item.groupName }}</p>
                        <p class="meta">{{ item.statusLabel }} · {{ item.leaderName }} · {{ item.memberCount }} 人</p>
                      </div>
                      <strong>{{ item.progressText }}</strong>
                    </div>
                    <div class="progress-track">
                      <span class="progress-bar" :class="item.tone" :style="{ width: item.width }"></span>
                    </div>
                    <div class="group-row-foot">
                      <span>完成 {{ item.groupCompletedSubtasks }} / {{ item.groupTotalSubtasks }}</span>
                      <span>{{ isDeadlinePassed ? '逾期' : '待完成' }} {{ item.overdueCount || item.activeCount }}</span>
                    </div>
                  </button>
                  <p v-if="!groupWorkQueue.length" class="empty">当前任务暂无小组进度数据。</p>
                </div>
              </div>
            </section>

            <section class="progress-column member-column">
              <div class="column-head">
                <h4>组员任务状态</h4>
                <span class="meta">{{ selectedGroupMembers.length }} 人</span>
              </div>
              <div v-if="!selectedGroupId" class="column-body column-empty">请先从左侧选择一个小组</div>
              <div v-else class="column-body">
                <div class="member-pick-list">
                  <button
                    v-for="item in selectedGroupMembers"
                    :key="`${selectedGroupId}-${item.studentId}`"
                    class="member-pick-row"
                    :class="[item.tone, { active: item.studentId === selectedMemberId }]"
                    type="button"
                    @click="selectMember(item.studentId)"
                  >
                    <div class="member-pick-head">
                      <div>
                        <p class="student-name">{{ item.studentName }}</p>
                        <p class="meta">学号 {{ item.studentId }}</p>
                      </div>
                      <strong>{{ item.progressText }}</strong>
                    </div>
                    <div class="progress-track compact">
                      <span class="progress-bar" :class="item.tone" :style="{ width: item.width }"></span>
                    </div>
                    <div class="member-pick-foot">
                      <span>认领 {{ item.completedSubtasks }}/{{ item.claimedSubtasks }}</span>
                      <span>待完成 {{ item.pendingSubtasks }}</span>
                    </div>
                  </button>
                  <p v-if="!selectedGroupMembers.length" class="empty">当前小组暂无成员进度数据。</p>
                </div>
              </div>
            </section>

            <section class="progress-column subtask-column">
              <div class="column-head">
                <h4>认领子任务详情</h4>
                <span class="meta">{{ selectedMemberSummary?.studentName || '未选择组员' }}</span>
              </div>
              <div v-if="!selectedMemberId" class="column-body column-empty">请先从中间列选择一位组员</div>
              <div v-else-if="memberSubtasksLoading" class="column-body column-empty">加载子任务中…</div>
              <div v-else class="column-body">
                <div class="member-subtask-list">
                  <button
                    v-for="item in memberSubtasks"
                    :key="item.subtaskId"
                    class="member-subtask-row member-subtask-button"
                    type="button"
                    @click="openSubtaskDetail(item)"
                  >
                    <div class="subtask-row-head">
                      <p class="subtask-name">{{ item.name }}</p>
                      <span class="subtask-status" :class="`status-${item.statusCode}`">{{ item.statusLabel }}</span>
                    </div>
                    <p class="meta">截止 {{ item.deadline }}</p>
                  </button>
                  <p v-if="!memberSubtasks.length" class="empty">该组员暂无已认领子任务。</p>
                </div>
              </div>
            </section>
          </div>
        </article>
      </section>

    <Teleport to="body">
      <div
        v-if="selectedSubtaskDetail"
        class="peer-modal-backdrop subtask-detail-backdrop"
        @click.self="closeSubtaskDetail"
      >
        <section class="peer-modal card subtask-detail-modal" role="dialog" aria-modal="true">
        <header class="peer-modal-head">
          <div>
            <p class="stage-kicker">子任务详情</p>
            <h3>{{ selectedSubtaskDetail.name }}</h3>
          </div>
          <button class="secondary-btn" type="button" @click="closeSubtaskDetail">关闭</button>
        </header>

        <div class="peer-modal-body">
          <dl class="subtask-detail-meta">
            <div>
              <dt>状态</dt>
              <dd><span class="subtask-status" :class="`status-${selectedSubtaskDetail.statusCode}`">{{ selectedSubtaskDetail.statusLabel }}</span></dd>
            </div>
            <div>
              <dt>截止时间</dt>
              <dd>{{ selectedSubtaskDetail.deadline || '-' }}</dd>
            </div>
            <div>
              <dt>提交时间</dt>
              <dd>{{ selectedSubtaskDetail.submittedAt || '-' }}</dd>
            </div>
            <div>
              <dt>审批时间</dt>
              <dd>{{ selectedSubtaskDetail.reviewedAt || '-' }}</dd>
            </div>
          </dl>

          <article class="peer-modal-block">
            <h4>子任务说明</h4>
            <p>{{ selectedSubtaskDetail.description || '暂无说明' }}</p>
          </article>

          <article class="peer-modal-block">
            <h4>完成说明</h4>
            <p>{{ selectedSubtaskDetail.submissionText || '暂无完成说明' }}</p>
          </article>

          <article
            v-if="selectedSubtaskDetail.parsedSubmission.files?.length || selectedSubtaskDetail.parsedSubmission.link"
            class="peer-modal-block"
          >
            <h4>提交附件</h4>
            <div class="subtask-file-list">
              <div
                v-for="(file, index) in selectedSubtaskDetail.parsedSubmission.files"
                :key="`${file.value}-${index}`"
                class="subtask-file-row"
              >
                <span>{{ file.name }}</span>
                <div>
                  <button v-if="canPreviewSubmissionFile(file)" type="button" @click="viewSubmissionFile(file)">查看</button>
                  <button type="button" @click="downloadSubmissionFile(file)">下载</button>
                </div>
              </div>
              <div v-if="selectedSubtaskDetail.parsedSubmission.link" class="subtask-file-row">
                <span>{{ displayLinkText(selectedSubtaskDetail.parsedSubmission.link) }}</span>
                <div>
                  <a :href="resolveMediaUrl(selectedSubtaskDetail.parsedSubmission.link)" target="_blank" rel="noreferrer">打开</a>
                </div>
              </div>
            </div>
          </article>

          <article v-if="selectedSubtaskDetail.reviewComment" class="peer-modal-block">
            <h4>审批备注</h4>
            <p>{{ selectedSubtaskDetail.reviewComment }}</p>
          </article>

          <article v-if="selectedSubtaskDetail.submissionHistories?.length" class="peer-modal-block">
            <h4>操作痕迹</h4>
            <div class="subtask-history-list">
              <section
                v-for="history in selectedSubtaskDetail.submissionHistories"
                :key="history.id"
                class="subtask-history-row"
              >
                <div class="peer-material-head">
                  <strong>版本 {{ history.versionNo || '-' }}</strong>
                  <span>{{ history.current ? '当前版本' : history.submittedAtText }}</span>
                </div>
                <p v-if="history.parsedSubmission.text">{{ history.parsedSubmission.text }}</p>
                <div
                  v-if="history.parsedSubmission.files?.length || history.parsedSubmission.link"
                  class="subtask-file-list"
                >
                  <div
                    v-for="(file, index) in history.parsedSubmission.files"
                    :key="`${history.id}-${file.value}-${index}`"
                    class="subtask-file-row"
                  >
                    <span>{{ file.name }}</span>
                    <div>
                      <button v-if="canPreviewSubmissionFile(file)" type="button" @click="viewSubmissionFile(file)">查看</button>
                      <button type="button" @click="downloadSubmissionFile(file)">下载</button>
                    </div>
                  </div>
                  <div v-if="history.parsedSubmission.link" class="subtask-file-row">
                    <span>{{ displayLinkText(history.parsedSubmission.link) }}</span>
                    <div>
                      <a :href="resolveMediaUrl(history.parsedSubmission.link)" target="_blank" rel="noreferrer">打开</a>
                    </div>
                  </div>
                </div>
              </section>
            </div>
          </article>
        </div>
        </section>
      </div>
    </Teleport>

    <!-- ═══ TAB: 互评 ═══ -->
    <section v-if="activeTab === 'peer-reviews'" class="card tab-panel">
      <div class="tab-panel-head">
        <div>
          <h3>{{ t('互评记录', 'Peer reviews') }}</h3>
          <p class="meta">查看该任务的学生互评结果，记录以匿名代号展示。</p>
        </div>
      </div>
      <div class="tab-panel-scroll">
        <div class="peer-summary-grid">
          <article v-for="item in peerReviewStats" :key="item.label" class="peer-summary-card">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </article>
        </div>

        <div v-if="peerReviewLoading" class="loading-tab">加载中…</div>
        <div v-else-if="!peerReviewItems.length" class="empty-tab">
          <strong>暂无互评数据</strong>
          <p>学生提交互评后会显示在这里；如果任务还未截止，互评阶段尚未开始。</p>
        </div>
        <div v-else class="peer-list">
          <article
            v-for="(item, index) in peerReviewItems"
            :key="item.id || `${item.reviewerAlias}-${item.revieweeAlias}-${index}`"
            class="peer-row"
          >
            <div class="peer-route">
              <span class="peer-reviewer">{{ item.reviewerName || item.reviewerAlias || '匿名' }}</span>
              <span class="peer-arrow">→</span>
              <span class="peer-reviewee">{{ item.revieweeName || item.revieweeAlias || '匿名' }}</span>
            </div>
            <span class="peer-group">{{ item.groupName || '未命名小组' }}</span>
            <span class="peer-score">{{ item.score }} 分</span>
            <button class="ghost-btn compact" type="button" @click="openPeerDetail(item)">互评详情</button>
          </article>
        </div>
      </div>
    </section>

    <Teleport to="body">
      <div v-if="peerDetailOpen" class="peer-modal-backdrop peer-review-detail-backdrop" @click.self="closePeerDetail">
        <section class="peer-modal card peer-review-detail-modal" role="dialog" aria-modal="true" aria-labelledby="peer-detail-title">
          <header class="peer-modal-head">
            <div>
              <p class="stage-kicker">互评详情</p>
              <h3 id="peer-detail-title">
                {{ peerDetailItem?.reviewerName || peerDetailItem?.reviewerAlias || '评价人' }}
                →
                {{ peerDetailItem?.revieweeName || peerDetailItem?.revieweeAlias || '被评人' }}
              </h3>
            </div>
          </header>
          <div class="peer-modal-body">
            <p class="peer-modal-score">评分：<strong>{{ peerDetailItem?.score ?? '-' }}</strong> 分</p>
            <p class="peer-modal-meta">提交时间：{{ formatDateTime(peerDetailItem?.submittedAt) || '-' }}</p>
            <article class="peer-modal-block">
              <h4>评分人评语</h4>
              <p>{{ peerDetailItem?.comment || '暂无评语' }}</p>
            </article>
          </div>
          <footer class="peer-modal-foot">
            <button class="secondary-btn" type="button" @click="closePeerDetail">关闭</button>
          </footer>
        </section>
      </div>
    </Teleport>

    <!-- ═══ TAB: 申诉 ═══ -->
    <section v-if="activeTab === 'appeals'" class="card tab-panel">
      <div class="tab-panel-head">
        <div>
          <h3>{{ t('任务申诉', 'Task appeals') }}</h3>
          <p class="meta">点击申诉记录可在申诉中心查看并处理。</p>
        </div>
      </div>
      <div class="tab-panel-scroll">
      <div v-if="appealLoading" class="loading-tab">加载中…</div>
      <div v-else-if="!appealItems.length" class="empty-tab">暂无申诉</div>
      <div v-else class="appeal-list">
        <article
          v-for="item in appealItems"
          :key="item.id"
          class="appeal-row appeal-row-clickable"
          role="button"
          tabindex="0"
          @click="openAppealRecord(item)"
          @keydown.enter.prevent="openAppealRecord(item)"
        >
          <div class="appeal-head">
            <span class="appeal-type">{{ item.type === 'teacher_score' ? '教师评分' : item.type === 'peer_review' ? '互评异常' : '子任务误判' }}</span>
            <span class="appeal-status" :class="item.status === 0 ? 'pending' : item.status === 3 ? 'done' : 'reject'">
              {{ item.status === 0 ? '待处理' : item.status === 3 ? '已通过' : '已驳回' }}
            </span>
          </div>
          <p class="appeal-reason">{{ item.reason }}</p>
          <p v-if="item.teacherResponse" class="appeal-response">教师回复：{{ item.teacherResponse }}</p>
        </article>
      </div>
      </div>
    </section>
    </div>
    </template>

    <WorkspaceDialogMask :open="editTaskOpen" @close="closeEditTaskDialog">
      <section class="card dialog-panel edit-task-dialog" role="dialog" aria-modal="true">
        <div class="panel-head">
          <div>
            <p class="stage-kicker">{{ t('任务设置', 'Task settings') }}</p>
            <h3>{{ t('编辑已发布任务', 'Edit published task') }}</h3>
          </div>
        </div>

        <div class="dialog-grid">
          <label class="full-width">
            <span>{{ t('任务名称', 'Task name') }}</span>
            <input
              v-model.trim="editTaskForm.name"
              type="text"
              :disabled="editTaskBusy"
              :placeholder="t('请输入任务名称', 'Enter task name')"
            />
          </label>

          <label class="full-width">
            <span>{{ t('任务描述', 'Description') }}</span>
            <textarea
              v-model.trim="editTaskForm.description"
              rows="4"
              :disabled="editTaskBusy"
              :placeholder="t('填写任务说明、交付要求和验收重点', 'Deliverables, requirements, acceptance criteria')"
            ></textarea>
          </label>

          <label>
            <span>{{ t('截止时间', 'Deadline') }}</span>
            <input v-model="editTaskForm.deadline" type="datetime-local" :disabled="editTaskBusy" />
          </label>

          <label>
            <span>{{ t('是否开启互评', 'Enable peer review') }}</span>
            <select v-model="editTaskForm.enablePeerReview" :disabled="editTaskBusy">
              <option :value="true">{{ t('开启', 'Enable') }}</option>
              <option :value="false">{{ t('关闭', 'Disable') }}</option>
            </select>
          </label>

          <label v-if="editTaskForm.enablePeerReview">
            <span>{{ t('互评时长（小时）', 'Peer review duration (hours)') }}</span>
            <input
              v-model.number="editTaskForm.peerReviewOffsetHours"
              type="number"
              min="1"
              :disabled="editTaskBusy"
            />
          </label>

          <article class="weight-tip full-width">
            {{ t('互评与教师评分将作为独立记录保存，系统仅提供参考汇总，不自动生成最终成绩。', 'Peer reviews and teacher scores are stored as separate records. The system provides reference summaries only, not final grades.') }}
          </article>

          <div class="task-attachment-editor full-width">
            <div class="attachment-head">
              <span>{{ t('任务附件', 'Task attachments') }}</span>
              <small>{{ t('支持文档、图片、PDF、压缩包，单文件不超过 10MB', 'Docs, images, PDFs and archives, up to 10MB each') }}</small>
            </div>
            <FileUploadZone
              v-model="editTaskForm.uploadedFiles"
              :disabled="editTaskBusy"
              :max-files="8"
              @error="handleEditTaskUploadError"
            />
          </div>

          <label class="full-width">
            <span>{{ t('附件链接', 'Attachment link') }}</span>
            <input
              v-model.trim="editTaskForm.attachmentLink"
              type="url"
              :disabled="editTaskBusy"
              :placeholder="t('可选，粘贴 http:// 或 https:// 开头的资料链接', 'Optional, paste a resource link starting with http:// or https://')"
            />
          </label>
        </div>

        <div class="panel-actions">
          <button class="secondary-btn" type="button" :disabled="editTaskBusy" @click="closeEditTaskDialog">
            {{ t('取消', 'Cancel') }}
          </button>
          <button class="primary-btn" type="button" :disabled="editTaskBusy" @click="submitEditTask">
            {{ editTaskBusy ? t('保存中...', 'Saving...') : t('保存修改', 'Save changes') }}
          </button>
        </div>
      </section>
    </WorkspaceDialogMask>
  </div>
</template>

<style scoped>
.entry-loading {
  padding: 24px 16px;
  text-align: center;
  color: var(--teacher-text-secondary);
}

.empty-guide {
  padding: 10px;
}

.teacher-page,
.board-grid,
.class-metric-grid,
.task-meta-grid,
.dialog-grid,
.overview-body,
.progress-workspace,
.progress-member-detail,
.progress-group-rail,
.rail-list,
.selected-group-panel,
.member-list-panel,
.member-rank-panel,
.selected-group-grid,
.members-body,
.progress-overview-strip {
  display: grid;
  gap: 14px;
}

.teacher-page {
  gap: 14px;
}

.task-detail-page {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.task-detail-head,
.task-tabs,
.task-detail-message {
  flex-shrink: 0;
}

.task-detail-stage {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.task-detail-stage .board-grid,
.task-detail-stage .tab-panel {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.task-detail-stage .board-grid {
  display: flex;
  flex-direction: column;
}

.task-detail-stage .board-grid-single {
  display: grid;
}

.card {
  background: var(--teacher-surface);
  border-radius: var(--teacher-radius-card);
  box-shadow: var(--teacher-shadow);
}

.topbar,
.dialog-panel,
.panel-card {
  padding: 16px;
}

.dialog-panel {
  width: min(980px, 92vw);
  min-height: min(720px, 88vh);
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
}

.dialog-grid {
  margin-top: 16px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-content: start;
  overflow-y: auto;
  padding: 4px;
}

.dialog-grid label,
.task-attachment-editor {
  display: grid;
  gap: 8px;
}

.dialog-grid label > span,
.attachment-head span {
  color: var(--teacher-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.dialog-grid input,
.dialog-grid textarea,
.dialog-grid select {
  width: 100%;
  border: 1px solid var(--teacher-border);
  border-radius: 12px;
  background: var(--teacher-surface);
  color: var(--teacher-text-primary);
  font: inherit;
  padding: 10px 12px;
}

.dialog-grid textarea {
  resize: vertical;
}

.full-width {
  grid-column: 1 / -1;
}

.attachment-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: baseline;
}

.attachment-head small {
  color: var(--teacher-text-tertiary);
  font-size: 12px;
  text-align: right;
}

.weight-tip {
  border: 1px solid var(--teacher-divider);
  border-radius: 14px;
  padding: 12px;
  color: var(--teacher-text-secondary);
}

.weight-tip.invalid {
  border-color: rgba(197, 48, 48, 0.3);
  color: var(--teacher-danger);
}

.topbar,
.panel-head,
.actions,
.panel-headline,
.panel-actions,
.progress-board-head,
.progress-board-actions,
.stage-badges,
.group-row-head,
.rank-row-head,
.rank-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.actions,
.stage-badges,
.panel-headline,
.progress-board-head,
.progress-board-actions,
.selected-group-head {
  flex-wrap: wrap;
}

.board-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: auto auto;
}

.board-grid-single {
  grid-template-columns: minmax(0, 1fr);
}

.panel-card {
  display: grid;
  overflow: hidden;
}

.task-card {
  grid-template-rows: auto auto auto auto;
  gap: 12px;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: auto;
  -webkit-overflow-scrolling: touch;
}

.overview-card {
  grid-template-rows: auto 1fr;
}

.progress-workspace-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  grid-column: 1 / -1;
}

.selected-group-panel {
  grid-template-rows: auto auto;
}

.member-list-panel,
.member-rank-panel {
  grid-template-rows: auto minmax(0, 1fr);
}

.task-card,
.overview-card,
.progress-workspace-card {
  min-height: 0;
}

.panel-headline {
  padding-bottom: 12px;
  border-bottom: 1px solid var(--teacher-divider);
}

.progress-board-head {
  padding-bottom: 14px;
  border-bottom: 1px solid var(--teacher-divider);
}

.overview-body,
.members-body,
.progress-workspace,
.progress-member-detail,
.progress-group-rail,
.selected-group-panel,
.member-list-panel,
.member-rank-panel {
  min-height: 0;
  display: grid;
  overflow: hidden;
}

.task-meta-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.overview-body {
  grid-template-columns: 180px minmax(0, 1fr);
  align-items: center;
}

.class-metric-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.overview-side {
  min-height: 0;
  display: grid;
  grid-template-rows: auto auto;
  gap: 14px;
}

.selected-group-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.task-hero {
  align-items: center;
}

.task-switcher-wrap {
  display: grid;
  gap: 6px;
  min-width: min(320px, 100%);
}

.switcher-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--teacher-text-tertiary);
}

.task-switcher {
  min-height: 40px;
  padding: 0 12px;
  border: 1px solid var(--teacher-divider);
  border-radius: 12px;
  background: var(--tt-surface);
  color: var(--teacher-text-primary);
  font-size: 14px;
}

.progress-three-columns {
  display: grid;
  grid-template-columns: minmax(260px, 0.9fr) minmax(260px, 1fr) minmax(280px, 1.1fr);
  gap: 14px;
  align-items: stretch;
  flex: 1;
  min-height: min(560px, calc(100vh - 280px));
  overflow: hidden;
}

.progress-column {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 8%, var(--tt-border-subtle));
  border-radius: 14px;
  background: linear-gradient(
    165deg,
    var(--tt-surface),
    color-mix(in srgb, var(--tt-accent) 3%, var(--tt-surface-muted))
  );
  min-height: 0;
  overflow: hidden;
}

.column-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: auto;
  -webkit-overflow-scrolling: touch;
  padding-right: 2px;
}

.column-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--teacher-divider);
  flex-shrink: 0;
}

.column-head h4 {
  margin: 0;
  font-size: 15px;
}

.column-empty,
.progress-three-columns .empty {
  flex: 1;
  width: 100%;
  min-height: 120px;
  display: grid;
  place-items: center;
  padding: 16px;
  text-align: center;
  color: var(--teacher-text-tertiary);
  font-size: 13px;
  line-height: 1.55;
  border-radius: 12px;
  border: 1px dashed color-mix(in srgb, var(--tt-accent) 20%, var(--tt-border-strong));
  background: color-mix(in srgb, var(--tt-accent) 5%, var(--tt-surface-muted));
  box-sizing: border-box;
}

.rail-list,
.member-pick-list,
.member-subtask-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-content: flex-start;
  width: 100%;
  box-sizing: border-box;
}

.member-pick-row {
  flex: 0 0 auto;
  display: grid;
  gap: 7px;
  width: 100%;
  min-height: 92px;
  padding: 11px 12px;
  border: 1px solid var(--tt-border-subtle);
  border-radius: 12px;
  background: var(--tt-surface-muted);
  text-align: left;
  cursor: pointer;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    box-shadow 0.15s ease,
    transform 0.15s ease;
}

.member-pick-row:hover,
.member-pick-row.active {
  background: var(--tt-surface-hover);
  border-color: var(--tt-accent-border);
  box-shadow: 0 8px 20px color-mix(in srgb, var(--tt-accent) 8%, transparent);
}

.member-pick-head,
.member-pick-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.member-pick-head strong {
  color: var(--teacher-text-primary);
}

.member-pick-foot span {
  font-size: 12px;
  color: var(--teacher-text-tertiary);
}

.member-subtask-row {
  flex: 0 0 auto;
  min-height: 64px;
  width: 100%;
  padding: 12px;
  border-radius: 12px;
  border: 1px solid var(--tt-border-subtle);
  background: var(--tt-surface-muted);
  box-sizing: border-box;
}

.member-subtask-button {
  display: block;
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}

.member-subtask-button:hover,
.member-subtask-button:focus-visible {
  background: var(--tt-surface-hover);
  border-color: var(--tt-accent-border);
  box-shadow: 0 8px 20px color-mix(in srgb, var(--tt-accent) 8%, transparent);
  outline: none;
}

.subtask-row-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.subtask-name {
  margin: 0;
  font-weight: 600;
}

.subtask-status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 800;
  padding: 2px 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--tt-accent) 10%, var(--tt-surface-muted));
  color: var(--tt-accent);
  border: 1px solid color-mix(in srgb, var(--tt-accent) 20%, var(--tt-border));
}

.subtask-status.status-4 {
  color: #047857;
  background: color-mix(in srgb, #10b981 14%, var(--tt-surface-muted));
  border-color: color-mix(in srgb, #10b981 28%, var(--tt-border));
}

.subtask-status.status-3 {
  color: #b45309;
  background: color-mix(in srgb, #f59e0b 16%, var(--tt-surface-muted));
  border-color: color-mix(in srgb, #f59e0b 30%, var(--tt-border));
}

.subtask-status.status-2 {
  color: var(--teacher-accent);
  background: color-mix(in srgb, var(--tt-accent) 12%, var(--tt-surface-muted));
  border-color: color-mix(in srgb, var(--tt-accent) 24%, var(--tt-border));
}

.peer-modal-backdrop {
  /* positioning handled by teacher-dialog.css */
}

.subtask-detail-backdrop {
  inset: 0 !important;
  left: 0 !important;
  right: 0 !important;
  z-index: 3200;
  padding: 36px;
  background: rgba(15, 23, 42, 0.24);
}

.peer-review-detail-backdrop {
  position: fixed !important;
  inset: 0 !important;
  z-index: 3300;
  display: grid;
  place-items: center;
  padding: 32px;
  background: rgba(15, 23, 42, 0.28);
}

.peer-modal {
  width: min(720px, 100%);
  max-height: min(80vh, 720px);
  overflow: auto;
  padding: 18px;
  display: grid;
  gap: 14px;
}

.peer-review-detail-modal {
  width: min(860px, calc(100vw - 64px));
  max-height: min(86vh, 760px);
  padding: 22px;
  border-radius: 18px;
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.2);
}

.subtask-detail-modal {
  width: min(1120px, calc(100vw - 72px));
  max-height: calc(100vh - 72px);
  padding: 24px;
  border-radius: 18px;
  box-shadow: 0 20px 56px rgba(15, 23, 42, 0.18);
}

.subtask-detail-meta {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.subtask-detail-meta div {
  min-width: 0;
  padding: 10px;
  border-radius: 12px;
  background: var(--tt-surface-muted);
}

.subtask-detail-meta dt {
  margin: 0 0 4px;
  color: var(--teacher-text-tertiary);
  font-size: 12px;
}

.subtask-detail-meta dd {
  margin: 0;
  color: var(--teacher-text-primary);
  font-size: 13px;
  font-weight: 700;
  overflow-wrap: anywhere;
}

.subtask-file-list,
.subtask-history-list {
  display: grid;
  gap: 8px;
}

.subtask-file-row,
.subtask-history-row {
  padding: 10px;
  border-radius: 12px;
  background: var(--tt-surface);
}

.subtask-file-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.subtask-file-row span {
  min-width: 0;
  color: var(--teacher-text-primary);
  font-size: 13px;
  font-weight: 700;
  overflow-wrap: anywhere;
}

.subtask-file-row div {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.subtask-file-row button,
.subtask-file-row a {
  border: 0;
  background: transparent;
  color: var(--teacher-accent);
  cursor: pointer;
  font-size: 13px;
  font-weight: 800;
  text-decoration: none;
}

.subtask-history-row {
  display: grid;
  gap: 8px;
}

.subtask-history-row p {
  margin: 0;
  color: var(--teacher-text-secondary);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

@media (max-width: 900px) {
  .subtask-detail-backdrop {
    padding: 16px;
  }

  .subtask-detail-modal {
    width: min(100%, calc(100vw - 32px));
    max-height: calc(100vh - 32px);
    padding: 18px;
  }

  .peer-review-detail-backdrop {
    padding: 16px;
  }

  .peer-review-detail-modal {
    width: min(100%, calc(100vw - 32px));
    max-height: calc(100vh - 32px);
    padding: 18px;
  }

  .subtask-detail-meta {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 640px) {
  .subtask-detail-meta {
    grid-template-columns: 1fr;
  }

  .subtask-file-row {
    align-items: stretch;
    flex-direction: column;
  }
}

.peer-modal-head,
.peer-material-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.peer-modal-body {
  display: grid;
  gap: 14px;
}

.peer-modal-block {
  padding: 12px;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.03);
}

.peer-modal-block h4 {
  margin: 0 0 8px;
  font-size: 14px;
}

.peer-modal-foot {
  display: flex;
  justify-content: flex-end;
  padding-top: 4px;
  border-top: 1px solid var(--teacher-divider);
}

.peer-modal-meta {
  margin: 0 0 4px;
  color: var(--teacher-muted);
  font-size: 13px;
}

.progress-group-rail {
  grid-template-rows: auto auto minmax(0, 1fr);
}

.progress-member-detail {
  grid-template-rows: auto minmax(0, 1fr);
}

.members-body {
  min-height: 0;
  grid-template-columns: minmax(0, 1.28fr) minmax(280px, 0.72fr);
}

.progress-group-rail,
.selected-group-panel,
.member-list-panel,
.member-rank-panel {
  padding: 14px;
  border: 1px solid var(--teacher-divider);
  border-radius: 16px;
  background: var(--tt-surface);
  box-shadow: var(--tt-shadow-xs);
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease,
    border-color 0.22s ease;
}

.rank-list,
.member-table {
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: auto;
  -webkit-overflow-scrolling: touch;
  padding-right: 2px;
  display: grid;
  gap: 8px;
}

.rail-list {
  min-height: 0;
}

.eyebrow,
.stage-kicker,
.meta,
.message,
.empty,
.focus-label {
  margin: 0;
  color: var(--teacher-text-tertiary);
  font-size: 12px;
  line-height: 1.5;
}

.eyebrow,
.stage-kicker,
.focus-label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

h2,
h3,
h4,
.group-name,
.student-name {
  margin: 0;
}

.group-name,
.student-name {
  color: var(--teacher-text-primary);
  font-size: 13px;
  font-weight: 700;
}

.progress-board-desc {
  max-width: 760px;
  margin: 7px 0 0;
  color: var(--teacher-text-secondary);
  font-size: 13px;
  line-height: 1.55;
}

.progress-board-actions {
  align-items: center;
}

.badge {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(36, 86, 173, 0.12);
  color: var(--teacher-accent);
  font-size: 12px;
  font-weight: 700;
}

.badge.muted {
  background: color-mix(in srgb, var(--tt-accent) 12%, var(--tt-surface-muted));
  color: var(--tt-accent);
  border: 1px solid color-mix(in srgb, var(--tt-accent) 22%, var(--tt-border));
  font-weight: 800;
}

.switch-btn,
.primary-btn,
.secondary-btn,
.back-btn,
.ghost-btn {
  min-height: 36px;
  border-radius: 10px;
  padding: 0 12px;
  border: 0;
  font-family: inherit;
  font-weight: 700;
  cursor: pointer;
}

.primary-btn {
  background: var(--teacher-accent);
  color: #fff;
}

.secondary-btn {
  background: var(--teacher-surface-muted);
  color: var(--teacher-text-primary);
}

.ghost-btn {
  border: 1px solid var(--teacher-border);
  background: var(--tt-surface-muted);
  color: var(--teacher-text-primary);
}

.ghost-btn.compact {
  min-height: 28px;
  border-radius: 999px;
  font-size: 12px;
}

.back-btn {
  border: 1px solid var(--teacher-border);
  background: var(--teacher-surface);
  color: var(--teacher-text-primary);
}

.primary-btn:disabled,
.secondary-btn:disabled,
.back-btn:disabled,
.ghost-btn:disabled,
.text-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.text-btn {
  border: 0;
  background: transparent;
  color: var(--teacher-accent);
  font-family: inherit;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 850;
}

.status-pill.success {
  background: rgba(60, 163, 108, 0.13);
  color: #246f4a;
}

.status-pill.warning {
  background: rgba(211, 146, 53, 0.14);
  color: #8a5a18;
}

.status-pill.danger {
  background: rgba(200, 79, 79, 0.14);
  color: #9a3535;
}

.status-pill.neutral {
  background: var(--teacher-surface-muted);
  color: var(--teacher-text-secondary);
}

.progress-ring {
  --progress: 0%;
  width: 176px;
  height: 176px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background:
    conic-gradient(
      #2456ad 0 var(--progress),
      rgba(17, 24, 39, 0.08) var(--progress) 100%
    );
}

.progress-ring-core {
  width: 118px;
  height: 118px;
  border-radius: 50%;
  background: var(--teacher-surface);
  box-shadow: inset 0 0 0 1px var(--teacher-divider);
  display: grid;
  place-items: center;
  text-align: center;
}

.progress-ring-core strong {
  color: var(--teacher-text-primary);
  font-size: 28px;
  line-height: 1;
}

.progress-ring-core span {
  color: var(--teacher-text-tertiary);
  font-size: 12px;
}

.metric-card,
.summary-card,
.status-cell,
.group-row,
.student-row,
.rank-row,
.meta-item,
.task-desc {
  border: 1px solid var(--tt-border-subtle);
  border-radius: 12px;
  background: var(--tt-surface-muted);
}

.metric-card,
.summary-card,
.status-cell {
  padding: 14px;
  display: grid;
  gap: 8px;
  align-content: center;
  min-width: 0;
}

.meta-item {
  padding: 12px 14px;
  min-height: 74px;
  display: grid;
  gap: 8px;
  align-content: center;
}

.task-desc {
  padding: 14px;
  display: grid;
  gap: 8px;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: auto;
  -webkit-overflow-scrolling: touch;
}

.task-attachments {
  gap: 12px;
}

.task-attachment-list {
  display: grid;
  gap: 10px;
}

.task-attachment-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid var(--teacher-divider);
  border-radius: 10px;
  background: var(--teacher-surface);
}

.task-attachment-row div:first-child {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.task-attachment-row strong {
  overflow: hidden;
  color: var(--teacher-text-primary);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-attachment-row small {
  color: var(--teacher-text-tertiary);
  font-size: 12px;
}

.task-attachment-actions {
  flex-shrink: 0;
}

.task-attachment-actions .secondary-btn {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  text-decoration: none;
}

.metric-card span,
.summary-card span,
.status-cell span,
.meta-item span,
.task-desc span {
  color: var(--teacher-text-tertiary);
  font-size: 12px;
  font-weight: 700;
}

.metric-card strong,
.summary-card strong,
.status-cell strong,
.meta-item strong {
  color: var(--teacher-text-primary);
  font-weight: 800;
}

.metric-card,
.summary-card,
.status-cell {
  min-height: 74px;
}

.metric-card strong {
  font-size: 18px;
}

.summary-card strong,
.status-cell strong {
  font-size: 15px;
}

.summary-card em,
.status-cell em {
  color: var(--teacher-text-tertiary);
  font-size: 12px;
  font-style: normal;
  word-break: break-word;
  line-height: 1.45;
}

.task-desc p {
  margin: 0;
  color: var(--teacher-text-primary);
  font-size: 13px;
  line-height: 1.6;
}

.metric-card.success,
.summary-card.success,
.status-cell.success {
  background: var(--tt-success-soft);
  border-color: color-mix(in srgb, var(--tt-success) 24%, var(--tt-border));
}

.metric-card.warning,
.summary-card.warning,
.status-cell.warning {
  background: var(--tt-warning-soft);
  border-color: color-mix(in srgb, var(--tt-warning) 24%, var(--tt-border));
}

.inline-error {
  flex-shrink: 0;
  margin: 0 0 12px;
  padding: 10px 12px;
  border: 1px solid color-mix(in srgb, var(--tt-danger) 28%, transparent);
  border-radius: 12px;
  background: var(--tt-danger-soft);
  color: var(--teacher-danger);
  font-size: 12px;
  line-height: 1.5;
}

.progress-loading-state {
  flex: 1;
  min-height: 0;
  display: grid;
  gap: 12px;
}

.progress-loading-state span {
  display: block;
  height: 58px;
  border-radius: 14px;
  background: linear-gradient(90deg, var(--tt-surface-muted), var(--tt-surface-hover), var(--tt-surface-muted));
  background-size: 220% 100%;
  animation: progress-skeleton 1.2s ease-in-out infinite;
}

@keyframes progress-skeleton {
  0% {
    background-position: 100% 0;
  }

  100% {
    background-position: -100% 0;
  }
}

.progress-overview-strip {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.progress-overview-strip article {
  min-width: 0;
  padding: 10px 11px;
  border: 1px solid var(--teacher-divider);
  border-radius: 12px;
  background: var(--tt-surface-muted);
  display: grid;
  gap: 4px;
}

.progress-overview-strip article.danger {
  background: var(--tt-danger-soft);
  border-color: color-mix(in srgb, var(--tt-danger) 24%, var(--tt-border));
}

.progress-overview-strip article.warning {
  background: var(--tt-warning-soft);
  border-color: color-mix(in srgb, var(--tt-warning) 24%, var(--tt-border));
}

.progress-overview-strip article.success {
  background: var(--tt-success-soft);
  border-color: color-mix(in srgb, var(--tt-success) 24%, var(--tt-border));
}

.progress-overview-strip span {
  color: var(--teacher-text-tertiary);
  font-size: 11px;
  font-weight: 800;
}

.progress-overview-strip strong {
  color: var(--teacher-text-primary);
  font-size: 18px;
  font-weight: 850;
  word-break: break-word;
}

.rail-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 2px 2px 0;
}

.rail-title h4 {
  font-size: 14px;
}

.rail-title > span {
  color: var(--teacher-text-tertiary);
  font-size: 12px;
  font-weight: 800;
}

.metric-card,
.summary-card,
.status-cell,
.meta-item,
.task-desc,
.group-row,
.student-row,
.rank-row {
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease,
    border-color 0.22s ease,
    background 0.22s ease;
}

.metric-card:hover,
.summary-card:hover,
.status-cell:hover,
.meta-item:hover,
.task-desc:hover,
.student-row:hover,
.rank-row:hover {
  transform: translateY(-2px);
  border-color: rgba(36, 86, 173, 0.22);
  box-shadow: 0 14px 26px rgba(15, 23, 42, 0.07);
}

.group-row,
.student-row,
.rank-row {
  padding: 11px 12px;
  display: grid;
  gap: 7px;
  overflow: hidden;
}

.group-row {
  flex: 0 0 auto;
  min-height: 92px;
  grid-template-rows: auto auto auto;
  text-align: left;
  cursor: pointer;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease;
}

.group-row:hover {
  border-color: rgba(36, 86, 173, 0.22);
  box-shadow: 0 10px 20px rgba(36, 86, 173, 0.08);
}

.group-row.active {
  border-color: color-mix(in srgb, var(--tt-accent) 28%, var(--tt-border));
  box-shadow: 0 10px 22px color-mix(in srgb, var(--tt-accent) 10%, transparent);
}

.group-row.success.active,
.group-row.warning.active,
.group-row.danger.active {
  background: linear-gradient(
    160deg,
    color-mix(in srgb, var(--tt-accent) 8%, var(--tt-surface-muted)),
    var(--tt-surface)
  );
}

.group-row-head strong,
.rank-row-head strong,
.focus-score strong {
  color: var(--teacher-text-primary);
  font-size: 16px;
  font-weight: 800;
}

.queue-no {
  flex: 0 0 auto;
  width: 26px;
  height: 26px;
  display: inline-grid;
  place-items: center;
  border-radius: 999px;
  background: var(--teacher-surface-muted);
  color: var(--teacher-text-secondary);
  font-size: 12px;
  font-weight: 850;
}

.student-progress-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 56px;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(36, 86, 173, 0.12);
  color: var(--teacher-accent);
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
}

.student-progress-badge.compact {
  min-width: 52px;
  min-height: 28px;
}

.focus-score {
  min-width: 86px;
  display: grid;
  gap: 4px;
  justify-items: end;
}

.focus-score span {
  color: var(--teacher-text-tertiary);
  font-size: 11px;
  font-weight: 800;
}

.focus-score strong {
  font-size: 30px;
  line-height: 1;
}

.status-breakdown {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.group-row-foot {
  display: grid;
  gap: 2px 8px;
}

.group-row-foot {
  grid-template-columns: repeat(2, max-content);
  justify-content: space-between;
}

.group-row-foot span {
  color: var(--teacher-text-tertiary);
  font-size: 11px;
  line-height: 1.35;
}

.selected-group-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.group-name,
.student-name,
.summary-card strong,
.meta-item strong {
  word-break: break-word;
}

.group-row-head > div,
.rank-row-head > div {
  min-width: 0;
}

.rank-row {
  min-height: 66px;
}

.student-row {
  min-height: 58px;
  grid-template-columns: minmax(130px, 1.25fr) minmax(130px, 1fr) 72px 72px;
  align-items: center;
  border-radius: 0;
  border-width: 0 0 1px;
  background: transparent;
}

.student-row:hover {
  transform: none;
  box-shadow: none;
  background: var(--tt-surface-hover);
}

.member-table-head {
  position: sticky;
  top: 0;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(130px, 1.25fr) minmax(130px, 1fr) 72px 72px;
  gap: 10px;
  padding: 0 12px 9px;
  background: var(--tt-surface);
  border-bottom: 1px solid var(--teacher-divider);
  color: var(--teacher-text-tertiary);
  font-size: 11px;
  font-weight: 850;
}

.member-progress-cell {
  display: grid;
  gap: 6px;
}

.member-progress-cell strong {
  color: var(--teacher-text-primary);
  font-size: 12px;
}

.pending-count {
  justify-self: start;
  min-width: 34px;
  min-height: 26px;
  display: inline-grid;
  place-items: center;
  border-radius: 999px;
  background: color-mix(in srgb, var(--tt-accent) 10%, var(--tt-surface-muted));
  color: var(--tt-accent);
  border: 1px solid color-mix(in srgb, var(--tt-accent) 22%, var(--tt-border));
  font-size: 12px;
  font-weight: 850;
}

.pending-count.warning {
  background: color-mix(in srgb, var(--tt-warning) 14%, var(--tt-surface-muted));
  color: #b45309;
  border-color: color-mix(in srgb, var(--tt-warning) 26%, var(--tt-border));
}

.progress-track {
  width: 100%;
  height: 8px;
  border-radius: 999px;
  background: var(--tt-border-subtle);
  overflow: hidden;
}

.progress-track.compact {
  height: 6px;
}

.progress-bar {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.progress-bar.success {
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--tt-accent) 72%, #93c5fd),
    var(--tt-accent)
  );
}

.progress-bar.warning {
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--tt-accent) 68%, #93c5fd),
    color-mix(in srgb, var(--tt-accent) 82%, #60a5fa)
  );
}

.progress-bar.danger {
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--tt-accent) 38%, #dbeafe),
    color-mix(in srgb, var(--tt-accent) 68%, #93c5fd)
  );
}

.focus-member-row {
  padding: 12px;
  display: grid;
  gap: 8px;
  border: 1px solid var(--teacher-divider);
  border-radius: 14px;
  background: var(--tt-surface-muted);
}

.focus-member-row.warning {
  background: var(--tt-warning-soft);
  border-color: color-mix(in srgb, var(--tt-warning) 24%, var(--tt-border));
}

.focus-member-row.danger {
  background: var(--tt-danger-soft);
  border-color: color-mix(in srgb, var(--tt-danger) 24%, var(--tt-border));
}

.focus-empty {
  min-height: 132px;
  padding: 18px;
  display: grid;
  place-content: center;
  gap: 8px;
  border: 1px dashed var(--tt-border-strong);
  border-radius: 12px;
  background: var(--tt-surface-muted);
  text-align: center;
}

.focus-empty strong {
  color: var(--teacher-text-primary);
  font-size: 14px;
}

.focus-empty span {
  color: var(--teacher-text-tertiary);
  font-size: 12px;
  line-height: 1.5;
}

.message.success {
  color: var(--teacher-success);
}

.message.error {
  color: var(--teacher-danger);
}

.peer-extend-block {
  margin-top: 12px;
  padding: 14px;
  display: grid;
  gap: 10px;
  border: 1px solid var(--tt-accent-border);
  border-radius: 16px;
  background: var(--tt-accent-soft);
}

.peer-extend-title {
  margin: 0;
  font-size: 13px;
  font-weight: 800;
  color: var(--teacher-text-primary);
}

.peer-extend-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--teacher-text-tertiary);
}

.peer-extend-inline-error {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.45;
  color: var(--teacher-danger);
}

.peer-extend-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 12px;
}

.peer-extend-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--teacher-text-tertiary);
}

.peer-extend-input {
  min-width: 200px;
  flex: 1 1 200px;
  padding: 10px 12px;
  border: 1px solid var(--tt-input-border, var(--teacher-divider));
  border-radius: 12px;
  font-size: 13px;
  color: var(--teacher-text-primary);
  background: var(--tt-input-bg, var(--tt-surface-muted));
}

.peer-extend-input:focus {
  outline: none;
  border-color: var(--teacher-accent);
  box-shadow: 0 0 0 3px rgba(36, 86, 173, 0.15);
}

@media (max-width: 1320px) {
  .board-grid {
    grid-template-columns: 1fr;
    grid-template-rows: auto;
  }

  .progress-three-columns {
    grid-template-columns: 1fr;
    min-height: 0;
  }

  .progress-column {
    min-height: min(420px, calc(100vh - 320px));
  }

  .progress-workspace,
  .members-body {
    grid-template-columns: 1fr;
  }

  .selected-group-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .overview-body {
    grid-template-columns: 1fr;
    justify-items: center;
  }

  .class-metric-grid,
  .selected-group-grid,
  .progress-overview-strip,
  .status-breakdown,
  .members-body,
  .task-meta-grid {
    grid-template-columns: 1fr;
  }

  .student-row,
  .member-table-head {
    grid-template-columns: minmax(0, 1fr) 100px;
  }

  .member-table-head span:nth-child(3),
  .member-table-head span:nth-child(4),
  .student-row > span {
    display: none;
  }
}

@media (max-width: 760px) {
  .topbar,
  .actions,
  .stage-badges,
  .panel-headline,
  .tab-panel-head,
  .detail-head,
  .group-row-head,
  .group-row-foot,
  .rank-row-head,
  .meta-item,
  .rank-head,
  .rail-title {
    flex-direction: column;
    align-items: stretch;
  }

  .peer-extend-row {
    flex-direction: column;
    align-items: stretch;
  }

  .peer-extend-input {
    min-width: 0;
    width: 100%;
  }

  .dialog-grid {
    grid-template-columns: 1fr;
  }

  .panel-head,
  .panel-actions,
  .attachment-head {
    flex-direction: column;
    align-items: stretch;
  }
}

/* ═══ TAB BAR ═══ */
.task-tabs {
  display: flex;
  gap: 4px;
  padding: 4px;
  border-radius: 14px;
  background: var(--tt-surface);
  border: 1px solid var(--tt-border);
  box-shadow: var(--tt-shadow-xs);
  overflow-x: auto;
  flex-shrink: 0;
}
.tab-btn {
  flex-shrink: 0;
  height: 38px;
  padding: 0 18px;
  border-radius: 11px;
  border: none;
  font-size: 14px;
  font-weight: 600;
  color: var(--teacher-text-secondary);
  background: transparent;
  cursor: pointer;
  transition: all 160ms ease;
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}
.tab-btn:hover {
  color: var(--teacher-text-primary);
  background: var(--tt-surface-hover);
}
.tab-btn.active {
  color: var(--tt-accent);
  background: var(--tt-accent-soft);
  box-shadow: none;
}
.tab-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  margin-left: 6px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
  color: #fff;
  background: linear-gradient(135deg, #f87171, #dc2626);
  box-shadow: 0 2px 8px rgba(220, 38, 38, 0.28);
  vertical-align: middle;
}

/* ═══ TAB PANELS ═══ */
.tab-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 0;
  padding: 0;
  overflow: hidden;
}

.tab-panel-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  padding: 16px 18px 12px;
  border-bottom: 1px solid var(--teacher-divider);
  flex-shrink: 0;
}

.tab-panel-scroll {
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: auto;
  -webkit-overflow-scrolling: touch;
  padding: 14px 18px 18px;
}
.loading-tab, .empty-tab {
  text-align: center;
  padding: 40px 20px;
  color: var(--teacher-text-secondary);
  font-size: 14px;
}

.empty-tab strong {
  display: block;
  margin-bottom: 8px;
  color: var(--teacher-text-primary);
}

.empty-tab p {
  margin: 0;
}

/* peer reviews */
.peer-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.peer-summary-card {
  padding: 12px;
  border: 1px solid var(--tt-border-subtle);
  border-radius: 12px;
  background: var(--tt-surface-muted);
}

.peer-summary-card span {
  display: block;
  color: var(--teacher-text-tertiary);
  font-size: 12px;
}

.peer-summary-card strong {
  display: block;
  margin-top: 6px;
  color: var(--teacher-text-primary);
  font-size: 20px;
}

.peer-list { display: flex; flex-direction: column; gap: 8px; }
.peer-row {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(140px, 0.45fr) auto auto;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 12px;
  background: var(--tt-surface-muted);
  border: 1px solid var(--tt-border-subtle);
  font-size: 14px;
}

.peer-route {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.peer-reviewer,
.peer-reviewee {
  min-width: 0;
  overflow-wrap: anywhere;
}

.peer-arrow { color: var(--teacher-text-tertiary); }
.peer-group {
  color: var(--teacher-text-secondary);
  font-size: 12px;
}
.peer-score { font-weight: 700; color: var(--teacher-accent); white-space: nowrap; }

/* appeals */
.appeal-list { display: flex; flex-direction: column; gap: 10px; }
.appeal-row {
  padding: 14px 16px;
  border-radius: 12px;
  background: var(--tt-surface-muted);
  border: 1px solid var(--tt-border-subtle);
}
.appeal-row-clickable {
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
}
.appeal-row-clickable:hover {
  border-color: var(--tt-accent-border);
  box-shadow: var(--tt-shadow-sm);
  transform: translateY(-1px);
}
.appeal-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.appeal-type { font-size: 13px; font-weight: 600; color: var(--teacher-text-primary); }
.appeal-status { font-size: 12px; padding: 2px 10px; border-radius: 999px; font-weight: 800; border: 1px solid transparent; }
.appeal-status.pending { color: #b42318; background: color-mix(in srgb, #f97316 14%, var(--tt-surface-muted)); border-color: color-mix(in srgb, #f97316 26%, var(--tt-border)); }
.appeal-status.done { color: #047857; background: color-mix(in srgb, #10b981 14%, var(--tt-surface-muted)); border-color: color-mix(in srgb, #10b981 28%, var(--tt-border)); }
.appeal-status.reject { color: #475467; background: color-mix(in srgb, var(--teacher-text-secondary) 16%, var(--tt-surface-muted)); border-color: color-mix(in srgb, var(--teacher-text-secondary) 24%, var(--tt-border)); }
.appeal-reason { font-size: 13px; color: var(--teacher-text-secondary); margin: 0; }
.appeal-response { font-size: 13px; color: var(--teacher-accent); margin: 4px 0 0; }

.task-switcher {
  height: 42px;
  padding: 0 14px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 18%, var(--teacher-border));
  border-radius: 10px;
  background: color-mix(in srgb, var(--tt-accent) 4%, var(--teacher-surface));
  color: var(--teacher-text-primary);
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  min-width: 180px;
  max-width: 320px;
}

.task-switcher:focus {
  outline: 2px solid var(--tt-accent);
  outline-offset: 2px;
}

.task-switcher:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
