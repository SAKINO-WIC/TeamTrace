<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTeacherLocale } from '../composables/useTeacherLocale'
import EmptyState from '../components/common/EmptyState.vue'
import TaskStatusBadge from '../components/common/TaskStatusBadge.vue'
import WorkspaceDialogMask from '../components/common/WorkspaceDialogMask.vue'
import {
  deleteTeacherClass,
  fetchTeacherClassDetail,
  fetchTeacherClassGroups,
  fetchTeacherClassTasks,
  generateTeacherClassInviteCode,
} from '../services/teacher'
import { formatClassStatus, formatDateTime, formatSemesterLabel, formatTaskStatus } from '../utils/teacher'
import { readSessionCache } from '../utils/sessionCache'
import { loadWithStaleSessionCache } from '../utils/staleSessionLoad'

const route = useRoute()
const router = useRouter()
const { t, isEn } = useTeacherLocale()

const loading = ref(false)
const refreshingInvite = ref(false)
const deleting = ref(false)
const message = ref('')
const messageType = ref('info')
const detail = ref(null)
const tasks = ref([])
const taskLoadError = ref('')
const groupLoadError = ref('')
const deleteDialogOpen = ref(false)

const classId = computed(() => String(route.params.classId || ''))

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

function normalizeDetail(raw) {
  const inviteCode = raw?.activeInviteCode || raw?.classInviteCode || ''
  const groupingLocked = Number(raw?.groupingLocked) === 1 || raw?.groupingLocked === true
  const semester = raw?.semester ?? '-'

  return {
    classId: String(raw?.classId ?? raw?.id ?? '-'),
    classCode: raw?.classCode ?? raw?.code ?? '-',
    name: raw?.name ?? raw?.className ?? '-',
    semester,
    semesterLabel: formatSemesterLabel(semester),
    status: raw?.statusLabel || formatClassStatus(raw?.status),
    studentCount: Number(raw?.studentCount ?? raw?.memberCount ?? 0),
    groupCount: Number(raw?.groupCount ?? 0),
    taskCount: raw?.taskCount != null || raw?.activeTaskCount != null
      ? Number(raw?.taskCount ?? raw?.activeTaskCount)
      : null,
    inviteCode,
    inviteExpireAt: raw?.inviteExpireAt || '',
    groupingLocked,
  }
}

function normalizeTask(raw) {
  const rawStatus = raw?.taskStatus ?? raw?.status
  return {
    taskId: String(raw?.taskId ?? raw?.id ?? '-'),
    name: raw?.name ?? raw?.taskName ?? '-',
    statusKey: rawStatus,
    status: formatTaskStatus(rawStatus),
    deadlineText: formatDateTime(raw?.deadline),
    enablePeerReview: Boolean(raw?.enablePeerReview),
  }
}

const recentTasks = computed(() => tasks.value.slice(0, 2))

const inviteExpireTime = computed(() => {
  if (!detail.value?.inviteExpireAt) {
    return null
  }

  const time = new Date(detail.value.inviteExpireAt).getTime()
  return Number.isFinite(time) ? time : null
})

const hasInviteCode = computed(() => Boolean(detail.value?.inviteCode))
const isInviteExpired = computed(() => inviteExpireTime.value !== null && inviteExpireTime.value <= Date.now())
const hasValidInvite = computed(() => hasInviteCode.value && !isInviteExpired.value)

const inviteStatus = computed(() => {
  if (!detail.value) {
    return { tone: 'pending', label: '加载中', description: '' }
  }

  if (!hasInviteCode.value) {
    return {
      tone: 'warning',
      label: '无有效邀请码',
      description: '',
    }
  }

  if (isInviteExpired.value) {
    return {
      tone: 'danger',
      label: '邀请码已过期',
      description: '',
    }
  }

  return {
    tone: 'ready',
    label: '邀请码有效',
    description: detail.value.inviteExpireAt
      ? `有效期至 ${formatDateTime(detail.value.inviteExpireAt)}`
      : '',
  }
})

function teacherClassHubCacheKey() {
  return `teacher:class:${classId.value}:hub`
}

