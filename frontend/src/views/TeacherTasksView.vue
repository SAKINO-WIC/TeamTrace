<script setup>
import EmptyState from '../components/common/EmptyState.vue'
import FileUploadZone from '../components/common/FileUploadZone.vue'
import TaskStatusBadge from '../components/common/TaskStatusBadge.vue'
import WorkspaceDialogMask from '../components/common/WorkspaceDialogMask.vue'
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTeacherLocale } from '../composables/useTeacherLocale'
import { createTeacherClassTask, fetchTeacherClassDetail, fetchTeacherClassTasks } from '../services/teacher'
import { buildTeacherTaskDetailLocation } from '../utils/teacherTaskNavigation'
import { readSessionCache } from '../utils/sessionCache'
import { loadWithStaleSessionCache } from '../utils/staleSessionLoad'
import { buildTaskAttachments } from '../utils/taskAttachments'
import { formatDateTime, formatTaskStatus } from '../utils/teacher'

const route = useRoute()
const router = useRouter()
const { t, isEn } = useTeacherLocale()

const classId = computed(() => String(route.params.classId || ''))
const loading = ref(false)
const publishing = ref(false)
const publishVisible = ref(false)
const classDetail = ref(null)
const tasks = ref([])
const taskLoadError = ref('')
const message = ref('')
const messageType = ref('info')
const recentlyCreatedTaskId = ref('')
let taskHighlightTimer = null

const filters = reactive({
  keyword: '',
  status: 'all',
  peerReview: 'all',
})

const publishForm = reactive({
  name: '',
  description: '',
  deadline: '',
  enablePeerReview: true,
  peerReviewOffsetHours: 1,
  attachmentLink: '',
  uploadedFiles: [],
})

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

function getTaskStatusKey(value) {
  const parsed = Number(value)
  if (!Number.isNaN(parsed)) return String(parsed)
  const normalized = String(value ?? '').trim().toLowerCase()
  const map = {
    open: '0', pending: '0', not_started: '0',
    in_progress: '1', active: '1', running: '1',
    closed: '2', overdue: '2', done: '2', finished: '2',
  }
  return map[normalized] || normalized
}

function normalizeTask(raw) {
  const rawStatus = raw?.taskStatus ?? raw?.status
  return {
    id: String(raw?.taskId ?? raw?.id ?? '-'),
    name: raw?.name ?? raw?.taskName ?? '-',
    description: raw?.description ?? '',
    statusKey: getTaskStatusKey(rawStatus),
    status: formatTaskStatus(rawStatus),
    rawDeadline: raw?.deadline ?? '',
    deadline: formatDateTime(raw?.deadline),
    enablePeerReview: Boolean(raw?.enablePeerReview),
  }
}

function normalizeClassDetail(raw) {
  return {
    studentCount: Number(raw?.studentCount ?? raw?.memberCount ?? 0),
    groupCount: Number(raw?.groupCount ?? 0),
  }
}

const filteredTasks = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return tasks.value.filter((item) => {
    const keywordHit =
      !keyword ||
      [item.name, item.description, item.id].some((field) => String(field || '').toLowerCase().includes(keyword))
    const statusHit = filters.status === 'all' || item.statusKey === filters.status
    const peerHit =
      filters.peerReview === 'all' ||
      (filters.peerReview === 'enabled' && item.enablePeerReview) ||
      (filters.peerReview === 'disabled' && !item.enablePeerReview)
    return keywordHit && statusHit && peerHit
  })
})

const hasActiveFilters = computed(() =>
  Boolean(filters.keyword.trim()) || filters.status !== 'all' || filters.peerReview !== 'all',
)

const studentCount = computed(() => classDetail.value?.studentCount ?? null)
const groupCount = computed(() => classDetail.value?.groupCount ?? null)
const hasNoStudents = computed(() => studentCount.value === 0)
const showEmptyTaskGuide = computed(() =>
  !loading.value && !taskLoadError.value && !tasks.value.length && !hasActiveFilters.value,
)

