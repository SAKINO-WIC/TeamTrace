<script setup>
import EmptyState from '../components/common/EmptyState.vue'
import WorkspaceDialogMask from '../components/common/WorkspaceDialogMask.vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTeacherLocale } from '../composables/useTeacherLocale'
import { fetchTeacherClassGroups, fetchTeacherClassStudents, removeTeacherClassStudent } from '../services/teacher'
import { formatDateTime, formatStudentStatus, normalizePagedPayload } from '../utils/teacher'
import { readSessionCache } from '../utils/sessionCache'
import { loadWithStaleSessionCache } from '../utils/staleSessionLoad'

const route = useRoute()
const router = useRouter()
const { t, isEn } = useTeacherLocale()

const classId = computed(() => String(route.params.classId || ''))
const loading = ref(false)
const removing = ref(false)
const message = ref('')
const messageType = ref('info')
const students = ref([])
const loadError = ref('')
const groupLoadError = ref('')
const detailDialogOpen = ref(false)
const detailStudent = ref(null)
const removeDialogOpen = ref(false)
const selectedStudent = ref(null)

const filters = reactive({
  keyword: '',
  status: 'all',
  group: 'all',
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0,
  pages: 1,
  hasNext: false,
})

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

function buildStudentGroupMap(rawGroups) {
  const groupMap = new Map()

  rawGroups.forEach((group) => {
    const memberIds = Array.isArray(group?.memberStudentIds) ? group.memberStudentIds : []
    memberIds.forEach((studentId) => {
      groupMap.set(String(studentId), group?.name || group?.groupName || '未分组')
    })
  })

  return groupMap
}

function normalizeStudent(raw, groupMap, groupUnavailable = false) {
  const studentId = String(raw?.studentId ?? raw?.id ?? '-')
  return {
    id: studentId,
    name: raw?.name ?? raw?.studentName ?? '-',
    email: raw?.email ?? '-',
    status: raw?.statusLabel || formatStudentStatus(raw?.status ?? 1),
    groupName: groupUnavailable ? '小组信息暂不可用' : groupMap.get(studentId) || raw?.groupName || '未分组',
    joinedAtText: formatDateTime(raw?.joinTime ?? raw?.joinedAt ?? raw?.createdAt),
  }
}

const groupOptions = computed(() => {
  return Array.from(
    new Set(
      students.value
        .map((item) => item.groupName)
        .filter((item) => item && item !== '小组信息暂不可用'),
    ),
  ).sort((left, right) => left.localeCompare(right, 'zh-CN'))
})

const filteredStudents = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return students.value.filter((item) => {
    const keywordHit =
      !keyword ||
      [item.id, item.name, item.email, item.groupName].some((field) =>
        String(field || '').toLowerCase().includes(keyword),
      )
    const statusHit = filters.status === 'all' || item.status === filters.status
    const groupHit = filters.group === 'all' || item.groupName === filters.group
    return keywordHit && statusHit && groupHit
  })
})

const hasActiveFilters = computed(() =>
  Boolean(filters.keyword.trim()) || filters.status !== 'all' || filters.group !== 'all',
)

const hasLoadError = computed(() => Boolean(loadError.value) && !loading.value)

const hasNoStudents = computed(() =>
  !loading.value && !hasLoadError.value && pagination.total === 0 && !students.value.length,
)

const pageStart = computed(() => {
  if (!pagination.total) {
    return 0
  }

  return (pagination.page - 1) * pagination.size + 1
})

const pageEnd = computed(() => Math.min(pagination.page * pagination.size, pagination.total))

const currentPageUngroupedCount = computed(() =>
  students.value.filter((item) => item.groupName === '未分组').length,
)

const currentPageGroupedCount = computed(() =>
  students.value.filter((item) => item.groupName && item.groupName !== '未分组' && item.groupName !== '小组信息暂不可用').length,
)

const nextAction = computed(() => {
  if (groupLoadError.value) {
    return {
      title: '小组信息暂不可用',
      description: '',
      label: '重新加载名单',
      action: 'reload',
    }
  }

  if (currentPageUngroupedCount.value > 0) {
    return {
      title: '当前页还有学生未分组',
      description: '',
      label: '进入分组管理',
      action: 'groups',
    }
  }

  return {
    title: '当前页学生已完成分组',
    description: '',
    label: '进入任务管理',
    action: 'tasks',
  }
})

