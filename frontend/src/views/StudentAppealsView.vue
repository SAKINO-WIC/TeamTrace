<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  createStudentAppeal,
  fetchStudentAllAppeals,
  fetchStudentAllTasks,
  fetchStudentClasses,
  fetchStudentTaskWorkspace,
} from '../services/student'
import { buildStudentTaskDetailLocation } from '../utils/studentTaskNavigation'
import { readSessionCache } from '../utils/sessionCache'
import { loadWithStaleSessionCache } from '../utils/staleSessionLoad'
import { useStudentLocale } from '../composables/useStudentLocale'
import AttachmentFileBadge from '../components/common/AttachmentFileBadge.vue'
import { resolveMediaUrl } from '../utils/mediaUrl'
import { buildAppealAttachments, parseAppealAttachments } from '../utils/submissionAttachments'
import { formatStudentDateTime, localizeAppealStatus } from '../utils/studentI18n'
import FileUploadZone from '../components/common/FileUploadZone.vue'
import { getCurrentUserId } from '../utils/auth'

const router = useRouter()
const { tm, isEn } = useStudentLocale()

const loading = ref(true)
const message = ref('')
const messageType = ref('info')
const appeals = ref([])
const filter = ref('all')
const activeId = ref('')
const loadError = ref('')

// ═══ 提交申诉表单 ═══
const showSubmitForm = ref(false)
const submitForm = ref({
  taskId: '',
  type: 'teacher_score',
  reason: '',
  subtaskId: '',
})
const submittingNew = ref(false)
const submitMessage = ref('')
const submitMessageType = ref('info')
const evidenceFiles = ref([])
const userTasks = ref([])
const taskSubtasks = ref([])
const taskContextLoading = ref(false)
const taskContextMessage = ref('')
const currentUserId = computed(() => Number(getCurrentUserId() || 0))

const appealTypeOptions = [
  { value: 'teacher_score', label: '成绩争议' },
  { value: 'peer_review', label: '互评异常' },
  { value: 'task_review', label: '任务 / 审批问题' },
]

const MAX_APPEALS_PER_TASK = 3

function getAppealTypeLabel(type) {
  const key = String(type || '').toLowerCase()
  if (isEn.value) {
    const enMap = {
      teacher_score: 'Grade dispute',
      peer_review: 'Peer review issue',
      task_review: 'Task / approval issue',
    }
    return enMap[key] || 'Appeal'
  }
  return appealTypeOptions.find((item) => item.value === key)?.label || '申诉'
}

const selectedSubmitTask = computed(() => {
  return userTasks.value.find((item) => String(item.taskId) === String(submitForm.value.taskId)) || null
})

const selectedSubmitTaskLocation = computed(() => {
  const task = selectedSubmitTask.value
  if (!task?.classId || !task?.taskId) return null
  return buildStudentTaskDetailLocation(task.classId, task.taskId, { tab: 'appeals', from: 'appeal-center' })
})

const selectedTaskAppeals = computed(() => {
  const taskId = String(submitForm.value.taskId || '')
  if (!taskId) return []
  return displayAppeals.value.filter((item) => String(item.taskId) === taskId)
})

const submitRemainingCount = computed(() => Math.max(MAX_APPEALS_PER_TASK - selectedTaskAppeals.value.length, 0))
const submitLimitReached = computed(() => Boolean(submitForm.value.taskId) && submitRemainingCount.value <= 0)

const submitLimitMessage = computed(() => {
  if (!submitForm.value.taskId) {
    return ''
  }
  if (submitLimitReached.value) {
    return isEn.value
      ? `This task has reached the ${MAX_APPEALS_PER_TASK}-appeal limit.`
      : `该任务已提交 ${MAX_APPEALS_PER_TASK} 次申诉，不能继续追加。`
  }
  return isEn.value
    ? `You can submit up to ${MAX_APPEALS_PER_TASK} appeals for this task. ${submitRemainingCount.value} remaining.`
    : `该任务最多可提交 ${MAX_APPEALS_PER_TASK} 次申诉，还可追加 ${submitRemainingCount.value} 次。`
})

const taskSubtaskOptions = computed(() => {
  const uid = currentUserId.value
  return taskSubtasks.value
    .filter((item) => {
      if (!uid) return true
      const assigneeId = Number(item?.assigneeId ?? item?.assignee?.id ?? 0)
      return assigneeId === uid
    })
    .map((item) => ({
      value: String(item?.subtaskId ?? item?.id ?? ''),
      label: item?.name || (isEn.value ? `Subtask ${item?.subtaskId ?? item?.id ?? ''}` : `子任务 ${item?.subtaskId ?? item?.id ?? ''}`),
      status: item?.statusLabel || item?.statusName || '',
    }))
    .filter((item) => item.value)
})

const canSubmitNewAppeal = computed(() => {
  if (submittingNew.value) return false
  if (!submitForm.value.taskId) return false
  if (submitLimitReached.value) return false
  if (!String(submitForm.value.reason || '').trim()) return false
  if (submitForm.value.type === 'task_review') {
    return Boolean(String(submitForm.value.subtaskId || '').trim())
  }
  return true
})

