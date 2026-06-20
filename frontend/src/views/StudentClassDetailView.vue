<script setup>
import { computed, onActivated, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  fetchStudentClassDetail,
  fetchStudentClassGroups,
  fetchStudentClassTasks,
  fetchStudentClassmates,
} from '../services/student'
import { getCurrentUserId } from '../utils/auth'
import { getStudentTaskKindLabel } from '../utils/studentTaskNavigation'
import TaskStatusBadge from '../components/common/TaskStatusBadge.vue'
import MemberDetailPopup from '../components/student/MemberDetailPopup.vue'
import { readSessionCache } from '../utils/sessionCache'
import { loadWithStaleSessionCache } from '../utils/staleSessionLoad'

const route = useRoute()
const router = useRouter()
const classId = route.params.classId

const loading = ref(true)
const detailLoadError = ref('')
const taskLoadError = ref('')
const detail = ref(null)
const tasks = ref([])
const groups = ref([])
const classmates = ref([])
const selectedMember = ref(null)
const message = ref('')
const messageType = ref('info')

const TAB_KEYS = ['info', 'tasks']
const activeTab = ref(resolveRouteTab(route.query.tab))

const tabs = [
  { key: 'info', label: '班级成员' },
  { key: 'tasks', label: '班级任务' },
]

const currentUserId = computed(() => getCurrentUserId())

const myGroup = computed(() => {
  if (!detail.value?.groupId) return null
  return groups.value.find((group) => String(group.groupId) === String(detail.value.groupId)) || null
})

const taskCount = computed(() => tasks.value.length)
const groupCount = computed(() => groups.value.length)
const myGroupLabel = computed(() => myGroup.value?.name || detail.value?.groupName || '未分组')

const classStudentCount = computed(() => {
  const fromDetail = Number(detail.value?.studentCount)
  if (Number.isFinite(fromDetail) && fromDetail >= 0) {
    return fromDetail
  }
  return classmates.value.length
})

const classStudents = computed(() =>
  classmates.value.map((item) => ({
    id: item.studentId,
    name: item.name || `学生 ${item.studentId}`,
    groupName: item.groupName || '',
    isLeader: Boolean(item.isLeader),
  })),
)

const canCreateOrJoin = computed(() => {
  if (!detail.value) return false
  if (detail.value.groupingLocked) return false
  if (detail.value.groupId) return false
  return true
})

function resolveRouteTab(rawTab) {
  const nextTab = String(rawTab || '').trim().toLowerCase()
  return TAB_KEYS.includes(nextTab) ? nextTab : 'info'
}

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

function formatDateTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN')
}

function groupEntryLabel() {
  if (myGroup.value) return '进入我的小组'
  if (canCreateOrJoin.value) return '创建或加入小组'
  return '查看班级小组'
}

function classHubCacheKey() {
  return `student:class:${classId}:hub`
}

function applyClassHubPayload(payload) {
  detail.value = payload?.detail || null
  tasks.value = Array.isArray(payload?.tasks) ? payload.tasks : []
  groups.value = Array.isArray(payload?.groups) ? payload.groups : []
  classmates.value = Array.isArray(payload?.classmates) ? payload.classmates : []
}