function studentsPageCacheKey() {
  return `teacher:class:${classId.value}:students:${pagination.page}:${pagination.size}`
}

function applyStudentsPayload(payload) {
  const groupMap = buildStudentGroupMap(Array.isArray(payload?.groups) ? payload.groups : [])
  groupLoadError.value = payload?.groupLoadError || ''
  const payloadPage = payload?.page || {}
  students.value = (payloadPage.list || []).map((item) =>
    normalizeStudent(item, groupMap, Boolean(groupLoadError.value)),
  )
  pagination.total = payloadPage.total ?? 0
  pagination.pages = payloadPage.pages || 1
  pagination.hasNext = Boolean(payloadPage.hasNext)
}

async function loadStudents(options = {}) {
  loadError.value = ''
  const cacheKey = studentsPageCacheKey()
  const hadCacheBefore = Boolean(readSessionCache(cacheKey, 120000))
  loading.value = !hadCacheBefore

  try {
    const { error } = await loadWithStaleSessionCache({
      cacheKey,
      ttlMs: 120000,
      force: options.force === true,
      apply: applyStudentsPayload,
      fetchFresh: async () => {
        const [studentsResult, groupsResult] = await Promise.allSettled([
          fetchTeacherClassStudents(classId.value, {
            page: pagination.page,
            size: pagination.size,
          }),
          fetchTeacherClassGroups(classId.value),
        ])
        if (studentsResult.status === 'rejected') {
          throw studentsResult.reason
        }
        const studentsResponse = studentsResult.value?.data || {}
        const payload = normalizePagedPayload(studentsResponse?.data || {})
        const groups =
          groupsResult.status === 'fulfilled' && Array.isArray(groupsResult.value?.data?.data)
            ? groupsResult.value.data.data
            : []
        const groupErr =
          groupsResult.status === 'rejected'
            ? groupsResult.reason?.message || '小组信息暂不可用'
            : ''
        return {
          groups,
          groupLoadError: groupErr,
          page: {
            list: payload.list,
            total: payload.total,
            pages: payload.pages || 1,
            hasNext: payload.hasNext,
          },
        }
      },
    })
    if (error && !students.value.length) {
      loadError.value = error?.message || '加载学生失败'
    } else {
      setMessage('')
    }
  } catch (error) {
    if (!students.value.length) {
      students.value = []
      pagination.total = 0
      pagination.pages = 1
      pagination.hasNext = false
      loadError.value = error.message || '加载学生失败'
    }
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.status = 'all'
  filters.group = 'all'
}

function openRemoveDialog(student) {
  selectedStudent.value = student
  removeDialogOpen.value = true
}

function openDetailDialog(student) {
  detailStudent.value = student
  detailDialogOpen.value = true
}

function closeDetailDialog() {
  detailStudent.value = null
  detailDialogOpen.value = false
}

function closeRemoveDialog() {
  if (removing.value) {
    return
  }
  selectedStudent.value = null
  removeDialogOpen.value = false
}

async function confirmRemoveStudent() {
  if (!selectedStudent.value) {
    return
  }

  removing.value = true
  try {
    const shouldStepBack = students.value.length === 1 && pagination.page > 1
    await removeTeacherClassStudent(classId.value, selectedStudent.value.id)
    setMessage(`${selectedStudent.value.name} 已移出班级。`, 'success')
    closeRemoveDialog()
    if (shouldStepBack) {
      pagination.page -= 1
    }
    await loadStudents()
  } catch (error) {
    setMessage(error.message || '移除学生失败', 'error')
  } finally {
    removing.value = false
  }
}

async function prevPage() {
  if (pagination.page <= 1 || loading.value) {
    return
  }
  pagination.page -= 1
  await loadStudents()
}

async function nextPage() {
  if (!pagination.hasNext || loading.value) {
    return
  }
  pagination.page += 1
  await loadStudents()
}

async function changePageSize(event) {
  const nextSize = Number(event.target.value)
  if (!Number.isFinite(nextSize) || nextSize === pagination.size || loading.value) {
    return
  }

  pagination.size = nextSize
  pagination.page = 1
  await loadStudents()
}

function runNextAction() {
  if (nextAction.value.action === 'reload') {
    loadStudents()
    return
  }

  if (nextAction.value.action === 'groups') {
    router.push(`/teacher/classes/${classId.value}/groups`)
    return
  }

  router.push(`/teacher/classes/${classId.value}/tasks`)
}

watch(classId, (nextId, prevId) => {
  if (nextId && nextId !== prevId) {
    students.value = []
    pagination.page = 1
    loadStudents()
  }
})
watch(classId, (nextId, prevId) => {
  if (nextId && nextId !== prevId) {
    students.value = []
    pagination.page = 1
    loadStudents()
  }
})
watch(isEn, loadStudents)
onMounted(loadStudents)
</script>

<template>
  <div class="teacher-page">
    <header class="card topbar">
      <div class="title-block">
        <h2>{{ t('学生管理', 'Students') }}</h2>
      </div>
    </header>

    <p v-if="message" class="message" :class="messageType">{{ message }}</p>

    <section v-if="hasLoadError" class="card state-panel error-panel">
      <div>
        <p class="panel-kicker">名单加载失败</p>
        <h3>暂时无法读取学生名单</h3>
        <p class="desc">{{ loadError }}</p>
      </div>
      <div class="state-actions">
        <button class="secondary-btn" type="button" @click="router.push(`/teacher/classes/${classId}`)">返回班级概览</button>
      </div>
    </section>

    <section v-else-if="loading && !students.length" class="card panel loading-panel">
      <div class="panel-head">
        <div>
          <p class="panel-kicker">正在读取</p>
          <h3>加载学生名单</h3>
        </div>
      </div>
      <div class="skeleton-table">
        <span v-for="item in 6" :key="item" class="skeleton-row"></span>
      </div>
    </section>

    <section v-if="hasNoStudents" class="card empty-guide">
      <EmptyState
        icon="👤"
        :title="t('等待学生使用邀请码加入', 'Waiting for students to join with invite code')"
        description=""
        action-label="返回班级详情复制邀请码"
        secondary-label="重新加载名单"
        @action="router.push(`/teacher/classes/${classId}`)"
        @secondary="loadStudents"
      />
    </section>

    <p v-if="groupLoadError && !hasNoStudents" class="inline-warning">{{ groupLoadError }}</p>

    <section v-if="!hasLoadError && !hasNoStudents && students.length" class="card panel student-workspace">

      <div class="panel-head">
        <div>
          <h3>{{ t('学生名单', 'Student list') }}</h3>
        </div>
      </div>

      <div class="filters">
        <label class="filter-item keyword-filter">
          <span>搜索学生</span>
          <input v-model.trim="filters.keyword" type="text" placeholder="学号 / 姓名 / 邮箱 / 小组" />
        </label>

        <label class="filter-item">
          <span>小组</span>
          <select v-model="filters.group">
            <option value="all">全部小组</option>
            <option v-for="item in groupOptions" :key="item" :value="item">{{ item }}</option>
          </select>
        </label>

        <label class="filter-item">
          <span>每页</span>
          <select :value="pagination.size" :disabled="loading" @change="changePageSize">
            <option value="20">20 人</option>
            <option value="50">50 人</option>
            <option value="100">100 人</option>
          </select>
        </label>

        <div class="filter-item filter-reset">
          <button class="ghost-btn" type="button" :disabled="!hasActiveFilters" @click="resetFilters">重置筛选</button>
        </div>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ t('学生', 'Student') }}</th>
              <th>邮箱</th>
              <th>状态</th>
              <th>小组</th>
              <th>加入时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredStudents" :key="item.id">
              <td>
                <button class="student-link-btn" type="button" @click="openDetailDialog(item)">
                  <strong class="student-name">{{ item.name }}</strong>
                </button>
              </td>
              <td class="mono-cell">{{ item.email }}</td>
              <td>
                <span class="status-badge" :class="item.status === '在班' ? 'active' : 'inactive'">
                  {{ item.status }}
                </span>
              </td>
              <td>
                <span class="group-badge" :class="{ muted: item.groupName === '未分组' }">
                  {{ item.groupName }}
                </span>
              </td>
              <td>{{ item.joinedAtText }}</td>
              <td>
                <button class="danger-link-btn" type="button" @click="openRemoveDialog(item)">移出班级</button>
              </td>
            </tr>
            <tr v-if="!filteredStudents.length">
              <td colspan="6" class="empty-cell">
                <div class="filter-empty">
                  <p>{{ hasActiveFilters ? '当前筛选下没有学生。' : '当前页没有可显示的学生。' }}</p>
                  <button v-if="hasActiveFilters" class="ghost-btn" type="button" @click="resetFilters">重置筛选</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pager">
        <button class="secondary-btn" type="button" :disabled="pagination.page <= 1 || loading" @click="prevPage">
          上一页
        </button>
        <span class="meta">第 {{ pagination.page }} / {{ pagination.pages || 1 }} 页，共 {{ pagination.total }} 人</span>
        <button class="secondary-btn" type="button" :disabled="!pagination.hasNext || loading" @click="nextPage">
          下一页
        </button>
      </div>
    </section>

    <WorkspaceDialogMask :open="detailDialogOpen" @close="closeDetailDialog">
      <section class="detail-dialog">
        <div class="dialog-body">
          <article class="dialog-summary">
            <p class="summary-name">{{ detailStudent?.name || '-' }}</p>
            <p class="summary-note">
              {{ detailStudent?.groupName || '未分组' }} · {{ detailStudent?.status || '-' }}
            </p>
          </article>

          <div class="detail-grid">
            <article class="detail-item">
              <p class="detail-label">学生账号</p>
              <p class="detail-value mono-cell">{{ detailStudent?.id || '-' }}</p>
            </article>
            <article class="detail-item">
              <p class="detail-label">邮箱</p>
              <p class="detail-value mono-cell">{{ detailStudent?.email || '-' }}</p>
            </article>
            <article class="detail-item">
              <p class="detail-label">当前状态</p>
              <p class="detail-value">{{ detailStudent?.status || '-' }}</p>
            </article>
            <article class="detail-item">
              <p class="detail-label">当前小组</p>
              <p class="detail-value">{{ detailStudent?.groupName || '未分组' }}</p>
            </article>
            <article class="detail-item detail-item-wide">
              <p class="detail-label">加入班级时间</p>
              <p class="detail-value">{{ detailStudent?.joinedAtText || '-' }}</p>
            </article>
          </div>
        </div>

        <div class="dialog-actions detail-dialog-actions">
          <button class="secondary-btn" type="button" @click="closeDetailDialog">关闭</button>
        </div>
      </section>
    </WorkspaceDialogMask>

    <WorkspaceDialogMask :open="removeDialogOpen" @close="closeRemoveDialog">
      <section class="danger-dialog">
        <div class="dialog-head">
          <div>
            <p class="dialog-eyebrow">学生移出确认</p>
            <h3>移出班级</h3>
          </div>
        </div>

        <div class="dialog-body">
          <div class="danger-banner">
            <span class="danger-mark">!</span>
            <div>
              <p class="danger-banner-title">将同步清理其当前分组归属</p>
            </div>
          </div>

          <article class="dialog-summary">
            <p class="summary-name">{{ selectedStudent?.name || '-' }}</p>
            <p class="summary-note">
              {{ selectedStudent?.id || '-' }} · {{ selectedStudent?.groupName || '未分组' }} · {{ selectedStudent?.status || '-' }}
            </p>
          </article>
        </div>

        <div class="dialog-actions">
          <button class="secondary-btn" type="button" :disabled="removing" @click="closeRemoveDialog">取消</button>
          <button class="danger-btn" type="button" :disabled="removing" @click="confirmRemoveStudent">
            {{ removing ? '处理中...' : '确认移出' }}
          </button>
        </div>
      </section>
    </WorkspaceDialogMask>
  </div>