const emptyTaskTitle = computed(() => (hasNoStudents.value ? t('可以先发布任务', 'You can publish a task first') : t('发布第一个任务', 'Publish your first task')))
const emptyTaskDescription = computed(() =>
  hasNoStudents.value
    ? t('当前班级还没有学生。可以先发布任务，但学生加入后才会开始协作。', 'No students yet. You can publish a task now; collaboration starts after students join.')
    : t('发布第一个任务后，小组即可开始拆解子任务。', 'After publishing the first task, groups can break down subtasks.'),
)

function buildClassTaskCenterQuery(taskId = '') {
  const query = {
    classId: classId.value,
    from: 'class-task',
  }

  if (taskId) {
    query.taskId = taskId
  }

  return query
}

function goToScores(taskId = '') {
  router.push({
    path: '/teacher/scores',
    query: buildClassTaskCenterQuery(taskId),
  })
}

function goToAppeals(taskId = '') {
  router.push({
    path: '/teacher/appeals',
    query: buildClassTaskCenterQuery(taskId),
  })
}

function goToTaskDetail(taskId) {
  router.push(buildTeacherTaskDetailLocation(classId.value, taskId, {}, 'class-tasks'))
}

function resetFilters() {
  filters.keyword = ''
  filters.status = 'all'
  filters.peerReview = 'all'
}

function teacherTasksCacheKey() {
  return `teacher:class:${classId.value}:tasks-page`
}

function applyTeacherTasksPayload(payload) {
  if (payload?.classDetail) {
    classDetail.value = payload.classDetail
  }
  tasks.value = Array.isArray(payload?.tasks) ? payload.tasks : []
}

async function loadTasks(options = {}) {
  const { silentSuccess = false } = options
  taskLoadError.value = ''
  const hadCacheBefore = Boolean(readSessionCache(teacherTasksCacheKey(), 180000))
  loading.value = !hadCacheBefore && !silentSuccess

  try {
    const { error } = await loadWithStaleSessionCache({
      cacheKey: teacherTasksCacheKey(),
      ttlMs: 180000,
      force: options.force === true,
      apply: applyTeacherTasksPayload,
      fetchFresh: async () => {
        const [detailResult, tasksResult] = await Promise.allSettled([
          fetchTeacherClassDetail(classId.value),
          fetchTeacherClassTasks(classId.value),
        ])
        const nextClassDetail =
          detailResult.status === 'fulfilled'
            ? normalizeClassDetail(detailResult.value?.data?.data || {})
            : null
        if (tasksResult.status === 'rejected') {
          throw tasksResult.reason
        }
        const payload = tasksResult.value?.data?.data || []
        const nextTasks = Array.isArray(payload) ? payload.map(normalizeTask) : []
        return { classDetail: nextClassDetail, tasks: nextTasks }
      },
    })
    if (error && !tasks.value.length) {
      taskLoadError.value = error?.message || t('加载任务失败', 'Failed to load tasks')
      if (!silentSuccess) {
        setMessage(taskLoadError.value, 'error')
      }
    } else if (!silentSuccess) {
      setMessage('')
    }
  } catch (error) {
    if (!tasks.value.length) {
      tasks.value = []
      taskLoadError.value = error.message || t('加载任务失败', 'Failed to load tasks')
      setMessage(taskLoadError.value, 'error')
    }
  } finally {
    loading.value = false
  }
}

function openPublishDialog() {
  publishForm.name = ''
  publishForm.description = ''
  publishForm.deadline = ''
  publishForm.enablePeerReview = true
  publishForm.peerReviewOffsetHours = 1
  publishForm.attachmentLink = ''
  publishForm.uploadedFiles = []
  publishVisible.value = true
}

function handleUploadError(text) {
  if (text) {
    setMessage(text, 'error')
  }
}

function closePublishDialog() {
  if (publishing.value) {
    return
  }
  publishVisible.value = false
}

function highlightCreatedTask(taskId) {
  if (!taskId) {
    return
  }
  recentlyCreatedTaskId.value = String(taskId)
  if (taskHighlightTimer) {
    clearTimeout(taskHighlightTimer)
  }
  taskHighlightTimer = window.setTimeout(() => {
    recentlyCreatedTaskId.value = ''
    taskHighlightTimer = null
  }, 3000)
}

