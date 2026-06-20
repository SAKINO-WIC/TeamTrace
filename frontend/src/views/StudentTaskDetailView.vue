<script setup>
import { computed, defineAsyncComponent, nextTick, provide, reactive, ref, watch } from 'vue'
import { areRouteIdsReady, resolvePositiveId } from '../utils/routeIds'
import { clearSessionCache, readSessionCache, writeSessionCache } from '../utils/sessionCache'
import { STUDENT_TASK_PAGE_KEY } from '../composables/studentTaskPageContext'
import { useRoute, useRouter } from 'vue-router'
import {
  claimStudentSubtask,
  createStudentSubtask,
  fetchStudentClassDetail,
  fetchStudentClassGroups,
  fetchStudentGroupSubtaskProgress,
  fetchStudentGroupSubtasks,
  fetchStudentTaskGroupReport,
  fetchStudentTaskDetail,
  fetchStudentTaskWorkspace,
  reviewStudentSubtask,
  submitStudentTaskGroupReport,
  submitStudentSubtask,
} from '../services/student'
import { getCurrentUserId } from '../utils/auth'
import { formatStudentTaskStatus, getStudentTaskWork, upsertStudentTaskWork } from '../utils/student'
import {
  getStudentTaskDetailBackLabel,
  isStudentTaskDetailRoute,
  resolveStudentTaskDetailBackLocation,
} from '../utils/studentTaskNavigation'
import FileUploadZone from '../components/common/FileUploadZone.vue'
import { downloadMediaFile, isUploadedMediaUrl, resolveMediaUrl, resolvePreviewMode } from '../utils/mediaUrl'
import { buildSubmissionContent, parseSubmissionContent } from '../utils/submissionAttachments'
import { normalizeTaskAttachments } from '../utils/taskAttachments'
const StudentTaskPeerReviewsView = defineAsyncComponent(() =>
  import('./StudentTaskPeerReviewsView.vue'),
)
const StudentTaskScoreSummaryView = defineAsyncComponent(() =>
  import('./StudentTaskScoreSummaryView.vue'),
)
const StudentTaskAppealsView = defineAsyncComponent(() =>
  import('./StudentTaskAppealsView.vue'),
)

const route = useRoute()
const router = useRouter()

const classId = computed(() => resolvePositiveId(route.params.classId))
const taskId = computed(() => resolvePositiveId(route.params.taskId))
const currentUserId = ref(getCurrentUserId())

const VALID_STUDENT_TABS = new Set(['tasks', 'progress', 'peer', 'score', 'appeals'])

function resolveStudentTab(value) {
  const tab = String(value || 'tasks')
  return VALID_STUDENT_TABS.has(tab) ? tab : 'tasks'
}

const studentTab = ref(resolveStudentTab(route.query.tab))
const tabMountState = reactive({ peer: false, score: false, appeals: false })

const loading = ref(false)
const message = ref('')
const messageType = ref('info')
const detail = ref(null)
const groupContext = ref(createEmptyGroupContext())
const progress = ref(null)
const progressLoading = ref(false)
const progressLoadError = ref('')
const subtaskLoadError = ref('')
const subtasks = ref([])
const selectedSubtaskId = ref('')
const draftStore = ref({ selectedSubtaskId: '', drafts: {}, legacyDraft: null })

const submitForm = reactive({
  content: '',
  attachment: '',
  uploadedFiles: [],
})

const groupReport = ref(null)
const groupReportForm = reactive({
  content: '',
  attachment: '',
  uploadedFiles: [],
})
const groupReportError = ref('')

const submitDialogError = ref('')

const actionState = reactive({
  createBusy: false,
  claimId: '',
  submitId: '',
  reviewId: '',
  reviewRefresh: false,
  groupReportBusy: false,
  groupReportLoading: false,
})

const dialogState = reactive({
  create: false,
  poolDetail: false,
  submit: false,
  groupReport: false,
  preview: false,
  review: false,
})

const poolDetailSubtask = ref(null)
const selectedReviewSubtaskId = ref('')
const progressMemberDetail = ref(null)

const previewState = reactive({
  url: '',
  mode: '',
  title: '',
  downloadName: '',
  canDownload: false,
})

const reviewForms = reactive({})
const createDialogBodyRef = ref(null)
const createRowRefs = ref([])

let createRowSeed = 0
const createRows = ref(createDefaultCreateRows())

function createDefaultCreateRows() {
  return [createEmptyCreateRow(), createEmptyCreateRow()]
}

function createEmptyCreateRow() {
  createRowSeed += 1
  return {
    key: `create-row-${createRowSeed}`,
    name: '',
    description: '',
    deadline: detail.value?.deadlineLocal || '',
  }
}

function createEmptyGroupContext() {
  return {
    groupId: '',
    groupName: '',
    groupJoinStatus: '',
    groupingLocked: null,
    leaderId: null,
    isLeader: false,
    memberCount: 0,
  }
}

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

function clearSubmitDialogError() {
  submitDialogError.value = ''
}

function setSubmitDialogError(text) {
  submitDialogError.value = text
}

function hasSubmitPayload() {
  if (submitForm.content.trim()) {
    return true
  }
  if (submitForm.attachment.trim()) {
    return true
  }
  return submitForm.uploadedFiles.some((file) => String(file?.url || file?.value || '').trim())
}

function setStudentTab(tab) {
  const nextTab = resolveStudentTab(tab)
  studentTab.value = nextTab
  if (nextTab === 'peer') tabMountState.peer = true
  if (nextTab === 'score') tabMountState.score = true
  if (nextTab === 'appeals') tabMountState.appeals = true
  if (nextTab === 'progress' && canUseGroupData.value && !progress.value && !progressLoading.value) {
    void loadGroupProgress()
  }
  const query = { ...route.query }
  if (nextTab === 'tasks') {
    delete query.tab
  } else {
    query.tab = nextTab
  }
  router.replace({ path: route.path, query })
}

function formatNumber(value, digits = 2) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }

  const parsed = Number(value)
  if (Number.isNaN(parsed)) {
    return '-'
  }

  return parsed.toFixed(digits).replace(/\.0+$/, '').replace(/(\.\d*[1-9])0+$/, '$1')
}

function formatPercent(value) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }

  return `${formatNumber(value)}%`
}

function normalizePercentNumber(value) {
  const parsed = Number(String(value ?? '').replace('%', '').trim())
  return Number.isNaN(parsed) ? 0 : parsed
}

