<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createSystemAnnouncement,
  deleteSystemAnnouncement,
  fetchAdminUsers,
  fetchSystemAnnouncements,
  withdrawSystemAnnouncement,
} from '../services/admin'
import IconSystem from '../components/common/IconSystem.vue'

const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const announcements = ref([])
const users = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)

const filters = reactive({
  keyword: '',
  status: '',
  targetScope: '',
})

const form = reactive({
  title: '',
  content: '',
  targetScope: 'all',
  priority: 1,
  popupEnabled: true,
  forceConfirm: false,
  startsAt: '',
  expiresAt: '',
  targetUserIds: [],
})

const totalPages = computed(() => Math.max(Math.ceil(total.value / size.value), 1))
const selectedUsers = computed(() => users.value.filter((user) => form.targetUserIds.includes(user.id)))

function formatDateTime(value) {
  if (!value) return '立即生效'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatScope(scope) {
  const map = {
    all: '全部教师与学生',
    teacher: '教师',
    student: '学生',
    selected: '指定用户',
  }
  return map[scope] || scope || '-'
}

function normalizeUser(row) {
  return {
    id: Number(row.id),
    name: row.name || '未命名用户',
    email: row.email || '',
    role: row.role || '',
    ceremonyNo: row.ceremonyNo || row.ceremonyCode || '',
  }
}

async function loadAnnouncements() {
  loading.value = true
  errorMessage.value = ''
  try {
    const { data } = await fetchSystemAnnouncements({
      page: page.value,
      size: size.value,
      keyword: filters.keyword || undefined,
      status: filters.status || undefined,
      targetScope: filters.targetScope || undefined,
    })
    const payload = data?.data || {}
    announcements.value = payload.items || []
    total.value = Number(payload.totalElements || 0)
  } catch (error) {
    errorMessage.value = error?.message || '公告列表加载失败'
  } finally {
    loading.value = false
  }
}

async function loadUsers() {
  try {
    const { data } = await fetchAdminUsers({ page: 1, size: 200, isDeleted: 0 })
    const rows = data?.data?.items || data?.data?.content || data?.data?.records || []
    users.value = rows
      .map(normalizeUser)
      .filter((user) => user.role === 'teacher' || user.role === 'student')
  } catch {
    users.value = []
  }
}

function resetForm() {
  form.title = ''
  form.content = ''
  form.targetScope = 'all'
  form.priority = 1
  form.popupEnabled = true
  form.forceConfirm = false
  form.startsAt = ''
  form.expiresAt = ''
  form.targetUserIds = []
}

function buildDateTime(value) {
  return value ? `${value}:00` : null
}

async function submitAnnouncement() {
  if (!form.title.trim() || !form.content.trim()) {
    errorMessage.value = '请填写公告标题和内容'
    return
  }
  if (form.targetScope === 'selected' && !form.targetUserIds.length) {
    errorMessage.value = '指定用户公告需要至少选择一个接收人'
    return
  }
  saving.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await createSystemAnnouncement({
      title: form.title.trim(),
      content: form.content.trim(),
      targetScope: form.targetScope,
      targetUserIds: form.targetScope === 'selected' ? form.targetUserIds : [],
      priority: Number(form.priority),
      popupEnabled: Boolean(form.popupEnabled),
      forceConfirm: Boolean(form.forceConfirm),
      startsAt: buildDateTime(form.startsAt),
      expiresAt: buildDateTime(form.expiresAt),
    })
    successMessage.value = '系统公告已发布'
    resetForm()
    page.value = 1
    await loadAnnouncements()
  } catch (error) {
    errorMessage.value = error?.message || '发布失败'
  } finally {
    saving.value = false
  }
}

async function withdraw(row) {
  if (!window.confirm(`确认撤回公告「${row.title}」？撤回后用户不再弹出。`)) return
  try {
    await withdrawSystemAnnouncement(row.id)
    await loadAnnouncements()
  } catch (error) {
    errorMessage.value = error?.message || '撤回失败'
  }
}

async function remove(row) {
  if (!window.confirm(`确认删除公告「${row.title}」？该操作会删除阅读记录。`)) return
  try {
    await deleteSystemAnnouncement(row.id)
    await loadAnnouncements()
  } catch (error) {
    errorMessage.value = error?.message || '删除失败'
  }
}

function toggleUser(userId) {
  const id = Number(userId)
  form.targetUserIds = form.targetUserIds.includes(id)
    ? form.targetUserIds.filter((item) => item !== id)
    : [...form.targetUserIds, id]
}

function applyFilters() {
  page.value = 1
  loadAnnouncements()
}