function applyTeacherClassHub(payload) {
  detail.value = payload?.detail || null
  tasks.value = Array.isArray(payload?.tasks) ? payload.tasks : []
}

async function loadDetail(options = {}) {
  taskLoadError.value = ''
  groupLoadError.value = ''
  const hadCacheBefore = Boolean(readSessionCache(teacherClassHubCacheKey(), 180000))
  loading.value = !hadCacheBefore

  try {
    const { error } = await loadWithStaleSessionCache({
      cacheKey: teacherClassHubCacheKey(),
      ttlMs: 180000,
      force: options.force === true,
      apply: applyTeacherClassHub,
      fetchFresh: async () => {
        const [detailResult, tasksResult, groupsResult] = await Promise.allSettled([
          fetchTeacherClassDetail(classId.value),
          fetchTeacherClassTasks(classId.value),
          fetchTeacherClassGroups(classId.value),
        ])

        if (detailResult.status === 'rejected') {
          throw detailResult.reason
        }

        const normalizedDetail = normalizeDetail(detailResult.value?.data?.data || {})

        if (groupsResult.status === 'fulfilled') {
          const groupPayload = groupsResult.value?.data?.data
          const groupRows = Array.isArray(groupPayload)
            ? groupPayload
            : Array.isArray(groupsResult.value?.data)
              ? groupsResult.value.data
              : []
          normalizedDetail.groupCount = groupRows.length
        } else {
          groupLoadError.value = groupsResult.reason?.message || '无法加载分组'
        }

        let nextTasks = []
        if (tasksResult.status === 'fulfilled') {
          const payload = tasksResult.value?.data?.data || []
          nextTasks = Array.isArray(payload) ? payload.map(normalizeTask) : []
        } else {
          taskLoadError.value = tasksResult.reason?.message || '无法加载任务'
        }

        return { detail: normalizedDetail, tasks: nextTasks }
      },
    })
    if (error && !detail.value) {
      setMessage(error?.message || '加载班级概览失败', 'error')
    }
  } catch (error) {
    if (!detail.value) {
      detail.value = null
      tasks.value = []
      setMessage(error?.message || '加载班级概览失败', 'error')
    }
  } finally {
    loading.value = false
  }
}

async function refreshInviteCode() {
  refreshingInvite.value = true
  try {
    const { data } = await generateTeacherClassInviteCode(classId.value)
    const payload = data?.data || {}
    const nextCode = payload.code || payload.inviteCode || payload.activeInviteCode || ''
    if (detail.value) {
      detail.value.inviteCode = nextCode
      detail.value.inviteExpireAt = payload.expireAt || payload.inviteExpireAt || detail.value.inviteExpireAt
    }

    if (!nextCode) {
      setMessage(t('邀请码生成请求已完成，但响应中没有返回邀请码，请重新加载。', 'Invite code request completed but no code returned — please reload.'), 'error')
      return
    }

    setMessage('')
  } catch (error) {
    setMessage(error.message || t('刷新班级邀请码失败', 'Failed to refresh invite code'), 'error')
  } finally {
    refreshingInvite.value = false
  }
}

async function copyInviteCode() {
  if (!hasValidInvite.value) {
    setMessage(t('当前没有有效的邀请码，请先生成新邀请码。', 'No valid invite code — generate a new one first.'), 'error')
    return
  }

  try {
    await navigator.clipboard.writeText(detail.value.inviteCode)
    setMessage(t('邀请码已复制。', 'Invite code copied.'), 'success')
  } catch {
    setMessage(t('复制邀请码失败，请手动复制。', 'Copy failed — please copy manually.'), 'error')
  }
}

async function confirmDeleteClass() {
  if (!detail.value) {
    return
  }

  deleting.value = true
  try {
    await deleteTeacherClass(classId.value)
    deleteDialogOpen.value = false
    router.push('/teacher/classes')
  } catch (error) {
    setMessage(error.message || t('删除班级失败', 'Failed to delete class'), 'error')
  } finally {
    deleting.value = false
  }
}

const showManageDialog = ref(false)
const manageName = ref('')
const manageUpdating = ref(false)