</template>

<style scoped>
.teacher-page,
.filters {
  display: grid;
  gap: 14px;
}

.teacher-page {
  gap: 16px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.card {
  background: var(--teacher-surface);
  border-radius: var(--teacher-radius-card);
  box-shadow: var(--teacher-shadow);
}

.topbar,
.panel,
.state-panel,
.detail-dialog,
.danger-dialog {
  padding: 18px;
}

.topbar,
.panel-head,
.panel-actions,
.state-panel,
.state-actions,
.next-step-strip,
.pager,
.dialog-head,
.dialog-actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.desc,
.message,
.meta,
.cell-note,
.panel-kicker,
.dialog-eyebrow,
.summary-note,
.danger-banner-note {
  margin: 0;
  color: var(--teacher-text-tertiary);
}

h2,
h3,
.summary-name,
.danger-banner-title {
  margin: 0;
}

.title-block {
  display: grid;
  gap: 8px;
}

h2 {
  font-size: 28px;
  font-weight: 800;
  line-height: 1.1;
}

h3 {
  font-size: 18px;
  font-weight: 800;
  line-height: 1.16;
}

.desc,
.message,
.meta,
.summary-note,
.danger-banner-note {
  margin-top: 8px;
  line-height: 1.6;
  font-size: 13px;
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

.state-panel {
  align-items: center;
}

.error-panel {
  border: 1px solid rgba(220, 38, 38, 0.16);
}

.state-actions {
  justify-content: flex-end;
  flex-wrap: wrap;
}

.panel-kicker {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.loading-panel {
  min-height: 260px;
}

.skeleton-table {
  display: grid;
  gap: 10px;
}

.skeleton-row {
  height: 46px;
  border-radius: 12px;
  background: linear-gradient(90deg, rgba(226, 232, 240, 0.72), rgba(248, 250, 252, 0.92), rgba(226, 232, 240, 0.72));
  background-size: 220% 100%;
  animation: shimmer 1.35s ease-in-out infinite;
}

.inline-warning {
  margin: 0;
  padding: 12px 14px;
  border: 1px solid rgba(230, 138, 45, 0.2);
  border-radius: 14px;
  background: rgba(255, 247, 237, 0.86);
  color: #9a5a13;
  font-size: 13px;
  line-height: 1.6;
}

.student-workspace {
  display: grid;
  gap: 16px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
}

.student-overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 16px;
  overflow: hidden;
  background: rgba(248, 250, 252, 0.58);
}

.overview-cell {
  min-width: 0;
  padding: 16px 18px;
  display: grid;
  gap: 6px;
}

.overview-cell + .overview-cell {
  border-left: 1px solid rgba(148, 163, 184, 0.14);
}

.overview-value {
  color: var(--teacher-text-primary);
  font-size: 30px;
  font-weight: 850;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.overview-label {
  color: var(--teacher-text-primary);
  font-size: 13px;
  font-weight: 800;
}

.overview-note {
  color: var(--teacher-text-tertiary);
  font-size: 12px;
  line-height: 1.5;
}

.next-step-strip {
  align-items: center;
  padding: 16px 18px;
  border: 1px solid rgba(36, 86, 173, 0.14);
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(244, 248, 255, 0.94), rgba(255, 255, 255, 0.92));
}

.panel-actions {
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.page-size-select {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--teacher-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.page-size-select select {
  width: 96px;
}

.filters {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  flex-wrap: wrap;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--teacher-text-secondary);
  font-size: 13px;
  flex-shrink: 0;
}

.filter-item.keyword-filter {
  flex: 1;
  min-width: 200px;
}

.filter-item label span,
.filter-item > span {
  font-size: 12px;
  font-weight: 500;
}

.keyword-filter {
  min-width: 0;
}

input,
select {
  height: 42px;
  border: 1px solid var(--teacher-border);
  border-radius: var(--teacher-radius-control);
  background: var(--teacher-surface);
  color: var(--teacher-text-primary);
  padding: 0 12px;
  font-family: inherit;
}

.table-wrap {
  overflow-x: auto;
  overflow-y: auto;
  min-height: 0;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.82);
}

table {
  width: 100%;
  border-collapse: collapse;
  min-width: 780px;
}

th,
td {
  padding: 14px 12px;
  border-bottom: 1px solid var(--teacher-divider);
  text-align: left;
  font-size: 14px;
  vertical-align: middle;
}

thead th {
  background: rgba(248, 250, 252, 0.92);
}

th {
  color: var(--teacher-text-tertiary);
  font-weight: 700;
  font-size: 12px;
  letter-spacing: 0.02em;
}

tbody tr:hover {
  background: rgba(36, 86, 173, 0.03);
}

.mono-cell {
  font-variant-numeric: tabular-nums;
  color: var(--teacher-text-secondary);
}

.student-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--teacher-text-primary);
}

