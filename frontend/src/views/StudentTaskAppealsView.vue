<script setup>
import { computed, inject, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { createStudentAppeal, fetchStudentTaskAppeals } from '../services/student'
import { STUDENT_TASK_PAGE_KEY } from '../composables/studentTaskPageContext'
import { appendStudentAppeal, formatStudentAppealStatus, loadStudentAppeals } from '../utils/student'
import AttachmentFileBadge from '../components/common/AttachmentFileBadge.vue'
import FileUploadZone from '../components/common/FileUploadZone.vue'
import { resolveMediaUrl } from '../utils/mediaUrl'
import { buildAppealAttachments, parseAppealAttachments } from '../utils/submissionAttachments'

const props = defineProps({
  embedded: {
    type: Boolean,
    default: false,
  },
})

const route = useRoute()
const pageCtx = inject(STUDENT_TASK_PAGE_KEY, null)

const classId = computed(() => pageCtx?.classId?.value ?? route.params.classId)
const taskId = computed(() => pageCtx?.taskId?.value ?? route.params.taskId)

const loading = ref(false)
const submitting = ref(false)
const message = ref('')
const messageType = ref('info')
const appeals = ref([])
const syncError = ref('')
const form = reactive({
  reason: '',
  type: 'teacher_score',
  subtaskId: '',
})
const evidenceFiles = ref([])

const appealTypeOptions = [
  { value: 'teacher_score', label: '成绩争议' },
  { value: 'peer_review', label: '互评异常' },
  { value: 'task_review', label: '任务 / 审批问题' },
]

const MAX_APPEALS_PER_TASK = 3
const taskDetail = computed(() => pageCtx?.detail?.value ?? null)
const enablePeerReview = computed(() => Boolean(taskDetail.value?.enablePeerReview))

const filteredAppealTypeOptions = computed(() => {
  return appealTypeOptions.filter((item) => {
    if (item.value === 'peer_review') {
      return enablePeerReview.value
    }
    return true
  })
})

const mySubtaskOptions = computed(() => {
  const list = pageCtx?.mySubtasks?.value
  if (!Array.isArray(list) || !list.length) {
    return []
  }
  return list.map((item) => ({
    value: String(item.subtaskId),
    label: item.name || `子任务 ${item.subtaskId}`,
  }))
})

const appealRemainingCount = computed(() => Math.max(MAX_APPEALS_PER_TASK - appeals.value.length, 0))
const appealLimitReached = computed(() => appealRemainingCount.value <= 0)

const appealBlockedReason = computed(() => {
  if (appealLimitReached.value) {
    return `该任务已提交 ${MAX_APPEALS_PER_TASK} 次申诉，不能继续追加。`
  }
  if (form.type === 'peer_review' && !enablePeerReview.value) {
    return '本任务未开启互评，无法提交互评类申诉。'
  }
  return ''
})

const formReady = computed(() => {
  if (!String(form.reason || '').trim()) return false
  if (form.type === 'task_review') {
    const sid = String(form.subtaskId || '').trim()
    return Boolean(sid) && !Number.isNaN(Number(sid))
  }
  return true
})

const canSubmitAppeal = computed(() => {
  return formReady.value && !appealBlockedReason.value
})

const appealStats = computed(() => {
  const pending = appeals.value.filter((item) => item.statusCode === 0).length
  const resolved = appeals.value.length - pending
  return {
    total: appeals.value.length,
    pending,
    resolved,
  }
})

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

function formatDateTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function resolveAppealType(rawType) {
  const value = String(rawType || 'teacher_score').toLowerCase()
  return appealTypeOptions.find((item) => item.value === value) || appealTypeOptions[0]
}

function statusClass(statusLabel) {
  const map = {
    待处理: 'pending',
    处理中: 'processing',
    已驳回: 'rejected',
    已通过: 'approved',
    已处理: 'approved',
  }
  return map[statusLabel] || 'pending'
}

function normalizeAppeal(item) {
  const type = resolveAppealType(item?.type)
  const statusCode = Number(item?.status ?? 0)
  const statusLabel = formatStudentAppealStatus(item?.status)
  const attachments = parseAppealAttachments(item?.attachments)
  return {
    appealId: item?.appealId ?? item?.id ?? '-',
    type: type.value,
    typeLabel: type.label,
    statusCode,
    statusLabel,
    statusClass: statusClass(statusLabel),
    createdAtText: formatDateTime(item?.createdAt),
    handledAtText: formatDateTime(item?.handledAt),
    reason: item?.reason ?? '-',
    teacherResponse: String(item?.teacherResponse || '').trim(),
    subtaskId: item?.subtaskId ?? null,
    attachments,
  }
}

function handleUploadError(message) {
  if (message) {
    setMessage(message, 'error')
  }
}

function openEvidenceFile(url) {
  const resolved = resolveMediaUrl(url)
  if (resolved) {
    window.open(resolved, '_blank', 'noopener,noreferrer')
  }
}

function selectAppealType(value) {
  form.type = value
  if (value !== 'task_review') {
    form.subtaskId = ''
  }
}

watch(
  filteredAppealTypeOptions,
  (options) => {
    if (!options.some((item) => item.value === form.type)) {
      form.type = options[0]?.value || 'teacher_score'
      form.subtaskId = ''
    }
  },
  { immediate: true },
)

async function loadAppeals(options = {}) {
  loading.value = true
  syncError.value = ''
  try {
    const { data } = await fetchStudentTaskAppeals(taskId.value)
    const payload = data?.data || []
    appeals.value = Array.isArray(payload) ? payload.map(normalizeAppeal) : []
    if (!options.silent) {
      setMessage('', 'info')
    }
  } catch (error) {
    appeals.value = loadStudentAppeals()
      .filter((item) => String(item.taskId) === String(taskId.value))
      .map(normalizeAppeal)
    syncError.value = error.message || '暂时无法同步最新申诉记录，请稍后刷新重试。'
    setMessage(syncError.value, 'error')
  } finally {
    loading.value = false
  }
}

async function submitAppeal() {
  if (!formReady.value) {
    setMessage('请填写申诉理由。', 'error')
    return
  }
  if (appealBlockedReason.value) {
    setMessage(appealBlockedReason.value, 'error')
    return
  }
  if (form.type === 'task_review') {
    const sid = String(form.subtaskId || '').trim()
    if (!sid || Number.isNaN(Number(sid))) {
      setMessage('请选择需要申诉的子任务。', 'error')
      return
    }
  }

  submitting.value = true
  try {
    const payload = { reason: String(form.reason).trim(), type: form.type }
    if (form.type === 'task_review') {
      payload.subtaskId = Number(String(form.subtaskId).trim())
    }
    const attachments = buildAppealAttachments(evidenceFiles.value)
    if (attachments) {
      payload.attachments = attachments
    }
    const { data } = await createStudentAppeal(taskId.value, payload)
    const created = data?.data || {}
    appendStudentAppeal({
      appealId: created?.appealId ?? created?.id,
      classId: classId.value,
      taskId: taskId.value,
      type: form.type,
      subtaskId: payload.subtaskId ?? null,
      reason: payload.reason,
      status: created?.status ?? 0,
      createdAt: created?.createdAt,
    })
    form.reason = ''
    form.subtaskId = ''
    evidenceFiles.value = []
    await loadAppeals({ silent: true })
    await pageCtx?.reloadTaskDetail?.()
    setMessage('申诉已提交，教师处理后会在此更新状态。', 'success')
  } catch (error) {
    setMessage(error.message || '申诉提交失败，请稍后重试。', 'error')
  } finally {
    submitting.value = false
  }
}

onMounted(loadAppeals)
</script>

<template>
  <div :class="embedded ? 'task-appeals-embedded' : 'student-page task-appeals-page'">
    <header v-if="!embedded" class="card appeal-hero">
      <p class="eyebrow">任务申诉</p>
      <p class="hero-lead">对成绩、互评或任务审批有异议时，可在此提交申诉并查看处理进度。</p>
    </header>

    <p v-if="message" class="message" :class="messageType">{{ message }}</p>

    <section class="appeal-layout">
      <article class="card submit-panel">
        <div class="panel-head">
          <div>
            <h2>提交申诉</h2>
            <p>申诉用于说明争议情况，教师会在申诉中心给出处理意见。</p>
          </div>
        </div>

        <p v-if="appealBlockedReason" class="submit-hint blocked-hint" role="alert">{{ appealBlockedReason }}</p>
        <p v-else class="submit-hint">
          本任务最多可提交 {{ MAX_APPEALS_PER_TASK }} 次申诉，还可追加 {{ appealRemainingCount }} 次。
        </p>

        <div class="submit-scroll">
        <div class="type-grid">
          <button
            v-for="item in filteredAppealTypeOptions"
            :key="item.value"
            class="type-option"
            :class="{ active: form.type === item.value }"
            type="button"
            @click="selectAppealType(item.value)"
          >
            {{ item.label }}
          </button>
        </div>

        <label v-if="form.type === 'task_review'" class="field">
          <span>相关子任务</span>
          <p class="field-hint">任务或审批问题必须关联具体子任务，列表只显示你负责的子任务。</p>
          <select v-model="form.subtaskId" :disabled="!mySubtaskOptions.length">
            <option value="" disabled>{{ mySubtaskOptions.length ? '请选择子任务' : '暂无可选子任务（请先在「我的任务」认领）' }}</option>
            <option v-for="item in mySubtaskOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </option>
          </select>
        </label>

        <label class="field">
          <span>申诉理由</span>
          <textarea
            v-model.trim="form.reason"
            rows="7"
            maxlength="800"
            placeholder="请说明异议原因、期望结果及相关依据"
          />
        </label>

        <div class="field">
          <span>证明材料（选填）</span>
          <p class="field-hint">可上传截图、文档等作为申诉依据，支持点击选择或拖拽上传。</p>
          <FileUploadZone
            v-model="evidenceFiles"
            :max-files="5"
            @error="handleUploadError"
          />
        </div>

        </div>

        <div class="submit-footer">
          <button class="primary-btn" type="button" :disabled="!canSubmitAppeal || submitting" @click="submitAppeal">
            {{ submitting ? '提交中...' : '提交申诉' }}
          </button>
        </div>
      </article>

      <article class="card records-panel">
        <div class="panel-head">
          <h2>处理记录</h2>
          <div class="record-stats">
            <span>{{ appealStats.total }} 条记录</span>
            <span>{{ appealStats.pending }} 条待处理</span>
            <span>{{ appealStats.resolved }} 条已处理</span>
          </div>
        </div>

        <div class="records-scroll">
        <div v-if="loading" class="loading-state">
          <div v-for="i in 4" :key="i" class="skeleton-row" />
        </div>

        <div v-else-if="syncError && !appeals.length" class="empty-state error-state">
          <h3>申诉记录同步失败</h3>
          <p>{{ syncError }}</p>
          <button class="secondary-btn" type="button" @click="loadAppeals">重新加载</button>
        </div>

        <div v-else-if="!appeals.length" class="empty-state">
          <h3>暂无申诉记录</h3>
          <p>提交申诉后，教师会在申诉中心处理，结果将显示在此处。</p>
        </div>

        <div v-else class="record-list">
          <article v-for="item in appeals" :key="item.appealId" class="record-row">
            <div class="record-main">
              <div class="record-title">
                <strong>{{ item.typeLabel }}</strong>
                <span v-if="item.subtaskId" class="subtask-tag">子任务 #{{ item.subtaskId }}</span>
              </div>
              <p class="record-reason">{{ item.reason }}</p>
              <div v-if="item.attachments.length" class="record-attachments">
                <span class="record-attachments-label">证明材料</span>
                <div class="record-attachment-list">
                  <button
                    v-for="file in item.attachments"
                    :key="file.id"
                    class="record-attachment-btn"
                    type="button"
                    @click="openEvidenceFile(file.url)"
                  >
                    <AttachmentFileBadge :name="file.name" :url="file.url" kind="file" size="sm" />
                    <span class="record-attachment-name">{{ file.name }}</span>
                  </button>
                </div>
              </div>
              <p v-if="item.teacherResponse" class="record-response">
                <span>教师回复</span>
                {{ item.teacherResponse }}
              </p>
              <span class="record-time">提交于 {{ item.createdAtText }}</span>
            </div>
            <span class="status-pill" :class="item.statusClass">{{ item.statusLabel }}</span>
          </article>
        </div>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.task-appeals-page,
.task-appeals-embedded {
  display: grid;
  gap: 20px;
}

.task-appeals-embedded {
  gap: 14px;
  margin-top: 4px;
}

.card {
  background: var(--tt-surface);
  border: 1px solid color-mix(in srgb, var(--tt-accent) 12%, var(--tt-border-subtle));
  border-radius: 20px;
  box-shadow: var(--tt-shadow-xs);
}

.appeal-hero {
  padding: 20px 22px;
  display: grid;
  gap: 8px;
}

.hero-lead {
  margin: 0;
  font-size: 14px;
  line-height: 1.55;
  color: var(--student-text-secondary);
}

.eyebrow,
h2,
h3 {
  margin: 0;
  color: var(--student-text-primary);
}

.message,
.record-reason,
.record-time,
.record-stats,
.empty-state p {
  margin: 0;
  color: var(--student-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.panel-head,
.submit-footer,
.record-row,
.record-title,
.record-stats {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.panel-head {
  padding-bottom: 14px;
  border-bottom: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border-subtle));
}

.panel-head p {
  margin: 6px 0 0;
  color: var(--student-text-secondary);
  font-size: 13px;
  line-height: 1.55;
}

.appeal-layout {
  display: grid;
  grid-template-columns: minmax(320px, 0.92fr) minmax(0, 1.08fr);
  gap: 20px;
  align-items: start;
}

.submit-panel,
.records-panel {
  padding: 20px 22px;
  display: grid;
  gap: 18px;
}

.submit-hint {
  margin: 0;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.5;
}

.blocked-hint {
  color: var(--tt-danger, #b42318);
  background: color-mix(in srgb, var(--tt-danger, #b42318) 8%, var(--tt-surface-muted));
  border: 1px solid color-mix(in srgb, var(--tt-danger, #b42318) 22%, var(--tt-border-subtle));
}

.type-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.type-option {
  min-height: 44px;
  padding: 8px 10px;
  border-radius: 12px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 12%, var(--tt-border-subtle));
  background: var(--tt-surface-muted);
  color: var(--student-text-primary);
  font-size: 14px;
  font-weight: 700;
  text-align: center;
  cursor: pointer;
  display: grid;
  gap: 4px;
  justify-items: center;
}

.type-option.active {
  background: color-mix(in srgb, var(--tt-accent) 10%, var(--tt-surface));
  border-color: color-mix(in srgb, var(--tt-accent) 32%, var(--tt-border));
  color: var(--tt-accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--tt-accent) 12%, transparent);
}

.type-option.disabled,
.type-option:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.type-pending-tag {
  font-size: 11px;
  font-weight: 700;
  color: #b45309;
}

.field {
  display: grid;
  gap: 8px;
  color: var(--student-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.field-hint {
  margin: 0;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.5;
  color: var(--student-text-tertiary);
}

.record-attachments {
  display: grid;
  gap: 8px;
}

.record-attachments-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--tt-accent);
}

.record-attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.record-attachment-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  max-width: 100%;
  padding: 6px 12px 6px 6px;
  border-radius: 12px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 18%, var(--tt-border-subtle));
  background: color-mix(in srgb, var(--tt-accent) 8%, var(--tt-surface));
  color: var(--tt-accent);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.record-attachment-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--student-text-primary);
  font-weight: 600;
}

.record-attachment-btn:hover {
  border-color: color-mix(in srgb, var(--tt-accent) 32%, var(--tt-border));
  background: color-mix(in srgb, var(--tt-accent) 12%, var(--tt-surface));
}

input,
textarea,
select {
  width: 100%;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 12%, var(--tt-border-subtle));
  border-radius: 14px;
  padding: 11px 14px;
  background: var(--tt-surface);
  color: var(--student-text-primary);
  font: inherit;
  box-sizing: border-box;
}

textarea {
  resize: vertical;
  min-height: 160px;
}

.submit-footer {
  justify-content: flex-end;
}

.record-stats {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.record-stats span {
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 12%, var(--tt-border-subtle));
  background: color-mix(in srgb, var(--tt-accent) 6%, var(--tt-surface-muted));
  font-size: 12px;
  font-weight: 700;
}

.record-list,
.loading-state {
  display: grid;
  gap: 12px;
}

.record-row {
  padding: 16px 18px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border-subtle));
  border-radius: 16px;
  background: color-mix(in srgb, var(--tt-accent) 3%, var(--tt-surface));
}