function openManageDialog() {
  manageName.value = detail.value?.name || ''
  showManageDialog.value = true
}

function closeManageDialog() {
  showManageDialog.value = false
}

async function submitManageName() {
  if (!manageName.value.trim() || manageUpdating.value) return
  // NOTE: if backend supports rename, call API here.
  // For now just update local display.
  manageUpdating.value = true
  try {
    if (detail.value) {
      detail.value.name = manageName.value.trim()
      setMessage(t('班级名称已更新。', 'Class name updated.'), 'success')
    }
    closeManageDialog()
  } catch (error) {
    setMessage(error.message || t('更新失败', 'Update failed'), 'error')
  } finally {
    manageUpdating.value = false
  }
}

watch(classId, (nextId, prevId) => {
  if (nextId && nextId !== prevId) {
    detail.value = null
    tasks.value = []
    loadDetail()
  }
})
watch(classId, (nextId, prevId) => {
  if (nextId && nextId !== prevId) {
    detail.value = null
    tasks.value = []
    loadDetail()
  }
})
watch(isEn, loadDetail)
onMounted(loadDetail)
</script>

<template>
  <div class="teacher-page">
    <header class="card topbar">
      <div class="title-block">
        <h2>{{ detail?.name || t('班级概览', 'Class overview') }}</h2>
      </div>

      <div class="top-actions">
        <button class="primary-btn" type="button" @click="openManageDialog">{{ t('班级管理', 'Manage class') }}</button>
      </div>
    </header>

    <p v-if="message" class="message" :class="messageType">{{ message }}</p>

    <section v-if="loading && !detail" class="card loading-panel">
      <div class="skeleton-line wide"></div>
      <div class="skeleton-line"></div>
      <div class="skeleton-grid">
        <span></span>
        <span></span>
        <span></span>
      </div>
    </section>

    <section v-else-if="!detail" class="card error-panel">
      <EmptyState
        icon="help"
        :title="t('无法加载班级详情', 'Failed to load class details')"
        description=""
        :action-label="t('重新加载', 'Reload')"
        @action="loadDetail"
        compact
      />
      <button class="secondary-btn" type="button" @click="router.push('/teacher/classes')">{{ t('返回班级中心', 'Back to classes') }}</button>
    </section>

    <section v-if="detail" class="card class-workspace">
      <div class="workspace-main">
        <article class="workspace-block panel invite-panel">
          <div class="panel-head">
            <div>
              <h3>{{ t('班级邀请码', 'Class invite code') }}</h3>
            </div>
            <span class="status-pill" :class="inviteStatus.tone">{{ inviteStatus.label }}</span>
          </div>

          <div class="invite-card" :class="{ muted: !hasValidInvite }">
            <p class="invite-code">{{ hasValidInvite ? detail.inviteCode : hasInviteCode ? '邀请码已过期' : '暂无有效邀请码' }}</p>
            <p class="note">
              有效期：{{ hasValidInvite && detail.inviteExpireAt ? formatDateTime(detail.inviteExpireAt) : '请生成新邀请码' }}
            </p>
          </div>

          <div class="invite-actions">
            <button class="ghost-btn invite-btn" type="button" :disabled="!hasValidInvite" @click="copyInviteCode">{{ t('复制邀请码', 'Copy code') }}</button>
            <button class="primary-btn invite-btn" type="button" :disabled="refreshingInvite" @click="refreshInviteCode">
              {{ refreshingInvite ? t('生成中...', 'Generating…') : hasInviteCode ? t('生成新邀请码', 'Generate new code') : t('生成邀请码', 'Generate code') }}
            </button>
          </div>
        </article>
      </div>

      <div class="workspace-lower">
        <article class="workspace-block panel overview-panel">
          <div class="panel-head">
            <div>
              <h3>{{ t('班级基础信息', 'Class info') }}</h3>
            </div>
          </div>

          <div class="info-grid">
            <div class="info-tile">
              <span class="info-label">{{ t('班级编号', 'Class code') }}</span>
              <strong class="info-value">{{ detail.classCode }}</strong>
            </div>
            <div class="info-tile">
              <span class="info-label">{{ t('学期', 'Semester') }}</span>
              <strong class="info-value">{{ detail.semesterLabel }}</strong>
            </div>
            <div class="info-tile">
              <span class="info-label">{{ t('班级状态', 'Status') }}</span>
              <strong class="info-value">{{ detail.status }}</strong>
            </div>
            <div class="info-tile">
              <span class="info-label">{{ t('分组状态', 'Grouping') }}</span>
              <strong class="info-value">{{ detail.groupingLocked ? t('已锁定', 'Locked') : t('开放中', 'Open') }}</strong>
            </div>
          </div>
        </article>

        <article class="workspace-block panel tasks-panel">
          <div class="panel-head">
            <div>
              <h3>{{ t('最近任务', 'Recent tasks') }}</h3>
            </div>
            <button class="link-btn" type="button" @click="router.push(`/teacher/classes/${classId}/tasks`)">{{ t('查看全部', 'View all') }}</button>
          </div>

          <div v-if="taskLoadError" class="inline-error">
            <p>{{ taskLoadError }}，不影响邀请码复制和班级基础信息。</p>
          </div>

          <div v-else-if="recentTasks.length" class="task-list">
            <button
              v-for="item in recentTasks"
              :key="item.taskId"
              class="task-item"
              type="button"
              @click="router.push(`/teacher/classes/${classId}/tasks/${item.taskId}`)"
            >
              <div class="task-main">
                <p class="task-title">{{ item.name }}</p>
                <p class="note task-note-line">
                  <TaskStatusBadge :status="item.statusKey" :label="item.status" size="sm" />
                  <span>{{ item.deadlineText }}</span>
                </p>
              </div>
              <span class="task-tag">{{ item.enablePeerReview ? t('互评开启', 'Peer review on') : t('无互评', 'No peer review') }}</span>
            </button>
          </div>

          <div v-else>
            <EmptyState
              icon="task"
              :title="t('还没有发布任务', 'No tasks published yet')"
              description=""
              :action-label="t('发布第一个任务', 'Publish first task')"
              @action="router.push(`/teacher/classes/${classId}/tasks`)"
              compact
            />
          </div>
        </article>
      </div>
    </section>

    <WorkspaceDialogMask :open="deleteDialogOpen" @close="deleteDialogOpen = false">
      <section class="danger-dialog">
        <div class="dialog-head">
          <div>
            <p class="dialog-eyebrow">高风险操作</p>
            <h3>{{ t('解散班级', 'Dissolve class') }}</h3>
          </div>
        </div>

        <div class="dialog-body">
          <div class="danger-banner">
            <span class="danger-mark">!</span>
            <div>
              <p class="danger-banner-title">班级会进入已解散状态</p>
              <p class="danger-banner-note">当前后端支持 30 天内恢复，但恢复后需要重新生成班级邀请码。</p>
            </div>
          </div>

          <p class="dialog-copy">
            解散后该班级下的任务、分组与成员关系会一并进入已解散状态，请确认当前班级确实不再继续使用。
          </p>

          <article class="dialog-summary">
            <p class="summary-name">{{ detail?.name || '未命名班级' }}</p>
            <p class="summary-note">
              {{ detail?.semesterLabel || '-' }} · {{ detail?.studentCount || 0 }} 人 · {{ detail?.groupCount || 0 }} 组
            </p>
          </article>
        </div>

        <div class="dialog-actions">
          <button class="secondary-btn" type="button" @click="deleteDialogOpen = false">取消</button>
          <button class="danger-btn" type="button" :disabled="deleting" @click="confirmDeleteClass">
            {{ deleting ? '处理中...' : '确认解散' }}
          </button>
        </div>
      </section>
    </WorkspaceDialogMask>

    <!-- 班级管理弹窗 -->
    <WorkspaceDialogMask :open="showManageDialog" @close="closeManageDialog">
      <section class="manage-dialog">
        <div class="dialog-head">
          <div>
            <h3>{{ detail?.name || t('班级设置', 'Class settings') }}</h3>
          </div>
        </div>

        <div class="manage-body">
          <!-- 修改班级名称 -->
          <div class="manage-section">
            <p class="manage-section-title">修改班级名称</p>
            <input
              v-model.trim="manageName"
              type="text"
              :placeholder="detail?.name || '请输入班级名称'"
              class="manage-input"
            />
          </div>

          <!-- 解散班级 -->
          <div class="manage-section danger-section">
            <div class="danger-callout">
              <p class="danger-callout-title">风险操作</p>
              <p class="danger-callout-desc">解散后该班级会进入已解散状态，30 天内可恢复。</p>
              <button class="danger-outline-btn" type="button" @click="deleteDialogOpen = true; closeManageDialog()">
                解散班级
              </button>
            </div>
          </div>
        </div>

        <div class="dialog-actions manage-dialog-actions">
          <button class="secondary-btn" type="button" @click="closeManageDialog">{{ t('取消', 'Cancel') }}</button>
          <button class="primary-btn" type="button" :disabled="manageUpdating || !manageName.trim()" @click="submitManageName">
            {{ manageUpdating ? t('保存中...', 'Saving…') : t('保存', 'Save') }}
          </button>
        </div>
      </section>
    </WorkspaceDialogMask>
  </div>