async function loadData(options = {}) {
  detailLoadError.value = ''
  taskLoadError.value = ''
  setMessage('', 'info')
  const hadCacheBefore = Boolean(readSessionCache(classHubCacheKey(), 180000))
  loading.value = !hadCacheBefore

  try {
    const { error } = await loadWithStaleSessionCache({
      cacheKey: classHubCacheKey(),
      ttlMs: 180000,
      force: options.force === true,
      apply: applyClassHubPayload,
      fetchFresh: async () => {
        const [detailRes, taskRes, groupRes, classmatesRes] = await Promise.allSettled([
          fetchStudentClassDetail(classId),
          fetchStudentClassTasks(classId),
          fetchStudentClassGroups(classId),
          fetchStudentClassmates(classId),
        ])
        if (detailRes.status === 'rejected') {
          throw detailRes.reason
        }
        const nextDetail = detailRes.value?.data?.data || null
        if (!nextDetail) {
          throw new Error('未找到该班级，或你暂时没有访问权限。')
        }
        const nextTasks =
          taskRes.status === 'fulfilled' && Array.isArray(taskRes.value?.data?.data)
            ? taskRes.value.data.data
            : []
        const nextGroups =
          groupRes.status === 'fulfilled' && Array.isArray(groupRes.value?.data?.data)
            ? groupRes.value.data.data
            : []
        const nextClassmates =
          classmatesRes.status === 'fulfilled' && Array.isArray(classmatesRes.value?.data?.data)
            ? classmatesRes.value.data.data
            : []
        if (taskRes.status === 'rejected') {
          taskLoadError.value = taskRes.reason?.message || '班级任务暂时不可用，请稍后重试。'
        }
        return { detail: nextDetail, tasks: nextTasks, groups: nextGroups, classmates: nextClassmates }
      },
    })
    if (error && !detail.value) {
      detailLoadError.value = error?.message || '班级信息加载失败，请稍后重试。'
    }
  } catch (error) {
    if (!detail.value) {
      detail.value = null
      tasks.value = []
      groups.value = []
      classmates.value = []
      taskLoadError.value = ''
      detailLoadError.value = error?.message || '班级信息加载失败，请稍后重试。'
    }
  } finally {
    loading.value = false
  }
}

function changeTab(tabKey) {
  activeTab.value = tabKey
  router.replace({
    path: route.path,
    query: tabKey === 'info' ? {} : { tab: tabKey },
  })
}

function goGroupCenter(action = '') {
  const target = { path: `/student/classes/${classId}/groups` }
  if (action) {
    target.query = { action }
  }
  router.push(target)
}

function openTaskDetail(task) {
  const taskId = task?.taskId ?? task?.id
  if (!taskId) return
  router.push(`/student/classes/${classId}/tasks/${taskId}`)
}

function taskTypeLabel(task) {
  return getStudentTaskKindLabel(task, 'group')
}

function taskStatusLabel(task) {
  const status = Number(task?.status)
  if (status === 1) return '进行中'
  if (status === 2) return '已截止'
  return '未开始'
}

watch(
  () => route.query.tab,
  (tab) => {
    activeTab.value = resolveRouteTab(tab)
  },
)

let hubMounted = false
onMounted(async () => {
  await loadData()
  hubMounted = true
})
onActivated(() => {
  if (hubMounted) {
    void loadData({ force: true })
  }
})
</script>