function changePage(nextPage) {
  page.value = Math.min(Math.max(nextPage, 1), totalPages.value)
  loadAnnouncements()
}

onMounted(() => {
  loadAnnouncements()
  loadUsers()
})
</script>

<template>
  <section class="announcement-page">
    <div class="announcement-grid">
      <form class="publisher-panel" @submit.prevent="submitAnnouncement">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Broadcast</p>
            <h2>发布系统公告</h2>
          </div>
          <button class="refresh-btn" type="button" @click="loadAnnouncements">
            <IconSystem name="refresh" :size="16" />
            刷新
          </button>
        </div>

        <div class="field-grid">
          <label class="field wide">
            <span>公告标题</span>
            <input v-model="form.title" maxlength="120" placeholder="例如：今晚 22:00 系统维护" />
          </label>
          <label class="field">
            <span>接收范围</span>
            <select v-model="form.targetScope">
              <option value="all">全部教师与学生</option>
              <option value="teacher">仅教师</option>
              <option value="student">仅学生</option>
              <option value="selected">指定用户</option>
            </select>
          </label>
          <label class="field">
            <span>重要级别</span>
            <select v-model.number="form.priority">
              <option :value="1">普通</option>
              <option :value="2">重要</option>
            </select>
          </label>
          <label class="field">
            <span>开始时间</span>
            <input v-model="form.startsAt" type="datetime-local" />
          </label>
          <label class="field">
            <span>结束时间</span>
            <input v-model="form.expiresAt" type="datetime-local" />
          </label>
        </div>

        <label class="field">
          <span>公告内容</span>
          <textarea v-model="form.content" maxlength="5000" rows="7" placeholder="写清楚变更、影响范围和用户需要做什么。" />
        </label>

        <div v-if="form.targetScope === 'selected'" class="recipient-picker">
          <div class="recipient-head">
            <strong>选择接收人</strong>
            <span>已选择 {{ selectedUsers.length }} 人</span>
          </div>
          <div class="recipient-list">
            <button
              v-for="user in users"
              :key="user.id"
              class="recipient-chip"
              :class="{ selected: form.targetUserIds.includes(user.id) }"
              type="button"
              @click="toggleUser(user.id)"
            >
              <span>{{ user.name }}</span>
              <small>{{ user.role === 'teacher' ? '教师' : '学生' }} · {{ user.email || user.ceremonyNo }}</small>
            </button>
          </div>
        </div>

        <div class="switch-row">
          <label class="switch-item">
            <input v-model="form.popupEnabled" type="checkbox" />
            <span>登录后弹窗提醒</span>
          </label>
          <label class="switch-item">
            <input v-model="form.forceConfirm" type="checkbox" />
            <span>要求用户确认</span>
          </label>
        </div>

        <div class="form-footer">
          <p v-if="errorMessage" class="message error">{{ errorMessage }}</p>
          <p v-else-if="successMessage" class="message success">{{ successMessage }}</p>
          <button class="submit-btn" type="submit" :disabled="saving">
            <IconSystem name="bell" :size="16" />
            {{ saving ? '发布中' : '发布公告' }}
          </button>
        </div>
      </form>

      <section class="list-panel">
        <div class="filter-bar">
          <input v-model="filters.keyword" placeholder="搜索标题或内容" @keyup.enter="applyFilters" />
          <select v-model="filters.status" @change="applyFilters">
            <option value="">全部状态</option>
            <option value="1">生效中</option>
            <option value="2">已撤回</option>
          </select>
          <select v-model="filters.targetScope" @change="applyFilters">
            <option value="">全部范围</option>
            <option value="all">全部</option>
            <option value="teacher">教师</option>
            <option value="student">学生</option>
            <option value="selected">指定用户</option>
          </select>
          <button type="button" @click="applyFilters">筛选</button>
        </div>

        <div class="announcement-list">
          <article v-for="item in announcements" :key="item.id" class="announcement-card">
            <div class="card-main">
              <div class="card-title-row">
                <h3>{{ item.title }}</h3>
                <span class="status-pill" :class="{ withdrawn: item.status === 2 }">
                  {{ item.status === 2 ? '已撤回' : '生效中' }}
                </span>
              </div>
              <p>{{ item.content }}</p>
              <div class="card-meta">
                <span>{{ formatScope(item.targetScope) }}</span>
                <span>{{ item.priority > 1 ? '重要' : '普通' }}</span>
                <span>接收 {{ item.recipientCount || 0 }}</span>
                <span>已读 {{ item.readCount || 0 }}</span>
                <span>确认 {{ item.confirmedCount || 0 }}</span>
                <span>{{ formatDateTime(item.createdAt) }}</span>
              </div>
            </div>
            <div class="card-actions">
              <button v-if="item.status !== 2" type="button" @click="withdraw(item)">撤回</button>
              <button class="danger" type="button" @click="remove(item)">删除</button>
            </div>
          </article>

          <div v-if="!loading && !announcements.length" class="empty-state">
            <IconSystem name="bell" :size="28" />
            <p>还没有系统公告</p>
          </div>
          <div v-if="loading" class="empty-state">
            <p>正在加载公告...</p>
          </div>
        </div>

        <div class="pagination">
          <button type="button" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
          <span>第 {{ page }} / {{ totalPages }} 页，共 {{ total }} 条</span>
          <button type="button" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.announcement-page {
  min-height: 100%;
}

