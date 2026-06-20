<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchStudentAllTasks, fetchStudentClasses, prefetchStudentTaskWorkspace } from '../services/student'
import { resolvePositiveId } from '../utils/routeIds'
import { readSessionCache } from '../utils/sessionCache'
import { loadWithStaleSessionCache } from '../utils/staleSessionLoad'
import {
  buildStudentTaskDetailLocation,
  getStudentTaskCenterBackLabel,
  resolveStudentTaskCenterBackLocation,
} from '../utils/studentTaskNavigation'
import { useStudentLocale } from '../composables/useStudentLocale'
import EmptyState from '../components/common/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const { tm, locale } = useStudentLocale()

const loading = ref(true)
const message = ref('')
const messageType = ref('info')
const loadError = ref('')
const tasks = ref([])
const classes = ref([])
const activeTab = ref('all')
const searchKeyword = ref('')
const selectedClassId = ref(resolveClassFilter(route.query))

const baseTabs = computed(() => [
  { key: 'all', label: tm('tasks.tabAll') },
  { key: '1', label: tm('tasks.statusActive') },
  { key: '2', label: tm('tasks.statusClosed') },
])

const classOptions = computed(() => {
  const map = new Map()

  classes.value.forEach((item) => {
    const classId = String(item?.classId ?? item?.id ?? '')
    if (!classId || map.has(classId)) return
    map.set(classId, {
      value: classId,
      label: item?.name || item?.className || tm('tasks.unnamedClass', { id: classId }),
    })
  })

  tasks.value.forEach((item) => {
    const classId = String(item?.classId ?? '')
    if (!classId || map.has(classId)) return
    map.set(classId, {
      value: classId,
      label: item?.className || tm('tasks.unnamedClass', { id: classId }),
    })
  })

  return Array.from(map.values())
})

const focusTaskId = computed(() => String(route.query.focusTaskId || ''))
const backLocation = computed(() => resolveStudentTaskCenterBackLocation(route.query))
const backLabel = computed(() => getStudentTaskCenterBackLabel(route.query))

const tabTaskPool = computed(() => {
  if (selectedClassId.value === 'all') {
    return tasks.value
  }
  return tasks.value.filter((task) => String(task.classId) === selectedClassId.value)
})

const tabs = computed(() => {
  return baseTabs.value.map((tab) => {
    const count =
      tab.key === 'all'
        ? tabTaskPool.value.length
        : tabTaskPool.value.filter((task) => String(task.status) === tab.key).length
    return { ...tab, count }
  })
})

const filteredTasks = computed(() => {
  let list = [...tasks.value]

  if (selectedClassId.value !== 'all') {
    list = list.filter((task) => String(task.classId) === selectedClassId.value)
  }

  if (activeTab.value !== 'all') {
    list = list.filter((task) => String(task.status) === activeTab.value)
  }

  if (searchKeyword.value.trim()) {
    const keyword = searchKeyword.value.trim().toLowerCase()
    list = list.filter(
      (task) =>
        (task.name || '').toLowerCase().includes(keyword) ||
        (task.className || '').toLowerCase().includes(keyword) ||
        String(task.taskId || '').toLowerCase().includes(keyword),
    )
  }

  return list.sort(compareTaskPriority)
})

const hasFilters = computed(() => {
  return selectedClassId.value !== 'all' || activeTab.value !== 'all' || Boolean(searchKeyword.value.trim())
})

const emptyState = computed(() => {
  if (tasks.value.length === 0) {
    return {
      title: tm('tasks.emptyNoTasksTitle'),
      description: tm('tasks.emptyDesc'),
      action: tm('tasks.emptyNoTasksAction'),
    }
  }

  return {
    title: tm('tasks.emptyNoMatchTitle'),
    description: tm('tasks.empty'),
    action: tm('tasks.emptyNoMatchAction'),
  }
})

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

function normalizeTask(item, classNameMap) {
  const classId = item?.classId ?? null
  return {
    ...item,
    classId,
    className: item?.className || classNameMap[String(classId)] || '',
  }
}

function resolveClassFilter(query = {}) {
  const routeClassFilter = String(query.classFilterId || '').trim()
  if (routeClassFilter) return routeClassFilter

  const sourceClassId = String(query.fromClassId || '').trim()
  if (sourceClassId) return sourceClassId

  return 'all'
}