function toggleSubmitForm() {
  if (showSubmitForm.value) {
    showSubmitForm.value = false
    submitForm.value = { taskId: '', type: 'teacher_score', reason: '', subtaskId: '' }
    evidenceFiles.value = []
    submitMessage.value = ''
    return
  }
  // 打开时加载用户任务列表
  loadUserTasksForAppeal()
  showSubmitForm.value = true
}

async function loadUserTasksForAppeal() {
  try {
    const [taskRes, classRes] = await Promise.allSettled([
      fetchStudentAllTasks(),
      fetchStudentClasses(),
    ])
    const tasks = extractList(taskRes)
    const classes = extractList(classRes)
    const classMap = {}
    classes.forEach((c) => {
      const cid = String(c?.classId ?? c?.id ?? '')
      if (cid) classMap[cid] = c?.name || c?.className || `班级 ${cid}`
    })
    userTasks.value = tasks.map((t) => {
      const taskId = String(t?.taskId ?? t?.id ?? '')
      const classId = String(t?.classId ?? t?.class?.classId ?? t?.class?.id ?? '')
      return {
        taskId,
        classId,
        name: t?.name || t?.title || `任务 #${taskId}`,
        className: classMap[classId] || t?.className || t?.class?.name || '',
      }
    }).filter((t) => t.taskId)
  } catch {
    userTasks.value = []
  }
}

function normalizeWorkspaceSubtask(raw) {
  const statusCode = Number(raw?.status)
  const statusMap = {
    1: '待认领',
    2: '进行中',
    3: '待审批',
    4: '已完成',
  }
  return {
    ...raw,
    subtaskId: raw?.subtaskId ?? raw?.id ?? '',
    name: raw?.name || raw?.title || `子任务 ${raw?.subtaskId ?? raw?.id ?? ''}`,
    assigneeId: raw?.assigneeId ?? raw?.assignee?.id ?? null,
    statusLabel: raw?.statusLabel || statusMap[statusCode] || '',
  }
}

async function loadTaskContextForAppeal() {
  taskSubtasks.value = []
  taskContextMessage.value = ''
  submitForm.value.subtaskId = ''

  const task = selectedSubmitTask.value
  if (!task?.taskId) {
    return
  }
  if (!task?.classId) {
    taskContextMessage.value = isEn.value
      ? 'The class for this task cannot be identified. Open the task detail page to submit task or review-related appeals.'
      : '暂时无法确认该任务所属班级，请进入任务详情页提交任务或审批相关申诉。'
    return
  }

  taskContextLoading.value = true
  try {
    const { data } = await fetchStudentTaskWorkspace(task.classId, task.taskId)
    const payload = data?.data || {}
    const subtasks = Array.isArray(payload?.subtasks) ? payload.subtasks : []
    taskSubtasks.value = subtasks.map(normalizeWorkspaceSubtask)
    if (!taskSubtaskOptions.value.length) {
      taskContextMessage.value = isEn.value
        ? 'No subtasks assigned to you were found for this task. Open the task detail page for submission or review issues.'
        : '当前任务暂未读取到你负责的子任务；如需申诉审批或提交问题，请进入任务详情页处理。'
    }
  } catch (error) {
    taskContextMessage.value = error?.message || (isEn.value
      ? 'Subtasks for this task cannot be loaded right now. Please open the task detail page.'
      : '暂时无法读取该任务的子任务，请进入任务详情页提交。')
  } finally {
    taskContextLoading.value = false
  }
}

async function submitNewAppeal() {
  const form = submitForm.value
  if (!form.taskId) {
    submitMessage.value = isEn.value ? 'Select a task to appeal.' : '请选择需要申诉的任务。'
    submitMessageType.value = 'error'
    return
  }
  if (submitLimitReached.value) {
    submitMessage.value = submitLimitMessage.value
    submitMessageType.value = 'error'
    return
  }
  if (!String(form.reason || '').trim()) {
    submitMessage.value = isEn.value ? 'Enter the appeal reason.' : '请填写申诉理由。'
    submitMessageType.value = 'error'
    return
  }
  if (form.type === 'task_review' && !String(form.subtaskId || '').trim()) {
    submitMessage.value = isEn.value
      ? 'Select the related subtask. If the list is empty, open the task detail page.'
      : '请选择相关子任务；如果列表为空，请进入任务详情页提交。'
    submitMessageType.value = 'error'
    return
  }
  submittingNew.value = true
  submitMessage.value = ''
  try {
    const payload = {
      type: form.type,
      reason: form.reason.trim(),
      subtaskId: form.type === 'task_review' ? Number(form.subtaskId) : null,
      attachments: buildAppealAttachments(evidenceFiles.value),
    }
    await createStudentAppeal(form.taskId, payload)
    submitMessage.value = isEn.value
      ? 'Appeal submitted. The status will update here after the teacher handles it.'
      : '申诉已提交，教师处理后会在此更新状态。'
    submitMessageType.value = 'success'
    // 关闭表单并刷新列表
    setTimeout(() => {
      showSubmitForm.value = false
      submitForm.value = { taskId: '', type: 'teacher_score', reason: '', subtaskId: '' }
      evidenceFiles.value = []
      submitMessage.value = ''
      loadAppeals({ force: true })
    }, 1500)
  } catch (error) {
    submitMessage.value = error?.message || (isEn.value ? 'Failed to submit the appeal. Please try again later.' : '申诉提交失败，请稍后重试。')
    submitMessageType.value = 'error'
  } finally {
    submittingNew.value = false
  }
}