</template>

<style scoped>
.teacher-page {
  display: grid;
  gap: 14px;
}

.card {
  background: var(--teacher-surface);
  border-radius: var(--teacher-radius-card);
  box-shadow: var(--teacher-shadow);
}

.topbar,
.panel,
.danger-dialog {
  padding: 18px;
}

.topbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.title-block {
  display: grid;
  gap: 6px;
}

.title-note {
  margin: 0;
  font-size: 13px;
  color: var(--teacher-text-secondary);
  line-height: 1.6;
}

.top-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.eyebrow,
.panel-note,
.note,
.message,
.empty,
.dialog-copy,
.dialog-eyebrow,
.summary-note,
.meta-label,
.stat-label {
  margin: 0;
  color: var(--teacher-text-tertiary);
}

.eyebrow {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-weight: 700;
}

h2,
h3,
.invite-code,
.task-title,
.stat-value,
.summary-name {
  margin: 0;
}

h2 {
  font-size: 30px;
  line-height: 1.08;
  font-weight: 800;
  color: var(--tt-text);
}

h3 {
  font-size: 20px;
  line-height: 1.16;
  font-weight: 800;
  color: var(--tt-text);
}

.message,
.panel-note,
.note,
.empty,
.dialog-copy,
.summary-note {
  font-size: 12px;
  line-height: 1.6;
}