function taskStatusLabel(value) {
  const map = {
    0: tm('tasks.statusNotStarted'),
    1: tm('tasks.statusActive'),
    2: tm('tasks.statusClosed'),
  }
  return map[Number(value)] || tm('tasks.statusUnknown')
}

function resolveDeadlineTime(deadline) {
  if (!deadline) return Number.POSITIVE_INFINITY
  const time = new Date(deadline).getTime()
  return Number.isNaN(time) ? Number.POSITIVE_INFINITY : time
}

function deadlineClass(deadline) {
  const diff = resolveDeadlineTime(deadline) - Date.now()
  if (!Number.isFinite(diff)) return ''
  if (diff < 0) return 'overdue'
  if (diff < 86400000) return 'urgent'
  return ''
}

function formatDeadlineDate(deadline) {
  const time = resolveDeadlineTime(deadline)
  if (!Number.isFinite(time)) return tm('tasks.noDeadline')
  const dateLocale = locale.value === 'en-US' ? 'en-US' : 'zh-CN'
  return new Intl.DateTimeFormat(dateLocale, {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(time))
}

function taskStatusClass(task) {
  const status = Number(task?.status)
  if (status === 1) return deadlineClass(task.deadline) || 'active'
  if (status === 2) return 'closed'
  return 'pending'
}

function compareTaskPriority(left, right) {
  const leftFocused = String(left.taskId) === focusTaskId.value ? 1 : 0
  const rightFocused = String(right.taskId) === focusTaskId.value ? 1 : 0
  if (leftFocused !== rightFocused) return rightFocused - leftFocused

  const leftStatus = Number(left.status) === 1 ? 1 : 0
  const rightStatus = Number(right.status) === 1 ? 1 : 0
  if (leftStatus !== rightStatus) return rightStatus - leftStatus

  return resolveDeadlineTime(left.deadline) - resolveDeadlineTime(right.deadline)
}

function prefetchTaskDetail(task) {
  const cid = resolvePositiveId(task?.classId)
  const tid = resolvePositiveId(task?.taskId)
  if (!cid || !tid) return
  prefetchStudentTaskWorkspace(cid, tid)
}

function openTaskDetail(task) {
  if (!task?.taskId || !task?.classId) return
  const preservedClassFilter =
    selectedClassId.value !== 'all'
      ? selectedClassId.value
      : String(route.query.classFilterId || '').trim()

  router.push(
    buildStudentTaskDetailLocation(task.classId, task.taskId, {
      from: 'task-center',
      focusTaskId: task.taskId,
      classFilterId: preservedClassFilter || undefined,
      parentFrom: route.query.from,
      parentFromClassId: route.query.fromClassId,
      parentFromTab: route.query.fromTab,
    }),
  )
}

function clearFilters() {
  selectedClassId.value = 'all'
  activeTab.value = 'all'
  searchKeyword.value = ''
}

function emptyAction() {
  if (tasks.value.length === 0) {
    router.push({ path: '/student', query: { action: 'join' } })
    return
  }
  clearFilters()
}

const TASKS_CACHE_KEY = 'student:tasks-center'

function applyTasksCenterPayload(payload) {
  const classPayload = Array.isArray(payload?.classes) ? payload.classes : []
  const taskPayload = Array.isArray(payload?.tasks) ? payload.tasks : []
  classes.value = classPayload
  const classNameMap = classPayload.reduce((acc, item) => {
    const classId = String(item?.classId ?? item?.id ?? '')
    if (!classId) return acc
    acc[classId] = item?.name || item?.className || tm('tasks.unnamedClass', { id: classId })
    return acc
  }, {})
  tasks.value = taskPayload.map((item) => normalizeTask(item, classNameMap))
}

async function loadTasks(options = {}) {
  loadError.value = ''
  setMessage('', 'info')
  const hadCacheBefore = Boolean(readSessionCache(TASKS_CACHE_KEY, 180000))
  loading.value = !hadCacheBefore && !options.silent

  try {
    const { hadCache, error } = await loadWithStaleSessionCache({
      cacheKey: TASKS_CACHE_KEY,
      ttlMs: 180000,
      force: options.force === true,
      apply: applyTasksCenterPayload,
      fetchFresh: async () => {
        const [taskRes, classRes] = await Promise.allSettled([
          fetchStudentAllTasks(),
          fetchStudentClasses(),
        ])
        const classPayload =
          classRes.status === 'fulfilled' && Array.isArray(classRes.value?.data?.data)
            ? classRes.value.data.data
            : []
        if (taskRes.status === 'rejected') {
          throw taskRes.reason
        }
        const taskPayload = Array.isArray(taskRes.value?.data?.data) ? taskRes.value.data.data : []
        return { classes: classPayload, tasks: taskPayload }
      },
    })
    if (error && !tasks.value.length) {
      loadError.value = error?.message || tm('tasks.loadErrorFallback')
    }
    if (!hadCache && !tasks.value.length && !loadError.value) {
      loadError.value = tm('tasks.loadErrorFallback')
    }
  } catch (error) {
    tasks.value = []
    classes.value = []
    loadError.value = error.message || tm('tasks.loadCenterFailed')
  } finally {
    loading.value = false
  }
}

watch(
  () => [route.query.classFilterId, route.query.fromClassId],
  () => {
    selectedClassId.value = resolveClassFilter(route.query)
  },
)

watch(
  () => route.fullPath,
  () => {
    if (!route.query.focusTaskId) return
    const focusedExists = tasks.value.some((task) => String(task.taskId) === String(route.query.focusTaskId))
    if (!focusedExists) return
    if (activeTab.value !== 'all') {
      activeTab.value = 'all'
    }
  },
)

watch(selectedClassId, (value) => {
  const nextQuery = { ...route.query }

  if (value !== 'all') {
    nextQuery.classFilterId = value
  } else if (route.query.from || route.query.fromClassId) {
    nextQuery.classFilterId = 'all'
  } else {
    delete nextQuery.classFilterId
  }

  const currentFilter = String(route.query.classFilterId || '')
  const nextFilter = String(nextQuery.classFilterId || '')

  if (currentFilter === nextFilter) return

  router.replace({
    path: route.path,
    query: nextQuery,
  })
})

onMounted(loadTasks)
</script>

<template>
  <div class="student-page student-tasks">
    <header class="card task-hero">
      <div class="hero-copy">
        <h1>{{ tm('shell.tasks') }}</h1>
      </div>
      <div v-if="backLocation" class="hero-actions">
        <button class="secondary-btn" type="button" @click="router.push(backLocation)">
          {{ backLabel }}
        </button>
      </div>
    </header>

    <p v-if="message" class="message" :class="messageType">{{ message }}</p>

    <section v-if="loadError" class="card error-state">
      <div>
        <p class="eyebrow">{{ tm('tasks.loadErrorEyebrow') }}</p>
        <h2>{{ tm('tasks.loadErrorTitle') }}</h2>
        <p>{{ loadError }}</p>
      </div>
    </section>

    <template v-else>
      <section class="card task-workspace">
        <div class="filter-bar">
          <select v-model="selectedClassId" class="filter-select" :aria-label="tm('tasks.filterClass')">
            <option value="all">{{ tm('tasks.allClasses') }}</option>
            <option v-for="item in classOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </option>
          </select>

          <div class="tabs">
            <button
              v-for="tab in tabs"
              :key="tab.key"
              class="tab-btn"
              :class="{ active: activeTab === tab.key }"
              type="button"
              @click="activeTab = tab.key"
            >
              {{ tab.label }} {{ tab.count }}
            </button>
          </div>

          <input
            v-model.trim="searchKeyword"
            type="search"
            :placeholder="tm('tasks.searchWide')"
            class="search-input"
          />

          <button v-if="hasFilters" class="secondary-btn compact" type="button" @click="clearFilters">
            {{ tm('tasks.clearFilters') }}
          </button>
        </div>

        <div v-if="loading" class="loading-state">
          <div v-for="i in 5" :key="i" class="skeleton-row" />
        </div>

        <EmptyState
          v-else-if="filteredTasks.length === 0"
          variant="panel"
          icon="task"
          :title="emptyState.title"
          :description="emptyState.description"
          :action-label="emptyState.action"
          @action="emptyAction"
        />

        <div v-else class="task-list">
          <article
            v-for="task in filteredTasks"
            :key="task.taskId"
            class="task-card"
            @mouseenter="prefetchTaskDetail(task)"
          >
            <div class="task-card-body">
              <span class="class-pill">{{ task.className || tm('tasks.classFallback') }}</span>
              <h3 class="task-card-title">{{ task.name }}</h3>
              <dl class="task-card-meta">
                <div class="task-meta-row">
                  <dt class="task-meta-label">{{ tm('tasks.deadline') }}</dt>
                  <dd class="task-meta-value">{{ formatDeadlineDate(task.deadline) }}</dd>
                </div>
              </dl>
            </div>
            <div class="task-card-foot">
              <span class="task-card-btn task-card-btn--status" :class="taskStatusClass(task)">{{ taskStatusLabel(task.status) }}</span>
              <button
                class="task-card-btn task-card-btn--primary"
                type="button"
                @mousedown="prefetchTaskDetail(task)"
                @click="openTaskDetail(task)"
              >
                {{ tm('tasks.enterDetail') }}
              </button>
            </div>
          </article>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.student-tasks {
  display: grid;
  gap: 18px;
}

.card {
  background: var(--tt-surface);
  border: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border-subtle));
  border-radius: 24px;
  box-shadow: 0 10px 28px color-mix(in srgb, var(--tt-accent) 5%, transparent);
}