.record-main {
  min-width: 0;
  display: grid;
  gap: 8px;
}

.record-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.record-reason {
  color: var(--student-text-primary);
  line-height: 1.55;
}

.record-response {
  margin: 0;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--student-text-primary);
  background: color-mix(in srgb, var(--tt-accent) 6%, var(--tt-surface-muted));
  border: 1px solid color-mix(in srgb, var(--tt-accent) 12%, var(--tt-border-subtle));
}

.record-response span {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 700;
  color: var(--tt-accent);
}

.record-time {
  font-size: 12px;
  color: var(--student-text-tertiary);
}

.subtask-tag {
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 18%, transparent);
  background: color-mix(in srgb, var(--tt-accent) 10%, #fff);
  color: var(--student-accent);
  font-size: 12px;
  font-weight: 700;
}

.status-pill {
  flex-shrink: 0;
  min-height: 30px;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  border: 1px solid transparent;
  font-size: 12px;
  font-weight: 700;
}

.status-pill.pending {
  background: color-mix(in srgb, #f59e0b 12%, #fff);
  border-color: color-mix(in srgb, #f59e0b 22%, transparent);
  color: #b45309;
}

.status-pill.processing {
  background: color-mix(in srgb, var(--tt-accent) 12%, #fff);
  border-color: color-mix(in srgb, var(--tt-accent) 22%, transparent);
  color: var(--student-accent);
}

.status-pill.approved {
  background: color-mix(in srgb, #10b981 12%, #fff);
  border-color: color-mix(in srgb, #10b981 22%, transparent);
  color: #047857;
}

.status-pill.rejected {
  background: color-mix(in srgb, #ef4444 10%, #fff);
  border-color: color-mix(in srgb, #ef4444 20%, transparent);
  color: var(--student-danger);
}

.empty-state {
  min-height: 200px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  text-align: center;
  border: 1px dashed color-mix(in srgb, var(--tt-accent) 16%, var(--tt-border-subtle));
  border-radius: 16px;
  padding: 24px;
  background: var(--tt-surface-muted);
}

.error-state {
  border-color: color-mix(in srgb, var(--tt-danger) 24%, var(--tt-border));
}

.skeleton-row {
  height: 74px;
  border-radius: 16px;
  border: 1px solid var(--tt-border-subtle);
  background: var(--student-surface-muted);
  animation: pulse 1.5s ease-in-out infinite;
}

.message {
  padding: 10px 14px;
  border-radius: 14px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 12%, var(--tt-border-subtle));
  background: var(--tt-surface);
}

.message.success {
  color: var(--student-success);
}

.message.error {
  color: var(--student-danger);
}

.primary-btn,
.secondary-btn {
  min-height: 42px;
  border-radius: 12px;
  padding: 0 20px;
  cursor: pointer;
  font-weight: 700;
  font-family: inherit;
}

.primary-btn {
  border: 0;
  background: linear-gradient(135deg, var(--tt-accent), color-mix(in srgb, var(--tt-accent) 82%, #0056d6));
  color: #fff;
}

.primary-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.secondary-btn {
  border: 1px solid var(--tt-border-subtle);
  background: var(--tt-surface);
  color: var(--student-text-primary);
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

@media (max-width: 1080px) {
  .appeal-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .panel-head,
  .record-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .type-grid {
    grid-template-columns: 1fr;
  }

  .submit-footer .primary-btn {
    width: 100%;
  }
}

/* Layout polish: equal-height panels with internal scrolling. */
.appeal-layout {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  height: clamp(560px, calc(100vh - 220px), 720px);
  align-items: stretch;
}

.submit-panel,
.records-panel {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.submit-panel {
  grid-template-rows: auto auto minmax(0, 1fr) auto;
}

.records-panel {
  grid-template-rows: auto minmax(0, 1fr);
}

.submit-scroll,
.records-scroll {
  min-height: 0;
  overflow-y: auto;
  display: grid;
  align-content: start;
  gap: 16px;
  padding-right: 4px;
}

.records-scroll .empty-state,
.records-scroll .loading-state {
  min-height: 0;
}

textarea {
  min-height: 124px;
}

@media (max-width: 1080px) {
  .appeal-layout {
    height: auto;
  }

  .submit-panel,
  .records-panel {
    height: auto;
  }
}
</style>
