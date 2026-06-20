<script setup>
import '../styles/admin-workspace.css'
import { computed, onMounted, reactive, ref } from 'vue'
import AdminToolbar from '../components/admin/AdminToolbar.vue'
import { fetchAdminOperationLogs } from '../services/admin'
import { formatDateTime, formatDuration, formatHttpStatus } from '../utils/admin'

const loading = ref(false)
const message = ref('')
const messageType = ref('info')
const logs = ref([])
const endpointAvailable = ref(true)

const filters = reactive({
  userId: '',
  role: '',
  pathContains: '',
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
  pages: 0,
  hasNext: false,
})

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

function getErrorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || fallback
}

function normalizePagedPayload(payload) {
  return {
    list: payload?.list || payload?.records || payload?.content || [],
    total: Number(payload?.total) || 0,
    pages: Number(payload?.pages) || 0,
    hasNext: Boolean(payload?.hasNext),
  }
}

function formatPath(item) {
  const method = item.httpMethod || '-'
  const path = item.path || '-'
  const query = item.queryString ? `?${item.queryString}` : ''
  return `${method} ${path}${query}`
}

const logSummary = computed(() => [
  {
    title: '当前页记录',
    value: `${logs.value.length}`,
    note: `总记录数 ${pagination.total}`,
  },
  {
    title: '接口状态',
    value: endpointAvailable.value ? '可用' : '不可用',
    note: endpointAvailable.value ? '实时读取后端日志' : '后端暂未返回日志',
  },
  {
    title: '分页',
    value: `${pagination.page}/${pagination.pages || 1}`,
    note: '按操作时间倒序',
  },
])

async function loadLogs() {
  loading.value = true
  setMessage('')

  try {
    const params = {
      page: pagination.page,
      size: pagination.size,
    }

    const userIdValue = String(filters.userId || '').trim()
    if (userIdValue) {
      const parsed = Number(userIdValue)
      if (!Number.isNaN(parsed)) params.userId = parsed
    }
    if (filters.role) params.role = filters.role
    if (String(filters.pathContains || '').trim()) params.pathContains = String(filters.pathContains).trim()

    const { data } = await fetchAdminOperationLogs(params)
    const payload = normalizePagedPayload(data?.data || {})
    logs.value = payload.list
    pagination.total = payload.total
    pagination.pages = payload.pages
    pagination.hasNext = payload.hasNext
    endpointAvailable.value = true
  } catch (error) {
    logs.value = []
    pagination.total = 0
    pagination.pages = 0
    pagination.hasNext = false
    endpointAvailable.value = false
    setMessage(getErrorMessage(error, '操作日志接口暂不可用，页面结构已准备好。'), 'warn')
  } finally {
    loading.value = false
  }
}

async function queryLogs() {
  pagination.page = 1
  await loadLogs()
}

async function resetFilters() {
  filters.userId = ''
  filters.role = ''
  filters.pathContains = ''
  pagination.page = 1
  await loadLogs()
}

async function prevPage() {
  if (pagination.page <= 1 || loading.value) return
  pagination.page -= 1
  await loadLogs()
}

async function nextPage() {
  if (!pagination.hasNext || loading.value) return
  pagination.page += 1
  await loadLogs()
}

async function changePageSize() {
  pagination.page = 1
  await loadLogs()
}

onMounted(loadLogs)
</script>

<template>
  <div class="admin-page">
    <section class="admin-hero">
      <div class="admin-hero__meta">
        <p class="admin-eyebrow">Audit</p>
        <span class="admin-chip">Structured logs</span>
      </div>
      <h2 class="admin-hero__title">操作日志</h2>
      <p class="admin-hero__desc">
        查询管理员关键操作记录，便于按用户、角色和请求路径定位问题。
      </p>
      <div class="admin-actions">
        <button type="button" class="admin-btn" :disabled="loading" @click="loadLogs">
          {{ loading ? '刷新中...' : '刷新日志' }}
        </button>
      </div>
    </section>

    <section class="admin-stats-grid">
      <article v-for="item in logSummary" :key="item.title" class="admin-stat-card">
        <p class="admin-stat-card__label">{{ item.title }}</p>
        <p class="admin-stat-card__value">{{ item.value }}</p>
        <p v-if="item.note" class="admin-stat-card__note">{{ item.note }}</p>
      </article>
    </section>

    <AdminToolbar>
      <template #label>日志筛选</template>
      <template #description>按用户、角色、请求路径和分页条件过滤操作日志。</template>
      <div class="admin-filter-row logs-filter-row">
        <input v-model.trim="filters.userId" type="text" placeholder="用户 ID，可选" />
        <select v-model="filters.role">
          <option value="">全部角色</option>
          <option value="admin">管理员</option>
          <option value="teacher">教师</option>
          <option value="student">学生</option>
        </select>
        <input v-model.trim="filters.pathContains" type="text" placeholder="如 /api/admin 或 /api/teacher/classes" />
        <select v-model.number="pagination.size" @change="changePageSize">
          <option :value="10">10 条/页</option>
          <option :value="20">20 条/页</option>
          <option :value="50">50 条/页</option>
        </select>
        <button type="button" class="admin-btn-secondary" :disabled="loading" @click="queryLogs">
          {{ loading ? '查询中...' : '查询日志' }}
        </button>
        <button type="button" class="admin-btn-ghost" :disabled="loading" @click="resetFilters">重置</button>
      </div>
      <p v-if="message" class="admin-message" :class="messageType">{{ message }}</p>
    </AdminToolbar>

    <section class="admin-panel">
      <div class="admin-panel__head">
        <div>
          <p class="admin-section-label">Log table</p>
          <h3 class="admin-panel__title">日志表格</h3>
        </div>
      </div>

      <div class="admin-table-wrap">
        <table class="admin-table logs-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>用户</th>
              <th>角色</th>
              <th>动作</th>
              <th>路径</th>
              <th>结果</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in logs" :key="item.id || index">
              <td>{{ formatDateTime(item.createdAt) }}</td>
              <td>{{ item.userId ?? '-' }}</td>
              <td>{{ item.role || '-' }}</td>
              <td>{{ item.action || '-' }}</td>
              <td>
                <div class="path-cell">
                  <span>{{ formatPath(item) }}</span>
                  <span v-if="item.durationMs != null">{{ formatDuration(item.durationMs) }}</span>
                  <span v-if="item.httpStatus != null">HTTP {{ formatHttpStatus(item.httpStatus) }}</span>
                </div>
              </td>
              <td>{{ item.success === true ? '成功' : item.success === false ? '失败' : '-' }}</td>
            </tr>
            <tr v-if="!logs.length && endpointAvailable">
              <td colspan="6" class="empty-row">当前筛选条件下没有匹配的操作日志。</td>
            </tr>
            <tr v-if="!logs.length && !endpointAvailable">
              <td colspan="6" class="empty-row">操作日志接口暂未开放或暂不可用。</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="admin-pager">
        <button type="button" class="admin-btn-secondary" :disabled="pagination.page <= 1 || loading" @click="prevPage">上一页</button>
        <span>第 {{ pagination.page }} / {{ pagination.pages || 1 }} 页，共 {{ pagination.total }} 条</span>
        <button type="button" class="admin-btn-secondary" :disabled="!pagination.hasNext || loading" @click="nextPage">下一页</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.logs-filter-row,
.logs-table {
  width: 100%;
}

.logs-table {
  min-width: 980px;
}

.path-cell {
  display: grid;
  gap: 4px;
}

.empty-row {
  text-align: center;
  color: var(--admin-text-secondary);
  padding: 30px 12px;
}
</style>