<template>
  <div class="student-page class-detail">
    <div v-if="loading" class="loading-state">
      <div v-for="i in 3" :key="i" class="skeleton-card" />
    </div>

    <section v-else-if="detailLoadError" class="card detail-error-state">
      <p class="eyebrow">班级</p>
      <h2>班级信息加载失败</h2>
      <p>{{ detailLoadError }}</p>
      <div class="error-actions">
        <button class="secondary-btn" type="button" @click="router.push('/student')">返回首页</button>
      </div>
    </section>

    <template v-else-if="detail">
      <section class="card workspace-topbar class-hero-card">
        <div class="hero-layout">
          <div class="title-block hero-copy">
            <p class="eyebrow">班级</p>
            <h2>{{ detail.name }}</h2>
          </div>

          <div class="hero-actions">
            <button class="primary-btn" type="button" @click="goGroupCenter()">
              {{ groupEntryLabel() }}
            </button>
          </div>
        </div>

        <div class="hero-stats">
          <article class="stat-tile stat-tile--students">
            <span class="stat-label">班级学生</span>
            <strong class="stat-value">{{ classStudentCount }}</strong>
          </article>
          <article class="stat-tile stat-tile--groups">
            <span class="stat-label">小组数量</span>
            <strong class="stat-value">{{ groupCount }}</strong>
          </article>
          <article class="stat-tile stat-tile--tasks">
            <span class="stat-label">班级任务</span>
            <strong class="stat-value">{{ taskCount }}</strong>
          </article>
          <article class="stat-tile stat-tile--mygroup">
            <span class="stat-label">我的小组</span>
            <strong class="stat-value compact" :title="myGroupLabel">{{ myGroupLabel }}</strong>
          </article>
        </div>
      </section>

      <div class="tab-bar">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="tab-btn"
          :class="{ active: activeTab === tab.key }"
          type="button"
          @click="changeTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </div>

      <p v-if="message" class="message" :class="messageType">{{ message }}</p>

      <section v-if="activeTab === 'info'" class="card detail-panel members-panel">
        <div class="member-section">
          <div class="student-section-bar">
            <p class="member-section-label">任课教师</p>
          </div>
          <ul class="member-list">
            <li class="member-row member-row--teacher">
              <div class="member-avatar teacher-avatar">{{ (detail.teacherName || '师').slice(0, 1) }}</div>
              <div class="member-main">
                <p class="member-name">{{ detail.teacherName || '未知教师' }}</p>
                <p class="member-meta">班级任课教师</p>
              </div>
              <div class="member-aside">
                <span class="member-tag teacher-tag">教师</span>
              </div>
            </li>
          </ul>
        </div>

        <div class="member-section">
          <div class="student-section-bar">
            <p class="member-section-label">班级学生</p>
            <span class="student-section-meta">{{ classStudentCount }} 人</span>
          </div>
          <ul v-if="classStudents.length" class="member-list">
            <li
              v-for="student in classStudents"
              :key="student.id"
              class="member-row member-row--student"
              :class="{ 'is-me': String(student.id) === String(currentUserId) }"
              @click="selectedMember = student"
            >
              <div class="member-avatar">{{ student.name.slice(0, 1) }}</div>
              <div class="member-main">
                <p class="member-name">
                  {{ student.name }}
                </p>
                <p
                  class="member-meta"
                  :class="{ muted: !student.groupName }"
                  :title="student.groupName"
                >
                  {{ student.groupName || '未分组' }}
                </p>
              </div>
              <div class="member-aside">
                <span v-if="student.isLeader" class="member-tag leader-tag">组长</span>
              </div>
            </li>
          </ul>
          <div v-else class="empty-card">暂无学生信息</div>
        </div>

        <MemberDetailPopup :member="selectedMember" @close="selectedMember = null" />
      </section>

      <section v-if="activeTab === 'tasks'" class="card detail-panel tasks-panel">
        <div v-if="taskLoadError" class="empty-card error-entry">
          <p>班级任务加载失败</p>
          <span>{{ taskLoadError }}</span>
        </div>

        <div v-else-if="tasks.length === 0" class="empty-card">暂无任务</div>

        <div v-else class="task-grid">
          <button
            v-for="task in tasks"
            :key="task.taskId || task.id"
            class="task-card"
            type="button"
            @click="openTaskDetail(task)"
          >
            <div class="task-card-body">
              <div class="task-card-main">
                <strong class="task-title">{{ task.name }}</strong>
                <div class="task-tags">
                  <span class="task-kind">{{ taskTypeLabel(task) }}</span>
                  <TaskStatusBadge :status="task.status" :label="taskStatusLabel(task)" size="sm" />
                </div>
                <p v-if="task.description" class="task-note">{{ task.description }}</p>
                <div class="task-deadline">
                  <span class="task-deadline-label">截止时间</span>
                  <strong class="task-deadline-value">{{ formatDateTime(task.deadline) }}</strong>
                </div>
              </div>
              <span class="task-entry-btn">查看详情</span>
            </div>
          </button>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.class-detail {
  gap: 16px;
}

.class-detail,
.detail-panel,
.info-grid,
.task-grid {
  display: grid;
  gap: 18px;
}

.workspace-topbar,
.detail-panel,
.info-grid,
.task-grid,
.topbar-main,
.title-block,
.section-subhead {
  display: grid;
  gap: 18px;
}

.workspace-topbar {
  padding: 26px;
}

.detail-error-state {
  min-height: 260px;
  padding: 30px;
  border-radius: 26px;
  border: 1px solid rgba(255, 59, 48, 0.16);
  background: rgba(255, 255, 255, 0.78);
  box-shadow: var(--student-shadow);
  display: grid;
  place-items: center;
  text-align: center;
  gap: 12px;
}

.detail-error-state h2,
.detail-error-state p {
  margin: 0;
}

.detail-error-state h2 {
  color: var(--student-text-primary);
  font-size: 22px;
}