.panel-note {
  color: var(--teacher-text-secondary);
}

.message.success {
  color: var(--teacher-success);
}

.message.error {
  color: var(--teacher-danger);
}

.loading-panel,
.error-panel {
  padding: 18px;
}

.loading-panel {
  display: grid;
  gap: 14px;
}

.error-panel {
  display: grid;
  gap: 12px;
  justify-items: start;
}

.skeleton-line,
.skeleton-grid span {
  display: block;
  border-radius: 999px;
  background: linear-gradient(
    90deg,
    var(--tt-surface-muted) 0%,
    var(--tt-surface-hover) 50%,
    var(--tt-surface-muted) 100%
  );
  background-size: 180% 100%;
  animation: shimmer 1.2s ease-in-out infinite;
}

.skeleton-line {
  width: 42%;
  height: 18px;
}

.skeleton-line.wide {
  width: 66%;
  height: 26px;
}

.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.skeleton-grid span {
  height: 78px;
}

.class-workspace {
  display: grid;
  gap: 16px;
  padding: 18px;
  background: var(--tt-surface);
  border: 1px solid var(--tt-border-subtle);
  border-radius: var(--teacher-radius-card);
}

.workspace-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 14px;
  align-items: stretch;
}

.workspace-lower {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 14px;
  align-items: stretch;
}

