<script setup>
import '../styles/admin-workspace.css'
import { computed, onMounted, reactive, ref } from 'vue'
import AdminToolbar from '../components/admin/AdminToolbar.vue'
import { fetchAdminMonitorClasses, fetchAdminMonitorOverview, fetchAdminMonitorTasks } from '../services/admin'
import { formatDateTime, formatMonitorStatus } from '../utils/admin'

const loadingOverview = ref(false)
const loadingClasses = ref(false)
const loadingTasks = ref(false)
const message = ref('')
const messageType = ref('info')

const overview = reactive({
  classCount: '-',
  taskCount: '-',
  groupCount: '-',
  activeClassCount: '-',
})

const classFilters = reactive({
  keyword: '',
  status: '',
})

const taskFilters = reactive({
  keyword: '',
  status: '',
})

const classPagination = reactive({
  page: 1,
  size: 5,
  total: 0,
  pages: 0,
  hasNext: false,
})

const taskPagination = reactive({
  page: 1,
  size: 5,
  total: 0,
  pages: 0,
  hasNext: false,
})

const classes = ref([])
const tasks = ref([])
const classesAvailable = ref(true)
const tasksAvailable = ref(true)
const overviewAvailable = ref(true)

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

function normalizePagedPayload(payload) {
  return {
    list: payload?.list || payload?.records || payload?.content || [],
    total: Number(payload?.total) || 0,
    pages: Number(payload?.pages) || 0,
    hasNext: Boolean(payload?.hasNext),
  }
}

const monitorStatus = computed(() => {
  const availableCount = [overviewAvailable.value, classesAvailable.value, tasksAvailable.value].filter(Boolean).length
  if (availableCount === 3) return '监控数据已全部加载。'
  if (availableCount === 0) return '监控数据暂不可用。'
  return '部分监控数据暂不可用。'
})

const overviewCards = computed(() => [
  { label: '班级数', value: overview.classCount },
  { label: '任务数', value: overview.taskCount },
  { label: '小组数', value: overview.groupCount },
  { label: '活跃班级数', value: overview.activeClassCount },
])

async function loadOverview() {
  loadingOverview.value = true
  try {
    const { data } = await fetchAdminMonitorOverview()
    const payload = data?.data || {}
    overview.classCount = payload.classCount ?? payload.totalClasses ?? '-'
    overview.taskCount = payload.taskCount ?? payload.totalTasks ?? '-'
    overview.groupCount = payload.groupCount ?? payload.totalGroups ?? '-'
    overview.activeClassCount = payload.activeClassCount ?? payload.activeClasses ?? '-'
    overviewAvailable.value = true
  } catch {
    overviewAvailable.value = false
    overview.classCount = '-'
    overview.taskCount = '-'
    overview.groupCount = '-'
    overview.activeClassCount = '-'
  } finally {
    loadingOverview.value = false
  }
}

async function loadClasses() {
  loadingClasses.value = true
  try {
    const params = {
      page: classPagination.page,
      size: classPagination.size,
    }
    if (classFilters.keyword.trim()) params.keyword = classFilters.keyword.trim()
    if (classFilters.status) params.status = classFilters.status

    const { data } = await fetchAdminMonitorClasses(params)
    const payload = normalizePagedPayload(data?.data || {})
    classes.value = payload.list
    classPagination.total = payload.total
    classPagination.pages = payload.pages
    classPagination.hasNext = payload.hasNext
    classesAvailable.value = true
  } catch {
    classes.value = []
    classPagination.total = 0
    classPagination.pages = 0
    classPagination.hasNext = false
    classesAvailable.value = false
  } finally {
    loadingClasses.value = false
  }
}

async function loadTasks() {
  loadingTasks.value = true
  try {
    const params = {
      page: taskPagination.page,
      size: taskPagination.size,
    }
    if (taskFilters.keyword.trim()) params.keyword = taskFilters.keyword.trim()
    if (taskFilters.status) params.status = taskFilters.status

    const { data } = await fetchAdminMonitorTasks(params)
    const payload = normalizePagedPayload(data?.data || {})
    tasks.value = payload.list
    taskPagination.total = payload.total
    taskPagination.pages = payload.pages
    taskPagination.hasNext = payload.hasNext
    tasksAvailable.value = true
  } catch {
    tasks.value = []
    taskPagination.total = 0
    taskPagination.pages = 0
    taskPagination.hasNext = false
    tasksAvailable.value = false
  } finally {
    loadingTasks.value = false
  }
}