function selectSubmitAppealType(value) {
  submitForm.value.type = value
  if (value !== 'task_review') {
    submitForm.value.subtaskId = ''
  }
}

function goSelectedTaskAppeal() {
  const location = selectedSubmitTaskLocation.value
  if (!location) return
  showSubmitForm.value = false
  router.push(location)
}

function statusGroup(status) {
  const raw = String(status ?? '').trim().toLowerCase()
  if (raw === '3' || raw === 'approved' || raw === 'resolved') return 'approved'
  if (raw === '2' || raw === 'rejected') return 'rejected'
  return 'pending'
}

function statusTone(status) {
  const group = statusGroup(status)
  if (group === 'approved') return 'success'
  if (group === 'rejected') return 'danger'
  const raw = String(status ?? '').trim().toLowerCase()
  return raw === '1' || raw === 'processing' ? 'processing' : 'warning'
}

function formatTime(value) {
  return formatStudentDateTime(value, isEn.value)
}

function getAppealTitle(item) {
  return item.taskName || (item.taskId ? `${tm('common.task')} #${item.taskId}` : tm('common.unnamedTask'))
}

const displayAppeals = computed(() => appeals.value.map((item) => {
  const attachments = parseAppealAttachments(item.attachments)
  return {
    ...item,
    attachments,
    hasAttachments: attachments.length > 0,
    statusLabel: localizeAppealStatus(item.status, tm),
    typeLabel: getAppealTypeLabel(item.appealType),
    reason: item.reason || tm('appeals.noReason'),
    createdAtText: formatTime(item.createdAtRaw),
    updatedAtText: formatTime(item.updatedAtRaw),
    className: item.className || tm('common.unnamedClass'),
  }
}))

const filterTabs = computed(() => [
  { key: 'all', label: tm('appeals.filterAll'), count: displayAppeals.value.length },
  { key: 'pending', label: tm('appeals.filterPending'), count: displayAppeals.value.filter((item) => item.statusGroup === 'pending').length },
  { key: 'approved', label: tm('appeals.filterApproved'), count: displayAppeals.value.filter((item) => item.statusGroup === 'approved').length },
  { key: 'rejected', label: tm('appeals.filterRejected'), count: displayAppeals.value.filter((item) => item.statusGroup === 'rejected').length },
])

const filteredAppeals = computed(() => {
  if (filter.value === 'all') return displayAppeals.value
  return displayAppeals.value.filter((item) => item.statusGroup === filter.value)
})

const activeAppeal = computed(() => {
  return filteredAppeals.value.find((item) => String(item.appealId) === String(activeId.value)) || filteredAppeals.value[0] || null
})

const summaryCards = computed(() => {
  const pending = displayAppeals.value.filter((item) => item.statusGroup === 'pending').length
  const approved = displayAppeals.value.filter((item) => item.statusGroup === 'approved').length
  const rejected = displayAppeals.value.filter((item) => item.statusGroup === 'rejected').length
  return [
    { label: tm('appeals.total'), value: displayAppeals.value.length },
    { label: tm('appeals.pendingTeacher'), value: pending },
    { label: tm('appeals.approved'), value: approved },
    { label: tm('appeals.rejected'), value: rejected },
  ]
})

function extractList(response) {
  const payload = response?.value?.data?.data
  if (Array.isArray(payload)) return payload
  if (Array.isArray(payload?.items)) return payload.items
  if (Array.isArray(payload?.list)) return payload.list
  if (Array.isArray(payload?.content)) return payload.content
  return []
}

function normalizeAppeal(item, taskMap, classMap) {
  const taskId = item?.taskId ?? item?.task?.taskId ?? item?.task?.id ?? null
  const task = taskMap[String(taskId)] || null
  const classId = item?.classId ?? item?.class?.classId ?? task?.classId ?? task?.class?.classId ?? null
  const normalizedStatus = item?.status ?? item?.appealStatus ?? 0
  const appealId = item?.appealId ?? item?.id ?? `${taskId || 'appeal'}-${item?.createdAt || Math.random()}`

  return {
    ...item,
    appealId,
    taskId,
    classId,
    taskName: item?.taskName || item?.task?.name || task?.name || task?.title || '',
    className: item?.className || item?.class?.name || classMap[String(classId)] || '',
    reason: item?.reason || item?.content || '',
    teacherResponse: item?.teacherResponse || item?.response || item?.reply || '',
    appealType: item?.type,
    status: normalizedStatus,
    statusGroup: statusGroup(normalizedStatus),
    statusTone: statusTone(normalizedStatus),
    createdAtRaw: item?.createdAt || item?.createTime || item?.submittedAt,
    updatedAtRaw: item?.updatedAt || item?.handledAt || item?.resolvedAt,
  }
}