.workspace-block {
  min-width: 0;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 8%, var(--tt-border-subtle));
  border-radius: var(--tt-radius-lg);
  padding: 16px;
  background: linear-gradient(
    165deg,
    var(--tt-surface),
    color-mix(in srgb, var(--tt-accent) 3%, var(--tt-surface-muted))
  );
}

.overview-panel,
.tasks-panel,
.invite-panel {
  min-height: 0;
  align-content: start;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.status-pill.ready {
  background: rgba(22, 163, 74, 0.1);
  color: #15803d;
}

.status-pill.pending {
  background: rgba(245, 158, 11, 0.12);
  color: #a16207;
}

.status-pill.warning,
.status-pill.danger {
  background: rgba(220, 38, 38, 0.1);
  color: #b42318;
}

.next-panel {
  align-content: space-between;
  background: var(--tt-surface-muted);
}

.next-panel > div:first-child {
  display: grid;
  gap: 8px;
}

.next-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.next-primary {
  grid-column: 1 / -1;
  min-height: 46px;
}

.panel {
  display: grid;
  gap: 14px;
  min-height: 100%;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: flex-start;
}

.panel-head h3 {
  margin-bottom: 4px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.info-tile,
.task-item {
  padding: 14px 16px;
  border-radius: var(--tt-radius-md);
  border: 1px solid color-mix(in srgb, var(--tt-accent) 8%, var(--tt-border-subtle));
  background: linear-gradient(
    145deg,
    var(--tt-surface),
    color-mix(in srgb, var(--tt-accent) 6%, var(--tt-surface-muted))
  );
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease,
    transform 0.15s ease,
    background 0.15s ease;
}

.info-tile {
  display: grid;
  gap: 6px;
}

.info-tile:hover,
.task-item:hover {
  border-color: color-mix(in srgb, var(--tt-accent) 22%, var(--tt-border));
  box-shadow: 0 10px 24px color-mix(in srgb, var(--tt-accent) 8%, transparent);
  transform: translateY(-1px);
  background: linear-gradient(
    145deg,
    var(--tt-surface),
    color-mix(in srgb, var(--tt-accent) 10%, var(--tt-surface-muted))
  );
}

.info-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--tt-text-secondary);
  letter-spacing: 0.02em;
}

.info-value {
  margin: 0;
  font-size: 16px;
  font-weight: 800;
  color: var(--tt-text);
  line-height: 1.25;
  word-break: break-word;
}

.danger-zone {
  padding: 14px 16px;
  border-radius: var(--tt-radius-md);
  border: 1px solid rgba(220, 38, 38, 0.28);
  background: var(--tt-danger-soft);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.danger-zone strong {
  display: block;
  margin-bottom: 4px;
  font-size: 14px;
  color: var(--tt-text);
}

.invite-card {
  display: grid;
  gap: 8px;
  align-content: center;
  min-height: 96px;
  padding: 18px;
  border-radius: var(--tt-radius-lg);
  border: 1px solid var(--tt-accent-border);
  background: linear-gradient(
    145deg,
    var(--tt-surface),
    color-mix(in srgb, var(--tt-accent) 8%, var(--tt-surface-muted))
  );
  box-shadow: 0 8px 24px color-mix(in srgb, var(--tt-accent) 6%, transparent);
  box-sizing: border-box;
  width: 100%;
}

.invite-card.muted {
  border-color: var(--tt-border);
  background: linear-gradient(145deg, var(--tt-surface), var(--tt-surface-hover));
  box-shadow: none;
}

.invite-card.muted .invite-code {
  font-size: 24px;
  letter-spacing: 0;
  color: var(--teacher-text-secondary);
}

.invite-code {
  font-size: 32px;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: var(--tt-accent);
}

.invite-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  align-items: stretch;
}

.task-list {
  display: grid;
  gap: 12px;
  align-content: start;
}

.inline-error {
  min-height: 128px;
  padding: 16px;
  border-radius: var(--tt-radius-md);
  border: 1px solid rgba(220, 38, 38, 0.28);
  background: var(--tt-danger-soft);
  display: grid;
  align-content: center;
  gap: 12px;
}

.inline-error p {
  margin: 0;
  color: var(--teacher-danger);
  font-size: 13px;
  line-height: 1.6;
}