async function loadMonitor() {
  setMessage('')
  await Promise.all([loadOverview(), loadClasses(), loadTasks()])
  if (!overviewAvailable.value && !classesAvailable.value && !tasksAvailable.value) {
    setMessage('监控接口暂未开放或暂不可用。', 'warn')
    return
  }
  setMessage('')
}

async function queryClasses() {
  classPagination.page = 1
  await loadClasses()
}

async function queryTasks() {
  taskPagination.page = 1
  await loadTasks()
}

async function resetClassFilters() {
  classFilters.keyword = ''
  classFilters.status = ''
  classPagination.page = 1
  await loadClasses()
}

async function resetTaskFilters() {
  taskFilters.keyword = ''
  taskFilters.status = ''
  taskPagination.page = 1
  await loadTasks()
}

async function prevClassPage() {
  if (classPagination.page <= 1 || loadingClasses.value) return
  classPagination.page -= 1
  await loadClasses()
}

async function nextClassPage() {
  if (!classPagination.hasNext || loadingClasses.value) return
  classPagination.page += 1
  await loadClasses()
}

async function prevTaskPage() {
  if (taskPagination.page <= 1 || loadingTasks.value) return
  taskPagination.page -= 1
  await loadTasks()
}

async function nextTaskPage() {
  if (!taskPagination.hasNext || loadingTasks.value) return
  taskPagination.page += 1
  await loadTasks()
}

onMounted(loadMonitor)
</script>