async function submitPublish() {
  if (!publishForm.name.trim()) {
    setMessage(t('请输入任务名称。', 'Task name is required.'), 'error')
    return
  }
  if (!publishForm.deadline) {
    setMessage(t('请选择截止时间。', 'Deadline is required.'), 'error')
    return
  }
  if (publishForm.enablePeerReview && Number(publishForm.peerReviewOffsetHours) <= 0) {
    setMessage(t('互评时长至少为 1 小时。', 'Peer review duration must be at least 1 hour.'), 'error')
    return
  }
  const attachmentLink = publishForm.attachmentLink.trim()
  if (attachmentLink && !/^https?:\/\//i.test(attachmentLink)) {
    setMessage(t('附件链接必须以 http:// 或 https:// 开头。', 'Attachment link must start with http:// or https://.'), 'error')
    return
  }

  publishing.value = true
  try {
    const result = await createTeacherClassTask(classId.value, {
      name: publishForm.name.trim(),
      description: publishForm.description.trim(),
      deadline: new Date(publishForm.deadline).toISOString(),
      enablePeerReview: publishForm.enablePeerReview,
      peerReviewOffsetHours: Number(publishForm.peerReviewOffsetHours || 1),
      peerReviewMaxScore: 100,
      peerReviewWeight: 0.4,
      teacherScoreWeight: 0.6,
      attachments: buildTaskAttachments({
        files: publishForm.uploadedFiles,
        link: attachmentLink,
      }),
    })
    const createdTask = result?.data?.data || result?.data || {}
    const createdTaskId = createdTask?.taskId ?? createdTask?.id ?? ''
    publishVisible.value = false
    publishing.value = false
    highlightCreatedTask(createdTaskId)
    setMessage(
      hasNoStudents.value ? t('任务已发布到当前班级，学生加入后即可开始协作。', 'Task published; collaboration starts when students join.') : t('任务已发布到当前班级。', 'Task published to this class.'),
      'success',
    )
    loadTasks({ force: true, silentSuccess: true }).catch(() => {})
  } catch (error) {
    setMessage(error.message || t('发布任务失败', 'Failed to publish task'), 'error')
  } finally {
    publishing.value = false
  }
}

watch(classId, (nextId, prevId) => {
  if (nextId && nextId !== prevId) {
    classDetail.value = null
    tasks.value = []
    loadTasks()
  }
})
watch(isEn, () => loadTasks({ silentSuccess: true }))
function tryOpenPublishFromQuery() {
  if (String(route.query.publish || '') === '1') {
    openPublishDialog()
    const nextQuery = { ...route.query }
    delete nextQuery.publish
    router.replace({ path: route.path, query: nextQuery })
  }
}

onMounted(async () => {
  await loadTasks()
  tryOpenPublishFromQuery()
})

onUnmounted(() => {
  if (taskHighlightTimer) {
    clearTimeout(taskHighlightTimer)
  }
})
</script>