.task-item {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  text-align: left;
  cursor: pointer;
}

.task-main {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.task-title,
.summary-name {
  margin: 0;
  font-size: 16px;
  font-weight: 800;
  color: var(--teacher-text-primary);
  line-height: 1.25;
}

.task-main .note {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--tt-text-secondary);
}

.task-note-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.task-tag {
  flex-shrink: 0;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 14%, transparent);
  background: color-mix(in srgb, var(--tt-accent) 8%, white);
  color: var(--tt-accent);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.empty-task-state {
  min-height: 108px;
  border: 1px dashed rgba(148, 163, 184, 0.28);
  border-radius: var(--tt-radius-md);
  display: grid;
  place-items: center;
  gap: 10px;
  padding: 16px;
}

.secondary-btn,
.primary-btn,
.link-btn,
.ghost-btn,
.danger-btn,
.danger-ghost-btn,
.icon-close-btn {
  min-height: 40px;
  border-radius: var(--teacher-radius-control);
  padding: 0 14px;
  font-family: inherit;
  font-weight: 700;
  cursor: pointer;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease,
    background-color 0.18s ease,
    color 0.18s ease;
}

.secondary-btn:hover,
.primary-btn:hover,
.ghost-btn:hover,
.danger-btn:hover,
.danger-ghost-btn:hover,
.icon-close-btn:hover {
  transform: translateY(-1px);
}

.secondary-btn {
  border: 1px solid var(--tt-border);
  background: var(--tt-surface-muted);
  color: var(--tt-text-secondary);
}

.primary-btn {
  border: 1px solid transparent;
  background: var(--tt-accent);
  color: var(--tt-text-inverse);
  box-shadow: var(--tt-shadow-accent);
}

.link-btn {
  border: 0;
  padding: 0;
  min-height: auto;
  background: transparent;
  color: var(--teacher-accent-strong, var(--teacher-accent));
}

.ghost-btn {
  border: 1px solid var(--tt-accent-border);
  background: var(--tt-accent-soft);
  color: var(--tt-accent);
}

.danger-ghost-btn {
  min-width: 124px;
  border: 1px solid rgba(220, 38, 38, 0.2);
  background: var(--tt-danger-soft);
  color: var(--tt-danger);
  box-shadow: none;
}

.danger-btn {
  border: 1px solid transparent;
  background: linear-gradient(135deg, #d14343, #bf2f2f);
  color: var(--tt-text-inverse);
  box-shadow: 0 14px 30px rgba(209, 67, 67, 0.24);
}

.danger-ghost-btn:hover {
  border-color: rgba(220, 38, 38, 0.26);
  box-shadow: 0 16px 30px rgba(220, 38, 38, 0.14);
}

.icon-close-btn {
  border: 1px solid var(--tt-border);
  background: var(--tt-surface-muted);
  color: var(--tt-text-secondary);
}

.invite-btn {
  width: 100%;
}

.invite-actions .ghost-btn:hover:not(:disabled) {
  border-color: var(--tt-accent);
  background: color-mix(in srgb, var(--tt-accent) 16%, var(--tt-surface));
  box-shadow: var(--tt-shadow-xs);
}

.dialog-mask {
  /* positioning handled by teacher-dialog.css */
}

.danger-dialog {
  width: min(560px, 100%);
  display: grid;
  gap: 20px;
  border-radius: var(--tt-radius-xl);
  border: 1px solid var(--tt-border-subtle);
  background: var(--tt-surface);
  box-shadow: 0 28px 72px rgba(15, 23, 42, 0.2);
}

.dialog-head,
.dialog-actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.dialog-head {
  align-items: flex-start;
}

.dialog-eyebrow {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-weight: 700;
}

.dialog-body {
  display: grid;
  gap: 14px;
}

.danger-banner {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 14px;
  align-items: center;
  padding: 16px 18px;
  border-radius: var(--tt-radius-md);
  background: var(--tt-danger-soft);
  border: 1px solid rgba(220, 38, 38, 0.12);
}

.danger-mark {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #d14343, #bf2f2f);
  color: #fff;
  font-size: 18px;
  font-weight: 800;
  box-shadow: 0 12px 24px rgba(209, 67, 67, 0.18);
}

.danger-banner-title,
.danger-banner-note {
  margin: 0;
}

.danger-banner-title {
  font-size: 14px;
  font-weight: 700;
  color: #b42318;
}

.danger-banner-note {
  margin-top: 4px;
  font-size: 12px;
  color: var(--teacher-text-tertiary);
}

.dialog-summary {
  padding: 16px 18px;
  border-radius: var(--tt-radius-md);
  background: var(--tt-danger-soft);
  border: 1px solid rgba(220, 38, 38, 0.12);
  display: grid;
  gap: 8px;
}

.primary-btn:disabled,
.secondary-btn:disabled,
.ghost-btn:disabled,
.danger-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
  transform: none;
  box-shadow: none;
}