function pickActive() {
  if (!filteredAppeals.value.length) {
    activeId.value = ''
    return
  }
  if (!filteredAppeals.value.some((item) => String(item.appealId) === String(activeId.value))) {
    activeId.value = String(filteredAppeals.value[0].appealId)
  }
}

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

const APPEALS_CACHE_KEY = 'student:appeals-center'

function applyAppealsPayload({ appeals: appealPayload, tasks: taskPayload, classes: classPayload }) {
  const classMap = classPayload.reduce((acc, item) => {
    const classId = String(item?.classId ?? item?.id ?? '')
    if (!classId) return acc
    acc[classId] = item?.name || item?.className || (isEn.value ? `Class ${classId}` : `班级 ${classId}`)
    return acc
  }, {})
  const taskMap = taskPayload.reduce((acc, item) => {
    const taskId = String(item?.taskId ?? item?.id ?? '')
    if (!taskId) return acc
    acc[taskId] = item
    return acc
  }, {})
  appeals.value = appealPayload.map((item) => normalizeAppeal(item, taskMap, classMap))
  pickActive()
}

async function loadAppeals(options = {}) {
  loadError.value = ''
  setMessage('', 'info')
  const hadCacheBefore = Boolean(readSessionCache(APPEALS_CACHE_KEY, 180000))
  loading.value = !hadCacheBefore

  try {
    const { error } = await loadWithStaleSessionCache({
      cacheKey: APPEALS_CACHE_KEY,
      ttlMs: 180000,
      force: options.force === true,
      apply: applyAppealsPayload,
      fetchFresh: async () => {
        const [appealRes, taskRes, classRes] = await Promise.allSettled([
          fetchStudentAllAppeals(),
          fetchStudentAllTasks(),
          fetchStudentClasses(),
        ])
        if (appealRes.status === 'rejected') {
          throw appealRes.reason
        }
        return {
          appeals: extractList(appealRes),
          tasks: extractList(taskRes),
          classes: extractList(classRes),
        }
      },
    })
    if (error && !appeals.value.length) {
      loadError.value = error?.message || tm('appeals.loadFailedTitle')
    }
  } catch (error) {
    appeals.value = []
    activeId.value = ''
    loadError.value = error.message || tm('appeals.loadFailedTitle')
  } finally {
    loading.value = false
  }
}

function setFilter(nextFilter) {
  filter.value = nextFilter
  pickActive()
}

function selectAppeal(item) {
  activeId.value = String(item.appealId)
}

function openTaskAppeal(item) {
  const cid = item?.classId ?? item?.class?.classId ?? null
  const tid = item?.taskId ?? item?.task?.taskId ?? null
  if (!cid || !tid) return
  // 使用 name+params 方式跳转，比 path 更可靠
  router.push({
    name: 'student-task-detail',
    params: { classId: String(cid), taskId: String(tid) },
    query: { from: 'appeal-center', tab: 'appeals' },
  }).catch(() => {
    // 回退到 path 方式
    router.push({
      path: '/student/classes/' + String(cid) + '/tasks/' + String(tid),
      query: { from: 'appeal-center', tab: 'appeals' },
    })
  })
}

function openAppealAttachment(url) {
  const resolved = resolveMediaUrl(url)
  if (resolved) {
    window.open(resolved, '_blank', 'noopener,noreferrer')
  }
}

watch(filteredAppeals, pickActive)

watch(
  () => submitForm.value.taskId,
  () => {
    void loadTaskContextForAppeal()
  },
)

watch(
  () => submitForm.value.type,
  (type) => {
    if (type !== 'task_review') {
      submitForm.value.subtaskId = ''
    }
  },
)

onMounted(loadAppeals)
</script>