.task-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  background:
    radial-gradient(circle at 100% 0%, color-mix(in srgb, var(--tt-accent) 12%, transparent), transparent 40%),
    linear-gradient(165deg, var(--tt-surface), color-mix(in srgb, var(--tt-accent) 5%, var(--tt-surface-muted)));
}

.hero-copy h1 {
  margin: 0;
  font-size: clamp(22px, 2vw, 26px);
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--student-text-primary);
}

.hero-actions {
  flex-shrink: 0;
}

.message {
  margin: 0;
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--student-text-secondary);
  background: color-mix(in srgb, var(--tt-accent) 6%, var(--tt-surface));
}

.message.error {
  color: var(--student-danger);
}

.task-workspace {
  padding: 20px 24px;
  display: grid;
  gap: 18px;
}

.filter-bar {
  display: grid;
  grid-template-columns: minmax(160px, 200px) 1fr minmax(180px, 240px) auto;
  gap: 12px;
  align-items: center;
  padding: 12px 14px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--tt-accent) 5%, var(--tt-surface-muted));
  border: 1px solid color-mix(in srgb, var(--tt-accent) 12%, var(--tt-border-subtle));
}

.filter-select,
.search-input {
  height: 40px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 12%, var(--tt-border-subtle));
  border-radius: 12px;
  background: var(--tt-surface);
  color: var(--student-text-primary);
  font: inherit;
  font-size: 14px;
}