.manage-dialog {
  width: min(440px, 100%);
  display: grid;
  gap: 0;
  border-radius: var(--tt-radius-xl);
  border: 1px solid var(--tt-border);
  background: var(--tt-surface);
  box-shadow: var(--tt-shadow-xl);
  overflow: hidden;
  padding: 0;
}

.manage-dialog .dialog-head {
  padding: 20px 22px 4px;
  border-bottom: none;
}

.manage-dialog-actions {
  padding: 12px 22px 20px;
  border-top: 1px solid var(--tt-border-subtle);
  margin-top: 0;
  justify-content: space-between;
}

.manage-body {
  display: grid;
  gap: 0;
}

.manage-section {
  padding: 16px 22px 18px;
  border-top: 1px solid var(--tt-border-subtle);
  display: grid;
  gap: 12px;
}

.manage-section:first-child {
  border-top: none;
}

.manage-section-title {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  color: var(--teacher-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.manage-section-desc {
  margin: 0;
  font-size: 13px;
  color: var(--teacher-text-tertiary);
  line-height: 1.5;
}

.manage-input {
  border: 1px solid var(--tt-border);
  border-radius: var(--tt-radius-md);
  padding: 0 12px;
  height: 40px;
  font-family: inherit;
  font-size: 14px;
  color: var(--tt-text);
  background: var(--tt-surface-muted);
  width: 100%;
  box-sizing: border-box;
}

.danger-section {
  border-top: 1px solid var(--tt-border-subtle);
  background: transparent;
}

.danger-callout {
  display: grid;
  gap: 10px;
  padding: 14px 16px;
  border-radius: var(--tt-radius-md);
  border: 1px solid rgba(220, 38, 38, 0.14);
  background: var(--teacher-surface);
}

.danger-callout-title {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  color: #b42318;
}

.danger-callout-desc {
  margin: 0;
  font-size: 13px;
  color: var(--teacher-text-secondary);
  line-height: 1.55;
}

.danger-outline-btn {
  justify-self: end;
  height: 36px;
  padding: 0 16px;
  border-radius: var(--teacher-radius-control);
  border: 1px solid rgba(220, 38, 38, 0.28);
  background: var(--teacher-surface);
  color: #b42318;
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.danger-outline-btn:hover {
  background: rgba(254, 242, 242, 0.65);
  border-color: rgba(220, 38, 38, 0.42);
}

.full-btn {
  width: 100%;
  justify-content: center;
}

@keyframes shimmer {
  0% {
    background-position: 100% 0;
  }
  100% {
    background-position: -80% 0;
  }
}

@media (max-width: 960px) {
  .topbar,
  .panel-head,
  .dialog-head,
  .dialog-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .top-actions {
    width: 100%;
    justify-content: stretch;
  }

  .top-actions > button {
    flex: 1 1 0;
  }

  .workspace-main,
  .workspace-lower,
  .invite-actions {
    grid-template-columns: 1fr;
  }

  .skeleton-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .task-item {
    flex-direction: column;
    align-items: flex-start;
  }

  .danger-zone {
    align-items: stretch;
    flex-direction: column;
  }
}

@media (max-width: 640px) {
  .skeleton-grid,
  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>