.student-link-btn {
  display: grid;
  gap: 4px;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: transform 0.18s ease, color 0.18s ease;
}

.student-link-btn:hover {
  transform: translateY(-1px);
}

.student-link-btn:hover .student-name {
  color: var(--teacher-accent-strong, var(--teacher-accent));
}

.student-id {
  display: block;
  font-size: 12px;
}

.status-badge,
.group-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 72px;
  padding: 5px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.status-badge.active {
  background: rgba(52, 199, 89, 0.12);
  color: #1f7a43;
}

.status-badge.inactive {
  background: rgba(255, 149, 0, 0.12);
  color: #9a5f00;
}

.group-badge {
  background: rgba(36, 86, 173, 0.08);
  color: var(--teacher-accent-strong, var(--teacher-accent));
}

.group-badge.muted {
  background: rgba(148, 163, 184, 0.14);
  color: var(--teacher-text-secondary);
}

.empty-cell {
  padding: 28px 16px;
  text-align: center;
  color: var(--teacher-text-tertiary);
}

.filter-empty {
  display: grid;
  justify-items: center;
  gap: 12px;
}

.filter-empty p {
  margin: 0;
}

.primary-btn,
.secondary-btn,
.ghost-btn,
.danger-btn,
.danger-link-btn,
.icon-close-btn {
  min-height: 40px;
  border-radius: 12px;
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

.primary-btn:hover,
.secondary-btn:hover,
.ghost-btn:hover,
.danger-btn:hover,
.danger-link-btn:hover,
.icon-close-btn:hover {
  transform: translateY(-1px);
}

.secondary-btn,
.icon-close-btn {
  border: 1px solid var(--tt-border);
  background: var(--tt-surface-muted);
  color: var(--tt-text);
}

.primary-btn {
  min-width: 128px;
  border: 1px solid transparent;
  background: var(--tt-accent-gradient);
  color: var(--tt-text-inverse);
  box-shadow: var(--tt-shadow-accent);
}

.ghost-btn {
  border: 1px solid var(--tt-accent-border);
  background: var(--tt-accent-soft);
  color: var(--tt-accent);
  box-shadow: none;
}

.danger-link-btn {
  min-height: 34px;
  border: 1px solid rgba(220, 38, 38, 0.2);
  border-radius: 999px;
  padding: 0 12px;
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

.dialog-mask {
  /* positioning handled by teacher-dialog.css */
}

.detail-dialog-actions {
  justify-content: flex-end;
  padding-top: 4px;
  border-top: 1px solid var(--tt-border-subtle);
  margin-top: 4px;
}

.detail-dialog,
.danger-dialog {
  width: min(560px, 100%);
  display: grid;
  gap: 20px;
  border-radius: 26px;
  border: 1px solid var(--tt-border-subtle);
  background: var(--tt-surface);
  box-shadow: var(--tt-shadow-xl);
}

.dialog-eyebrow {
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
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
  border-radius: 18px;
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

.danger-banner-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--tt-danger);
}

.dialog-summary {
  padding: 16px 18px;
  border-radius: 18px;
  background: var(--tt-surface-muted);
  border: 1px solid var(--tt-border-subtle);
  display: grid;
  gap: 8px;
}

.summary-name {
  font-size: 16px;
  font-weight: 800;
  color: var(--teacher-text-primary);
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-item {
  padding: 16px 18px;
  border-radius: 18px;
  background: var(--tt-surface-muted);
  border: 1px solid var(--tt-border-subtle);
  display: grid;
  gap: 8px;
}

.detail-item-wide {
  grid-column: 1 / -1;
}

.detail-label,
.detail-value {
  margin: 0;
}

.detail-label {
  color: var(--teacher-text-tertiary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.detail-value {
  color: var(--teacher-text-primary);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.5;
}

.primary-btn:disabled,
.secondary-btn:disabled,
.ghost-btn:disabled,
.danger-btn:disabled,
.icon-close-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
  transform: none;
  box-shadow: none;
}

@keyframes shimmer {
  0% {
    background-position: 120% 0;
  }

  100% {
    background-position: -120% 0;
  }
}

@media (max-width: 960px) {
  .filters {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-item {
    min-width: 100%;
  }

  .student-overview {
    grid-template-columns: 1fr;
  }

  .overview-cell + .overview-cell {
    border-left: none;
    border-top: 1px solid rgba(148, 163, 184, 0.14);
  }
}

@media (max-width: 760px) {
  .topbar,
  .header-actions,
  .panel-head,
  .panel-actions,
  .state-panel,
  .state-actions,
  .next-step-strip,
  .pager,
  .dialog-head,
  .dialog-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }

  .detail-item-wide {
    grid-column: auto;
  }
}
</style>