.filter-select {
  width: 100%;
  padding: 0 12px;
}

.search-input {
  width: 100%;
  padding: 0 12px;
}

.tabs {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: center;
}

.tab-btn,
.primary-btn,
.secondary-btn {
  min-height: 40px;
  border-radius: 12px;
  padding: 0 14px;
  border: none;
  cursor: pointer;
  font-weight: 700;
  font-family: inherit;
  font-size: 13px;
}

.tab-btn {
  min-height: 36px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border-subtle));
  background: var(--tt-surface);
  color: var(--student-text-secondary);
}

.tab-btn.active {
  background: var(--tt-surface);
  border-color: color-mix(in srgb, var(--tt-accent) 28%, var(--tt-border));
  color: var(--student-accent);
  box-shadow: 0 4px 12px color-mix(in srgb, var(--tt-accent) 12%, transparent);
}

.primary-btn {
  background: linear-gradient(135deg, #007aff, #0056d6);
  color: #fff;
  box-shadow: 0 6px 16px color-mix(in srgb, var(--tt-accent) 28%, transparent);
}

.secondary-btn {
  background: var(--tt-surface);
  color: var(--student-text-primary);
  border: 1px solid color-mix(in srgb, var(--tt-accent) 12%, var(--tt-border-subtle));
}

.secondary-btn.compact {
  min-height: 40px;
  white-space: nowrap;
}

.task-list,
.loading-state {
  display: grid;
  gap: 14px;
}

.task-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 28px;
  align-items: center;
  padding: 18px 24px;
  border-radius: 18px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border-subtle));
  background:
    radial-gradient(circle at 100% 0%, color-mix(in srgb, var(--tt-accent) 6%, transparent), transparent 38%),
    var(--tt-surface);
  box-shadow: var(--tt-shadow-xs);
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease,
    transform 160ms ease;
}

.task-card:hover {
  transform: translateY(-2px);
  border-color: color-mix(in srgb, var(--tt-accent) 22%, var(--tt-border));
  box-shadow: 0 12px 28px color-mix(in srgb, var(--tt-accent) 10%, transparent);
}