<template>
  <div class="teacher-page">
    <header class="card topbar">
      <div>
        <h2>{{ t('任务管理', 'Task management') }}</h2>
      </div>
      <div class="actions">
        <button class="primary-btn publish-btn" type="button" :disabled="publishing" @click="openPublishDialog">
          {{ tasks.length ? t('发布任务', 'Publish task') : t('发布第一个任务', 'Publish first task') }}
        </button>
      </div>
    </header>

    <p v-if="message" class="message" :class="messageType">{{ message }}</p>

    <section v-if="taskLoadError" class="card empty-guide">
      <EmptyState
        icon="task"
        :title="t('任务列表暂不可用', 'Task list unavailable')"
        description=""
        :action-label="t('重新加载任务', 'Reload tasks')"
        :secondary-label="t('发布任务', 'Publish task')"
        @action="loadTasks"
        @secondary="openPublishDialog"
      />
    </section>

    <section v-else-if="showEmptyTaskGuide" class="card empty-guide">
      <EmptyState
        icon="task"
        :title="emptyTaskTitle"
        :description="emptyTaskDescription"
        :action-label="t('发布第一个任务', 'Publish first task')"
        :secondary-label="t('返回班级概览', 'Back to class overview')"
        @action="openPublishDialog"
        @secondary="router.push(`/teacher/classes/${classId}`)"
      />
    </section>

    <section v-if="!taskLoadError && !showEmptyTaskGuide" class="card panel task-workspace">
      <div class="panel-head">
        <div>
          <h3>{{ t('任务列表', 'Task list') }}</h3>
          <p class="desc compact">{{ t('共 {total} 个任务，筛选结果 {filtered} 条', '{total} tasks, {filtered} shown').replace('{total}', tasks.length).replace('{filtered}', filteredTasks.length) }}</p>
        </div>
        <button class="ghost-btn" type="button" :disabled="!hasActiveFilters" @click="resetFilters">{{ t('重置筛选', 'Reset filters') }}</button>
      </div>

      <div class="filters">
        <label>
          <span>{{ t('搜索任务', 'Search tasks') }}</span>
          <input v-model.trim="filters.keyword" type="text" :placeholder="t('任务名称 / 描述 / ID', 'Name / description / ID')" />
        </label>
        <label>
          <span>{{ t('状态', 'Status') }}</span>
          <select v-model="filters.status">
            <option value="all">{{ t('全部状态', 'All statuses') }}</option>
            <option value="0">{{ t('未开始', 'Not started') }}</option>
            <option value="1">{{ t('进行中', 'In progress') }}</option>
            <option value="2">{{ t('已截止', 'Closed') }}</option>
          </select>
        </label>
        <label>
          <span>{{ t('互评状态', 'Peer review') }}</span>
          <select v-model="filters.peerReview">
            <option value="all">{{ t('全部', 'All') }}</option>
            <option value="enabled">{{ t('已开启互评', 'Peer review on') }}</option>
            <option value="disabled">{{ t('未开启互评', 'Peer review off') }}</option>
          </select>
        </label>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ t('任务名称', 'Task name') }}</th>
              <th>{{ t('状态', 'Status') }}</th>
              <th>{{ t('截止时间', 'Deadline') }}</th>
              <th>{{ t('互评', 'Peer review') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in filteredTasks"
              :key="item.id"
              class="task-row-link"
              :class="{ 'created-task-row': item.id === recentlyCreatedTaskId }"
              @click="goToTaskDetail(item.id)"
            >
              <td>
                <strong>{{ item.name }}</strong>
                <p class="desc inline">{{ item.description || t('暂无任务描述', 'No description') }}</p>
              </td>
              <td>
                <TaskStatusBadge :status="item.statusKey" :label="item.status" />
              </td>
              <td>{{ item.deadline }}</td>
              <td>
                <span class="peer-pill" :class="{ active: item.enablePeerReview }">
                  {{ item.enablePeerReview ? t('已开启', 'Enabled') : t('未开启', 'Disabled') }}
                </span>
                <span v-if="hasNoStudents" class="wait-pill table-wait">{{ t('等待学生加入', 'Waiting for students') }}</span>
              </td>
            </tr>
            <tr v-if="!filteredTasks.length">
              <td colspan="4" class="empty">
                <div class="filter-empty">
                  <p>{{ hasActiveFilters ? t('当前筛选下没有任务。', 'No tasks match the current filters.') : emptyTaskDescription }}</p>
                  <button v-if="hasActiveFilters" class="ghost-btn" type="button" @click="resetFilters">{{ t('重置筛选', 'Reset filters') }}</button>
                  <button v-else class="primary-btn" type="button" @click="openPublishDialog">{{ t('发布第一个任务', 'Publish first task') }}</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <WorkspaceDialogMask :open="publishVisible" @close="closePublishDialog">
      <section class="card dialog-panel">
        <div class="panel-head">
          <h3>{{ t('发布任务', 'Publish task') }}</h3>
        </div>

        <div class="dialog-grid">
          <label class="full-width">
            <span>{{ t('任务名称', 'Task name') }}</span>
            <input v-model.trim="publishForm.name" type="text" :placeholder="t('例如：需求分析与原型设计', 'e.g. Requirements & prototype')" />
          </label>

          <label class="full-width">
            <span>{{ t('任务描述', 'Description') }}</span>
            <textarea v-model.trim="publishForm.description" rows="4" :placeholder="t('填写任务说明、交付要求和验收重点', 'Deliverables, requirements, acceptance criteria')"></textarea>
          </label>

          <label>
            <span>{{ t('截止时间', 'Deadline') }}</span>
            <input v-model="publishForm.deadline" type="datetime-local" />
          </label>

          <div class="publish-attachment-block full-width">
            <div class="attachment-head">
              <span>{{ t('任务附件', 'Task attachments') }}</span>
              <small>{{ t('支持文档、图片、PDF、压缩包，单文件不超过 10MB', 'Docs, images, PDFs and archives, up to 10MB each') }}</small>
            </div>
            <FileUploadZone
              v-model="publishForm.uploadedFiles"
              :disabled="publishing"
              :max-files="8"
              @error="handleUploadError"
            />
          </div>

          <label class="full-width">
            <span>{{ t('附件链接', 'Attachment link') }}</span>
            <input
              v-model.trim="publishForm.attachmentLink"
              type="url"
              :placeholder="t('可选，粘贴 http:// 或 https:// 开头的资料链接', 'Optional, paste a resource link starting with http:// or https://')"
            />
          </label>

          <label>
            <span>{{ t('是否开启互评', 'Enable peer review') }}</span>
            <select v-model="publishForm.enablePeerReview">
              <option :value="true">{{ t('开启', 'Enable') }}</option>
              <option :value="false">{{ t('关闭', 'Disable') }}</option>
            </select>
          </label>

          <label v-if="publishForm.enablePeerReview">
            <span>{{ t('互评时长（小时）', 'Peer review duration (hours)') }}</span>
            <input v-model.number="publishForm.peerReviewOffsetHours" type="number" min="1" />
          </label>

          <article class="weight-tip full-width">
            {{ t('互评与教师评分将作为独立记录保存，系统仅提供参考汇总，不自动生成最终成绩。', 'Peer reviews and teacher scores are stored as separate records. The system provides reference summaries only, not final grades.') }}
          </article>
        </div>

        <div class="panel-actions">
          <button class="secondary-btn" type="button" :disabled="publishing" @click="closePublishDialog">{{ t('取消', 'Cancel') }}</button>
          <button class="primary-btn" type="button" :disabled="publishing" @click="submitPublish">
            {{ publishing ? t('发布中...', 'Publishing…') : t('确认发布', 'Confirm publish') }}
          </button>
        </div>
      </section>
    </WorkspaceDialogMask>
  </div>
</template>

<style scoped>
.teacher-page,
.filters,
.dialog-grid {
  display: grid;
  gap: 16px;
}

.teacher-page {
  gap: 18px;
}

.card {
  background: var(--teacher-surface);
  border-radius: var(--teacher-radius-card);
  box-shadow: var(--teacher-shadow);
}

.topbar,
.panel,
.dialog-panel {
  padding: 20px;
}

.topbar,
.actions,
.panel-head,
.panel-actions,
.action-list {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.filters {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.desc,
.message,
.empty {
  margin: 8px 0 0;
  color: var(--teacher-text-tertiary);
}

.compact {
  margin-top: 4px;
}

.task-workspace {
  display: grid;
  gap: 16px;
}

.message.success {
  color: var(--teacher-success);
}

.message.error {
  color: var(--teacher-danger);
}

.empty-guide {
  padding: 10px;
}

.wait-pill {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(245, 158, 11, 0.12);
  color: #9a5a13;
  font-size: 12px;
  font-weight: 700;
}

.table-wait {
  margin-left: 8px;
  margin-top: 6px;
}

.filter-empty {
  display: grid;
  justify-items: center;
  gap: 12px;
}

.filter-empty p {
  margin: 0;
}

h2,
h3 {
  margin: 0;
}

label {
  display: grid;
  gap: 8px;
  color: var(--teacher-text-secondary);
  font-size: 13px;
}

input,
select,
textarea {
  border: 1px solid var(--teacher-border);
  border-radius: var(--teacher-radius-control);
  background: var(--teacher-surface);
  color: var(--teacher-text-primary);
  padding: 0 12px;
  font-family: inherit;
}

input,
select {
  height: 42px;
}

textarea {
  padding: 10px 12px;
  resize: vertical;
}

input:focus,
input:focus-visible,
select:focus,
select:focus-visible,
textarea:focus,
textarea:focus-visible {
  outline: none;
  border-color: color-mix(in srgb, var(--teacher-accent) 40%, var(--teacher-border));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--teacher-accent) 14%, transparent);
}

.status-pill,
.peer-pill,
.deadline-pill {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.status-pill {
  background: rgba(36, 86, 173, 0.12);
  color: var(--teacher-accent);
}

.peer-pill {
  background: var(--teacher-surface-muted);
  color: var(--teacher-text-secondary);
}

.peer-pill.active {
  background: rgba(36, 86, 173, 0.12);
  color: var(--teacher-accent);
}

.deadline-pill {
  background: rgba(17, 24, 39, 0.06);
  color: var(--teacher-text-primary);
}

.link-btn,
.secondary-btn,
.primary-btn,
.ghost-btn,
.table-primary-btn {
  min-height: 40px;
  border: 0;
  border-radius: 10px;
  padding: 0 14px;
  font-family: inherit;
  font-weight: 700;
  cursor: pointer;
}

.link-btn:disabled,
.secondary-btn:disabled,
.primary-btn:disabled,
.ghost-btn:disabled,
.table-primary-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.link-btn {
  min-height: auto;
  background: transparent;
  color: var(--teacher-accent);
  padding: 0;
}

.secondary-btn {
  background: var(--teacher-surface-muted);
  color: var(--teacher-text-primary);
}

.primary-btn,
.table-primary-btn {
  background: var(--teacher-accent);
  color: #fff;
}

.publish-btn {
  min-height: 46px;
  padding: 0 20px;
  border-radius: 12px;
  font-size: 15px;
}

.ghost-btn {
  background: transparent;
  border: 1px solid var(--teacher-border);
  color: var(--teacher-text-primary);
}

.table-primary-btn {
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
}

.table-wrap {
  margin-top: 16px;
  max-height: 620px;
  overflow: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  min-width: 860px;
}

th,
td {
  padding: 14px 10px;
  border-bottom: 1px solid var(--teacher-divider);
  text-align: left;
  vertical-align: top;
  font-size: 14px;
}

.created-task-row {
  animation: task-row-flash 3s ease-out;
}

.task-row-link {
  cursor: pointer;
  transition: background 0.15s ease;
}

.task-row-link:hover {
  background: rgba(248, 250, 252, 0.92);
}

th {
  color: var(--teacher-text-tertiary);
  font-weight: 600;
}

.inline {
  margin-top: 6px;
}

.dialog-mask {
  /* positioning handled by teacher-dialog.css */
}

.dialog-panel {
  width: min(980px, 92vw);
  min-height: min(720px, 88vh);
  display: grid;
  grid-template-rows: auto 1fr auto;
}

.dialog-grid {
  margin-top: 16px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-content: start;
  overflow-y: auto;
  padding: 4px;
}

.full-width {
  grid-column: 1 / -1;
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

.publish-attachment-block {
  display: grid;
  gap: 10px;
}

.attachment-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: baseline;
}

.attachment-head span {
  color: var(--teacher-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.attachment-head small {
  color: var(--teacher-text-tertiary);
  font-size: 12px;
  text-align: right;
}

@keyframes task-row-flash {
  0% {
    background: rgba(36, 86, 173, 0.14);
  }
  100% {
    background: transparent;
  }
}

@media (max-width: 1180px) {
  .filters {
    grid-template-columns: 1fr;
  }

  .table-wrap {
    max-height: none;
  }
}

@media (max-width: 760px) {
  .topbar,
  .actions,
  .panel-head,
  .panel-actions,
  .action-list {
    flex-direction: column;
    align-items: flex-start;
  }

  .dialog-grid {
    grid-template-columns: 1fr;
  }
}
</style>