function formatDateTime(value) {
  if (!value) {
    return '-'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }

  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function toDateTimeLocalValue(value) {
  if (!value) {
    return ''
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hour}:${minute}`
}

function toIsoOffsetString(value) {
  if (!value) {
    return ''
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }

  return date.toISOString()
}

function formatPeerReviewPhase(value) {
  const map = {
    disabled: '未开启互评',
    not_started: '未到互评阶段',
    open: '互评进行中',
    closed: '互评已关闭',
  }
  return map[value] || '未知'
}

function formatSubtaskStatus(value) {
  const parsed = Number(value)
  const map = {
    1: '待认领',
    2: '进行中',
    3: '待审批',
    4: '已完成',
  }
  return map[parsed] || '-'
}

function normalizeTaskDetail(raw) {
  return {
    taskId: raw?.taskId ?? raw?.id ?? taskId.value,
    name: raw?.name ?? raw?.taskName ?? '未命名任务',
    description: raw?.description ?? '暂无描述',
    status: formatStudentTaskStatus(raw?.taskStatus ?? raw?.status),
    deadlineAt: raw?.deadline ?? '',
    deadlineText: formatDateTime(raw?.deadline),
    deadlineLocal: toDateTimeLocalValue(raw?.deadline),
    enablePeerReview: Boolean(raw?.enablePeerReview),
    canPeerReviewNow: Boolean(raw?.canPeerReviewNow),
    canSubmitAppeal: Boolean(raw?.canSubmitAppeal),
    peerReviewPhaseRaw: raw?.peerReviewPhase ?? '',
    peerReviewDeadlineValue: raw?.peerReviewDeadline ?? '',
    peerReviewDeadlineText: formatDateTime(raw?.peerReviewDeadline),
    peerReviewDeadline: raw?.peerReviewDeadline ?? '',
    peerReviewOffsetHours: raw?.peerReviewOffsetHours ?? null,
    peerReviewMaxScore: raw?.peerReviewMaxScore ?? 100,
    peerReviewPhase: formatPeerReviewPhase(raw?.peerReviewPhase),
    attachments: normalizeTaskAttachments(raw?.attachments),
  }
}

function normalizeClassContext(raw) {
  return {
    groupId: resolvePositiveId(raw?.groupId ?? raw?.group?.id),
    groupName: raw?.groupName ?? '',
    groupJoinStatus: raw?.groupJoinStatus ?? '',
    groupingLocked: raw?.groupingLocked ?? null,
    leaderId: null,
    isLeader: false,
    memberCount: 0,
  }
}

function extractApiListPayload(response) {
  const envelope = response?.data
  if (!envelope || typeof envelope !== 'object') {
    return []
  }
  if (envelope.success === false) {
    return []
  }
  const payload = envelope.data
  return Array.isArray(payload) ? payload : []
}

function normalizeProgress(raw) {
  const members = Array.isArray(raw?.members) ? raw.members : []
  const mine = members.find((item) => Number(item?.studentId) === currentUserId.value) || null
  const groupProgressValue = normalizePercentNumber(raw?.groupProgressPercent)
  const personalProgressValue = normalizePercentNumber(mine?.progressPercent)

  return {
    groupClaimedSubtasks: raw?.groupClaimedSubtasks ?? 0,
    groupCompletedSubtasks: raw?.groupCompletedSubtasks ?? 0,
    groupTotalSubtasks: raw?.groupTotalSubtasks ?? members.reduce((sum, item) => sum + Number(item?.claimedSubtasks || 0), 0),
    groupProgressValue,
    groupProgressPercent: formatPercent(raw?.groupProgressPercent),
    personalClaimedSubtasks: mine?.claimedSubtasks ?? 0,
    personalCompletedSubtasks: mine?.completedSubtasks ?? 0,
    personalProgressValue,
    personalProgressPercent: formatPercent(mine?.progressPercent),
    members: members.map((item) => ({
      studentId: item?.studentId,
      studentName: item?.studentName || item?.name || '',
      claimedSubtasks: Number(item?.claimedSubtasks ?? 0),
      completedSubtasks: Number(item?.completedSubtasks ?? 0),
      progressPercent: normalizePercentNumber(item?.progressPercent),
    })),
  }
}

const progressMembers = computed(() => {
  const list = progress.value?.members || []
  return [...list]
    .map((item) => ({
      ...item,
      isMe: Number(item.studentId) === Number(currentUserId.value),
    }))
    .sort((left, right) => {
      if (left.isMe !== right.isMe) return left.isMe ? -1 : 1
      return (right.progressPercent || 0) - (left.progressPercent || 0)
    })
})

function memberProgressLabel(member) {
  if (member?.isMe) return member?.studentName || `学生 ${member?.studentId ?? '—'}`
  if (member?.studentName) return member.studentName
  return `学生 ${member?.studentId ?? '—'}`
}

function memberProgressInitial(member) {
  const label = memberProgressLabel(member)
  const ch = label.replace(/^学生\s*/, '').trim()
  return (ch || label).slice(0, 1).toUpperCase()
}

function memberProgressTone(percent) {
  const value = Number(percent) || 0
  if (value >= 100) return 'done'
  if (value >= 60) return 'good'
  if (value > 0) return 'active'
  return 'idle'
}

function normalizeSubtask(raw) {
  const parsedSubmission = parseSubmissionContent(raw?.submissionContent)
  const submissionHistories = Array.isArray(raw?.submissionHistories)
    ? raw.submissionHistories.map((history) => ({
        id: String(history?.id ?? `${raw?.id ?? raw?.subtaskId ?? 'subtask'}-${history?.versionNo ?? ''}`),
        versionNo: Number(history?.versionNo ?? 0),
        submittedAtText: formatDateTime(history?.submittedAt),
        submissionContent: history?.submissionContent ?? '',
        parsedSubmission: parseSubmissionContent(history?.submissionContent),
        current: Boolean(history?.current),
      }))
    : []
  const assigneeId = raw?.assigneeId ?? null
  const statusCode = Number(raw?.status)

  return {
    subtaskId: String(raw?.subtaskId ?? raw?.id ?? ''),
    name: raw?.name ?? '未命名子任务',
    description: raw?.description ?? '',
    qualityRequirement: raw?.qualityRequirement ?? '',
    assigneeId,
    assigneeLabel: assigneeId ? (Number(assigneeId) === currentUserId.value ? (groupContext.value.memberNames?.[currentUserId.value] || `成员 ${currentUserId.value}`) : (groupContext.value.memberNames?.[assigneeId] || `成员 ${assigneeId}`)) : '待认领',
    isMine: assigneeId !== null && Number(assigneeId) === currentUserId.value,
    statusCode,
    statusLabel: formatSubtaskStatus(statusCode),
    deadlineAt: raw?.deadline ?? '',
    deadlineText: formatDateTime(raw?.deadline),
    submittedAtText: formatDateTime(raw?.submittedAt),
    reviewedAtText: formatDateTime(raw?.reviewedAt),
    submissionContent: raw?.submissionContent ?? '',
    parsedSubmission,
    submissionHistories,
    reviewComment: raw?.reviewComment ?? '',
  }
}

function normalizeGroupReport(raw) {
  if (!raw || typeof raw !== 'object') {
    return null
  }
  const parsedReport = parseSubmissionContent(raw?.reportContent)
  const histories = Array.isArray(raw?.histories)
    ? raw.histories.map((history) => ({
        id: String(history?.id ?? `${raw?.reportId ?? 'report'}-${history?.versionNo ?? ''}`),
        versionNo: Number(history?.versionNo ?? 0),
        submittedAtText: formatDateTime(history?.submittedAt),
        reportContent: history?.reportContent ?? '',
        parsedReport: parseSubmissionContent(history?.reportContent),
        current: Boolean(history?.current),
      }))
    : []

  return {
    reportId: String(raw?.reportId ?? ''),
    taskId: raw?.taskId ?? taskId.value,
    groupId: raw?.groupId ?? resolvedGroupId.value,
    submitterId: raw?.submitterId ?? null,
    versionNo: Number(raw?.versionNo ?? 0),
    reportContent: raw?.reportContent ?? '',
    parsedReport,
    submittedAtText: formatDateTime(raw?.submittedAt),
    histories,
  }
}

function syncGroupReportForm() {
  const parsed = groupReport.value?.parsedReport
  groupReportForm.content = parsed?.text ?? ''
  groupReportForm.attachment = parsed?.link ?? ''
  groupReportForm.uploadedFiles = (parsed?.files || []).map((file, index) => ({
    id: `group-report-${index}`,
    name: file.name || '附件',
    url: file.value || '',
    size: file.size ?? null,
  }))
}

function hasGroupReportPayload() {
  if (groupReportForm.content.trim()) return true
  if (groupReportForm.attachment.trim()) return true
  return groupReportForm.uploadedFiles.some((file) => String(file?.url || file?.value || '').trim())
}

function buildGroupReportPayload() {
  return buildSubmissionContent({
    text: groupReportForm.content,
    link: groupReportForm.attachment,
    files: groupReportForm.uploadedFiles,
  })
}

function normalizeStoredDraft(raw) {
  const drafts =
    raw?.drafts && typeof raw.drafts === 'object' && !Array.isArray(raw.drafts)
      ? raw.drafts
      : {}

  const hasLegacyDraft =
    typeof raw?.content === 'string' || typeof raw?.attachment === 'string' || typeof raw?.status === 'string'

  return {
    selectedSubtaskId: raw?.selectedSubtaskId ? String(raw.selectedSubtaskId) : '',
    drafts,
    legacyDraft: hasLegacyDraft
      ? {
          content: raw?.content ?? '',
          attachment: raw?.attachment ?? '',
          status: raw?.status ?? '草稿',
          updatedAt: raw?.updatedAt ?? '',
        }
      : null,
  }
}

function hydrateDraftState() {
  const normalized = normalizeStoredDraft(getStudentTaskWork(taskId.value) || {})
  const myIds = mySubtasks.value.map((item) => item.subtaskId)

  let nextSelectedId = normalized.selectedSubtaskId || selectedSubtaskId.value
  if (!myIds.includes(nextSelectedId)) {
    nextSelectedId = myIds[0] || ''
  }

  if (!normalized.drafts[nextSelectedId] && normalized.legacyDraft && nextSelectedId) {
    normalized.drafts = {
      ...normalized.drafts,
      [nextSelectedId]: normalized.legacyDraft,
    }
  }

  draftStore.value = normalized
  selectedSubtaskId.value = nextSelectedId
  syncSubmissionForm()
}

function syncSubmissionForm() {
  const active = activeSubmissionSubtask.value
  if (!active) {
    submitForm.content = ''
    submitForm.attachment = ''
    submitForm.uploadedFiles = []
    return
  }

  const draft = draftStore.value.drafts?.[active.subtaskId]
  const fallback = active.parsedSubmission

  submitForm.content = draft?.content ?? fallback?.text ?? ''
  submitForm.attachment = draft?.attachment ?? fallback?.link ?? ''
  submitForm.uploadedFiles = Array.isArray(draft?.uploadedFiles)
    ? draft.uploadedFiles.map((file, index) => ({
        id: file.id || `draft-${index}`,
        name: file.name || '附件',
        url: file.url || file.value || '',
        size: file.size ?? null,
      }))
    : (fallback?.files || []).map((file, index) => ({
        id: `saved-${index}`,
        name: file.name || '附件',
        url: file.value || '',
        size: file.size ?? null,
      }))
}

function saveDraft(status = '草稿') {
  const active = activeSubmissionSubtask.value
  if (!active) {
    setMessage('请先选择你负责的任务。', 'error')
    return false
  }

  if (active.statusCode !== 2) {
    setMessage('只有进行中的任务才可以保存草稿。', 'error')
    return false
  }

  const nextDrafts = {
    ...(draftStore.value.drafts || {}),
    [active.subtaskId]: {
      content: submitForm.content.trim(),
      attachment: submitForm.attachment.trim(),
      uploadedFiles: submitForm.uploadedFiles.map((file) => ({
        id: file.id,
        name: file.name,
        url: file.url,
        size: file.size ?? null,
      })),
      status,
      updatedAt: new Date().toISOString(),
    },
  }

  draftStore.value = {
    selectedSubtaskId: active.subtaskId,
    drafts: nextDrafts,
    legacyDraft: null,
  }

  upsertStudentTaskWork(taskId.value, {
    selectedSubtaskId: active.subtaskId,
    drafts: nextDrafts,
  })

  return true
}

function buildSubmissionPayload() {
  return buildSubmissionContent({
    text: submitForm.content,
    link: submitForm.attachment,
    files: submitForm.uploadedFiles,
  })
}

function handleUploadError(message) {
  if (message) {
    setMessage(message, 'error')
  }
}

const resolvedGroupId = computed(() => resolvePositiveId(groupContext.value.groupId))

const canUseGroupData = computed(() => {
  return Boolean(resolvedGroupId.value) && groupContext.value.groupJoinStatus !== '待审批'
})

const leaderCanManage = computed(() => {
  return canUseGroupData.value && groupContext.value.isLeader
})

const mySubtasks = computed(() => subtasks.value.filter((item) => item.isMine))

provide(STUDENT_TASK_PAGE_KEY, {
  detail,
  groupContext,
  classId,
  taskId,
  canUseGroupData,
  mySubtasks,
  setStudentTab,
  reloadTaskDetail: () => loadDetail({ force: true }),
})
const pendingReviewSubtasks = computed(() => {
  if (!groupContext.value.isLeader) {
    return []
  }
  return subtasks.value.filter((item) => item.statusCode === 3)
})

const reviewPoolSubtasks = computed(() => {
  if (!groupContext.value.isLeader) {
    return []
  }
  return [...subtasks.value].sort((left, right) => {
    const order = { 3: 0, 2: 1, 1: 2, 4: 3 }
    const leftOrder = order[Number(left.statusCode)] ?? 9
    const rightOrder = order[Number(right.statusCode)] ?? 9
    if (leftOrder !== rightOrder) return leftOrder - rightOrder
    return String(left.name || '').localeCompare(String(right.name || ''), 'zh-CN')
  })
})

const activeReviewSubtask = computed(() => {
  if (!reviewPoolSubtasks.value.length) return null
  return (
    reviewPoolSubtasks.value.find((item) => item.subtaskId === selectedReviewSubtaskId.value) ||
    reviewPoolSubtasks.value[0]
  )
})

const groupSubtaskRows = computed(() => {
  return [...subtasks.value].sort((left, right) => {
    const leftAssignee = String(left.assigneeLabel || '')
    const rightAssignee = String(right.assigneeLabel || '')
    if (leftAssignee !== rightAssignee) return leftAssignee.localeCompare(rightAssignee, 'zh-CN')
    return String(left.name || '').localeCompare(String(right.name || ''), 'zh-CN')
  })
})

const groupSubtasksByMember = computed(() => {
  const map = new Map()
  groupSubtaskRows.value.forEach((item) => {
    const key = item.assigneeId ? String(item.assigneeId) : 'unassigned'
    if (!map.has(key)) {
      map.set(key, {
        key,
        label: item.assigneeLabel,
        items: [],
      })
    }
    map.get(key).items.push(item)
  })
  return Array.from(map.values())
})

const progressMemberDetailTasks = computed(() => {
  if (!progressMemberDetail.value) return []
  const memberId = String(progressMemberDetail.value.studentId ?? '')
  return groupSubtaskRows.value.filter((item) => String(item.assigneeId ?? '') === memberId)
})
const pooledSubtasks = computed(() =>
  subtasks.value.filter((item) => !item.assigneeId && item.statusCode === 1),
)
const displayPooledSubtasks = computed(() => pooledSubtasks.value)
const displayMySubtasks = computed(() => mySubtasks.value)
const pooledCountText = computed(() => `${pooledSubtasks.value.length} 项`)
const myCountText = computed(() => `${mySubtasks.value.length} 项`)
const poolHasClaimedOnly = computed(
  () => subtasks.value.length > 0 && pooledSubtasks.value.length === 0,
)

const activeSubmissionSubtask = computed(() => {
  return mySubtasks.value.find((item) => item.subtaskId === selectedSubtaskId.value) || mySubtasks.value[0] || null
})

const poolDetailCanClaim = computed(() => {
  const item = poolDetailSubtask.value
  return Boolean(item) && Number(item.statusCode) === 1 && !item.assigneeId
})

function isBeforeSubtaskDeadline(item) {
  if (!item?.deadlineAt) return true
  const deadline = new Date(item.deadlineAt)
  return !Number.isNaN(deadline.getTime()) && Date.now() < deadline.getTime()
}

function isBeforeTaskDeadline() {
  if (!detail.value?.deadlineAt) return true
  const deadline = new Date(detail.value.deadlineAt)
  return !Number.isNaN(deadline.getTime()) && Date.now() < deadline.getTime()
}

function canSubmitInProgressSubtask(item) {
  return Boolean(item?.isMine) && Number(item.statusCode) === 2 && isBeforeTaskDeadline() && isBeforeSubtaskDeadline(item)
}

function canModifySubmittedSubtask(item) {
  const status = Number(item?.statusCode)
  return Boolean(item?.isMine) && (status === 3 || status === 4) && isBeforeTaskDeadline() && isBeforeSubtaskDeadline(item)
}

const canCreateSubtask = computed(() => leaderCanManage.value && isBeforeTaskDeadline())
const canSubmitGroupReport = computed(() => leaderCanManage.value && isBeforeTaskDeadline())
const groupReportButtonTitle = computed(() => {
  if (!canUseGroupData.value) return '加入小组后可查看小组总报告'
  if (!groupContext.value.isLeader) return '仅组长可提交小组总报告'
  if (!isBeforeTaskDeadline()) return '总任务已截止，不能再提交或修改小组总报告'
  return groupReport.value ? '查看或修改小组总报告' : '提交小组总报告'
})
const groupReportVersionText = computed(() => {
  if (!groupReport.value?.versionNo) return '尚未提交'
  return `已提交版本 V${groupReport.value.versionNo} · ${groupReport.value.submittedAtText || '暂无时间'}，保存后生成新版本`
})

const workspaceEditable = computed(() => {
  const item = activeSubmissionSubtask.value
  if (!item?.isMine) return false
  return canSubmitInProgressSubtask(item) || canModifySubmittedSubtask(item)
})

function submissionActionLabel(item) {
  if (Number(item?.statusCode) === 2) return '提交任务'
  if (canModifySubmittedSubtask(item)) return '修改提交'
  return '查看提交'
}

function submitButtonLabel(item) {
  if (actionState.submitId === item?.subtaskId) {
    return canModifySubmittedSubtask(item) ? '修改中...' : '提交中...'
  }
  return canModifySubmittedSubtask(item) ? '确认修改提交' : '提交当前任务'
}

const groupSummaryText = computed(() => {
  if (!resolvedGroupId.value) {
    return '你当前还没有加入小组，暂时只能查看任务基础信息。'
  }

  if (groupContext.value.groupJoinStatus === '待审批') {
    return '你的小组申请仍在待审批，审批通过后才能查看和操作当前任务下的子任务。'
  }

  return `当前位于 ${groupContext.value.groupName || '未命名小组'}，身份为${groupContext.value.isLeader ? '组长' : '组员'}。`
})

const noGroupTaskActionText = computed(() => {
  if (!resolvedGroupId.value) {
    return '先前往班级小组加入或创建小组，之后就能参与当前任务。'
  }

  if (groupContext.value.groupJoinStatus === '待审批') {
    return '你的小组申请仍在待审批，通过后即可参与子任务。'
  }

  return ''
})

const emptyPoolTitle = computed(() => {
  if (poolHasClaimedOnly.value) {
    return '暂无可认领子任务'
  }
  return leaderCanManage.value ? '还没有子任务' : '等待组长拆解任务'
})

const emptyPoolDesc = computed(() => {
  if (poolHasClaimedOnly.value) {
    return '子任务池中的任务都已被认领，请在右侧「我的任务」中继续处理。'
  }
  if (leaderCanManage.value) {
    return '可以先创建子任务，明确成员分工、截止时间和验收要求。'
  }

  return '当前小组还没有可认领的子任务，组长拆解后会显示在这里。'
})

const emptyMyTaskDesc = computed(() => {
  if (!canUseGroupData.value) {
    return noGroupTaskActionText.value
  }

  return '可以先从左侧子任务池认领，或等待组长分配。'
})

const createDeadlineMax = computed(() => detail.value?.deadlineLocal || '')
const detailBackLocation = computed(() => resolveStudentTaskDetailBackLocation(classId.value, route.query))
const detailBackLabel = computed(() => getStudentTaskDetailBackLabel(route.query))

// [已移除]学生端子任务进度条函数

function resolveClaimLabel(item) {
  if (!item?.assigneeId) return '未认领'
  return `已认领 · ${item.assigneeLabel}`
}

function resolveAttachmentPreviewMode(url, title = '') {
  const resolved = resolveMediaUrl(url)
  const mode = resolvePreviewMode(resolved)
  if (mode === 'image' || mode === 'pdf') return mode
  return resolvePreviewMode(title)
}

function openAttachmentPreview(url, title = '附件') {
  const resolved = resolveMediaUrl(url)
  if (!resolved) return
  const mode = resolveAttachmentPreviewMode(resolved, title)
  if (mode === 'pdf') {
    window.open(resolved, '_blank', 'noopener,noreferrer')
    return
  }
  if (mode !== 'image') {
    if (isUploadedMediaUrl(resolved)) {
      downloadAttachment(resolved, title)
    } else {
      window.open(resolved, '_blank', 'noopener,noreferrer')
    }
    return
  }
  previewState.url = resolved
  previewState.mode = mode
  previewState.title = title
  previewState.downloadName = title
  previewState.canDownload = isUploadedMediaUrl(resolved)
  dialogState.preview = true
}

function clearAttachmentPreview() {
  previewState.url = ''
  previewState.mode = ''
  previewState.title = ''
  previewState.downloadName = ''
  previewState.canDownload = false
  dialogState.preview = false
}

async function downloadAttachment(url, title = '附件') {
  try {
    await downloadMediaFile(url, title)
  } catch (error) {
    setMessage(error?.message || '文件下载失败，请稍后重试。', 'error')
  }
}

function canPreviewAttachment(url, title = '') {
  const mode = resolveAttachmentPreviewMode(url, title)
  return mode === 'image' || mode === 'pdf'
}

function displayLinkText(value) {
  const text = String(value || '').trim()
  return text || '链接'
}

function statusClass(statusCode) {
  const parsed = Number(statusCode)
  return `status-${parsed || 0}`
}

function canReviewSubtask(item) {
  return leaderCanManage.value && Number(item?.statusCode) === 3
}

function progressCacheKey() {
  return `student:progress:${classId.value}:${taskId.value}:${resolvedGroupId.value}`
}

function workspaceCacheKey() {
  return `student:workspace:${classId.value}:${taskId.value}`
}

function applyWorkspacePayload(raw) {
  if (!raw || typeof raw !== 'object') {
    return
  }

  if (raw.task) {
    detail.value = normalizeTaskDetail(raw.task)
  }

  if (raw.classContext) {
    groupContext.value = normalizeClassContext(raw.classContext)
  }

  if (Array.isArray(raw.groups)) {
    syncGroupContextFromGroups(raw.groups)
  }

  if (!canUseGroupData.value) {
    subtasks.value = []
    progress.value = null
    groupReport.value = null
    hydrateDraftState()
    return
  }

  if (Array.isArray(raw.subtasks)) {
    subtasks.value = raw.subtasks.map(normalizeSubtask)
    hydrateDraftState()
  }

  if (raw.progress) {
    const normalized = normalizeProgress(raw.progress?.data || raw.progress || {})
    progress.value = normalized
    writeSessionCache(progressCacheKey(), normalized)
  }

  if (raw.groupReport !== undefined) {
    groupReport.value = normalizeGroupReport(raw.groupReport)
  } else {
    void loadGroupReport()
  }

  resetCreateRows()
  syncEmbeddedTabMount()
}

function syncEmbeddedTabMount() {
  if (studentTab.value === 'peer') {
    tabMountState.peer = true
  }
  if (studentTab.value === 'score') {
    tabMountState.score = true
  }
  if (studentTab.value === 'appeals') {
    tabMountState.appeals = true
  }
}

async function loadWorkspaceLegacy() {
  const [taskResult, classResult, groupResult] = await Promise.allSettled([
    fetchStudentTaskDetail(classId.value, taskId.value),
    fetchStudentClassDetail(classId.value),
    fetchStudentClassGroups(classId.value),
  ])

  if (taskResult.status === 'rejected') {
    throw taskResult.reason
  }

  const payload = {
    task: taskResult.value?.data?.data || {},
    classContext: classResult.status === 'fulfilled' ? classResult.value?.data?.data || {} : {},
    groups: groupResult.status === 'fulfilled' && Array.isArray(groupResult.value?.data?.data)
      ? groupResult.value.data.data
      : [],
    subtasks: null,
    progress: null,
  }

  if (classResult.status === 'rejected') {
    throw new Error('当前班级小组上下文加载失败，暂时无法操作子任务。')
  }

  applyWorkspacePayload(payload)

  if (canUseGroupData.value && !subtasks.value.length) {
    await Promise.allSettled([loadGroupResources(), loadGroupProgress({ force: true })])
  }
}

function syncGroupContextFromGroups(groups) {
  if (!Array.isArray(groups) || !groups.length) {
    return
  }

  const currentId = resolvedGroupId.value
  let currentGroup = currentId
    ? groups.find((item) => resolvePositiveId(item?.groupId ?? item?.id) === currentId)
    : null

  if (!currentGroup && currentUserId.value !== null) {
    currentGroup =
      groups.find((item) => {
        const memberIds = Array.isArray(item?.memberStudentIds) ? item.memberStudentIds : []
        return memberIds.some((id) => Number(id) === Number(currentUserId.value))
      }) || null
  }

  if (!currentGroup) {
    return
  }

  const nextGroupId = resolvePositiveId(currentGroup?.groupId ?? currentGroup?.id)
  if (!nextGroupId) {
    return
  }

  const memberIds = Array.isArray(currentGroup?.memberStudentIds) ? currentGroup.memberStudentIds : []
  const leaderId = currentGroup?.leaderId ?? null

  groupContext.value = {
    ...groupContext.value,
    groupId: nextGroupId,
    groupName: currentGroup?.name ?? currentGroup?.groupName ?? groupContext.value.groupName,
    leaderId,
    isLeader: leaderId !== null && Number(leaderId) === Number(currentUserId.value),
    memberCount: memberIds.length,
    memberNames: currentGroup?.memberNames || {},
  }
}

async function loadGroupProgress(options = {}) {
  if (!canUseGroupData.value) {
    progress.value = null
    progressLoadError.value = ''
    return
  }
  if (progressLoading.value) {
    return
  }

  const force = options.force === true
  const cached = force ? null : readSessionCache(progressCacheKey(), 120000)
  if (cached && typeof cached === 'object') {
    progress.value = cached
  }

  progressLoading.value = true
  progressLoadError.value = ''
  try {
    const progressResult = await fetchStudentGroupSubtaskProgress(
      classId.value,
      taskId.value,
      resolvedGroupId.value,
    )
    const payload = progressResult?.data?.data
    const normalized = normalizeProgress(payload?.data || payload || {})
    progress.value = normalized
    writeSessionCache(progressCacheKey(), normalized)
  } catch (error) {
    if (!cached) {
      progress.value = null
    }
    progressLoadError.value = error?.message || '小组进度暂时不可用，子任务仍可继续处理。'
  } finally {
    progressLoading.value = false
  }
}

async function loadGroupResources(options = {}) {
  progressLoadError.value = ''
  subtaskLoadError.value = ''
  if (!canUseGroupData.value) {
    subtasks.value = []
    progress.value = null
    groupReport.value = null
    hydrateDraftState()
    return
  }

  const preferredSubtaskId = options.preferredSubtaskId || ''

  let subtasksResult
  let groupReportResult
  try {
    ;[subtasksResult, groupReportResult] = await Promise.all([
      fetchStudentGroupSubtasks(classId.value, taskId.value, resolvedGroupId.value),
      fetchStudentTaskGroupReport(classId.value, taskId.value, resolvedGroupId.value).catch(() => null),
    ])
  } catch (error) {
    subtasks.value = []
    progress.value = null
    subtaskLoadError.value = error?.message || '子任务列表暂时加载失败，请稍后重试。'
    hydrateDraftState()
    return
  }

  const payload = subtasksResult?.data?.data
  const normalizedPayload = Array.isArray(payload)
    ? payload
    : Array.isArray(payload?.list)
      ? payload.list
      : extractApiListPayload(subtasksResult)
  subtasks.value = normalizedPayload.map(normalizeSubtask)
  groupReport.value = normalizeGroupReport(groupReportResult?.data?.data)

  if (preferredSubtaskId) {
    selectedSubtaskId.value = preferredSubtaskId
  }

  hydrateDraftState()

  if (options.syncProgress) {
    await loadGroupProgress({ force: true })
    clearSessionCache(workspaceCacheKey())
  }
}

function resetCreateRows() {
  createRows.value = createDefaultCreateRows()
  createRowRefs.value = []
}

async function addCreateRow() {
  createRows.value.push(createEmptyCreateRow())
  await scrollToCreateRow(createRows.value.length - 1)
}

function removeCreateRow(index) {
  if (createRows.value.length === 1) {
    resetCreateRows()
    return
  }
  createRows.value.splice(index, 1)
  createRowRefs.value.splice(index, 1)
}

function setCreateRowRef(el, index) {
  if (el) {
    createRowRefs.value[index] = el
  }
}

async function scrollToCreateRow(index) {
  await nextTick()
  const containerEl = createDialogBodyRef.value
  const rowEl = createRowRefs.value[index]
  if (!rowEl || !(rowEl instanceof HTMLElement)) {
    return
  }

  if (containerEl instanceof HTMLElement && containerEl.contains(rowEl)) {
    rowEl.scrollIntoView({
      behavior: 'smooth',
      block: 'nearest',
      inline: 'nearest',
    })
  }

  const firstInput = rowEl.querySelector('input, textarea')
  if (firstInput instanceof HTMLElement) {
    firstInput.focus({ preventScroll: true })
  }
}

function openCreateDialog() {
  if (!leaderCanManage.value) {
    setMessage('只有组长可以拆解子任务。', 'error')
    return
  }
  if (!isBeforeTaskDeadline()) {
    setMessage('总任务已超过截止时间，不能再创建子任务。', 'error')
    return
  }
  clearAttachmentPreview()
  resetCreateRows()
  dialogState.create = true
  nextTick(() => {
    scrollToCreateRow(0)
  })
}

function closeCreateDialog() {
  dialogState.create = false
}

function goClassGroups() {
  router.push({ path: `/student/classes/${classId.value}/groups` })
}

function openSubmitDialog(item) {
  if (!item?.subtaskId) return
  clearAttachmentPreview()
  clearSubmitDialogError()
  selectedSubtaskId.value = item.subtaskId
  syncSubmissionForm()
  dialogState.submit = true
}

function openPoolDetailDialog(item) {
  if (!item?.subtaskId) return
  clearAttachmentPreview()
  poolDetailSubtask.value = item
  dialogState.poolDetail = true
}

function closePoolDetailDialog() {
  dialogState.poolDetail = false
  poolDetailSubtask.value = null
  clearAttachmentPreview()
}

async function claimFromPoolDetail() {
  if (!poolDetailSubtask.value) return
  await claimSubtaskAction(poolDetailSubtask.value)
  closePoolDetailDialog()
}

function closeSubmitDialog() {
  dialogState.submit = false
  clearSubmitDialogError()
  clearAttachmentPreview()
}

function openReviewDialog() {
  if (!leaderCanManage.value) {
    setMessage('只有组长可以打开审批任务池。', 'error')
    return
  }
  clearAttachmentPreview()
  selectedReviewSubtaskId.value = reviewPoolSubtasks.value[0]?.subtaskId || ''
  dialogState.review = true
}

async function loadGroupReport(options = {}) {
  if (!canUseGroupData.value) {
    groupReport.value = null
    return
  }
  if (actionState.groupReportLoading) return

  actionState.groupReportLoading = true
  try {
    const { data } = await fetchStudentTaskGroupReport(
      classId.value,
      taskId.value,
      resolvedGroupId.value,
    )
    groupReport.value = normalizeGroupReport(data?.data)
  } catch (error) {
    if (options.showError) {
      setMessage(error?.message || '小组总报告加载失败，请稍后重试。', 'error')
    }
  } finally {
    actionState.groupReportLoading = false
  }
}

async function openGroupReportDialog() {
  if (!canUseGroupData.value) {
    setMessage('加入小组后才能查看小组总报告。', 'error')
    return
  }
  if (!groupContext.value.isLeader) {
    setMessage('仅组长可提交小组总报告。', 'error')
    return
  }

  clearAttachmentPreview()
  groupReportError.value = ''
  await loadGroupReport({ showError: true })
  syncGroupReportForm()
  dialogState.groupReport = true
}

function closeGroupReportDialog() {
  dialogState.groupReport = false
  groupReportError.value = ''
  clearAttachmentPreview()
}

async function submitGroupReportAction() {
  groupReportError.value = ''
  if (!canSubmitGroupReport.value) {
    const text = groupContext.value.isLeader
      ? '总任务已截止，不能再提交或修改小组总报告。'
      : '仅组长可提交小组总报告。'
    groupReportError.value = text
    setMessage(text, 'error')
    return
  }
  if (!hasGroupReportPayload()) {
    groupReportError.value = '请填写总报告说明，或上传文件，或填写网盘 / 仓库链接。'
    return
  }

  actionState.groupReportBusy = true
  try {
    const { data } = await submitStudentTaskGroupReport(
      classId.value,
      taskId.value,
      resolvedGroupId.value,
      { reportContent: buildGroupReportPayload() },
    )
    groupReport.value = normalizeGroupReport(data?.data)
    syncGroupReportForm()
    setMessage(groupReport.value?.versionNo > 1 ? '小组总报告已更新。' : '小组总报告已提交。', 'success')
  } catch (error) {
    const text = error?.message || '小组总报告提交失败，请稍后重试。'
    groupReportError.value = text
    setMessage(text, 'error')
  } finally {
    actionState.groupReportBusy = false
  }
}

function closeReviewDialog() {
  dialogState.review = false
  selectedReviewSubtaskId.value = ''
  clearAttachmentPreview()
}

function selectReviewSubtask(item) {
  if (!item?.subtaskId) return
  selectedReviewSubtaskId.value = item.subtaskId
}

function openProgressMemberDetail(member) {
  if (!member?.studentId) return
  progressMemberDetail.value = member
}

function closeProgressMemberDetail() {
  progressMemberDetail.value = null
}

async function refreshReviewPool() {
  if (actionState.reviewRefresh) return
  actionState.reviewRefresh = true
  try {
    await loadGroupResources({ syncProgress: true })
    selectedReviewSubtaskId.value =
      reviewPoolSubtasks.value.find((item) => item.subtaskId === selectedReviewSubtaskId.value)?.subtaskId ||
      reviewPoolSubtasks.value[0]?.subtaskId ||
      ''
    setMessage('任务池已刷新。', 'success')
  } catch (error) {
    setMessage(error?.message || '刷新任务池失败，请稍后重试。', 'error')
  } finally {
    actionState.reviewRefresh = false
  }
}

async function loadDetail(options = {}) {
  if (!areRouteIdsReady(classId.value, taskId.value)) {
    setMessage('页面地址无效，请从任务中心重新进入该任务。', 'error')
    loading.value = false
    return
  }

  const force = options.force === true
  const hasSameRoute =
    detail.value &&
    resolvePositiveId(detail.value?.taskId) === taskId.value &&
    classId.value === resolvePositiveId(route.params.classId)

  if (!hasSameRoute) {
    message.value = ''
    detail.value = null
    groupContext.value = createEmptyGroupContext()
    progress.value = null
    progressLoadError.value = ''
    subtaskLoadError.value = ''
    subtasks.value = []
    groupReport.value = null
    selectedSubtaskId.value = ''
    draftStore.value = { selectedSubtaskId: '', drafts: {}, legacyDraft: null }
  }

  const cached = !force && hasSameRoute ? readSessionCache(workspaceCacheKey(), 300000) : null
  if (cached && typeof cached === 'object') {
    applyWorkspacePayload(cached)
    loading.value = false
  } else if (!hasSameRoute || !detail.value) {
    loading.value = true
  } else {
    loading.value = false
  }

  try {
    let workspaceRes
    try {
      workspaceRes = await fetchStudentTaskWorkspace(classId.value, taskId.value)
    } catch (workspaceError) {
      const status = workspaceError?.status
      if (status !== 404 && status !== 405) {
        throw workspaceError
      }
      await loadWorkspaceLegacy()
      writeSessionCache(workspaceCacheKey(), {
        task: detail.value,
        classContext: {
          groupId: groupContext.value.groupId,
          groupName: groupContext.value.groupName,
          groupJoinStatus: groupContext.value.groupJoinStatus,
          groupingLocked: groupContext.value.groupingLocked,
        },
        groups: [],
        subtasks: subtasks.value,
        progress: progress.value,
      })
      return
    }

    const payload = workspaceRes?.data?.data
    applyWorkspacePayload(payload)
    writeSessionCache(workspaceCacheKey(), payload)
    syncEmbeddedTabMount()
  } catch (error) {
    if (!cached) {
      setMessage(error?.message || '加载任务详情失败，请稍后重试。', 'error')
    }
  } finally {
    loading.value = false
  }
}

async function createSubtaskAction() {
  if (!leaderCanManage.value) {
    setMessage('只有组长可以拆解子任务。', 'error')
    return
  }
  if (!isBeforeTaskDeadline()) {
    setMessage('总任务已超过截止时间，不能再创建子任务。', 'error')
    return
  }

  const normalizedRows = createRows.value
    .map((row, index) => ({
      index,
      name: String(row.name || '').trim(),
      description: String(row.description || '').trim(),
      deadline: String(row.deadline || '').trim(),
    }))
    .filter((row) => row.name || row.description || row.deadline)

  if (!normalizedRows.length) {
    setMessage('请至少填写一条子任务。', 'error')
    return
  }

  const taskDeadline = detail.value?.deadlineAt ? new Date(detail.value.deadlineAt) : null

  for (const row of normalizedRows) {
    if (!row.name) {
      setMessage(`第 ${row.index + 1} 条子任务还没有填写名称。`, 'error')
      return
    }

    if (!row.deadline) {
      setMessage(`第 ${row.index + 1} 条子任务还没有填写截止时间。`, 'error')
      return
    }

    const createDeadline = new Date(row.deadline)
    if (
      taskDeadline &&
      !Number.isNaN(taskDeadline.getTime()) &&
      !Number.isNaN(createDeadline.getTime()) &&
      createDeadline.getTime() > taskDeadline.getTime()
    ) {
      setMessage(`第 ${row.index + 1} 条子任务的截止时间不能晚于总任务截止时间。`, 'error')
      return
    }
  }

  actionState.createBusy = true
  try {
    for (const row of normalizedRows) {
      await createStudentSubtask(classId.value, taskId.value, resolvedGroupId.value, {
        name: row.name,
        description: row.description || null,
        deadline: toIsoOffsetString(row.deadline),
      })
    }

    resetCreateRows()
    closeCreateDialog()
    setMessage(`已创建 ${normalizedRows.length} 条子任务，当前均已进入子任务池。`, 'success')
    await loadGroupResources({ syncProgress: true })
  } catch (error) {
    setMessage(error.message || '创建子任务失败，请稍后重试。', 'error')
  } finally {
    actionState.createBusy = false
  }
}

async function claimSubtaskAction(item) {
  if (!canUseGroupData.value) {
    setMessage('当前尚不具备操作小组子任务的条件。', 'error')
    return
  }

  actionState.claimId = item.subtaskId
  try {
    await claimStudentSubtask(classId.value, taskId.value, resolvedGroupId.value, item.subtaskId)
    setMessage(`已认领子任务：${item.name}`, 'success')
    await loadGroupResources({ preferredSubtaskId: item.subtaskId, syncProgress: true })
    const claimed = subtasks.value.find((subtask) => subtask.subtaskId === item.subtaskId)
    if (claimed) {
      selectedSubtaskId.value = claimed.subtaskId
      syncSubmissionForm()
    }
  } catch (error) {
    setMessage(error.message || '认领子任务失败，请稍后重试。', 'error')
  } finally {
    actionState.claimId = ''
  }
}

async function submitWork() {
  clearSubmitDialogError()

  const active = activeSubmissionSubtask.value
  if (!active) {
    const text = '请先选择你负责的任务。'
    setSubmitDialogError(text)
    setMessage(text, 'error')
    return
  }

  if (!workspaceEditable.value) {
    const text = '当前状态或截止时间不允许修改提交。'
    setSubmitDialogError(text)
    setMessage(text, 'error')
    return
  }

  if (!hasSubmitPayload()) {
    const text = '请填写完成说明，或上传文件，或填写网盘 / 仓库链接。'
    setSubmitDialogError(text)
    return
  }

  actionState.submitId = active.subtaskId
  try {
    await submitStudentSubtask(classId.value, taskId.value, resolvedGroupId.value, active.subtaskId, {
      submissionContent: buildSubmissionPayload(),
    })
    saveDraft('已提交')
    closeSubmitDialog()
    setMessage(`${canModifySubmittedSubtask(active) ? '已修改提交' : '已提交任务'}：${active.name}`, 'success')
    try {
      await loadGroupResources({ preferredSubtaskId: active.subtaskId, syncProgress: true })
    } catch (error) {
      setMessage(error?.message || '提交成功，但刷新任务列表失败，请手动刷新页面。', 'error')
    }
  } catch (error) {
    const text = error.message || '提交任务失败，请稍后重试。'
    setSubmitDialogError(text)
    setMessage(text, 'error')
  } finally {
    actionState.submitId = ''
  }
}

async function reviewPendingSubtask(item, approved) {
  if (!groupContext.value.isLeader) {
    setMessage('只有组长可以审批子任务。', 'error')
    return
  }

  const reviewComment = String(reviewForms[item.subtaskId] || '').trim()
  if (!approved && !reviewComment) {
    setMessage('打回修改时请填写审批说明。', 'error')
    return
  }

  actionState.reviewId = item.subtaskId
  const currentIndex = reviewPoolSubtasks.value.findIndex((task) => task.subtaskId === item.subtaskId)
  const fallbackNext =
    reviewPoolSubtasks.value[currentIndex + 1] ||
    reviewPoolSubtasks.value[currentIndex - 1] ||
    null
  try {
    await reviewStudentSubtask(classId.value, taskId.value, resolvedGroupId.value, item.subtaskId, {
      approved,
      reviewComment: reviewComment || null,
    })
    reviewForms[item.subtaskId] = ''
    setMessage(approved ? `已通过子任务：${item.name}` : `已打回子任务：${item.name}`, 'success')
    await loadGroupResources({ syncProgress: true })
    const stillExistsNext = reviewPoolSubtasks.value.find((task) => task.subtaskId === fallbackNext?.subtaskId)
    const refreshedCurrent = reviewPoolSubtasks.value.find((task) => task.subtaskId === item.subtaskId)
    selectedReviewSubtaskId.value =
      stillExistsNext?.subtaskId || refreshedCurrent?.subtaskId || reviewPoolSubtasks.value[0]?.subtaskId || ''
  } catch (error) {
    setMessage(error.message || '审批子任务失败，请稍后重试。', 'error')
  } finally {
    actionState.reviewId = ''
  }
}

watch(
  [classId, taskId],
  () => {
    if (!isStudentTaskDetailRoute(route)) {
      return
    }
    currentUserId.value = getCurrentUserId()
    loadDetail()
  },
  { immediate: true },
)

watch(
  () => activeSubmissionSubtask.value?.subtaskId,
  () => {
    syncSubmissionForm()
  },
)

watch(
  () => route.query.tab,
  (value) => {
    if (!isStudentTaskDetailRoute(route)) {
      return
    }
    const nextTab = resolveStudentTab(value)
    studentTab.value = nextTab
    if (nextTab === 'progress' && canUseGroupData.value && !progress.value && !progressLoading.value) {
      void loadGroupProgress()
    }
    if (nextTab === 'peer') tabMountState.peer = true
    if (nextTab === 'score') tabMountState.score = true
    if (nextTab === 'appeals') tabMountState.appeals = true
  },
)
</script>

<template>
  <div class="student-page">
    <section class="card hero task-hero-card">
      <div class="hero-layout">
        <div class="hero-copy">
          <p class="eyebrow">班级任务</p>
          <h2>{{ detail?.name || '任务详情' }}</h2>
          <p class="hero-deadline">
            <span class="hero-deadline-label">截止时间</span>
            <span class="hero-deadline-value">{{ detail?.deadlineText || '—' }}</span>
          </p>
        </div>
        <div class="actions hero-actions">
          <button class="hero-btn hero-btn-create" type="button" :disabled="!canCreateSubtask" @click="openCreateDialog">
            创建子任务
          </button>
          <button class="hero-btn hero-btn-review" type="button" :disabled="!leaderCanManage" @click="openReviewDialog">
            审批任务池
          </button>
          <button
            class="hero-btn hero-btn-report"
            type="button"
            :disabled="!canSubmitGroupReport"
            :title="groupReportButtonTitle"
            @click="openGroupReportDialog"
          >
            提交小组总报告
          </button>
        </div>
      </div>
    </section>

    <p v-if="message" class="message" :class="messageType">{{ message }}</p>
    <p
      v-if="subtaskLoadError && subtaskLoadError !== message"
      class="message error"
    >
      {{ subtaskLoadError }}
    </p>

    <nav class="task-tabs task-tabs-full">
      <button class="tab-btn" :class="{ active: studentTab === 'tasks' }" type="button" @click="setStudentTab('tasks')">我的任务</button>
      <button class="tab-btn" :class="{ active: studentTab === 'progress' }" type="button" @click="setStudentTab('progress')">小组进度</button>
      <button class="tab-btn" :class="{ active: studentTab === 'peer' }" type="button" @click="setStudentTab('peer')">互评</button>
      <button class="tab-btn" :class="{ active: studentTab === 'score' }" type="button" @click="setStudentTab('score')">成绩</button>
      <button class="tab-btn" :class="{ active: studentTab === 'appeals' }" type="button" @click="setStudentTab('appeals')">申诉</button>
    </nav>

    <div v-show="studentTab === 'tasks'" class="tasks-workspace">

    <section class="card task-desc-panel">
      <p class="task-desc-label">任务描述</p>
      <p class="task-desc-body">{{ detail?.description || '暂无任务描述' }}</p>
      <div v-if="detail?.attachments?.length" class="task-materials">
        <p class="task-desc-label">任务附件</p>
        <div class="attachment-file-stack compact">
          <div v-for="file in detail.attachments" :key="file.id" class="attachment-file-row">
            <div>
              <strong class="attachment-file-name">{{ file.name }}</strong>
              <span>{{ file.isFile ? '文件资料' : displayLinkText(file.url) }}</span>
            </div>
            <div class="attachment-actions">
              <button
                v-if="file.isFile && canPreviewAttachment(file.url, file.name)"
                class="secondary-btn"
                type="button"
                @click="openAttachmentPreview(file.url, file.name)"
              >
                查看
              </button>
              <button
                v-if="file.isFile"
                class="attachment-link"
                type="button"
                @click="downloadAttachment(file.url, file.name)"
              >
                下载
              </button>
              <a v-else class="attachment-link" :href="resolveMediaUrl(file.url)" target="_blank" rel="noreferrer">
                {{ displayLinkText(file.url) }}
              </a>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="layout layout-spaced">
      <article class="card panel pool-panel">
        <div class="panel-head">
          <h3>子任务池</h3>
          <span class="panel-count">{{ pooledCountText }}</span>
        </div>

        <div v-if="!canUseGroupData" class="empty-workspace">
          <div>
            <h4>先加入小组后参与子任务</h4>
            <div class="empty-actions">
              <button class="primary-btn" type="button" @click="goClassGroups">前往班级小组</button>
            </div>
          </div>
        </div>

        <div v-else-if="subtaskLoadError" class="empty-workspace error-workspace">
          <div>
            <h4>暂时无法读取子任务池</h4>
            <p>{{ subtaskLoadError }}</p>
            <div class="empty-actions">
              <button class="primary-btn" type="button" :disabled="loading" @click="loadGroupResources">重新加载子任务</button>
            </div>
          </div>
        </div>

        <div v-else-if="!displayPooledSubtasks.length" class="empty-workspace">
          <div>
            <h4>{{ emptyPoolTitle }}</h4>
            <div v-if="leaderCanManage" class="empty-actions">
              <button class="primary-btn" type="button" :disabled="!canCreateSubtask" @click="openCreateDialog">创建子任务</button>
            </div>
          </div>
        </div>

        <div v-else class="pool-scroll">
          <div class="pool-grid">
          <article
            v-for="item in displayPooledSubtasks"
            :key="item.subtaskId"
            class="pool-card pool-card-compact pool-card-clickable"
            role="button"
            tabindex="0"
            @click="openPoolDetailDialog(item)"
            @keydown.enter.prevent="openPoolDetailDialog(item)"
            @keydown.space.prevent="openPoolDetailDialog(item)"
          >
            <div class="pool-card-body">
              <h4 class="task-name">{{ item.name }}</h4>
              <dl class="pool-meta-list">
                <div class="pool-meta-row">
                  <dt class="pool-meta-label">认领状态</dt>
                  <dd class="pool-meta-value">
                    <span class="assign-pill" :class="{ unclaimed: !item.assigneeId }">{{ resolveClaimLabel(item) }}</span>
                  </dd>
                </div>
                <div class="pool-meta-row">
                  <dt class="pool-meta-label">截止时间</dt>
                  <dd class="pool-meta-value pool-meta-value--deadline">{{ item.deadlineText || '—' }}</dd>
                </div>
              </dl>
            </div>
          </article>
        </div>
        </div>
      </article>

      <article class="card panel my-panel">
        <div class="panel-head">
          <h3>我的任务</h3>
          <span class="panel-count">{{ myCountText }}</span>
        </div>

        <div v-if="!canUseGroupData" class="empty-workspace">
          <div>
            <h4>暂时没有我的任务</h4>
            <div class="empty-actions">
              <button class="primary-btn" type="button" @click="goClassGroups">前往班级小组</button>
            </div>
          </div>
        </div>

        <div v-else-if="subtaskLoadError" class="empty-workspace error-workspace">
          <div>
            <h4>暂时无法读取我的任务</h4>
            <p>{{ subtaskLoadError }}</p>
            <div class="empty-actions">
              <button class="primary-btn" type="button" :disabled="loading" @click="loadGroupResources">重新加载我的任务</button>
            </div>
          </div>
        </div>

        <div v-else-if="!displayMySubtasks.length" class="empty-workspace">
          <div>
            <h4>还没有认领任务</h4>
            <div v-if="leaderCanManage && !displayPooledSubtasks.length" class="empty-actions">
              <button class="primary-btn" type="button" :disabled="!canCreateSubtask" @click="openCreateDialog">创建子任务</button>
            </div>
          </div>
        </div>

        <div v-else class="my-scroll">
          <div class="pool-grid my-task-grid">
          <article
            v-for="item in displayMySubtasks"
            :key="item.subtaskId"
            class="pool-card pool-card-compact my-task-card"
          >
            <div class="pool-card-body">
              <h4 class="task-name">{{ item.name }}</h4>
              <dl class="pool-meta-list">
                <div class="pool-meta-row">
                  <dt class="pool-meta-label">认领状态</dt>
                  <dd class="pool-meta-value">
                    <span class="assign-pill">{{ resolveClaimLabel(item) }}</span>
                  </dd>
                </div>
                <div class="pool-meta-row">
                  <dt class="pool-meta-label">截止时间</dt>
                  <dd class="pool-meta-value pool-meta-value--deadline">{{ item.deadlineText || '—' }}</dd>
                </div>
              </dl>
            </div>
            <div class="pool-card-section pool-card-section--status">
              <span class="status-pill" :class="`status-${item.statusCode}`">{{ item.statusLabel }}</span>
            </div>
            <div class="pool-card-foot compact-foot">
              <button class="detail-entry-btn" type="button" @click.stop="openSubmitDialog(item)">
                {{ submissionActionLabel(item) }}
              </button>
            </div>
          </article>
        </div>
        </div>
      </article>
    </section>

    <Teleport to="body">
      <div v-if="dialogState.create" class="dialog-overlay" @click.self="closeCreateDialog">
        <section class="dialog-panel wide-dialog create-dialog">
          <div class="panel-head dialog-topbar">
            <div>
              <p class="eyebrow">组长拆解子任务</p>
              <h3>批量创建子任务</h3>
            </div>
          </div>

          <div ref="createDialogBodyRef" class="create-dialog-body">
            <div class="create-dialog-tip bento-surface">
              <span class="default-pill">默认两条</span>
            </div>

            <div class="batch-list create-batch-list">
              <article
                v-for="(row, index) in createRows"
                :key="row.key"
                :ref="(el) => setCreateRowRef(el, index)"
                class="batch-row bento-surface"
              >
                <div class="batch-row-head">
                  <strong>子任务 {{ index + 1 }}</strong>
                  <button class="ghost-btn" type="button" @click="removeCreateRow(index)">删除</button>
                </div>
                <div class="batch-grid">
                  <label class="field">
                    <span>子任务名称</span>
                    <input v-model.trim="row.name" type="text" maxlength="100" placeholder="例如：资料整理、原型核对、页面联调" />
                  </label>
                  <label class="field">
                    <span>截止时间</span>
                    <input v-model="row.deadline" type="datetime-local" :max="createDeadlineMax || undefined" />
                  </label>
                  <label class="field batch-full">
                    <span>子任务描述</span>
                    <textarea v-model.trim="row.description" rows="3" placeholder="补充任务目标、产出物或执行说明"></textarea>
                  </label>
                </div>
              </article>
            </div>
          </div>

          <div class="dialog-actions create-dialog-actions">
            <button class="secondary-btn outlined-btn" type="button" @click="closeCreateDialog">关闭</button>
            <div class="create-dialog-actions-right">
              <button class="secondary-btn outlined-btn" type="button" @click="addCreateRow">新增一条</button>
              <button class="secondary-btn outlined-btn" type="button" @click="resetCreateRows">重置表单</button>
              <button class="primary-btn" type="button" :disabled="actionState.createBusy" @click="createSubtaskAction">
                {{ actionState.createBusy ? '创建中...' : '一次创建全部子任务' }}
              </button>
            </div>
          </div>
        </section>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="dialogState.poolDetail" class="dialog-overlay" @click.self="closePoolDetailDialog">
        <section class="dialog-panel detail-dialog pool-detail-dialog">
          <div v-if="poolDetailSubtask" class="pool-detail-body">
            <article class="batch-row bento-surface pool-detail-row">
              <div class="batch-row-head">
                <strong>子任务详情</strong>
                <span class="status-pill" :class="`status-${poolDetailSubtask.statusCode}`">
                  {{ poolDetailSubtask.statusLabel }}
                </span>
              </div>
              <div class="batch-grid">
                <label class="field pool-detail-field">
                  <span>子任务名称</span>
                  <input :value="poolDetailSubtask.name" type="text" readonly />
                </label>
                <label class="field pool-detail-field">
                  <span>截止时间</span>
                  <input :value="poolDetailSubtask.deadlineText || '—'" type="text" readonly />
                </label>
                <label class="field batch-full pool-detail-field">
                  <span>子任务描述</span>
                  <textarea rows="3" readonly :value="poolDetailSubtask.description || '暂无描述'"></textarea>
                </label>
              </div>
            </article>
          </div>

          <footer v-if="poolDetailSubtask" class="pool-detail-footer">
            <button class="secondary-btn outlined-btn dialog-close-btn" type="button" @click="closePoolDetailDialog">关闭</button>
            <div class="pool-detail-actions">
              <button
                v-if="poolDetailCanClaim"
                class="primary-btn"
                type="button"
                :disabled="!canUseGroupData || actionState.claimId === poolDetailSubtask.subtaskId"
                @click="claimFromPoolDetail"
              >
                {{ actionState.claimId === poolDetailSubtask.subtaskId ? '认领中...' : '认领子任务' }}
              </button>
            </div>
          </footer>
        </section>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="dialogState.submit" class="dialog-overlay" @click.self="closeSubmitDialog">
        <section class="dialog-panel detail-dialog submit-dialog" @click.stop>
          <header v-if="activeSubmissionSubtask" class="submit-dialog-head">
            <h3>{{ activeSubmissionSubtask.name }}</h3>
            <div class="submit-dialog-meta">
              <span class="meta-inline">
                <span class="meta-inline-label">截止时间</span>
                <strong>{{ activeSubmissionSubtask.deadlineText || '—' }}</strong>
              </span>
              <span class="status-pill" :class="`status-${activeSubmissionSubtask.statusCode}`">
                {{ activeSubmissionSubtask.statusLabel }}
              </span>
            </div>
          </header>

          <div v-if="activeSubmissionSubtask" class="dialog-body submit-dialog-body">
            <label class="field submit-field">
              <span>完成说明</span>
              <textarea
                v-model.trim="submitForm.content"
                rows="5"
                :disabled="!workspaceEditable"
                placeholder="描述你已完成的内容、当前进度和补充说明"
              ></textarea>
            </label>

            <div class="field submit-field">
              <span>上传文件</span>
              <FileUploadZone
                v-model="submitForm.uploadedFiles"
                :disabled="!workspaceEditable"
                @error="handleUploadError"
              />
            </div>

            <label class="field submit-field submit-field-optional">
              <span>网盘 / 仓库链接（选填）</span>
              <input
                v-model.trim="submitForm.attachment"
                type="url"
                :disabled="!workspaceEditable"
                placeholder="如有外链可补充填写，与上传文件可同时使用"
              />
            </label>

            <div
              v-if="activeSubmissionSubtask.parsedSubmission.files?.length || activeSubmissionSubtask.parsedSubmission.link"
              class="attachment-inline submit-attachment"
            >
              <span>已提交附件</span>
              <div class="attachment-file-stack">
                <div
                  v-for="(file, fileIndex) in activeSubmissionSubtask.parsedSubmission.files"
                  :key="`${file.value}-${fileIndex}`"
                  class="attachment-file-row"
                >
                  <span class="attachment-file-name">{{ file.name }}</span>
                  <div class="attachment-actions">
                    <button
                      v-if="canPreviewAttachment(file.value, file.name)"
                      class="secondary-btn"
                      type="button"
                      @click="openAttachmentPreview(file.value, file.name)"
                    >
                      在线查看
                    </button>
                    <button
                      class="attachment-link"
                      type="button"
                      @click="downloadAttachment(file.value, file.name)"
                    >
                      下载
                    </button>
                  </div>
                </div>
                <div v-if="activeSubmissionSubtask.parsedSubmission.link" class="attachment-file-row">
                  <span class="attachment-file-name">{{ displayLinkText(activeSubmissionSubtask.parsedSubmission.link) }}</span>
                  <div class="attachment-actions">
                    <a
                      class="attachment-link"
                      :href="resolveMediaUrl(activeSubmissionSubtask.parsedSubmission.link)"
                      target="_blank"
                      rel="noreferrer"
                    >
                      打开链接
                    </a>
                  </div>
                </div>
              </div>
            </div>

            <div
              v-if="activeSubmissionSubtask.submissionHistories?.length"
              class="submission-history submit-history"
            >
              <div class="submission-history-head">
                <strong>提交记录</strong>
                <span>{{ activeSubmissionSubtask.submissionHistories.length }} 次</span>
              </div>
              <div class="submission-history-list">
                <article
                  v-for="history in activeSubmissionSubtask.submissionHistories"
                  :key="history.id"
                  class="submission-history-item"
                >
                  <div class="submission-history-meta">
                    <strong>版本 {{ history.versionNo || '-' }}</strong>
                    <span>{{ history.current ? '当前版本' : history.submittedAtText }}</span>
                  </div>
                  <p v-if="history.parsedSubmission.text" class="submission-history-text">
                    {{ history.parsedSubmission.text }}
                  </p>
                  <div
                    v-if="history.parsedSubmission.files?.length || history.parsedSubmission.link"
                    class="attachment-file-stack submission-history-files"
                  >
                    <div
                      v-for="(file, fileIndex) in history.parsedSubmission.files"
                      :key="`${history.id}-${file.value}-${fileIndex}`"
                      class="attachment-file-row"
                    >
                      <span class="attachment-file-name">{{ file.name }}</span>
                      <div class="attachment-actions">
                        <button
                          v-if="canPreviewAttachment(file.value, file.name)"
                          class="secondary-btn"
                          type="button"
                          @click="openAttachmentPreview(file.value, file.name)"
                        >
                          查看
                        </button>
                        <button
                          class="attachment-link"
                          type="button"
                          @click="downloadAttachment(file.value, file.name)"
                        >
                          下载
                        </button>
                      </div>
                    </div>
                    <div v-if="history.parsedSubmission.link" class="attachment-file-row">
                      <span class="attachment-file-name">{{ displayLinkText(history.parsedSubmission.link) }}</span>
                      <div class="attachment-actions">
                        <a
                          class="attachment-link"
                          :href="resolveMediaUrl(history.parsedSubmission.link)"
                          target="_blank"
                          rel="noreferrer"
                        >
                          打开
                        </a>
                      </div>
                    </div>
                  </div>
                </article>
              </div>
            </div>

            <p v-if="submitDialogError" class="submit-dialog-hint submit-dialog-error" role="alert">
              {{ submitDialogError }}
            </p>
          </div>

          <footer v-if="activeSubmissionSubtask" class="submit-dialog-footer">
            <button class="secondary-btn outlined-btn dialog-close-btn" type="button" @click="closeSubmitDialog">关闭</button>
            <div class="submit-dialog-actions">
              <button
                class="primary-btn"
                type="button"
                :disabled="!workspaceEditable || actionState.submitId === activeSubmissionSubtask.subtaskId"
                @click="submitWork"
              >
                {{ submitButtonLabel(activeSubmissionSubtask) }}
              </button>
            </div>
          </footer>
        </section>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="dialogState.review" class="dialog-overlay" @click.self="closeReviewDialog">
        <section class="dialog-panel review-dialog">
          <div class="panel-head dialog-topbar">
            <div>
              <p class="eyebrow">审批任务池</p>
              <h3>组长审批与完成管理</h3>
            </div>
            <button class="secondary-btn" type="button" @click="closeReviewDialog">关闭</button>
          </div>

          <div class="review-layout review-workbench">
            <aside class="review-list-pane">
              <div class="panel-head compact-head review-list-head">
                <div>
                  <h3>任务池</h3>
                  <p class="review-section-desc">点击左侧任务，在右侧查看提交内容和当前状态。</p>
                </div>
                <div class="review-head-actions">
                  <span class="count">{{ reviewPoolSubtasks.length }} 项</span>
                  <button
                    class="ghost-btn review-refresh-btn"
                    type="button"
                    :disabled="actionState.reviewRefresh"
                    @click="refreshReviewPool"
                  >
                    {{ actionState.reviewRefresh ? '刷新中...' : '刷新' }}
                  </button>
                </div>
              </div>

              <div class="review-list">
                <button
                  v-for="item in reviewPoolSubtasks"
                  :key="item.subtaskId"
                  class="review-list-item"
                  :class="{ active: activeReviewSubtask?.subtaskId === item.subtaskId }"
                  type="button"
                  @click="selectReviewSubtask(item)"
                >
                  <span class="review-list-row">
                    <span class="review-list-title">{{ item.name }}</span>
                    <span class="status-pill review-list-status" :class="statusClass(item.statusCode)">{{ item.statusLabel }}</span>
                  </span>
                  <span class="review-list-meta">负责人：{{ item.assigneeLabel }}</span>
                  <span class="review-list-meta">提交：{{ item.submittedAtText }}</span>
                </button>
                <p v-if="!reviewPoolSubtasks.length" class="empty review-empty">当前还没有小组子任务。</p>
              </div>
            </aside>

            <section class="review-detail-pane">
              <template v-if="activeReviewSubtask">
                <header class="review-detail-head">
                  <div>
                    <p class="eyebrow">任务处理</p>
                    <h3>{{ activeReviewSubtask.name }}</h3>
                  </div>
                  <span class="status-pill" :class="statusClass(activeReviewSubtask.statusCode)">{{ activeReviewSubtask.statusLabel }}</span>
                </header>

                <dl class="review-detail-meta">
                  <div>
                    <dt>负责人</dt>
                    <dd>{{ activeReviewSubtask.assigneeLabel }}</dd>
                  </div>
                  <div>
                    <dt>提交时间</dt>
                    <dd>{{ activeReviewSubtask.submittedAtText }}</dd>
                  </div>
                  <div>
                    <dt>截止时间</dt>
                    <dd>{{ activeReviewSubtask.deadlineText || '-' }}</dd>
                  </div>
                </dl>

                <section class="review-detail-section">
                  <h4>完成说明</h4>
                  <p class="review-content-text-full">
                    {{ activeReviewSubtask.parsedSubmission.text || '暂无完成说明' }}
                  </p>
                </section>

                <section
                  v-if="activeReviewSubtask.parsedSubmission.files?.length || activeReviewSubtask.parsedSubmission.link"
                  class="review-detail-section"
                >
                  <h4>提交附件</h4>
                  <div class="attachment-file-stack compact">
                    <div
                      v-for="(file, fileIndex) in activeReviewSubtask.parsedSubmission.files"
                      :key="`${file.value}-${fileIndex}`"
                      class="attachment-file-row"
                    >
                      <span class="attachment-file-name">{{ file.name }}</span>
                      <div class="attachment-actions">
                        <button
                          v-if="canPreviewAttachment(file.value, file.name)"
                          class="secondary-btn"
                          type="button"
                          @click="openAttachmentPreview(file.value, file.name)"
                        >
                          查看
                        </button>
                        <button
                          class="attachment-link"
                          type="button"
                          @click="downloadAttachment(file.value, file.name)"
                        >
                          下载
                        </button>
                      </div>
                    </div>
                    <div v-if="activeReviewSubtask.parsedSubmission.link" class="attachment-file-row">
                      <span class="attachment-file-name">{{ displayLinkText(activeReviewSubtask.parsedSubmission.link) }}</span>
                      <div class="attachment-actions">
                        <a
                          class="attachment-link"
                          :href="resolveMediaUrl(activeReviewSubtask.parsedSubmission.link)"
                          target="_blank"
                          rel="noreferrer"
                        >
                          打开
                        </a>
                      </div>
                    </div>
                  </div>
                </section>

                <label v-if="canReviewSubtask(activeReviewSubtask)" class="field review-field review-note-field review-decision-note">
                  <span>审批说明</span>
                  <textarea
                    v-model.trim="reviewForms[activeReviewSubtask.subtaskId]"
                    rows="4"
                    placeholder="通过可留空；打回时请写明修改要求"
                  ></textarea>
                </label>

                <footer v-if="canReviewSubtask(activeReviewSubtask)" class="review-detail-actions">
                  <button
                    class="secondary-btn"
                    type="button"
                    :disabled="actionState.reviewId === activeReviewSubtask.subtaskId"
                    @click="reviewPendingSubtask(activeReviewSubtask, false)"
                  >
                    {{ actionState.reviewId === activeReviewSubtask.subtaskId ? '处理中...' : '打回修改' }}
                  </button>
                  <button
                    class="primary-btn"
                    type="button"
                    :disabled="actionState.reviewId === activeReviewSubtask.subtaskId"
                    @click="reviewPendingSubtask(activeReviewSubtask, true)"
                  >
                    {{ actionState.reviewId === activeReviewSubtask.subtaskId ? '处理中...' : '审批通过' }}
                  </button>
                </footer>
                <p v-else class="review-readonly-note">当前状态无需审批，可在这里查看任务说明、提交内容和附件。</p>
              </template>

              <div v-else class="review-detail-empty">
                <h3>当前没有小组子任务</h3>
                <p>创建或认领子任务后会出现在左侧任务池。</p>
              </div>
            </section>
          </div>
        </section>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="dialogState.groupReport" class="dialog-overlay" @click.self="closeGroupReportDialog">
        <section class="dialog-panel detail-dialog submit-dialog group-report-dialog" @click.stop>
          <header class="submit-dialog-head">
            <h3>小组总报告</h3>
            <div class="submit-dialog-meta">
              <span class="meta-inline">
                <span class="meta-inline-label">当前状态</span>
                <strong>{{ groupReportVersionText }}</strong>
              </span>
              <span class="meta-inline">
                <span class="meta-inline-label">截止时间</span>
                <strong>{{ detail?.deadlineText || '—' }}</strong>
              </span>
            </div>
          </header>

          <div class="dialog-body submit-dialog-body">
            <label class="field submit-field">
              <span>总报告说明</span>
              <textarea
                v-model.trim="groupReportForm.content"
                rows="6"
                :disabled="!canSubmitGroupReport"
                placeholder="汇总小组最终成果、分工说明、交付物说明或教师需要重点查看的内容"
              ></textarea>
            </label>

            <div class="field submit-field">
              <span>上传总报告文件</span>
              <FileUploadZone
                v-model="groupReportForm.uploadedFiles"
                :disabled="!canSubmitGroupReport"
                @error="handleUploadError"
              />
            </div>

            <label class="field submit-field submit-field-optional">
              <span>网盘 / 仓库链接（选填）</span>
              <input
                v-model.trim="groupReportForm.attachment"
                type="url"
                :disabled="!canSubmitGroupReport"
                placeholder="如总报告在外部平台，可填写链接"
              />
            </label>

            <div v-if="groupReport?.histories?.length" class="submission-history submit-history">
              <div class="submission-history-head">
                <strong>报告版本记录</strong>
                <span>{{ groupReport.histories.length }} 次</span>
              </div>
              <div class="submission-history-list">
                <article v-for="history in groupReport.histories" :key="history.id" class="submission-history-item">
                  <div class="submission-history-meta">
                    <strong>版本 {{ history.versionNo || '-' }}</strong>
                    <span>{{ history.current ? '当前版本' : history.submittedAtText }}</span>
                  </div>
                  <p v-if="history.parsedReport.text" class="submission-history-text">
                    {{ history.parsedReport.text }}
                  </p>
                  <div v-if="history.parsedReport.files?.length || history.parsedReport.link" class="attachment-file-stack submission-history-files">
                    <div
                      v-for="(file, fileIndex) in history.parsedReport.files"
                      :key="`${history.id}-${file.value}-${fileIndex}`"
                      class="attachment-file-row"
                    >
                      <span class="attachment-file-name">{{ file.name }}</span>
                      <div class="attachment-actions">
                        <button
                          v-if="canPreviewAttachment(file.value, file.name)"
                          class="secondary-btn"
                          type="button"
                          @click="openAttachmentPreview(file.value, file.name)"
                        >
                          查看
                        </button>
                        <button class="attachment-link" type="button" @click="downloadAttachment(file.value, file.name)">
                          下载
                        </button>
                      </div>
                    </div>
                    <div v-if="history.parsedReport.link" class="attachment-file-row">
                      <span class="attachment-file-name">{{ displayLinkText(history.parsedReport.link) }}</span>
                      <div class="attachment-actions">
                        <a class="attachment-link" :href="resolveMediaUrl(history.parsedReport.link)" target="_blank" rel="noreferrer">
                          打开
                        </a>
                      </div>
                    </div>
                  </div>
                </article>
              </div>
            </div>

            <p v-if="groupReportError" class="submit-dialog-hint submit-dialog-error" role="alert">
              {{ groupReportError }}
            </p>
          </div>

          <footer class="submit-dialog-footer">
            <button class="secondary-btn outlined-btn dialog-close-btn" type="button" @click="closeGroupReportDialog">关闭</button>
            <div class="submit-dialog-actions">
              <button
                class="primary-btn"
                type="button"
                :disabled="!canSubmitGroupReport || actionState.groupReportBusy"
                @click="submitGroupReportAction"
              >
                {{ actionState.groupReportBusy ? '保存中...' : (groupReport ? '保存修改' : '提交总报告') }}
              </button>
            </div>
          </footer>
        </section>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="dialogState.preview" class="dialog-overlay" @click.self="clearAttachmentPreview">
        <section class="dialog-panel preview-dialog" @click.stop>
          <header class="preview-dialog-head">
            <h3>{{ previewState.title || '附件预览' }}</h3>
            <button class="ghost-btn" type="button" @click="clearAttachmentPreview">关闭</button>
          </header>
          <div class="preview-dialog-body">
            <img :src="previewState.url" class="preview-dialog-image" alt="附件预览" />
          </div>
          <footer class="preview-dialog-footer">
            <button
              v-if="previewState.canDownload"
              class="attachment-link"
              type="button"
              @click="downloadAttachment(previewState.url, previewState.downloadName)"
            >
              下载附件
            </button>
          </footer>
        </section>
      </div>
    </Teleport>

    </div> <!-- end v-show tasks -->

    <!-- ═══ TAB: 小组进度 ═══ -->
    <section v-show="studentTab === 'progress'" class="tab-panel">
      <div v-if="!canUseGroupData" class="loading-tab">暂无进度数据，请先加入小组</div>
      <div v-else-if="progressLoadError" class="loading-tab error-tab">
        <strong>小组进度暂时不可用</strong>
        <span>{{ progressLoadError }}</span>
        <button class="primary-btn" type="button" style="margin-top:12px" @click="loadGroupProgress({ force: true })">重试</button>
      </div>
      <div v-else-if="progressLoading && !progress" class="loading-tab">正在加载小组进度…</div>
      <div v-else-if="progress" class="progress-panel">
        <div class="progress-hero">
          <div class="progress-hero-top">
            <div>
              <span class="progress-hero-label">小组完成进度</span>
              <strong class="progress-hero-value">
                <span class="progress-fraction-num">{{ progress.groupCompletedSubtasks || 0 }}</span>
                <span class="progress-fraction-sep">/</span>
                <span class="progress-fraction-den">{{ progress.groupTotalSubtasks || 0 }}</span>
                <span class="progress-fraction-label">项</span>
              </strong>
            </div>
            <span class="progress-hero-meta">
              {{ progress.groupClaimedSubtasks || 0 }} 项已认领
            </span>
            <button
              class="ghost-btn progress-refresh-btn"
              type="button"
              :disabled="actionState.reviewRefresh"
              @click="refreshReviewPool"
            >
              {{ actionState.reviewRefresh ? '刷新中...' : '刷新进度' }}
            </button>
          </div>
          <div class="progress-bar-wrap progress-bar-wrap--hero">
            <div class="progress-bar-fill" :style="{ width: (progress.groupTotalSubtasks > 0 ? Math.round((progress.groupCompletedSubtasks || 0) / progress.groupTotalSubtasks * 100) : 0) + '%' }" />
          </div>
        </div>

        <div v-if="progressMembers.length" class="member-progress-list">
          <div class="member-progress-head">
            <h4>成员进度</h4>
            <span class="member-progress-count">{{ progressMembers.length }} 人</span>
          </div>
          <ul class="member-progress-stripes">
            <li
              v-for="member in progressMembers"
              :key="member.studentId"
              class="member-strip"
              :class="{
                'is-me': member.isMe,
                [`tone-${memberProgressTone(member.progressPercent)}`]: true,
              }"
            >
              <button class="member-strip-button" type="button" @click="openProgressMemberDetail(member)">
                <span class="member-avatar" :class="{ 'is-me': member.isMe }">{{ memberProgressInitial(member) }}</span>
                <div class="member-strip-info">
                  <strong class="member-strip-name">{{ memberProgressLabel(member) }}</strong>
                  <span class="member-strip-meta">
                    <template v-if="member.claimedSubtasks > 0">
                      已完成 {{ member.completedSubtasks }}/{{ member.claimedSubtasks }} 个子任务
                    </template>
                    <template v-else>暂未认领子任务</template>
                  </span>
                </div>
                <span class="member-strip-fraction">
                  <span class="fraction-num" :class="{ 'fraction-done': member.claimedSubtasks > 0 && member.completedSubtasks === member.claimedSubtasks }">{{ member.completedSubtasks }}</span>
                  <span class="fraction-sep">/</span>
                  <span class="fraction-den">{{ member.claimedSubtasks }}</span>
                </span>
              </button>
            </li>
          </ul>
        </div>

      </div>
      <div v-else class="loading-tab">
        <span>暂无进度数据</span>
        <button class="primary-btn" type="button" style="margin-top:12px" @click="loadGroupProgress({ force: true })">加载进度</button>
      </div>

      <Teleport to="body">
        <div v-if="progressMemberDetail" class="dialog-overlay" @click.self="closeProgressMemberDetail">
          <section class="dialog-panel member-task-dialog">
            <header class="preview-dialog-head">
              <h3>{{ memberProgressLabel(progressMemberDetail) }}负责的子任务</h3>
              <button class="ghost-btn" type="button" @click="closeProgressMemberDetail">关闭</button>
            </header>

            <div class="member-task-dialog-body">
              <article
                v-for="item in progressMemberDetailTasks"
                :key="item.subtaskId"
                class="group-subtask-row"
              >
                <div class="group-subtask-row-head">
                  <div>
                    <strong>{{ item.name }}</strong>
                    <span>{{ item.description || '暂无任务说明' }}</span>
                  </div>
                  <span class="status-pill" :class="statusClass(item.statusCode)">{{ item.statusLabel }}</span>
                </div>

                <dl class="group-subtask-meta">
                  <div>
                    <dt>负责人</dt>
                    <dd>{{ item.assigneeLabel }}</dd>
                  </div>
                  <div>
                    <dt>截止时间</dt>
                    <dd>{{ item.deadlineText || '-' }}</dd>
                  </div>
                  <div>
                    <dt>提交时间</dt>
                    <dd>{{ item.submittedAtText || '-' }}</dd>
                  </div>
                </dl>

                <p v-if="item.parsedSubmission.text" class="group-subtask-text">
                  {{ item.parsedSubmission.text }}
                </p>

                <div
                  v-if="item.parsedSubmission.files?.length || item.parsedSubmission.link"
                  class="attachment-file-stack group-subtask-files"
                >
                  <div
                    v-for="(file, fileIndex) in item.parsedSubmission.files"
                    :key="`${item.subtaskId}-${file.value}-${fileIndex}`"
                    class="attachment-file-row"
                  >
                    <span class="attachment-file-name">{{ file.name }}</span>
                    <div class="attachment-actions">
                      <button
                        v-if="canPreviewAttachment(file.value, file.name)"
                        class="secondary-btn"
                        type="button"
                        @click="openAttachmentPreview(file.value, file.name)"
                      >
                        查看
                      </button>
                      <button
                        class="attachment-link"
                        type="button"
                        @click="downloadAttachment(file.value, file.name)"
                      >
                        下载
                      </button>
                    </div>
                  </div>
                  <div v-if="item.parsedSubmission.link" class="attachment-file-row">
                    <span class="attachment-file-name">{{ displayLinkText(item.parsedSubmission.link) }}</span>
                    <div class="attachment-actions">
                      <a
                        class="attachment-link"
                        :href="resolveMediaUrl(item.parsedSubmission.link)"
                        target="_blank"
                        rel="noreferrer"
                      >
                        打开
                      </a>
                    </div>
                  </div>
                </div>

                <details v-if="item.submissionHistories?.length" class="group-subtask-history">
                  <summary>提交记录 {{ item.submissionHistories.length }} 次</summary>
                  <div class="submission-history-list">
                    <article
                      v-for="history in item.submissionHistories"
                      :key="history.id"
                      class="submission-history-item"
                    >
                      <div class="submission-history-meta">
                        <strong>版本 {{ history.versionNo || '-' }}</strong>
                        <span>{{ history.current ? '当前版本' : history.submittedAtText }}</span>
                      </div>
                      <p v-if="history.parsedSubmission.text" class="submission-history-text">
                        {{ history.parsedSubmission.text }}
                      </p>
                      <div
                        v-if="history.parsedSubmission.files?.length || history.parsedSubmission.link"
                        class="attachment-file-stack submission-history-files"
                      >
                        <div
                          v-for="(file, fileIndex) in history.parsedSubmission.files"
                          :key="`${history.id}-${file.value}-${fileIndex}`"
                          class="attachment-file-row"
                        >
                          <span class="attachment-file-name">{{ file.name }}</span>
                          <div class="attachment-actions">
                            <button
                              v-if="canPreviewAttachment(file.value, file.name)"
                              class="secondary-btn"
                              type="button"
                              @click="openAttachmentPreview(file.value, file.name)"
                            >
                              查看
                            </button>
                            <button
                              class="attachment-link"
                              type="button"
                              @click="downloadAttachment(file.value, file.name)"
                            >
                              下载
                            </button>
                          </div>
                        </div>
                        <div v-if="history.parsedSubmission.link" class="attachment-file-row">
                          <span class="attachment-file-name">{{ displayLinkText(history.parsedSubmission.link) }}</span>
                          <div class="attachment-actions">
                            <a
                              class="attachment-link"
                              :href="resolveMediaUrl(history.parsedSubmission.link)"
                              target="_blank"
                              rel="noreferrer"
                            >
                              打开
                            </a>
                          </div>
                        </div>
                      </div>
                    </article>
                  </div>
                </details>
              </article>

              <p v-if="!progressMemberDetailTasks.length" class="empty group-subtask-empty">
                该成员暂未负责子任务。
              </p>
            </div>
          </section>
        </div>
      </Teleport>
    </section>

    <!-- ═══ TAB: 互评（v-show 保留实例，避免每次切换重载） ═══ -->
    <div v-show="studentTab === 'peer'">
      <StudentTaskPeerReviewsView v-if="tabMountState.peer" embedded />
    </div>

    <!-- ═══ TAB: 成绩 ═══ -->
    <div v-show="studentTab === 'score'">
      <StudentTaskScoreSummaryView v-if="tabMountState.score" embedded />
    </div>

    <!-- ═══ TAB: 申诉 ═══ -->
    <div v-show="studentTab === 'appeals'">
      <StudentTaskAppealsView v-if="tabMountState.appeals" embedded />
    </div>
  </div>
</template>

<style scoped>
.student-page {
  display: grid;
  gap: 18px;
}

.card {
  background: var(--tt-surface);
  border: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border-subtle));
  border-radius: 24px;
  box-shadow: 0 10px 28px color-mix(in srgb, var(--tt-accent) 5%, transparent);
  transition:
    transform 160ms cubic-bezier(0.25, 0.1, 0.25, 1),
    box-shadow 160ms cubic-bezier(0.25, 0.1, 0.25, 1),
    border-color 160ms cubic-bezier(0.25, 0.1, 0.25, 1);
}

.hero,
.panel,
.task-context-strip {
  padding: 22px 24px;
}

.task-hero-card {
  background:
    radial-gradient(circle at 100% 0%, color-mix(in srgb, var(--tt-accent) 14%, transparent), transparent 40%),
    linear-gradient(165deg, var(--tt-surface), color-mix(in srgb, var(--tt-accent) 5%, var(--tt-surface-muted)));
}

.hero-layout {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}

.hero-copy {
  min-width: 0;
  display: grid;
  gap: 8px;
}

.hero-deadline {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 10px;
  margin: 2px 0 0;
}

.hero-deadline-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--student-text-secondary);
}

.hero-deadline-value {
  font-size: 15px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: color-mix(in srgb, var(--tt-accent) 78%, var(--tt-text));
}

.eyebrow {
  margin: 0;
}

h2,
h3,
h4 {
  margin: 0;
}

h2 {
  font-size: clamp(22px, 2vw, 28px);
  line-height: 1.25;
  letter-spacing: -0.02em;
  color: var(--student-text-primary);
}

h3 {
  font-size: 18px;
  color: var(--student-text-primary);
}

.desc,
.message,
.task-meta,
.empty,
.count,
.label,
.context-tip,
.task-desc,
.section-title {
  margin: 0;
  color: var(--student-text-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.message {
  margin-top: -4px;
}

.message.success {
  color: var(--student-success);
}

.message.info {
  color: var(--student-accent);
}

.message.error {
  color: var(--student-danger);
}

.actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.hero-actions {
  justify-content: flex-end;
  flex-shrink: 0;
  align-self: center;
}

.hero-btn {
  min-height: 42px;
  padding: 0 18px;
  border: 0;
  border-radius: 14px;
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition:
    transform 160ms ease,
    box-shadow 160ms ease,
    opacity 160ms ease;
}

.hero-btn:hover:not(:disabled) {
  transform: translateY(-1px);
}

.hero-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.hero-btn-create {
  color: #fff;
  background: linear-gradient(135deg, #007aff, #5856d6);
  box-shadow: 0 8px 20px rgba(0, 122, 255, 0.28);
}

.hero-btn-review {
  color: #fff;
  background: linear-gradient(135deg, #0056d6, #007aff);
  box-shadow: 0 8px 20px rgba(0, 122, 255, 0.28);
}

.hero-btn-report {
  color: #fff;
  background: linear-gradient(135deg, #0f766e, #16a34a);
  box-shadow: 0 8px 20px rgba(22, 163, 74, 0.24);
}

.hero-btn-appeal {
  color: var(--tt-accent);
  background: color-mix(in srgb, var(--tt-accent) 8%, var(--tt-surface));
  border: 1px solid color-mix(in srgb, var(--tt-accent) 28%, var(--tt-border-subtle));
  box-shadow: none;
}

.tasks-workspace {
  display: grid;
  gap: 24px;
  margin-top: 20px;
}

.task-desc-panel {
  padding: 20px 24px;
  display: grid;
  gap: 10px;
}

.task-desc-label {
  margin: 0;
}

.task-desc-body {
  margin: 0;
  font-size: 15px;
  line-height: 1.65;
  color: var(--student-text-primary);
  white-space: pre-wrap;
  word-break: break-word;
}

.task-materials {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.layout {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  align-items: start;
}

.layout-spaced {
  gap: 24px;
}

.panel {
  display: grid;
  gap: 18px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border-subtle));
}

.panel-count {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 14px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--tt-accent) 10%, var(--tt-surface-muted));
  color: var(--tt-accent);
  font-size: 13px;
  font-weight: 700;
}

.compact-head {
  align-items: flex-start;
}

.value,
.context-value {
  margin: 8px 0 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--student-text-primary);
  line-height: 1.35;
  word-break: break-word;
}

.task-context-strip {
  display: grid;
  gap: 0;
}

.context-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.context-summary-item {
  border: 1px solid color-mix(in srgb, var(--tt-accent) 12%, var(--tt-border-subtle));
  border-radius: 16px;
  padding: 14px 16px;
  background: color-mix(in srgb, var(--tt-accent) 4%, var(--tt-surface-muted));
  min-width: 0;
}

.context-summary-item .label {
  font-size: 12px;
  font-weight: 600;
  color: var(--student-text-secondary);
}

.context-summary-item .context-value {
  margin-top: 6px;
  font-size: 15px;
}

.pool-grid,
.card-list,
.batch-list,
.review-column {
  display: grid;
  gap: 14px;
}

.pool-grid {
  grid-template-columns: 1fr;
  gap: 16px;
}

.pool-card {
  border: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border-subtle));
  border-radius: 18px;
  padding: 18px 20px;
  display: grid;
  gap: 14px;
  background:
    radial-gradient(circle at 100% 0%, color-mix(in srgb, var(--tt-accent) 6%, transparent), transparent 38%),
    var(--tt-surface);
  box-shadow: var(--tt-shadow-xs);
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease,
    transform 160ms ease;
}

.pool-card-compact,
.my-task-card {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 16px 18px;
}

.pool-card-compact.my-task-card {
  min-height: 0;
}

.pool-card-clickable {
  cursor: pointer;
}

.pool-card-clickable:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--tt-accent) 45%, transparent);
  outline-offset: 2px;
}

.pool-card-body {
  display: grid;
  gap: 12px;
  text-align: left;
  align-content: start;
  width: 100%;
}

.pool-card-body .task-name {
  min-height: 0;
  width: 100%;
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: -0.02em;
  color: var(--student-text-primary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

.pool-meta-list {
  margin: 0;
  display: grid;
  gap: 8px;
}

.pool-meta-row {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.pool-meta-row--progress {
  margin-bottom: 8px;
}

.pool-meta-label {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.3;
  color: var(--student-text-secondary);
}

.pool-meta-value {
  margin: 0;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.35;
  color: var(--student-text-primary);
}

.pool-meta-value--deadline {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.pool-meta-progress {
  margin: 0;
  justify-self: end;
  font-size: 13px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--student-text-primary);
}

.pool-card-section {
  width: 100%;
}

.pool-card-section--progress {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border-subtle));
}

.compact-foot {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  width: 100%;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed color-mix(in srgb, var(--tt-accent) 12%, var(--tt-border-subtle));
}

.compact-note {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  min-height: 36px;
  margin-left: auto;
  text-align: right;
  font-size: 12px;
  color: var(--student-text-secondary);
}

.detail-entry-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
  padding: 0 16px;
  margin-left: auto;
  border: none;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #007aff, #0056d6);
  box-shadow: 0 4px 14px color-mix(in srgb, var(--tt-accent) 28%, transparent);
  cursor: pointer;
  font-family: inherit;
  flex-shrink: 0;
}

.progress-block.compact-progress {
  display: grid;
  gap: 0;
  width: 100%;
}

.progress-block-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.progress-block-head strong {
  font-size: 14px;
  font-weight: 700;
  color: var(--student-text-primary);
}

.pool-card:hover,
.task-select:hover {
  transform: translateY(-2px);
  border-color: color-mix(in srgb, var(--tt-accent) 24%, var(--tt-border));
  box-shadow:
    0 12px 28px color-mix(in srgb, var(--tt-accent) 10%, transparent);
}

.pool-card-top {
  display: grid;
  gap: 8px;
  text-align: left;
}

.pool-card-id {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  color: color-mix(in srgb, var(--tt-accent) 60%, var(--student-text-secondary));
}

.pool-card-top .task-name {
  font-size: 17px;
  font-weight: 700;
  line-height: 1.32;
  letter-spacing: -0.02em;
  color: var(--student-text-primary);
}

.pool-card-top .task-desc {
  margin-top: 2px;
}

.pool-card-foot,
.task-card-footline,
.task-badges,
.attachment-actions,
.dialog-actions,
.batch-row-head,
.submit-summary,
.review-layout {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.pool-card-foot {
  padding-top: 4px;
}

.my-task-foot {
  border-top: 1px dashed color-mix(in srgb, var(--tt-accent) 12%, var(--tt-border-subtle));
  padding-top: 12px;
  margin-top: 2px;
}

.pool-meta-grid,
.batch-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.pool-meta-box,
.summary-chip {
  min-height: 72px;
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 12%, var(--tt-border-subtle));
  background: color-mix(in srgb, var(--tt-accent) 6%, var(--tt-surface-muted));
  display: grid;
  gap: 6px;
  align-content: center;
  text-align: left;
}

.pool-meta-box.deadline-box strong {
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

.meta-kicker {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--student-text-secondary);
}

.pool-meta-box strong,
.summary-chip strong {
  font-size: 20px;
  font-weight: 800;
  color: var(--student-text-primary);
  line-height: 1.2;
}

.quality-line {
  margin: 0;
  padding: 10px 12px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--tt-accent) 6%, var(--tt-surface-muted));
  border: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border-subtle));
  font-size: 13px;
  color: var(--student-text-secondary);
  text-align: left;
}

.task-badges {
  justify-content: flex-start;
  flex-wrap: wrap;
}
.status-pill,
.assign-pill,
.default-pill {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 13px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
}
.status-pill.status-1 {
  background: var(--tt-surface-muted);
  color: var(--student-text-secondary);
  border: 1px solid var(--tt-border-subtle);
}

.status-pill.status-2 {
  background: var(--tt-accent-soft);
  color: var(--tt-accent);
  border: 1px solid var(--tt-accent-border);
}

.status-pill.status-3 {
  background: color-mix(in srgb, #f59e0b 16%, var(--tt-surface-muted));
  color: #f59e0b;
  border: 1px solid color-mix(in srgb, #f59e0b 28%, var(--tt-border-subtle));
}

.status-pill.status-4 {
  background: color-mix(in srgb, #22c55e 16%, var(--tt-surface-muted));
  color: #22c55e;
  border: 1px solid color-mix(in srgb, #22c55e 28%, var(--tt-border-subtle));
}

.assign-pill {
  background: var(--tt-accent-soft);
  color: var(--tt-accent);
  border: 1px solid var(--tt-accent-border);
}

.assign-pill.unclaimed {
  background: color-mix(in srgb, var(--tt-danger) 12%, var(--tt-surface-muted));
  color: var(--tt-danger);
  border: 1px solid color-mix(in srgb, var(--tt-danger) 24%, var(--tt-border-subtle));
}
.default-pill { background: rgba(88, 86, 214, 0.12); color: #4c46c7; }
.empty-workspace {
  min-height: 220px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
  border: 1px dashed var(--tt-border);
  border-radius: 22px;
  background: var(--tt-surface-muted);
  text-align: center;
}
.empty-workspace.error-workspace {
  border-color: color-mix(in srgb, var(--tt-danger) 24%, var(--tt-border));
  background: color-mix(in srgb, var(--tt-danger) 8%, var(--tt-surface-muted));
}
.empty-workspace h4 {
  margin: 0;
  color: var(--student-text-primary);
  font-size: 18px;
}
.empty-workspace p {
  max-width: 340px;
  margin: 0;
  color: var(--student-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}
.empty-kicker {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 12px;
  margin: 0 0 10px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--tt-accent);
  background: var(--tt-accent-soft);
  border: 1px solid var(--tt-accent-border);
}

.empty-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 0;
}
.task-list { margin-top: 12px; display: grid; gap: 10px; }
.task-list.compact { margin-top: 10px; }
.task-item,.task-select {
  border: 1px solid var(--tt-border-subtle);
  border-radius: 20px;
  padding: 12px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  background: var(--tt-surface-muted);
  text-align: left;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}
.task-select {
  cursor: pointer;
  color: inherit;
}
.my-task-card {
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  align-content: stretch;
  padding: 16px 18px;
  gap: 0;
  border-radius: 18px;
  text-align: left;
  color: inherit;
  font-family: inherit;
  cursor: default;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border-subtle));
  background:
    radial-gradient(circle at 100% 0%, color-mix(in srgb, var(--tt-accent) 6%, transparent), transparent 38%),
    var(--tt-surface);
}

.my-task-card:hover {
  transform: translateY(-2px);
  border-color: color-mix(in srgb, var(--tt-accent) 24%, var(--tt-border));
  box-shadow: 0 12px 28px color-mix(in srgb, var(--tt-accent) 10%, transparent);
}

.task-select {
  cursor: pointer;
  color: inherit;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border-subtle));
  background:
    radial-gradient(circle at 100% 0%, color-mix(in srgb, var(--tt-accent) 6%, transparent), transparent 38%),
    var(--tt-surface);
}

.task-main {
  flex: 1;
  min-width: 0;
}

.task-name {
  margin: 0;
  font-weight: 700;
  color: var(--student-text-primary);
}
.task-card-foot { align-items: center; }
.enter-link {
  color: var(--student-accent);
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}
.field { display: grid; gap: 8px; margin-top: 12px; color: var(--student-text-secondary); font-size: 13px; }
.review-field { margin-top: 0; }
textarea,input,select {
  border: 1px solid var(--student-border);
  border-radius: 16px;
  padding: 10px 12px;
  background: var(--student-surface);
  color: var(--student-text-primary);
  font-family: inherit;
}
input,select { height: 40px; }
textarea[readonly],input[readonly] {
  background: var(--student-surface-muted);
  color: var(--student-text-secondary);
}
.section-title { font-weight: 700; color: var(--student-text-secondary); }
.review-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.review-note-field {
  flex: 1 1 260px;
  min-width: min(260px, 100%);
  padding: 0;
  border: 0;
  background: transparent;
}

.review-note-field textarea {
  min-height: 88px;
  resize: vertical;
}

.review-block {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid var(--tt-border-subtle);
  background: var(--tt-surface);
  box-shadow: var(--tt-shadow-sm);
}
.primary-btn,.secondary-btn,.back-btn,.ghost-btn {
  height: 40px;
  border-radius: 16px;
  padding: 0 14px;
  border: 0;
  cursor: pointer;
  font-weight: 600;
}
.primary-btn {
  background: var(--student-accent-gradient, linear-gradient(135deg, #007aff, #0056d6));
  color: #fff;
  box-shadow: 0 6px 16px color-mix(in srgb, var(--tt-accent) 28%, transparent);
}

.back-primary-btn {
  min-width: 132px;
}

.secondary-btn {
  background: var(--tt-surface-muted);
  color: var(--student-text-primary);
  border: 1px solid var(--tt-border-subtle);
}
.back-btn { background: var(--student-surface); border: 1px solid var(--student-border); color: var(--student-text-primary); }
.ghost-btn { background: transparent; color: var(--student-text-secondary); border: 1px solid var(--student-divider); }
.primary-btn:disabled,.secondary-btn:disabled,.back-btn:disabled,.ghost-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.progress-track {
  position: relative;
  height: 8px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.08);
  overflow: hidden;
}
.progress-track.large { height: 10px; }
.progress-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #007aff, #34c759);
}
.pool-note,
.attachment-inline span,
.preview-empty,
.preview-title {
  margin: 0;
  font-size: 13px;
  color: var(--student-text-secondary);
}
.attachment-inline {
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 16px;
  background: var(--tt-surface-muted);
  border: 1px solid var(--tt-border-subtle);
}
.compact-inline { padding: 12px; }
.attachment-link {
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--student-accent);
  cursor: pointer;
  font-size: 13px;
  font-weight: 700;
  font-family: inherit;
  text-decoration: none;
}
.bento-surface {
  border-radius: 24px;
  background: var(--student-tile-bg, var(--tt-surface));
  border: 1px solid var(--tt-border-subtle);
  box-shadow: var(--tt-shadow-sm);
  backdrop-filter: blur(22px) saturate(125%);
  transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease;
}
.bento-surface:hover {
  transform: translateY(-2px);
  box-shadow: var(--tt-shadow-md);
}
.dialog-panel {
  width: min(760px, var(--tt-dialog-available-width, 100%));
  max-height: calc(100vh - 48px);
  overflow: auto;
  animation: dialog-rise 0.24s ease-out;
}
.dialog-topbar {
  padding: 0 0 16px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
}
.create-dialog {
  overflow: hidden;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
}
.detail-dialog.pool-detail-dialog {
  display: grid;
  gap: 20px;
  padding: 26px 28px 24px;
  overflow: hidden;
}

.pool-detail-dialog {
  width: min(720px, var(--tt-dialog-available-width, 100%));
  padding: 26px 28px 24px;
  gap: 20px;
  border-radius: 22px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 14%, var(--tt-border-subtle));
  background:
    radial-gradient(circle at 100% 0%, color-mix(in srgb, var(--tt-accent) 10%, transparent), transparent 42%),
    var(--tt-surface);
  box-shadow: 0 24px 56px color-mix(in srgb, var(--tt-accent) 12%, transparent);
}

.pool-detail-body {
  display: grid;
  gap: 0;
}

.pool-detail-row {
  margin: 0;
  padding: 22px 24px;
  border-radius: 18px;
}

.batch-row-head .status-pill {
  min-height: 32px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 700;
}

.batch-row-head strong {
  font-size: 17px;
}

.pool-detail-field {
  margin-top: 0;
}

.pool-detail-field span {
  font-size: 14px;
  font-weight: 600;
}

.pool-detail-field input,
.pool-detail-field textarea {
  min-height: 46px;
  padding: 12px 14px;
  font-size: 15px;
  line-height: 1.45;
  border-color: color-mix(in srgb, var(--tt-accent) 14%, var(--tt-border-subtle));
  background: color-mix(in srgb, var(--tt-accent) 4%, var(--tt-surface));
}

.pool-detail-field textarea {
  min-height: 100px;
  resize: vertical;
}

.pool-detail-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 4px;
  padding-top: 18px;
  border-top: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border-subtle));
}

.pool-detail-footer .dialog-close-btn {
  min-width: 96px;
}

.pool-detail-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
  margin-left: auto;
}

.submit-dialog {
  display: grid;
  overflow: hidden;
  width: min(640px, var(--tt-dialog-available-width, 100%));
  max-height: min(88vh, 720px);
  grid-template-rows: auto minmax(0, 1fr) auto;
  border-radius: 22px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 16%, var(--tt-border-subtle));
  background:
    radial-gradient(circle at 100% 0%, color-mix(in srgb, var(--tt-accent) 12%, transparent), transparent 42%),
    linear-gradient(165deg, var(--tt-surface), color-mix(in srgb, var(--tt-accent) 5%, var(--tt-surface-muted)));
  box-shadow: 0 24px 56px color-mix(in srgb, var(--tt-accent) 14%, transparent);
}

.submit-dialog-head {
  padding: 18px 18px 14px;
  border-bottom: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border-subtle));
  display: grid;
  gap: 10px;
  text-align: left;
}

.submit-dialog-head h3 {
  font-size: 18px;
  line-height: 1.3;
}

.submit-dialog-hint {
  margin: 0;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--student-text-secondary);
  background: color-mix(in srgb, var(--tt-accent) 6%, var(--tt-surface-muted));
  border: 1px solid color-mix(in srgb, var(--tt-accent) 12%, var(--tt-border-subtle));
}

.submit-dialog-error {
  margin-top: 4px;
  color: var(--tt-danger, #b42318);
  background: color-mix(in srgb, var(--tt-danger, #b42318) 8%, var(--tt-surface-muted));
  border-color: color-mix(in srgb, var(--tt-danger, #b42318) 24%, var(--tt-border-subtle));
}

.dialog-lead {
  margin: 0;
  font-size: 14px;
  line-height: 1.55;
  color: var(--student-text-secondary);
}

.submit-dialog-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 4px;
}

.meta-inline {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--tt-accent) 8%, var(--tt-surface-muted));
  border: 1px solid color-mix(in srgb, var(--tt-accent) 14%, var(--tt-border-subtle));
}

.meta-inline-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--student-text-secondary);
}

.meta-inline strong {
  font-size: 14px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--student-text-primary);
}

.submit-dialog-body {
  margin: 0;
  padding: 14px 18px 16px;
  overflow-y: auto;
  display: grid;
  gap: 12px;
  align-content: start;
}

.submit-field {
  margin-top: 0;
  padding: 0;
  border-radius: 0;
  background: transparent;
  border: 0;
  gap: 8px;
}

.submit-field span {
  font-size: 13px;
  font-weight: 600;
  color: var(--student-text-secondary);
}

.submit-field textarea,
.submit-field input {
  width: 100%;
  border-radius: 12px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 12%, var(--tt-border-subtle));
  background: var(--tt-surface);
  pointer-events: auto;
}

.submit-field textarea {
  min-height: 120px;
  resize: vertical;
}

.submit-field textarea:not(:disabled),
.submit-field input:not(:disabled) {
  cursor: text;
}

.submit-field textarea:disabled,
.submit-field input:disabled {
  opacity: 0.92;
  cursor: not-allowed;
}

.submit-field-optional input {
  font-size: 13px;
}

.submit-attachment {
  padding: 14px 16px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--tt-accent) 5%, var(--tt-surface-muted));
  border: 1px solid color-mix(in srgb, var(--tt-accent) 12%, var(--tt-border-subtle));
}

.attachment-file-stack {
  display: grid;
  gap: 8px;
  width: 100%;
}

.attachment-file-stack.compact {
  gap: 6px;
}

.attachment-file-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 10px;
  background: var(--tt-surface);
  border: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border-subtle));
}

.attachment-file-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--student-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.submission-history {
  display: grid;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--tt-accent) 4%, var(--tt-surface-muted));
  border: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border-subtle));
}

.submission-history-head,
.submission-history-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.submission-history-head strong,
.submission-history-meta strong {
  font-size: 13px;
  color: var(--student-text-primary);
}

.submission-history-head span,
.submission-history-meta span {
  font-size: 12px;
  color: var(--student-text-secondary);
}

.submission-history-list {
  display: grid;
  gap: 8px;
}

.submission-history-item {
  display: grid;
  gap: 8px;
  padding: 10px;
  border-radius: 12px;
  background: var(--tt-surface);
  border: 1px solid color-mix(in srgb, var(--tt-accent) 8%, var(--tt-border-subtle));
}

.submission-history-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--student-text-secondary);
}

.submission-history-files {
  gap: 6px;
}

.preview-fallback p {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--student-text-secondary);
}

.submit-dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border-top: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border-subtle));
  background: color-mix(in srgb, var(--tt-accent) 4%, var(--tt-surface));
}

.dialog-close-btn {
  min-width: 88px;
}

.outlined-btn {
  background: var(--tt-surface);
  color: var(--student-text-primary);
  border: 1px solid color-mix(in srgb, var(--tt-accent) 24%, var(--tt-border-subtle));
  box-shadow: none;
}

.outlined-btn:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--tt-accent) 38%, var(--tt-border-subtle));
  background: color-mix(in srgb, var(--tt-accent) 4%, var(--tt-surface));
}

.dialog-readonly-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.65;
  color: var(--student-text-primary);
  white-space: pre-wrap;
  word-break: break-word;
}

.compact-dialog-tip {
  min-height: auto;
  padding: 14px 16px;
  border-radius: 14px;
  border-style: solid;
  background: color-mix(in srgb, var(--tt-accent) 5%, var(--tt-surface-muted));
}

.compact-dialog-tip p {
  margin: 0;
  font-size: 13px;
  color: var(--student-text-secondary);
}

.submit-dialog-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
  margin-left: auto;
}

.wide-dialog { width: min(1080px, var(--tt-dialog-available-width, 100%)); }
.review-dialog {
  width: min(1120px, var(--tt-dialog-available-width, 100%));
  max-height: min(90vh, 820px);
  overflow: hidden;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
}
.dialog-body { margin-top: 18px; display: grid; gap: 16px; }
.dialog-actions { margin-top: 18px; flex-wrap: wrap; justify-content: flex-end; }
.create-dialog-body {
  min-height: 0;
  overflow: auto;
  margin-top: 18px;
  padding-right: 6px;
  display: grid;
  gap: 16px;
}
.create-dialog-tip {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
}
.create-batch-list {
  padding-bottom: 8px;
}
.create-dialog-actions {
  margin-top: 0;
  padding-top: 16px;
  border-top: 1px solid var(--tt-border-subtle);
  background: color-mix(in srgb, var(--tt-accent) 4%, var(--tt-surface));
  backdrop-filter: blur(16px);
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.create-dialog-actions-right {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
  margin-left: auto;
}
.batch-row {
  padding: 18px;
  display: grid;
  gap: 14px;
}
.batch-row-head strong,
.preview-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--student-text-primary);
}
.batch-full { grid-column: 1 / -1; }
.form-card {
  margin-top: 0;
  padding: 16px;
  border-radius: 22px;
}
.review-layout {
  margin-top: 18px;
  align-items: stretch;
  gap: 16px;
}

.review-workbench {
  grid-template-columns: minmax(260px, 340px) minmax(0, 1fr);
  min-height: 0;
  overflow: hidden;
  display: grid;
  align-items: stretch;
}

.review-list-pane,
.review-detail-pane {
  min-height: 0;
  min-width: 0;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border-subtle));
  background: color-mix(in srgb, var(--tt-accent) 3%, var(--tt-surface));
}

.review-list-pane {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  overflow: hidden;
  border-radius: 18px;
}

.review-list-head {
  padding: 14px 14px 12px;
  border-bottom: 1px solid color-mix(in srgb, var(--tt-accent) 8%, var(--tt-border-subtle));
}

.review-list {
  display: grid;
  align-content: start;
  gap: 8px;
  min-height: 0;
  overflow-y: auto;
  padding: 12px;
}

.review-list-item {
  width: 100%;
  min-width: 0;
  display: grid;
  gap: 6px;
  padding: 12px;
  border: 1px solid transparent;
  border-radius: 14px;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.review-list-item:hover,
.review-list-item.active,
.review-list-item:focus-visible {
  border-color: color-mix(in srgb, var(--tt-accent) 18%, var(--tt-border-subtle));
  background: color-mix(in srgb, var(--tt-accent) 7%, var(--tt-surface));
}

.review-list-item.active {
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--tt-accent) 22%, transparent);
}

.review-list-title {
  color: var(--student-text-primary);
  font-size: 14px;
  font-weight: 800;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.review-list-meta {
  color: var(--student-text-secondary);
  font-size: 12px;
  line-height: 1.35;
}

.review-detail-pane {
  display: grid;
  align-content: start;
  gap: 16px;
  overflow-y: auto;
  padding: 18px;
  border-radius: 20px;
}

.review-section {
  display: grid;
  gap: 12px;
}

.review-section .task-list {
  display: grid;
  gap: 10px;
}

.review-section-desc {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.45;
  color: var(--student-text-secondary);
}

.review-card {
  display: grid;
  gap: 12px;
  padding: 14px;
  border-radius: 14px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border-subtle));
  background: color-mix(in srgb, var(--tt-accent) 3%, var(--tt-surface));
}

.review-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.review-card-head .task-name {
  min-width: 0;
  overflow-wrap: anywhere;
}

.review-content-text {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.review-attachments {
  padding: 10px;
  border-radius: 12px;
  background: var(--tt-surface-muted);
}

.review-detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.review-detail-head h3 {
  margin: 4px 0 0;
  color: var(--student-text-primary);
  font-size: 20px;
  line-height: 1.3;
  overflow-wrap: anywhere;
}

.review-detail-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.review-detail-meta div {
  min-width: 0;
  padding: 12px;
  border-radius: 14px;
  background: var(--tt-surface-muted);
}

.review-detail-meta dt {
  margin: 0 0 4px;
  color: var(--student-text-secondary);
  font-size: 12px;
}

.review-detail-meta dd {
  margin: 0;
  color: var(--student-text-primary);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.review-detail-section {
  display: grid;
  gap: 10px;
  padding: 14px;
  border-radius: 16px;
  background: var(--tt-surface-muted);
}

.review-detail-section h4 {
  margin: 0;
  color: var(--student-text-primary);
  font-size: 14px;
  font-weight: 800;
}

.review-content-text-full {
  margin: 0;
  color: var(--student-text-secondary);
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.review-decision-note {
  margin-top: 0;
}

.review-detail-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 4px;
}

.review-detail-actions .primary-btn,
.review-detail-actions .secondary-btn {
  min-width: 116px;
}

.review-detail-empty {
  display: grid;
  place-items: center;
  min-height: 360px;
  padding: 32px;
  text-align: center;
}

.review-detail-empty h3 {
  margin: 0;
  color: var(--student-text-primary);
  font-size: 18px;
}

.review-detail-empty p {
  margin: 8px 0 0;
  color: var(--student-text-secondary);
  font-size: 13px;
}

.review-empty {
  margin: 8px 0;
}

.review-actions .primary-btn,
.review-actions .secondary-btn {
  min-width: 104px;
}

.summary-chip,
.attachment-inline,
.review-block {
  border-radius: 22px;
}

.preview-dialog {
  display: grid;
  width: min(920px, var(--tt-dialog-available-width, 100%));
  max-height: min(90vh, 760px);
  grid-template-rows: auto minmax(0, 1fr) auto;
  overflow: hidden;
  border-radius: 22px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 16%, var(--tt-border-subtle));
  background: var(--tt-surface);
  box-shadow: 0 24px 56px color-mix(in srgb, var(--tt-accent) 14%, transparent);
}

.preview-dialog-head,
.preview-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
}

.preview-dialog-head {
  border-bottom: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border-subtle));
}

.preview-dialog-head h3 {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--student-text-primary);
  font-size: 16px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-dialog-body {
  display: grid;
  min-height: 0;
  place-items: center;
  overflow: auto;
  padding: 16px;
  background: color-mix(in srgb, var(--tt-accent) 4%, var(--tt-surface-muted));
}

.preview-dialog-image {
  display: block;
  max-width: 100%;
  max-height: calc(90vh - 150px);
  object-fit: contain;
  border-radius: 14px;
  background: var(--tt-surface);
}

.preview-dialog-footer {
  justify-content: flex-end;
  border-top: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border-subtle));
}
textarea,
input,
select,
.primary-btn,
.secondary-btn,
.back-btn,
.ghost-btn {
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease, background-color 0.18s ease;
}
.primary-btn:hover:not(:disabled),
.secondary-btn:hover:not(:disabled),
.back-btn:hover:not(:disabled),
.ghost-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.08);
}
@keyframes dialog-rise {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.985);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

/* ═══ STUDENT TAB BAR ═══ */
.task-tabs {
  display: flex;
  flex-wrap: nowrap;
  align-items: stretch;
  gap: 6px;
  padding: 6px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--tt-accent) 6%, var(--tt-surface-muted));
  border: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border-subtle));
  box-sizing: border-box;
}

.task-tabs-full {
  width: 100%;
  overflow-x: auto;
  scrollbar-width: none;
}

.task-tabs-full::-webkit-scrollbar {
  display: none;
}

.task-tabs-full .tab-btn {
  flex: 1 1 0;
  min-width: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.tab-btn {
  flex-shrink: 0;
  height: 40px;
  padding: 0 12px;
  border-radius: 10px;
  border: none;
  font-size: 14px;
  font-weight: 600;
  color: var(--student-text-secondary);
  background: transparent;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
}

.tab-btn.active {
  background: var(--tt-surface);
  color: var(--student-accent);
  box-shadow: 0 4px 12px color-mix(in srgb, var(--tt-accent) 12%, transparent);
}
.tab-panel { padding: 8px 0; }
.loading-tab, .empty-tab {
  text-align: center; padding: 40px 20px;
  color: var(--student-text-secondary); font-size: 14px;
}
.loading-tab.error-tab {
  display: grid;
  justify-items: center;
  gap: 12px;
  border: 1px dashed color-mix(in srgb, var(--tt-danger) 24%, var(--tt-border));
  border-radius: 22px;
  background: color-mix(in srgb, var(--tt-danger) 8%, var(--tt-surface-muted));
}
.loading-tab.error-tab strong {
  color: var(--student-text-primary);
  font-size: 16px;
}
.support-panel {
  padding: 20px;
  border-radius: 22px;
  border: 1px solid var(--tt-border-subtle);
  background: var(--tt-surface-muted);
  display: grid;
  gap: 14px;
}
.support-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.support-note {
  margin: 0;
  color: var(--student-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.progress-panel {
  display: grid;
  gap: 16px;
}

.progress-hero {
  padding: 20px 22px;
  border-radius: 18px;
  background: var(--tt-surface);
  border: 1px solid var(--tt-border-subtle);
  display: grid;
  gap: 14px;
}

.progress-hero-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}

.progress-hero-label {
  font-size: 13px;
  color: var(--tt-text-secondary);
}

.progress-hero-value {
  font-size: clamp(24px, 2vw, 28px);
  font-weight: 800;
  display: block;
  margin-top: 4px;
  color: var(--tt-text);
  line-height: 1.1;
}

.progress-hero-meta {
  font-size: 12px;
  color: var(--tt-text-tertiary);
  white-space: nowrap;
}

.progress-bar-wrap {
  height: 10px;
  border-radius: 999px;
  background: var(--tt-surface-muted);
  border: 1px solid var(--tt-border-subtle);
  overflow: hidden;
}

.progress-bar-wrap--hero {
  height: 12px;
}

.progress-bar-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--tt-accent), var(--tt-accent-secondary, #60a5fa));
  transition: width 0.6s ease;
}

.member-progress-list {
  display: grid;
  gap: 12px;
}

.member-progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.member-progress-head h4 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--tt-text);
}

.member-progress-count {
  font-size: 12px;
  font-weight: 600;
  color: var(--tt-text-tertiary);
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--tt-surface-muted);
  border: 1px solid var(--tt-border-subtle);
}

.member-progress-stripes {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 10px;
}

.member-strip {
  padding: 14px 16px;
  border-radius: 16px;
  background: var(--tt-surface);
  border: 1px solid var(--tt-border-subtle);
  display: grid;
  gap: 12px;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.member-strip:hover {
  border-color: color-mix(in srgb, var(--tt-accent) 22%, var(--tt-border));
  box-shadow: var(--tt-shadow-xs);
}

.member-strip.is-me {
  border-color: color-mix(in srgb, var(--tt-accent) 28%, var(--tt-border));
  background: linear-gradient(
    165deg,
    var(--tt-surface),
    color-mix(in srgb, var(--tt-accent) 6%, var(--tt-surface-muted))
  );
}

.member-strip.tone-done {
  border-color: color-mix(in srgb, #22c55e 24%, var(--tt-border));
}

.member-strip-top {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.member-avatar {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 800;
  color: var(--tt-accent);
  background: var(--tt-accent-soft);
  border: 1px solid var(--tt-accent-border);
}

.member-avatar.is-me {
  color: #fff;
  background: var(--tt-accent-gradient, var(--tt-accent));
  border-color: transparent;
}

.member-strip-info {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 4px;
}

.member-strip-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--tt-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-strip-meta {
  font-size: 12px;
  color: var(--tt-text-secondary);
  line-height: 1.4;
}

.member-strip-pct {
  flex-shrink: 0;
  min-width: 44px;
  text-align: right;
  font-size: 15px;
  font-weight: 800;
  color: var(--tt-text);
  font-variant-numeric: tabular-nums;
}

.member-strip.tone-done .member-strip-pct {
  color: #22c55e;
}

.member-strip.tone-idle .member-strip-pct {
  color: var(--tt-text-tertiary);
}

.member-strip-bar {
  height: 10px;
  border-radius: 999px;
  background: var(--tt-surface-muted);
  border: 1px solid var(--tt-border-subtle);
  overflow: hidden;
}

.member-strip-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--tt-accent), var(--tt-accent-secondary, #60a5fa));
  transition: width 0.55s ease;
}

.member-strip.tone-done .member-strip-fill {
  background: linear-gradient(90deg, #22c55e, #4ade80);
}

.member-strip.tone-idle .member-strip-fill {
  background: var(--tt-border-strong);
  opacity: 0.35;
}

.member-strip.tone-good .member-strip-fill {
  background: linear-gradient(90deg, var(--tt-accent), #38bdf8);
}
.primary-btn {
  height: 44px; padding: 0 24px; border-radius: 14px;
  border: none; font-size: 15px; font-weight: 700;
  background: var(--student-accent); color: #fff;
  cursor: pointer;
}

@media (max-width: 900px) {
  .hero-layout {
    flex-direction: column;
    align-items: stretch;
  }

  .hero-actions {
    justify-content: flex-start;
  }

  .layout {
    grid-template-columns: 1fr;
  }

  .task-tabs-full .tab-btn {
    flex: 1 0 auto;
    min-width: 68px;
    padding: 0 10px;
    font-size: 13px;
  }

  .context-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .pool-meta-grid,
  .batch-grid {
    grid-template-columns: 1fr;
  }
  .create-dialog-tip {
    align-items: flex-start;
    flex-direction: column;
  }
  .dialog-panel {
    padding: 18px;
    border-radius: 26px;
  }
  .review-workbench {
    grid-template-columns: 1fr;
    overflow-y: auto;
  }
  .review-list {
    max-height: 260px;
  }
  .review-detail-meta {
    grid-template-columns: 1fr;
  }
  .review-detail-head,
  .review-detail-actions {
    flex-direction: column;
    align-items: stretch;
  }
  .task-item,.task-select { flex-direction: column; }
  .review-actions { width: 100%; }
  .pool-card-head,
  .pool-card-foot,
  .task-card-headline,
  .task-card-foot,
  .task-badges,
  .attachment-actions,
  .dialog-actions,
  .batch-row-head,
  .submit-summary,
  .create-dialog-actions,
  .submit-dialog-footer,
  .pool-detail-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .create-dialog-actions-right,
  .submit-dialog-actions,
  .pool-detail-actions {
    width: 100%;
    margin-left: 0;
  }

  .create-dialog-actions-right .primary-btn,
  .create-dialog-actions-right .secondary-btn,
  .submit-dialog-actions .primary-btn,
  .submit-dialog-actions .secondary-btn,
  .pool-detail-actions .primary-btn,
  .dialog-close-btn {
    width: 100%;
  }
}

@media (max-width: 560px) {
  .context-summary-grid { grid-template-columns: 1fr; }
}

/* ── 学生端进度改造样式 ── */
.pool-scroll,
.my-scroll {
  max-height: 420px;
  overflow-y: auto;
  scroll-behavior: smooth;
  padding-right: 4px;
}
.pool-scroll::-webkit-scrollbar,
.my-scroll::-webkit-scrollbar { width: 5px; }
.pool-scroll::-webkit-scrollbar-thumb,
.my-scroll::-webkit-scrollbar-thumb {
  border-radius: 999px; background: var(--tt-border);
}
.pool-scroll::-webkit-scrollbar-track,
.my-scroll::-webkit-scrollbar-track { background: transparent; }
.pool-card-section--status {
  padding-top: 4px; display: flex; align-items: center;
}
.member-strip-fraction {
  display: inline-flex; align-items: baseline; gap: 2px;
  font-size: 16px; font-weight: 800; font-variant-numeric: tabular-nums;
  color: var(--tt-text-secondary);
}
.member-strip-fraction .fraction-num {
  color: var(--tt-text); font-size: 20px;
}
.member-strip-fraction .fraction-num.fraction-done { color: #22c55e; }
.member-strip-fraction .fraction-sep {
  color: var(--tt-text-tertiary); font-size: 14px;
}
.member-strip-fraction .fraction-den {
  color: var(--tt-text-tertiary);
}

.member-strip-button {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.member-strip-button:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--tt-accent) 42%, transparent);
  outline-offset: 4px;
  border-radius: 12px;
}

.member-task-dialog {
  width: min(920px, var(--tt-dialog-available-width, 100%));
  max-height: min(88vh, 780px);
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  overflow: hidden;
}

.member-task-dialog-body {
  display: grid;
  align-content: start;
  gap: 12px;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
}

.progress-fraction-num, .progress-fraction-den { font-variant-numeric: tabular-nums; }
.progress-fraction-num { color: var(--tt-accent); }
.progress-fraction-sep {
  color: var(--tt-text-tertiary); margin: 0 2px; font-size: 0.7em;
}
.progress-fraction-label {
  font-size: 0.5em; color: var(--tt-text-secondary);
  margin-left: 4px; font-weight: 600;
}

.progress-refresh-btn {
  margin-left: auto;
}

.group-subtask-board {
  display: grid;
  gap: 14px;
  padding: 18px;
  border-radius: 20px;
  border: 1px solid var(--tt-border-subtle);
  background: var(--tt-surface);
  box-shadow: var(--tt-shadow-xs);
}

.group-subtask-groups {
  display: grid;
  gap: 14px;
}

.group-subtask-member {
  display: grid;
  gap: 10px;
}

.group-subtask-member-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--tt-text);
}

.group-subtask-member-head span {
  color: var(--tt-text-tertiary);
  font-size: 12px;
  font-weight: 700;
}

.group-subtask-row {
  display: grid;
  gap: 12px;
  padding: 14px;
  border-radius: 16px;
  border: 1px solid var(--tt-border-subtle);
  background: color-mix(in srgb, var(--tt-accent) 3%, var(--tt-surface-muted));
}

.group-subtask-row-head {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
}

.group-subtask-row-head div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.group-subtask-row-head strong {
  color: var(--tt-text);
  font-size: 14px;
  overflow-wrap: anywhere;
}

.group-subtask-row-head span {
  color: var(--tt-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.group-subtask-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
}

.group-subtask-meta div {
  padding: 10px;
  border-radius: 12px;
  background: var(--tt-surface);
}

.group-subtask-meta dt {
  margin: 0 0 4px;
  color: var(--tt-text-tertiary);
  font-size: 12px;
}

.group-subtask-meta dd {
  margin: 0;
  color: var(--tt-text);
  font-size: 13px;
  font-weight: 700;
  overflow-wrap: anywhere;
}

.group-subtask-text {
  margin: 0;
  color: var(--tt-text-secondary);
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.group-subtask-files {
  padding: 10px;
  border-radius: 12px;
  background: var(--tt-surface);
}

.group-subtask-history {
  display: grid;
  gap: 10px;
}

.group-subtask-history summary {
  cursor: pointer;
  color: var(--tt-accent);
  font-size: 13px;
  font-weight: 800;
}

.group-subtask-empty {
  margin: 0;
}

@media (max-width: 900px) {
  .group-subtask-row-head,
  .progress-hero-top {
    flex-direction: column;
    align-items: stretch;
  }

  .group-subtask-meta {
    grid-template-columns: 1fr;
  }

  .progress-refresh-btn {
    margin-left: 0;
  }
}
</style>