.detail-error-state p {
  max-width: 440px;
  color: var(--student-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.error-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin-top: 4px;
}

.class-hero-card {
  gap: 22px;
  border-radius: 24px;
  border: 1px solid var(--class-detail-border, rgba(148, 163, 184, 0.22));
  background: var(--class-detail-surface, linear-gradient(165deg, rgba(255, 255, 255, 0.92), rgba(248, 250, 252, 0.86)));
  box-shadow:
    var(--class-detail-shadow, 0 14px 36px rgba(15, 23, 42, 0.06)),
    var(--class-detail-inset, inset 0 1px 0 rgba(255, 255, 255, 0.78));
}

.hero-layout {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
}

.hero-actions {
  flex-shrink: 0;
}

.hero-copy {
  gap: 12px;
  min-width: 0;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.stat-tile {
  position: relative;
  display: grid;
  gap: 8px;
  padding: 16px 18px 16px 22px;
  border-radius: 16px;
  border: 1px solid var(--class-detail-border, rgba(148, 163, 184, 0.22));
  background: var(--class-detail-surface-muted, linear-gradient(165deg, rgba(255, 255, 255, 0.72), rgba(241, 245, 249, 0.78)));
  overflow: hidden;
}

.stat-tile::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  border-radius: 4px 0 0 4px;
}

.stat-tile--students::before {
  background: linear-gradient(180deg, #3b82f6, #60a5fa);
}

.stat-tile--groups::before {
  background: linear-gradient(180deg, #14b8a6, #5eead4);
}

.stat-tile--tasks::before {
  background: linear-gradient(180deg, #f59e0b, #fcd34d);
}

.stat-tile--mygroup::before {
  background: linear-gradient(180deg, #8b5cf6, #a78bfa);
}

.stat-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--student-text-secondary);
}

.stat-value {
  font-size: 28px;
  font-weight: 800;
  line-height: 1.1;
  color: var(--student-text-primary);
}

.stat-value.compact {
  font-size: 15px;
  font-weight: 700;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hero-status-row,
.task-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.hero-badge {
  background: rgba(0, 122, 255, 0.12);
  color: var(--student-accent);
}

.hero-badge.soft {
  background: rgba(15, 23, 42, 0.05);
  color: var(--student-text-secondary);
}

.topbar-main,
.topbar-actions,
.section-head,
.group-actions,
.entry-head,
.progress-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.topbar-main h2,
.section-head h3,
.section-subhead h3,
.group-title,
.metric-value {
  margin: 0;
}

.eyebrow {
  margin: 0;
}

.message {
  margin: 0;
  font-size: 13px;
  color: var(--student-text-tertiary);
}

.message.error {
  color: var(--student-danger);
}

.topbar-note {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--student-text-secondary);
}

.topbar-actions,
.group-actions {
  flex-wrap: wrap;
}

.primary-btn,
.secondary-btn,
.neutral-btn,
.tab-btn {
  min-height: 42px;
  padding: 0 14px;
  border-radius: 12px;
  border: 1px solid transparent;
  font-family: inherit;
  font-weight: 700;
  cursor: pointer;
}

.primary-btn {
  background: var(--student-accent);
  color: #fff;
}

.secondary-btn {
  background: var(--student-surface);
  color: var(--student-text-primary);
  border-color: var(--student-border);
}

.neutral-btn {
  background: var(--student-surface-muted);
  color: var(--student-text-primary);
  border-color: var(--student-border);
}

.tag,
.entry-badge,
.readonly-badge,
.group-count,
.task-status {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  border-radius: 999px;
}

.tag.group {
  background: var(--student-accent-soft);
  color: var(--student-accent);
}

.tag.ungrouped {
  background: var(--student-surface-muted);
  color: var(--student-text-tertiary);
}

.meta-label,
.group-meta,
.metric-label,
.section-note,
.task-note,
.progress-loading {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--student-text-secondary);
}

.progress-loading.error {
  color: var(--student-danger);
}

.tab-bar {
  display: flex;
  gap: 6px;
  width: 100%;
  max-width: 100%;
  padding: 5px;
  border-radius: 16px;
  background: var(--class-detail-surface-muted, linear-gradient(165deg, rgba(255, 255, 255, 0.72), rgba(241, 245, 249, 0.78)));
  border: 1px solid var(--class-detail-border, rgba(148, 163, 184, 0.22));
  box-shadow:
    var(--class-detail-shadow, 0 14px 36px rgba(15, 23, 42, 0.06)),
    var(--class-detail-inset, inset 0 1px 0 rgba(255, 255, 255, 0.78));
}

.tab-btn {
  flex: 1;
  background: transparent;
  color: var(--student-text-secondary);
  border-color: transparent;
  min-height: 42px;
  border-radius: 12px;
  transition:
    background 180ms ease,
    color 180ms ease,
    box-shadow 180ms ease,
    transform 180ms ease;
}

.tab-btn:hover:not(.active) {
  color: var(--student-text-primary);
  background: rgba(255, 255, 255, 0.55);
}

.tab-btn.active {
  background: linear-gradient(135deg, #007aff, #5856d6);
  color: #fff;
  border-color: transparent;
  box-shadow: 0 8px 20px rgba(0, 122, 255, 0.28);
  transform: translateY(-1px);
}

.detail-panel {
  padding: 24px;
  display: grid;
  gap: 22px;
  border-radius: 24px;
}

.panel-head,
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding-bottom: 4px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}

.panel-head h3,
.section-head h3 {
  margin: 0;
  font-size: 20px;
  color: var(--student-text-primary);
}

.count-pill {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.05);
  color: var(--student-text-secondary);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.info-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.info-row {
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(255, 255, 255, 0.7);
  display: grid;
  gap: 8px;
}

.label {
  font-size: 12px;
  color: var(--student-text-tertiary);
}

.value {
  color: var(--student-text-primary);
  font-weight: 700;
}

.value.mono {
  font-family: 'SF Mono', monospace;
}

.status-active {
  color: var(--student-accent);
}

.member-section {
  display: grid;
  gap: 12px;
}

.member-section + .member-section {
  padding-top: 8px;
  border-top: 1px solid var(--tt-border-subtle);
  margin-top: 4px;
}

.member-section-label {
  margin: 0;
}

.member-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 8px;
}

.member-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  border-radius: 14px;
  border: 1px solid var(--tt-border-subtle);
  background: var(--tt-surface-muted);
  transition:
    border-color 160ms ease,
    background 160ms ease,
    transform 160ms ease;
}