<template>
  <div class="student-page student-appeals">
    <header class="card appeal-hero">
      <p class="eyebrow">{{ tm('appeals.eyebrow') }}</p>
    </header>

    <p v-if="message" class="message" :class="messageType">{{ message }}</p>

    <section class="appeals-toolbar card">
      <div>
        <h2>{{ isEn ? 'Appeal Records' : '申诉记录' }}</h2>
        <p>{{ isEn ? 'View all task appeals. Choose a specific task and dispute type when creating a new appeal.' : '查看全部任务申诉；新建申诉时请选择明确的任务和争议类型。' }}</p>
      </div>
      <button class="primary-btn submit-toggle" type="button" @click="toggleSubmitForm">
        {{ isEn ? 'Submit Appeal' : '提交申诉' }}
      </button>
    </section>

    <!-- ═══ 提交申诉弹窗 ═══ -->
    <Teleport to="body">
      <div v-if="showSubmitForm" class="modal-overlay" @click.self="toggleSubmitForm">
        <div class="modal-container">
          <div class="modal-head">
            <div>
              <h3>{{ isEn ? 'Submit Appeal' : '提交申诉' }}</h3>
              <p>{{ isEn ? 'Appeals are used to report disputes to the teacher. Any grade adjustment is still handled by the teacher in the scoring center.' : '申诉只用于向教师反馈争议，具体成绩调整仍由教师在评分中心处理。' }}</p>
            </div>
            <button class="close-btn" type="button" @click="toggleSubmitForm">×</button>
          </div>

          <p v-if="submitMessage" class="message" :class="submitMessageType">{{ submitMessage }}</p>

          <label class="field">
            <span>{{ isEn ? 'Task' : '选择任务' }}</span>
            <select v-model="submitForm.taskId">
              <option value="" disabled>{{ isEn ? 'Select a task to appeal' : '请选择需要申诉的任务' }}</option>
              <option v-for="t in userTasks" :key="t.taskId" :value="t.taskId">
                {{ t.className ? `${t.className} · ` : '' }}{{ t.name }}
              </option>
            </select>
            <p v-if="submitLimitMessage" class="field-help" :class="{ danger: submitLimitReached }">
              {{ submitLimitMessage }}
            </p>
          </label>

          <div class="type-grid">
            <button
              v-for="item in appealTypeOptions"
              :key="item.value"
              class="type-option"
              :class="{ active: submitForm.type === item.value }"
              type="button"
              @click="selectSubmitAppealType(item.value)"
            >
              {{ getAppealTypeLabel(item.value) }}
            </button>
          </div>

          <div v-if="submitForm.type === 'task_review'" class="field">
            <span>{{ isEn ? 'Related subtask' : '相关子任务' }}</span>
            <p class="field-help">{{ isEn ? 'Task or approval issues must be linked to a specific subtask. The list prioritizes subtasks assigned to you.' : '任务或审批问题需要关联具体子任务，系统会优先显示你负责的子任务。' }}</p>
            <select v-model="submitForm.subtaskId" :disabled="taskContextLoading || !taskSubtaskOptions.length">
              <option value="" disabled>
                {{ taskContextLoading ? (isEn ? 'Loading subtasks...' : '正在读取子任务...') : (taskSubtaskOptions.length ? (isEn ? 'Select a related subtask' : '请选择相关子任务') : (isEn ? 'No available subtasks' : '暂无可选子任务')) }}
              </option>
              <option v-for="item in taskSubtaskOptions" :key="item.value" :value="item.value">
                {{ getAppealTypeLabel(item.value) }}{{ item.status ? ` · ${item.status}` : '' }}
              </option>
            </select>
            <div v-if="taskContextMessage" class="context-note">
              <span>{{ taskContextMessage }}</span>
              <button
                v-if="selectedSubmitTaskLocation"
                class="link-btn"
                type="button"
                @click="goSelectedTaskAppeal"
              >
                {{ isEn ? 'Open Task Detail' : '进入任务详情' }}
              </button>
            </div>
          </div>

          <label class="field">
            <span>{{ isEn ? 'Reason' : '申诉理由' }}</span>
            <textarea
              v-model.trim="submitForm.reason"
              rows="4"
              maxlength="800"
              :placeholder="isEn ? 'Describe the dispute, expected outcome, and supporting details' : '请说明异议原因、期望结果及相关依据'"
            />
          </label>

          <div class="field">
            <span>{{ isEn ? 'Evidence (optional)' : '证明材料（选填）' }}</span>
            <FileUploadZone v-model="evidenceFiles" :max-files="5" />
          </div>

          <div class="modal-actions">
            <button class="cancel-btn" type="button" @click="toggleSubmitForm">{{ isEn ? 'Cancel' : '取消' }}</button>
            <button class="primary-btn" type="button" :disabled="!canSubmitNewAppeal" @click="submitNewAppeal">
              {{ submittingNew ? (isEn ? 'Submitting...' : '提交中...') : (isEn ? 'Submit Appeal' : '提交申诉') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <section v-if="loadError" class="card error-state">
      <div>
        <p class="eyebrow">{{ tm('appeals.loadFailedEyebrow') }}</p>
        <h2>{{ tm('appeals.loadFailedTitle') }}</h2>
        <p>{{ loadError }}</p>
      </div>
      <button class="primary-btn" type="button" :disabled="loading" @click="loadAppeals">
        {{ loading ? tm('common.loading') : tm('common.reload') }}
      </button>
    </section>

    <template v-else>
      <section class="summary-strip">
        <article v-for="item in summaryCards" :key="item.label" class="card summary-card">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </article>
      </section>

      <section class="appeal-workspace">
        <aside class="card appeal-queue">
          <div class="queue-head">
            <p class="eyebrow">{{ tm('appeals.queueEyebrow') }}</p>
          </div>

          <div class="filter-tabs">
            <button
              v-for="item in filterTabs"
              :key="item.key"
              class="tab-btn"
              :class="{ active: filter === item.key }"
              type="button"
              @click="setFilter(item.key)"
            >
              <span>{{ item.label }}</span>
              <strong>{{ item.count }}</strong>
            </button>
          </div>

          <div v-if="loading" class="loading-state">
            <div v-for="i in 5" :key="i" class="skeleton-row" />
          </div>

          <div v-else-if="!filteredAppeals.length" class="empty-state compact">
            <h3>{{ filter === 'all' ? tm('appeals.emptyAll') : tm('appeals.emptyFilter') }}</h3>
          </div>

          <div v-else class="queue-list">
            <button
              v-for="item in filteredAppeals"
              :key="item.appealId"
              class="appeal-row"
              :class="{ active: String(activeAppeal?.appealId) === String(item.appealId) }"
              type="button"
              @click="selectAppeal(item)"
            >
              <span class="status-pill" :class="item.statusTone">{{ item.statusLabel }}</span>
              <strong>{{ getAppealTitle(item) }}</strong>
              <span class="row-meta">{{ item.typeLabel }}</span>
            </button>
          </div>
        </aside>

        <article class="card appeal-detail" :class="{ 'is-empty': !activeAppeal }">
          <template v-if="activeAppeal">
            <div class="detail-head">
              <div>
                <span class="status-pill" :class="activeAppeal.statusTone">{{ activeAppeal.statusLabel }}</span>
                <h2>{{ getAppealTitle(activeAppeal) }}</h2>
                <p>{{ activeAppeal.className }} · {{ activeAppeal.typeLabel }}</p>
              </div>
              <button
                class="primary-btn"
                type="button"
                :disabled="!activeAppeal.classId || !activeAppeal.taskId"
                @click="openTaskAppeal(activeAppeal)"
              >
                {{ tm('appeals.openTaskAppeal') }}
              </button>
            </div>

            <dl class="detail-grid">
              <div>
                <dt>{{ tm('appeals.submittedAt') }}</dt>
                <dd>{{ activeAppeal.createdAtText }}</dd>
              </div>
              <div>
                <dt>{{ tm('appeals.updatedAt') }}</dt>
                <dd>{{ activeAppeal.updatedAtText }}</dd>
              </div>
              <div>
                <dt>{{ tm('appeals.taskId') }}</dt>
                <dd>{{ activeAppeal.taskId || '—' }}</dd>
              </div>
              <div>
                <dt>{{ tm('appeals.appealId') }}</dt>
                <dd>{{ activeAppeal.appealId || '—' }}</dd>
              </div>
            </dl>

            <section class="detail-block">
              <span>{{ tm('appeals.reason') }}</span>
              <p>{{ activeAppeal.reason }}</p>
            </section>

            <section v-if="activeAppeal.hasAttachments" class="detail-block evidence-block">
              <span>{{ tm('appeals.evidence') }}</span>
              <div class="evidence-list">
                <button
                  v-for="file in activeAppeal.attachments"
                  :key="file.id"
                  class="evidence-item"
                  type="button"
                  @click="openAppealAttachment(file.url)"
                >
                  <AttachmentFileBadge :name="file.name" :url="file.url" kind="file" size="sm" />
                  <span class="evidence-name">{{ file.name }}</span>
                </button>
              </div>
            </section>

            <section class="detail-block response">
              <span>{{ tm('appeals.teacherReply') }}</span>
              <p>{{ activeAppeal.teacherResponse || tm('appeals.replyPlaceholder') }}</p>
            </section>
          </template>

          <div v-else class="empty-state detail-empty">
            <h3>{{ tm('appeals.selectHint') }}</h3>
          </div>
        </article>
      </section>
    </template>
  </div>
</template>

<style scoped>
.student-appeals,
.summary-strip,
.appeal-workspace,
.appeal-queue,
.appeal-detail,
.queue-list,
.filter-tabs,
.loading-state {
  display: grid;
  gap: 14px;
}

.card {
  background: var(--tt-surface);
  border: 1px solid var(--tt-border-subtle);
  border-radius: 24px;
  box-shadow: var(--tt-shadow-xs);
}

.appeal-hero,
.appeals-toolbar,
.detail-head,
.queue-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.appeal-hero {
  padding: 18px 20px;
  align-items: center;
}

.appeals-toolbar {
  padding: 16px 18px;
  align-items: center;
}

.appeals-toolbar h2 {
  font-size: 18px;
}

.appeals-toolbar p {
  margin: 6px 0 0;
  color: var(--student-text-secondary);
  font-size: 13px;
  line-height: 1.55;
}

.eyebrow {
  margin: 0;
}

h1,
h2,
h3 {
  margin: 0;
  color: var(--student-text-primary);
}

h1 {
  margin-top: 8px;
  font-size: clamp(20px, 1.85vw, 24px);
  line-height: 1.28;
}

h2 {
  font-size: clamp(16px, 1.5vw, 18px);
  line-height: 1.32;
}

h3 {
  font-size: 18px;
}

.hero-note,
.message,
.summary-card span,
.row-meta,
.row-time,
.detail-head p,
.empty-state p,
.detail-block p {
  margin: 8px 0 0;
  color: var(--student-text-secondary);
  font-size: 13px;
  line-height: 1.65;
}

.summary-strip {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.summary-card {
  padding: 16px 18px;
}

.summary-card strong {
  display: block;
  margin-top: 8px;
  color: var(--student-text-primary);
  font-size: clamp(20px, 1.8vw, 24px);
  line-height: 1.1;
}

.appeal-workspace {
  grid-template-columns: minmax(340px, 0.92fr) minmax(0, 1.08fr);
  align-items: start;
}

.appeal-queue,
.appeal-detail {
  padding: 18px;
}

.appeal-detail {
  position: sticky;
  top: 18px;
  min-height: 470px;
  align-content: start;
}

.filter-tabs {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.tab-btn {
  min-height: 58px;
  padding: 10px 12px;
  border: 1px solid var(--tt-border-subtle);
  border-radius: 16px;
  background: var(--tt-surface-muted);
  color: var(--tt-text-secondary);
  font-family: inherit;
  cursor: pointer;
  display: grid;
  gap: 5px;
  text-align: left;
}

.tab-btn.active {
  border-color: var(--tt-accent-border);
  background: var(--tt-accent-soft);
  color: var(--tt-accent);
}

.tab-btn strong {
  color: var(--student-text-primary);
  font-size: 18px;
}

.appeal-row {
  min-height: 118px;
  padding: 14px 16px;
  border: 1px solid var(--tt-border-subtle);
  border-radius: 20px;
  background: var(--tt-surface);
  text-align: left;
  font-family: inherit;
  cursor: pointer;
  display: grid;
  align-content: start;
  gap: 8px;
  min-width: 0;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease, background-color 0.18s ease;
}

.appeal-row:hover,
.appeal-row.active {
  transform: translateY(-1px);
  border-color: var(--tt-accent-border);
  box-shadow: var(--tt-shadow-sm);
}

.appeal-row.active {
  background:
    radial-gradient(circle at top right, color-mix(in srgb, var(--tt-accent) 12%, transparent), transparent 34%),
    var(--tt-surface);
}

.appeal-row strong {
  color: var(--student-text-primary);
  font-size: 15px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: fit-content;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.status-pill.warning {
  background: var(--student-warning-soft);
  color: var(--student-warning);
}

.status-pill.processing {
  background: var(--student-accent-soft);
  color: var(--student-accent);
}

.status-pill.success {
  background: var(--student-success-soft);
  color: var(--student-success);
}

.status-pill.danger {
  background: var(--student-danger-soft);
  color: var(--student-danger);
}

.detail-head h2 {
  margin-top: 12px;
  font-size: clamp(16px, 1.6vw, 19px);
  line-height: 1.3;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.detail-grid {
  margin: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.detail-grid div,
.detail-block {
  padding: 12px 14px;
  border-radius: 18px;
  background: var(--tt-surface-muted);
  border: 1px solid var(--tt-border-subtle);
  min-width: 0;
}

.detail-grid dt,
.detail-block span {
  color: var(--student-text-tertiary);
  font-size: 12px;
  font-weight: 700;
}

.detail-grid dd {
  margin: 6px 0 0;
  color: var(--tt-text);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.45;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.detail-block p {
  margin-top: 10px;
  color: var(--student-text-primary);
  font-size: 14px;
}

.detail-block.response p {
  color: var(--student-text-secondary);
}

.evidence-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.evidence-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
  min-height: 40px;
  padding: 6px 12px 6px 6px;
  border-radius: 12px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 18%, var(--tt-border-subtle));
  background: color-mix(in srgb, var(--tt-accent) 8%, var(--tt-surface));
  cursor: pointer;
  font: inherit;
}

.evidence-item:hover {
  border-color: color-mix(in srgb, var(--tt-accent) 32%, var(--tt-border));
  background: color-mix(in srgb, var(--tt-accent) 12%, var(--tt-surface));
}

.evidence-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--student-text-primary);
  font-size: 13px;
  font-weight: 600;
}

.message {
  padding: 10px 14px;
  border-radius: 16px;
  background: var(--tt-surface-muted);
  border: 1px solid var(--tt-border-subtle);
}

.message.error {
  color: var(--student-danger);
}

.message.success {
  color: var(--student-success);
}

.primary-btn,
.secondary-btn {
  min-height: 42px;
  padding: 0 15px;
  border: 0;
  border-radius: 14px;
  font-family: inherit;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.primary-btn {
  background: var(--student-accent);
  color: #f8fbff;
}

.secondary-btn {
  background: var(--student-surface-muted);
  color: var(--student-text-primary);
  border: 1px solid var(--student-border);
}

.primary-btn:hover:not(:disabled),
.secondary-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.08);
}

.primary-btn:disabled,
.secondary-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.empty-state {
  min-height: 220px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  text-align: center;
  border: 1px dashed var(--tt-border);
  border-radius: 22px;
  background: var(--tt-surface-muted);
}

.error-state {
  min-height: 280px;
  padding: 30px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  text-align: center;
  border-color: rgba(255, 59, 48, 0.16);
}

.error-state p {
  max-width: 460px;
  margin: 0;
  color: var(--student-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.empty-state.compact {
  min-height: 180px;
}

.detail-empty {
  min-height: 390px;
}

.skeleton-row {
  height: 88px;
  border-radius: 18px;
  background: var(--student-surface-muted);
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.55;
  }
}

@media (max-width: 1120px) {
  .appeal-workspace {
    grid-template-columns: 1fr;
  }

  .appeal-detail {
    position: static;
  }
}

@media (max-width: 760px) {
  .appeal-hero,
  .appeals-toolbar,
  .detail-head,
  .queue-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .submit-toggle {
    width: 100%;
  }

  .summary-strip,
  .filter-tabs,
  .detail-grid,
  .type-grid {
    grid-template-columns: 1fr;
  }
}

.submit-toggle {
  flex: 0 0 auto;
  min-width: 112px;
}

/* ═══ 弹窗 ═══ */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.modal-container {
  width: 100%;
  max-width: 640px;
  max-height: 90vh;
  overflow-y: auto;
  background: var(--tt-surface);
  border-radius: 20px;
  padding: 28px;
  display: grid;
  gap: 18px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.2);
}

.modal-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.modal-head h3 {
  margin: 0;
  font-size: 1.2rem;
}

.modal-head p {
  margin: 6px 0 0;
  color: var(--student-text-secondary);
  font-size: 13px;
  line-height: 1.55;
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  color: var(--tt-text-muted);
  padding: 0 4px;
  line-height: 1;
}

.type-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.type-option {
  min-height: 44px;
  padding: 10px 12px;
  border: 1px solid var(--tt-border-subtle);
  border-radius: 14px;
  background: var(--tt-surface);
  cursor: pointer;
  font-size: 0.92rem;
  font-weight: 700;
  text-align: center;
}

.type-option.active {
  border-color: var(--tt-accent);
  background: color-mix(in srgb, var(--tt-accent) 12%, var(--tt-surface));
  color: var(--tt-accent);
}

.field {
  display: grid;
  gap: 6px;
}

.field span {
  font-weight: 600;
  font-size: 0.9rem;
}

.field-help {
  margin: 0;
  color: var(--student-text-secondary);
  font-size: 12px;
  line-height: 1.55;
}

.field select,
.field input,
.field textarea {
  padding: 10px 14px;
  border: 1px solid var(--tt-border-subtle);
  border-radius: 10px;
  font-size: 0.92rem;
  background: var(--tt-surface);
  color: var(--tt-text);
}

.field textarea {
  resize: vertical;
  min-height: 90px;
}

.context-note {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 18%, var(--tt-border-subtle));
  background: color-mix(in srgb, var(--tt-accent) 7%, var(--tt-surface-muted));
}

.context-note span {
  color: var(--student-text-secondary);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.55;
}

.link-btn {
  flex: 0 0 auto;
  min-height: 32px;
  padding: 0 10px;
  border-radius: 10px;
  border: 1px solid var(--tt-accent-border);
  background: var(--tt-accent-soft);
  color: var(--tt-accent);
  font-family: inherit;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.cancel-btn {
  min-height: 42px;
  padding: 0 18px;
  border: 1px solid var(--tt-border-subtle);
  border-radius: 14px;
  background: var(--tt-surface-muted);
  font-family: inherit;
  font-weight: 700;
  cursor: pointer;
  color: var(--tt-text-secondary);
}

/* ═══ 固定滚动区域 ═══ */
.appeal-queue {
  max-height: calc(100vh - 200px);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.queue-list {
  overflow-y: auto;
  flex: 1;
  min-height: 0;
  padding-right: 4px;
  gap: 8px;
}

.appeal-detail {
  max-height: calc(100vh - 200px);
  overflow-y: auto;
}

/* ═══ 紧凑卡片 ═══ */
.appeal-row {
  min-height: 52px;
  padding: 10px 14px;
  gap: 3px;
}

.appeal-row strong {
  font-size: 13px;
  line-height: 1.3;
}

.appeal-row .row-meta {
  font-size: 12px;
  margin: 0;
}

/* Layout polish: keep the appeal workbench within one screen and scroll inside cards. */
.appeal-workspace {
  grid-template-columns: minmax(360px, 1fr) minmax(0, 1fr);
  height: clamp(540px, calc(100vh - 292px), 700px);
  align-items: stretch;
}

.appeal-queue,
.appeal-detail {
  height: 100%;
  max-height: none;
  min-height: 0;
  overflow: hidden;
}

.appeal-queue {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
}

.queue-list,
.loading-state,
.empty-state.compact {
  min-height: 0;
}

.queue-list {
  overflow-y: auto;
  align-content: start;
  grid-auto-rows: min-content;
}

.appeal-detail {
  position: static;
  align-content: start;
  overflow-y: auto;
}

@media (max-width: 1120px) {
  .appeal-workspace {
    height: auto;
    grid-template-columns: 1fr;
  }

  .appeal-queue,
  .appeal-detail {
    height: auto;
    max-height: none;
  }
}

/* Empty detail should fill the right card, not leave a blank lower half. */
.appeal-detail.is-empty {
  display: flex;
}

.appeal-detail.is-empty .detail-empty {
  flex: 1;
  width: 100%;
  height: 100%;
  min-height: 0;
  margin: 0;
}

</style>