<template>
  <div class="admin-page">
    <section class="admin-hero">
      <div class="admin-hero__meta">
        <p class="admin-eyebrow">Monitoring</p>
        <span class="admin-chip">Read only</span>
      </div>
      <h2 class="admin-hero__title">系统监控</h2>
      <p class="admin-hero__desc">
        查看全局班级、任务和小组运行情况。管理员端只做全局观察，不直接介入教师或学生业务数据。
      </p>
      <div class="admin-actions">
        <button type="button" class="admin-btn" :disabled="loadingOverview || loadingClasses || loadingTasks" @click="loadMonitor">
          {{ loadingOverview || loadingClasses || loadingTasks ? '刷新中...' : '刷新监控' }}
        </button>
      </div>
    </section>

    <p v-if="message" class="admin-message" :class="messageType">{{ message }}</p>

    <section class="admin-stats-grid">
      <article v-for="item in overviewCards" :key="item.label" class="admin-stat-card">
        <p class="admin-stat-card__label">{{ item.label }}</p>
        <p class="admin-stat-card__value">{{ item.value }}</p>
      </article>
    </section>

    <section class="admin-panel">
      <div class="admin-panel__head">
        <div>
          <p class="admin-section-label">Status</p>
          <h3 class="admin-panel__title">监控状态</h3>
        </div>
      </div>
      <p class="admin-muted-copy">{{ monitorStatus }}</p>
    </section>

    <section class="admin-grid-2 monitor-grid">
      <article class="admin-panel">
        <div class="admin-panel__head">
          <div>
            <p class="admin-section-label">Classes</p>
            <h3 class="admin-panel__title">班级监控</h3>
          </div>
        </div>

        <AdminToolbar>
          <template #label>班级筛选</template>
          <template #description>按关键词和状态过滤班级监控数据。</template>
          <div class="admin-filter-row">
            <input v-model.trim="classFilters.keyword" type="text" placeholder="班级名或教师名" />
            <select v-model="classFilters.status">
              <option value="">全部状态</option>
              <option value="active">进行中</option>
              <option value="archived">已归档</option>
              <option value="pending">待开始</option>
            </select>
            <button type="button" class="admin-btn-secondary" :disabled="loadingClasses" @click="queryClasses">
              {{ loadingClasses ? '加载中...' : '查询班级' }}
            </button>
            <button type="button" class="admin-btn-ghost" :disabled="loadingClasses" @click="resetClassFilters">重置</button>
          </div>
        </AdminToolbar>

        <div class="admin-table-wrap">
          <table class="admin-table monitor-table">
            <thead>
              <tr>
                <th>班级名称</th>
                <th>教师</th>
                <th>状态</th>
                <th>成员数</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, index) in classes" :key="item.id || index">
                <td>{{ item.name || item.className || '-' }}</td>
                <td>{{ item.teacherName || item.ownerName || '-' }}</td>
                <td>{{ formatMonitorStatus(item.status || item.classStatus) }}</td>
                <td>{{ item.memberCount ?? item.studentCount ?? '-' }}</td>
              </tr>
              <tr v-if="!classes.length && classesAvailable">
                <td colspan="4" class="empty-row">当前查询条件下没有班级监控数据。</td>
              </tr>
              <tr v-if="!classes.length && !classesAvailable">
                <td colspan="4" class="empty-row">班级监控接口暂未开放或暂不可用。</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="admin-pager">
          <button type="button" class="admin-btn-secondary" :disabled="classPagination.page <= 1 || loadingClasses" @click="prevClassPage">上一页</button>
          <span>第 {{ classPagination.page }} / {{ classPagination.pages || 1 }} 页，共 {{ classPagination.total }} 条</span>
          <button type="button" class="admin-btn-secondary" :disabled="!classPagination.hasNext || loadingClasses" @click="nextClassPage">下一页</button>
        </div>
      </article>

      <article class="admin-panel">
        <div class="admin-panel__head">
          <div>
            <p class="admin-section-label">Tasks</p>
            <h3 class="admin-panel__title">任务监控</h3>
          </div>
        </div>

        <AdminToolbar>
          <template #label>任务筛选</template>
          <template #description>按关键词和状态过滤任务监控数据。</template>
          <div class="admin-filter-row">
            <input v-model.trim="taskFilters.keyword" type="text" placeholder="任务名或班级名" />
            <select v-model="taskFilters.status">
              <option value="">全部状态</option>
              <option value="open">未开始</option>
              <option value="in_progress">进行中</option>
              <option value="closed">已结束</option>
            </select>
            <button type="button" class="admin-btn-secondary" :disabled="loadingTasks" @click="queryTasks">
              {{ loadingTasks ? '加载中...' : '查询任务' }}
            </button>
            <button type="button" class="admin-btn-ghost" :disabled="loadingTasks" @click="resetTaskFilters">重置</button>
          </div>
        </AdminToolbar>

        <div class="admin-table-wrap">
          <table class="admin-table monitor-table">
            <thead>
              <tr>
                <th>任务</th>
                <th>班级</th>
                <th>状态</th>
                <th>截止时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, index) in tasks" :key="item.id || index">
                <td>{{ item.name || item.taskName || '-' }}</td>
                <td>{{ item.className || '-' }}</td>
                <td>{{ formatMonitorStatus(item.status || item.taskStatus) }}</td>
                <td>{{ formatDateTime(item.deadline || item.endAt) }}</td>
              </tr>
              <tr v-if="!tasks.length && tasksAvailable">
                <td colspan="4" class="empty-row">当前查询条件下没有任务监控数据。</td>
              </tr>
              <tr v-if="!tasks.length && !tasksAvailable">
                <td colspan="4" class="empty-row">任务监控接口暂未开放或暂不可用。</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="admin-pager">
          <button type="button" class="admin-btn-secondary" :disabled="taskPagination.page <= 1 || loadingTasks" @click="prevTaskPage">上一页</button>
          <span>第 {{ taskPagination.page }} / {{ taskPagination.pages || 1 }} 页，共 {{ taskPagination.total }} 条</span>
          <button type="button" class="admin-btn-secondary" :disabled="!taskPagination.hasNext || loadingTasks" @click="nextTaskPage">下一页</button>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.monitor-grid {
  align-items: start;
}

.monitor-table {
  min-width: 720px;
}

.empty-row {
  text-align: center;
  color: var(--admin-text-secondary);
  padding: 30px 12px;
}
</style>