.member-row--student {
  cursor: pointer;
}

.member-row--student:hover {
  border-color: color-mix(in srgb, var(--tt-accent) 22%, var(--tt-border-subtle));
  background: color-mix(in srgb, var(--tt-accent) 4%, var(--tt-surface-muted));
}

.member-row.is-me {
  border-color: var(--tt-accent-border);
  background: var(--tt-accent-soft);
}

.member-row--teacher {
  border-color: color-mix(in srgb, #f59e0b 22%, var(--tt-border-subtle));
  background: color-mix(in srgb, #f59e0b 8%, var(--tt-surface-muted));
}

.member-avatar {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: var(--tt-accent-soft);
  color: var(--tt-accent);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 15px;
  flex-shrink: 0;
}

.teacher-avatar {
  background: color-mix(in srgb, #f59e0b 16%, var(--tt-surface-muted));
  color: #d97706;
}

.member-main {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.member-aside {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-shrink: 0;
  min-width: 0;
}

.member-name {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.35;
  color: var(--student-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.me-mark {
  margin-left: 2px;
  font-weight: 600;
  color: var(--student-accent);
}

.member-meta {
  margin: 0;
  font-size: 12px;
  line-height: 1.4;
  color: var(--student-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-meta.muted {
  color: var(--student-text-tertiary);
}

.member-tag {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  border: 1px solid transparent;
}

.teacher-tag {
  background: color-mix(in srgb, #f59e0b 14%, var(--tt-surface-muted));
  color: #d97706;
  border-color: color-mix(in srgb, #f59e0b 24%, var(--tt-border-subtle));
}

.leader-tag {
  background: color-mix(in srgb, #22c55e 12%, var(--tt-surface-muted));
  color: #15803d;
  border-color: color-mix(in srgb, #22c55e 22%, var(--tt-border-subtle));
}

.entry-card,
.group-card {
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(255, 255, 255, 0.78);
  border-radius: 22px;
  box-shadow: var(--student-shadow);
  transition:
    transform 160ms cubic-bezier(0.25, 0.1, 0.25, 1),
    border-color 160ms cubic-bezier(0.25, 0.1, 0.25, 1),
    box-shadow 160ms cubic-bezier(0.25, 0.1, 0.25, 1);
}

.task-card {
  border: 1px solid var(--class-detail-border, rgba(148, 163, 184, 0.22));
  border-radius: 20px;
  background: var(--class-detail-surface-muted, linear-gradient(165deg, rgba(255, 255, 255, 0.72), rgba(241, 245, 249, 0.78)));
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.04);
  transition:
    transform 160ms cubic-bezier(0.25, 0.1, 0.25, 1),
    border-color 160ms cubic-bezier(0.25, 0.1, 0.25, 1),
    box-shadow 160ms cubic-bezier(0.25, 0.1, 0.25, 1);
}

.entry-card,
.task-card {
  cursor: pointer;
}

.entry-card:hover,
.group-card:hover {
  transform: translateY(-4px);
  border-color: rgba(0, 122, 255, 0.16);
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.08);
}

.task-card:hover {
  transform: translateY(-3px);
  border-color: color-mix(in srgb, var(--tt-accent) 28%, var(--tt-border));
  box-shadow:
    0 16px 36px color-mix(in srgb, var(--tt-accent) 14%, transparent),
    inset 0 1px 0 rgba(255, 255, 255, 0.72);
}

.my-group-card,
.group-card,
.empty-entry {
  padding: 20px;
}

.entry-badge {
  background: rgba(0, 122, 255, 0.12);
  color: var(--student-accent);
}

.readonly-badge {
  background: rgba(15, 23, 42, 0.05);
  color: var(--student-text-secondary);
}

.group-title {
  font-size: 22px;
  line-height: 1.2;
  color: var(--student-text-primary);
}

.entry-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.metric-box {
  min-height: 92px;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(255, 255, 255, 0.74);
  display: grid;
  gap: 10px;
  align-content: space-between;
}

.metric-label {
  font-size: 12px;
}

.metric-value {
  font-size: 24px;
  font-weight: 800;
  color: var(--student-text-primary);
}

.metric-value.compact {
  font-size: 18px;
  line-height: 1.35;
}

.progress-block {
  display: grid;
  gap: 8px;
  margin-top: 16px;
}

.progress-head span {
  font-size: 12px;
  color: var(--student-text-secondary);
}

.progress-head strong {
  font-size: 13px;
  color: var(--student-text-primary);
}

.progress-track {
  position: relative;
  height: 8px;
  border-radius: 999px;
  background: var(--tt-border-subtle);
  overflow: hidden;
}

.progress-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #007aff, #34c759);
}

.empty-entry,
.empty-card {
  border-radius: 16px;
  border: 1px dashed color-mix(in srgb, var(--class-detail-border, rgba(148, 163, 184, 0.22)) 80%, transparent);
  color: var(--student-text-tertiary);
  text-align: center;
  background: var(--class-detail-surface-muted, linear-gradient(165deg, rgba(255, 255, 255, 0.72), rgba(241, 245, 249, 0.78)));
}

.empty-entry {
  display: grid;
  gap: 16px;
  background: var(--class-detail-surface-muted, linear-gradient(165deg, rgba(255, 255, 255, 0.72), rgba(241, 245, 249, 0.78)));
}

.empty-entry p {
  margin: 0;
  font-size: 14px;
}

.error-entry {
  display: grid;
  place-items: center;
  gap: 10px;
  border-color: rgba(255, 59, 48, 0.16);
}

.error-entry span {
  max-width: 440px;
  color: var(--student-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.section-subhead { gap: 6px; }

.group-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.group-card {
  padding: 18px;
  display: grid;
  gap: 14px;
}

.group-foot {
  display: flex;
  justify-content: flex-end;
}

.group-count {
  background: rgba(0, 122, 255, 0.1);
  color: var(--student-accent);
}

.task-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.task-card {
  text-align: left;
  padding: 0;
  overflow: hidden;
}

.task-card-body {
  display: flex;
  align-items: center;
  gap: 20px;
  width: 100%;
  padding: 22px 24px;
  min-height: 156px;
}

.task-card-main {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 12px;
  align-content: center;
}

.task-title {
  font-size: clamp(17px, 1.05rem, 19px);
  font-weight: 700;
  line-height: 1.32;
  letter-spacing: -0.02em;
  color: var(--student-text-primary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

.task-tags {
  gap: 8px;
}

.task-tags .task-kind,
.task-tags .task-status {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 11px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
}

.task-note {
  margin: 0;
  font-size: 14px;
  line-height: 1.55;
  color: var(--tt-text-secondary);
}

.task-deadline {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 2px;
  padding: 10px 14px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--tt-accent) 9%, var(--tt-surface-muted));
  border: 1px solid color-mix(in srgb, var(--tt-accent) 18%, var(--tt-border-subtle));
  width: fit-content;
  max-width: 100%;
}

.task-deadline-label {
  font-size: 13px;
  font-weight: 600;
  color: color-mix(in srgb, var(--tt-accent) 52%, var(--tt-text-secondary));
  white-space: nowrap;
}

.task-deadline-value {
  font-size: 15px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: color-mix(in srgb, var(--tt-accent) 78%, var(--tt-text));
  line-height: 1.35;
}

.task-entry-btn {
  flex-shrink: 0;
  align-self: center;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  min-width: 104px;
  padding: 0 18px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.01em;
  color: #fff;
  background: var(--student-accent-gradient, linear-gradient(135deg, #007aff, #0056d6));
  border: 1px solid color-mix(in srgb, #0056d6 40%, transparent);
  box-shadow:
    0 8px 18px color-mix(in srgb, var(--tt-accent) 32%, transparent),
    inset 0 1px 0 rgba(255, 255, 255, 0.22);
  transition:
    transform 160ms cubic-bezier(0.25, 0.1, 0.25, 1),
    box-shadow 160ms cubic-bezier(0.25, 0.1, 0.25, 1);
}

.task-card:hover .task-entry-btn {
  box-shadow:
    0 10px 22px color-mix(in srgb, var(--tt-accent) 38%, transparent),
    inset 0 1px 0 rgba(255, 255, 255, 0.28);
}

.task-tags .task-kind {
  background: color-mix(in srgb, var(--tt-accent) 14%, #fff);
  color: color-mix(in srgb, var(--tt-accent) 82%, #0f172a);
  border: 1px solid color-mix(in srgb, var(--tt-accent) 24%, transparent);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.55);
}

.task-tags .task-status.is-active {
  background: color-mix(in srgb, #22c55e 14%, #fff);
  color: #15803d;
  border: 1px solid color-mix(in srgb, #22c55e 28%, transparent);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.55);
}

.task-tags .task-status.is-pending {
  background: color-mix(in srgb, #f59e0b 12%, #fff);
  color: #b45309;
  border: 1px solid color-mix(in srgb, #f59e0b 24%, transparent);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.55);
}

.task-tags .task-status.is-ended {
  background: color-mix(in srgb, var(--tt-text-tertiary) 12%, #fff);
  color: var(--tt-text-secondary);
  border: 1px solid color-mix(in srgb, var(--tt-text-tertiary) 22%, transparent);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.55);
}

.loading-state {
  display: grid;
  gap: 18px;
}

.skeleton-card {
  height: 120px;
  border-radius: 24px;
  background: var(--student-surface-muted);
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.5;
  }
}

@media (max-width: 960px) {
  .hero-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .entry-metrics,
  .group-grid,
  .task-grid,
  .info-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 760px) {
  .hero-layout,
  .topbar-main,
  .topbar-actions,
  .panel-head,
  .section-head,
  .group-actions,
  .entry-head,
  .progress-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .task-card-body {
    flex-direction: column;
    align-items: stretch;
    min-height: 0;
  }

  .task-entry-btn {
    width: 100%;
  }

  .hero-actions,
  .hero-actions .primary-btn {
    width: 100%;
  }

  .hero-stats,
  .entry-metrics,
  .group-grid,
  .task-grid,
  .info-grid {
    grid-template-columns: 1fr;
  }

  .tab-bar {
    width: 100%;
  }

  .tab-btn {
    flex: 1;
  }
}
</style>