.task-card-body {
  min-width: 0;
  display: grid;
  gap: 10px;
  align-content: start;
  justify-items: start;
  width: 100%;
  text-align: left;
  padding-right: 8px;
}

.task-card-title {
  margin: 0;
  width: 100%;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: -0.02em;
  color: var(--student-text-primary);
}

.task-card-meta {
  margin: 0;
  width: 100%;
  display: grid;
  gap: 6px;
}

.task-meta-row {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  align-items: baseline;
  gap: 10px;
  width: 100%;
  min-width: 0;
}

.task-meta-label {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--student-text-secondary);
}

.task-meta-value {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: color-mix(in srgb, var(--tt-accent) 72%, var(--tt-text));
  word-break: break-word;
}

.task-card-foot {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  justify-content: center;
  gap: 8px;
  flex-shrink: 0;
  min-width: 104px;
}

.task-card-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 38px;
  min-width: 104px;
  padding: 0 16px;
  border-radius: 12px;
  border: 1px solid transparent;
  font-size: 13px;
  font-weight: 700;
  line-height: 1;
  font-family: inherit;
  box-sizing: border-box;
  white-space: nowrap;
}

.task-card-btn--status.pending {
  background: color-mix(in srgb, var(--tt-text-tertiary) 10%, #fff);
  color: var(--student-text-secondary);
  border-color: color-mix(in srgb, var(--tt-text-tertiary) 18%, transparent);
}

.task-card-btn--status.active {
  background: color-mix(in srgb, var(--tt-accent) 10%, #fff);
  color: var(--student-accent);
  border-color: color-mix(in srgb, var(--tt-accent) 24%, transparent);
}

.task-card-btn--status.urgent {
  background: color-mix(in srgb, #f59e0b 10%, #fff);
  color: #b45309;
  border-color: color-mix(in srgb, #f59e0b 22%, transparent);
}

.task-card-btn--status.overdue,
.task-card-btn--status.closed {
  background: color-mix(in srgb, #ef4444 8%, #fff);
  color: #dc2626;
  border-color: color-mix(in srgb, #ef4444 20%, transparent);
}

.task-card-btn--primary {
  border: none;
  color: #fff;
  cursor: pointer;
  background: linear-gradient(135deg, #007aff, #0056d6);
  box-shadow: 0 4px 14px color-mix(in srgb, var(--tt-accent) 28%, transparent);
  transition:
    transform 160ms ease,
    box-shadow 160ms ease;
}

.task-card-btn--primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 18px color-mix(in srgb, var(--tt-accent) 34%, transparent);
}

.class-pill {
  display: inline-flex;
  align-items: center;
  align-self: start;
  max-width: 100%;
  min-height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.25;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: var(--tt-accent-soft);
  color: var(--tt-accent);
  border: 1px solid var(--tt-accent-border);
}

.empty-state {
  min-height: 240px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  text-align: center;
  border: 1px dashed color-mix(in srgb, var(--tt-accent) 16%, var(--tt-border-subtle));
  border-radius: 18px;
  padding: 28px;
  background: color-mix(in srgb, var(--tt-accent) 4%, var(--tt-surface-muted));
}

.empty-state h3 {
  margin: 0;
  font-size: 18px;
  color: var(--student-text-primary);
}

.empty-state p {
  max-width: 420px;
  margin: 0;
  color: var(--student-text-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.error-state {
  min-height: 260px;
  padding: 28px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  text-align: center;
  border-color: color-mix(in srgb, #ef4444 22%, var(--tt-border-subtle));
}

.error-state h2 {
  margin: 0;
  font-size: 20px;
}

.error-state p {
  max-width: 460px;
  margin: 0;
  color: var(--student-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.skeleton-row {
  height: 96px;
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

@media (max-width: 900px) {
  .filter-bar {
    grid-template-columns: 1fr 1fr;
  }

  .tabs {
    grid-column: 1 / -1;
    justify-content: flex-start;
  }

  .search-input {
    grid-column: 1 / -1;
  }

  .secondary-btn.compact {
    grid-column: 1 / -1;
    width: 100%;
  }

  .task-card {
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .task-card-foot {
    flex-direction: row;
    justify-content: space-between;
    align-items: center;
    width: 100%;
  }
}

@media (max-width: 760px) {
  .task-hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .filter-bar {
    grid-template-columns: 1fr;
  }
}
</style>