.announcement-grid {
  display: grid;
  grid-template-columns: minmax(360px, 0.9fr) minmax(0, 1.1fr);
  gap: 18px;
  align-items: start;
}

.publisher-panel,
.list-panel {
  border: 1px solid rgba(126, 143, 171, 0.22);
  border-radius: 16px;
  background: var(--tt-surface, rgba(255, 255, 255, 0.92));
  box-shadow: 0 16px 40px rgba(23, 36, 64, 0.08);
}

.publisher-panel {
  padding: 22px;
}

.list-panel {
  padding: 18px;
}

.panel-head,
.form-footer,
.recipient-head,
.card-title-row,
.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.eyebrow {
  margin: 0 0 5px;
  color: var(--tt-muted, #667085);
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
}

h2,
h3,
p {
  margin: 0;
}

.panel-head h2 {
  font-size: 22px;
  color: var(--tt-text, #172033);
}

.refresh-btn,
.submit-btn,
.filter-bar button,
.pagination button,
.card-actions button {
  border: 0;
  border-radius: 11px;
  font-weight: 800;
  cursor: pointer;
}

.refresh-btn {
  height: 36px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0 12px;
  background: rgba(37, 74, 255, 0.1);
  color: #2149d8;
}

.field-grid {
  margin-top: 20px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.field-grid .wide {
  grid-column: 1 / -1;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin-top: 14px;
}

.field span,
.recipient-head strong {
  color: var(--tt-text, #172033);
  font-size: 13px;
  font-weight: 800;
}

.field input,
.field select,
.field textarea,
.filter-bar input,
.filter-bar select {
  width: 100%;
  border: 1px solid rgba(126, 143, 171, 0.28);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.82);
  color: var(--tt-text, #172033);
  font: inherit;
  outline: none;
}

.field input,
.field select,
.filter-bar input,
.filter-bar select {
  height: 40px;
  padding: 0 12px;
}

.field textarea {
  resize: vertical;
  padding: 12px;
  line-height: 1.6;
}

.recipient-picker {
  margin-top: 16px;
  padding: 14px;
  border-radius: 14px;
  background: rgba(45, 92, 255, 0.06);
}

.recipient-head span {
  color: var(--tt-muted, #667085);
  font-size: 12px;
}

.recipient-list {
  margin-top: 12px;
  max-height: 168px;
  overflow: auto;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.recipient-chip {
  border: 1px solid rgba(126, 143, 171, 0.28);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.72);
  padding: 8px 10px;
  text-align: left;
  color: var(--tt-text, #172033);
  cursor: pointer;
}

.recipient-chip.selected {
  border-color: rgba(37, 74, 255, 0.45);
  background: rgba(37, 74, 255, 0.12);
}

.recipient-chip span,
.recipient-chip small {
  display: block;
}

.recipient-chip small {
  margin-top: 2px;
  color: var(--tt-muted, #667085);
}

.switch-row {
  margin-top: 16px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.switch-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--tt-text, #172033);
  font-size: 13px;
  font-weight: 700;
}

.form-footer {
  margin-top: 18px;
}

.message {
  font-size: 13px;
}

.message.error {
  color: #b42318;
}

.message.success {
  color: #067647;
}

.submit-btn {
  height: 42px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 18px;
  background: #1f4fff;
  color: #f8fbff;
  box-shadow: 0 14px 28px rgba(31, 79, 255, 0.18);
}

.submit-btn:disabled {
  opacity: 0.62;
  cursor: wait;
}

.filter-bar {
  display: grid;
  grid-template-columns: minmax(160px, 1fr) 120px 120px 74px;
  gap: 10px;
}

.filter-bar button,
.pagination button,
.card-actions button {
  height: 38px;
  padding: 0 12px;
  background: rgba(31, 45, 75, 0.08);
  color: var(--tt-text, #172033);
}

.announcement-list {
  margin-top: 14px;
  max-height: calc(100vh - 310px);
  min-height: 360px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-right: 4px;
}

.announcement-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  padding: 15px;
  border: 1px solid rgba(126, 143, 171, 0.2);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.58);
}

.announcement-card h3 {
  font-size: 16px;
  color: var(--tt-text, #172033);
}

.announcement-card p {
  margin-top: 8px;
  color: var(--tt-muted, #667085);
  font-size: 13px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.status-pill {
  flex: 0 0 auto;
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(5, 150, 105, 0.12);
  color: #047857;
  font-size: 12px;
  font-weight: 800;
}

.status-pill.withdrawn {
  background: rgba(100, 116, 139, 0.14);
  color: #475569;
}

.card-meta {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--tt-muted, #667085);
  font-size: 12px;
}

.card-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.card-actions .danger {
  color: #b42318;
  background: rgba(180, 35, 24, 0.08);
}

.empty-state {
  min-height: 240px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 10px;
  color: var(--tt-muted, #667085);
}

.pagination {
  margin-top: 14px;
  color: var(--tt-muted, #667085);
  font-size: 13px;
}

.pagination button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

@media (max-width: 1180px) {
  .announcement-grid {
    grid-template-columns: 1fr;
  }

  .announcement-list {
    max-height: none;
  }
}

@media (max-width: 720px) {
  .field-grid,
  .filter-bar,
  .announcement-card {
    grid-template-columns: 1fr;
  }

  .card-actions {
    flex-direction: row;
  }
}
</style>

/* Moyu announcement workspace polish */
.announcement-grid {
  gap: 22px;
}

.publisher-panel,
.list-panel {
  border-color: var(--admin-border, rgba(126, 143, 171, 0.22));
  border-radius: 28px;
  background:
    radial-gradient(circle at 8% 6%, oklch(0.86 0.1 207 / 0.2), transparent 32%),
    oklch(0.99 0.014 220 / 0.82);
  box-shadow: 0 24px 68px oklch(0.5 0.09 230 / 0.14);
  backdrop-filter: blur(16px);
}

.publisher-panel {
  padding: 26px;
}

.list-panel {
  padding: 22px;
}

.panel-head h2 {
  color: var(--admin-text-primary, #172033);
  font-size: 24px;
}

.eyebrow {
  color: var(--admin-text-tertiary, #667085);
  font-weight: 900;
}

.refresh-btn,
.submit-btn,
.filter-bar button,
.pagination button,
.card-actions button {
  border-radius: 999px;
  font-weight: 900;
}

.refresh-btn {
  background: oklch(0.87 0.08 202 / 0.52);
  color: oklch(0.35 0.12 238);
}

.submit-btn {
  background: linear-gradient(135deg, oklch(0.67 0.18 235), oklch(0.73 0.14 208));
  color: oklch(0.99 0.006 230);
  box-shadow: 0 18px 34px oklch(0.53 0.13 224 / 0.24);
}

.field input,
.field select,
.field textarea,
.filter-bar input,
.filter-bar select {
  border-color: var(--admin-input-border, rgba(126, 143, 171, 0.28));
  border-radius: 16px;
  background: oklch(0.995 0.008 226 / 0.86);
  color: var(--admin-text-primary, #172033);
}

.field span,
.recipient-head strong,
.switch-item,
.announcement-card h3 {
  color: var(--admin-text-primary, #172033);
}

.recipient-picker {
  background: oklch(0.87 0.08 202 / 0.26);
  border: 1px solid oklch(0.7 0.12 220 / 0.24);
}

.recipient-chip,
.announcement-card {
  border-color: oklch(0.76 0.045 224 / 0.34);
  background: oklch(0.995 0.01 226 / 0.66);
  box-shadow: 0 12px 32px oklch(0.5 0.08 230 / 0.08);
}

.recipient-chip.selected {
  border-color: oklch(0.7 0.12 220 / 0.4);
  background: oklch(0.87 0.08 202 / 0.42);
}

.announcement-card {
  border-radius: 20px;
}

.announcement-card p,
.card-meta,
.pagination,
.empty-state,
.recipient-chip small,
.recipient-head span {
  color: var(--admin-text-secondary, #667085);
}

.status-pill {
  background: oklch(0.9 0.08 160 / 0.42);
  color: oklch(0.5 0.13 155);
}

.status-pill.withdrawn {
  background: oklch(0.94 0.022 230 / 0.72);
  color: var(--admin-text-secondary, #475569);
}
