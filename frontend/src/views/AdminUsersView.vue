<script setup>
import '../styles/admin-workspace.css'
import { computed, onMounted, reactive, ref } from 'vue'
import AdminToolbar from '../components/admin/AdminToolbar.vue'
import {
  createAdminUser,
  deleteAdminUser,
  fetchAdminUsers,
  resetAdminUserPassword,
  restoreAdminUser,
  updateAdminUser,
  updateAdminUserRole,
  updateAdminUserStatus,
} from '../services/admin'
import { formatDateTime, getRoleLabel, getStatusLabel, getStatusTone } from '../utils/admin'

const PASSWORD_RULE_TEXT = '至少 8 位，包含大写字母、小写字母、数字和特殊符号'
const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z\d\s]).{8,64}$/

const loading = ref(false)
const saving = ref(false)
const actionLoadingId = ref(null)
const message = ref('')
const messageType = ref('info')
const users = ref([])
const summaryUsers = ref([])
const editingUser = ref(null)
const isDialogOpen = ref(false)

const filters = reactive({
  keyword: '',
  role: '',
  status: '',
  isDeleted: 0,
})

const userForm = reactive({
  role: 'teacher',
  name: '',
  email: '',
  phone: '',
  password: '',
  status: 1,
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

const isEditing = computed(() => Boolean(editingUser.value))
const isDeletedView = computed(() => Number(filters.isDeleted) === 1)

const filteredUsers = computed(() => {
  const keyword = filters.keyword.trim()
  if (!keyword) return users.value

  return users.value.filter((item) => {
    const fields = [item.name, item.email, item.ceremonyCode, item.ceremonyNo, getRoleLabel(item.role)]
    return fields.some((field) => String(field || '').includes(keyword))
  })
})

const summaryCards = computed(() => {
  const totalCount = summaryUsers.value.length
  const activeCount = summaryUsers.value.filter((item) => Number(item.status) === 1).length
  const teacherCount = summaryUsers.value.filter((item) => item.role === 'teacher').length
  const studentCount = summaryUsers.value.filter((item) => item.role === 'student').length
  const deletedCount = summaryUsers.value.filter((item) => Number(item.isDeleted) === 1).length

  return [
    { title: '账户总数', value: `${totalCount}`, note: `当前筛选共 ${pagination.total}` },
    { title: '启用账户', value: `${activeCount}`, note: '当前筛选统计' },
    { title: '教师 / 学生', value: `${teacherCount} / ${studentCount}`, note: '管理员账号受保护' },
    { title: '已删除', value: `${deletedCount}`, note: isDeletedView.value ? '当前筛选恢复池' : '切换筛选可查看' },
  ]
})

function getUserAccount(user) {
  return user.email || getCeremonyCode(user) || user.name || user.id
}

function getCeremonyCode(user) {
  if (user?.ceremonyCode) return user.ceremonyCode
  const no = Number(user?.ceremonyNo)
  if (!Number.isFinite(no) || no <= 0) return '-'
  return `TT-${String(Math.trunc(no)).padStart(6, '0')}`
}

function isAdminUser(user) {
  return user.role === 'admin'
}

function canManageUser(user) {
  return !isAdminUser(user) && Number(user.isDeleted) !== 1
}

function canDeleteUser(user) {
  return (user.role === 'teacher' || user.role === 'student') && Number(user.isDeleted) !== 1
}

function canRestoreUser(user) {
  return (user.role === 'teacher' || user.role === 'student') && Number(user.isDeleted) === 1
}

function getUserActionNote(user) {
  if (isAdminUser(user)) return '管理员账户受保护：不可新增同级账号，不可编辑、禁用或删除。'
  if (Number(user.isDeleted) === 1) return '账户已软删除，历史数据保留；恢复后默认禁用，需要再手动启用。'
  if (user.role === 'teacher') return user.teacherInviteCode ? `教师邀请码：${user.teacherInviteCode}` : '教师账号；可改为学生，解绑并删除邀请码。'
  if (user.role === 'student') return '可编辑、启停、重置密码、软删除；班级和小组仍走原业务流程。'
  return '当前角色可执行的操作以后台接口为准。'
}

function resetForm() {
  editingUser.value = null
  userForm.role = 'teacher'
  userForm.name = ''
  userForm.email = ''
  userForm.phone = ''
  userForm.password = ''
  userForm.status = 1
}

function openCreateDialog() {
  resetForm()
  isDialogOpen.value = true
}

function openEditDialog(user) {
  if (!canManageUser(user)) return
  editingUser.value = user
  userForm.role = user.role
  userForm.name = user.name || ''
  userForm.email = user.email || ''
  userForm.phone = user.phone || ''
  userForm.password = ''
  userForm.status = Number(user.status) === 1 ? 1 : 0
  isDialogOpen.value = true
}

function closeDialog() {
  if (saving.value) return
  isDialogOpen.value = false
  resetForm()
}

function buildUserPayload() {
  return {
    role: userForm.role,
    name: userForm.name.trim(),
    email: userForm.email.trim(),
    phone: userForm.phone.trim(),
    password: userForm.password,
    status: Number(userForm.status),
  }
}

function validateUserForm() {
  if (!userForm.name.trim()) return '请输入姓名。'
  if (!userForm.email.trim()) return '请输入邮箱。'
  if (userForm.phone.trim() && !/^1\d{10}$/.test(userForm.phone.trim())) return '手机号需为 11 位中国大陆手机号。'
  if (!isEditing.value && !PASSWORD_PATTERN.test(userForm.password)) return `初始密码需满足：${PASSWORD_RULE_TEXT}。`
  return ''
}

async function loadUsers(options = {}) {
  const shouldClearMessage = options.shouldClearMessage !== false
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.size,
      isDeleted: Number(filters.isDeleted),
    }
    if (filters.role) params.role = filters.role
    if (filters.status !== '') params.status = Number(filters.status)

    const { data } = await fetchAdminUsers(params)
    const payload = data?.data || {}
    users.value = payload.list || []
    pagination.total = Number(payload.total) || 0
    pagination.pages = Number(payload.pages) || 0
    pagination.hasNext = Boolean(payload.hasNext)
    if (shouldClearMessage) setMessage('')
  } catch (error) {
    users.value = []
    pagination.total = 0
    pagination.pages = 0
    pagination.hasNext = false
    setMessage(getErrorMessage(error, '加载账户列表失败'), 'error')
  } finally {
    loading.value = false
  }
}

function buildSummaryParams(page) {
  const params = {
    page,
    size: 100,
    isDeleted: Number(filters.isDeleted),
  }
  if (filters.role) params.role = filters.role
  if (filters.status !== '') params.status = Number(filters.status)
  return params
}

async function loadSummaryUsers() {
  const firstRes = await fetchAdminUsers(buildSummaryParams(1))
  const firstPayload = firstRes?.data?.data || {}
  const pages = Math.max(1, Number(firstPayload.pages) || 1)
  const list = Array.isArray(firstPayload.list) ? [...firstPayload.list] : []

  for (let page = 2; page <= pages; page += 1) {
    const { data } = await fetchAdminUsers(buildSummaryParams(page))
    const payload = data?.data || {}
    if (Array.isArray(payload.list)) {
      list.push(...payload.list)
    }
  }

  summaryUsers.value = list
}

async function refreshUsers(options = {}) {
  await loadUsers(options)
  try {
    await loadSummaryUsers()
  } catch (error) {
    setMessage(getErrorMessage(error, '加载账户统计失败'), 'error')
  }
}

async function queryUsers() {
  pagination.page = 1
  await refreshUsers()
}

async function resetFilters() {
  filters.keyword = ''
  filters.role = ''
  filters.status = ''
  filters.isDeleted = 0
  pagination.page = 1
  await refreshUsers()
}

async function prevPage() {
  if (pagination.page <= 1 || loading.value) return
  pagination.page -= 1
  await loadUsers()
}

async function nextPage() {
  if (!pagination.hasNext || loading.value) return
  pagination.page += 1
  await loadUsers()
}

async function changePageSize() {
  pagination.page = 1
  await loadUsers()
}

async function submitUserForm() {
  const validationMessage = validateUserForm()
  if (validationMessage) {
    setMessage(validationMessage, 'warn')
    return
  }

  saving.value = true
  try {
    const payload = buildUserPayload()
    if (isEditing.value) {
      await updateAdminUser(editingUser.value.id, {
        name: payload.name,
        email: payload.email,
        phone: payload.phone,
        status: payload.status,
      })
      setMessage('账户信息已保存。', 'success')
    } else {
      await createAdminUser(payload)
      setMessage('账户已新增。', 'success')
      pagination.page = 1
    }
    isDialogOpen.value = false
    resetForm()
    await refreshUsers({ shouldClearMessage: false })
  } catch (error) {
    setMessage(getErrorMessage(error, isEditing.value ? '保存账户失败' : '新增账户失败'), 'error')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(user) {
  if (!canManageUser(user)) return
  const nextStatus = Number(user.status) === 1 ? 0 : 1
  const actionText = nextStatus === 1 ? '启用' : '禁用'
  const account = getUserAccount(user)
  if (!window.confirm(`确认${actionText}账户 ${account} 吗？`)) return

  actionLoadingId.value = user.id
  try {
    await updateAdminUserStatus(user.id, nextStatus)
    setMessage(`账户已${actionText}`, 'success')
    await refreshUsers({ shouldClearMessage: false })
  } catch (error) {
    setMessage(getErrorMessage(error, `账户${actionText}失败`), 'error')
  } finally {
    actionLoadingId.value = null
  }
}

async function changeUserRole(user) {
  if (!canManageUser(user)) return
  const account = getUserAccount(user)
  const currentRole = user.role
  const nextRole = currentRole === 'student' ? 'teacher' : currentRole === 'teacher' ? 'student' : ''
  if (!nextRole) return

  if (nextRole === 'teacher') {
    const inviteCode = window.prompt(`将学生账户 ${account} 修改为教师。请输入有效、未使用、未过期的教师邀请码：`, '')
    if (inviteCode === null) return
    const normalizedCode = inviteCode.trim()
    if (!normalizedCode) {
      setMessage('学生改为教师必须填写教师邀请码。', 'warn')
      return
    }
    if (!window.confirm(`确认将 ${account} 修改为教师并消耗邀请码 ${normalizedCode} 吗？`)) return
    actionLoadingId.value = user.id
    try {
      await updateAdminUserRole(user.id, { role: 'teacher', inviteCode: normalizedCode })
      setMessage('账户角色已修改为教师。', 'success')
      await refreshUsers({ shouldClearMessage: false })
    } catch (error) {
      setMessage(getErrorMessage(error, '修改账户角色失败'), 'error')
    } finally {
      actionLoadingId.value = null
    }
    return
  }

  if (!window.confirm(`确认将教师账户 ${account} 修改为学生吗？该教师已绑定的邀请码会被解绑并删除，不再复用。`)) return
  actionLoadingId.value = user.id
  try {
    await updateAdminUserRole(user.id, { role: 'student' })
    setMessage('账户角色已修改为学生，原教师邀请码已删除。', 'success')
    await refreshUsers({ shouldClearMessage: false })
  } catch (error) {
    setMessage(getErrorMessage(error, '修改账户角色失败'), 'error')
  } finally {
    actionLoadingId.value = null
  }
}

async function resetPassword(user) {
  if (Number(user.isDeleted) === 1) return
  const account = getUserAccount(user)
  const customPassword = window.prompt(
    `为 ${account} 重置密码。留空则使用系统统一临时密码；输入内容则使用你填写的新密码。`,
    '',
  )
  if (customPassword === null) return

  actionLoadingId.value = user.id
  try {
    const payload = customPassword.trim()
    await resetAdminUserPassword(user.id, payload || undefined)
    setMessage(payload ? '密码已重置为指定密码。' : '密码已重置为系统统一临时密码。', 'success')
  } catch (error) {
    setMessage(getErrorMessage(error, '重置密码失败'), 'error')
  } finally {
    actionLoadingId.value = null
  }
}

async function removeUser(user) {
  if (!canDeleteUser(user)) return
  const account = getUserAccount(user)
  const roleLabel = getRoleLabel(user.role)
  if (!window.confirm(`确认删除${roleLabel}账户 ${account} 吗？删除后历史数据保留，可在“已删除”筛选中恢复。`)) return

  actionLoadingId.value = user.id
  try {
    const { data } = await deleteAdminUser(user.id)
    const removedInviteCodes = data?.data?.removedInviteCodes || []
    setMessage(
      removedInviteCodes.length
        ? `账户已删除，已删除绑定邀请码 ${removedInviteCodes.join('、')}。`
        : '账户已删除。',
      'success',
    )
    await refreshUsers({ shouldClearMessage: false })
  } catch (error) {
    setMessage(getErrorMessage(error, '删除账户失败'), 'error')
  } finally {
    actionLoadingId.value = null
  }
}

async function restoreUser(user) {
  if (!canRestoreUser(user)) return
  const account = getUserAccount(user)
  if (!window.confirm(`确认恢复账户 ${account} 吗？恢复后账户默认禁用，需要手动启用。`)) return

  actionLoadingId.value = user.id
  try {
    await restoreAdminUser(user.id)
    setMessage('账户已恢复，当前为禁用状态。', 'success')
    await refreshUsers({ shouldClearMessage: false })
  } catch (error) {
    setMessage(getErrorMessage(error, '恢复账户失败'), 'error')
  } finally {
    actionLoadingId.value = null
  }
}

onMounted(refreshUsers)
</script>

<template>
  <div class="admin-page users-page">
    <section class="admin-stats-grid">
      <article v-for="item in summaryCards" :key="item.title" class="admin-stat-card">
        <p class="admin-stat-card__label">{{ item.title }}</p>
        <p class="admin-stat-card__value">{{ item.value }}</p>
        <p class="admin-stat-card__note">{{ item.note }}</p>
      </article>
    </section>

    <AdminToolbar>
      <template #label>账户筛选</template>
      <template #description>管理员可新增教师/学生，编辑账号资料，软删除并恢复账号；管理员账号受保护。</template>
      <div class="admin-filter-row users-filter-row">
        <input v-model.trim="filters.keyword" type="text" placeholder="当前页内搜索姓名、邮箱、仪式编号" />
        <select v-model="filters.role">
          <option value="">全部角色</option>
          <option value="admin">管理员</option>
          <option value="teacher">教师</option>
          <option value="student">学生</option>
        </select>
        <select v-model="filters.status">
          <option value="">全部状态</option>
          <option :value="1">启用</option>
          <option :value="0">禁用</option>
        </select>
        <select v-model.number="filters.isDeleted" @change="queryUsers">
          <option :value="0">正常账户</option>
          <option :value="1">已删除账户</option>
        </select>
        <select v-model.number="pagination.size" @change="changePageSize">
          <option :value="10">10 条/页</option>
          <option :value="20">20 条/页</option>
          <option :value="50">50 条/页</option>
        </select>
        <button type="button" class="admin-btn" :disabled="loading" @click="queryUsers">
          {{ loading ? '查询中...' : '查询' }}
        </button>
        <button type="button" class="admin-btn-secondary" :disabled="loading" @click="resetFilters">重置</button>
      </div>
      <p v-if="message" class="admin-message" :class="messageType">{{ message }}</p>
    </AdminToolbar>

    <section class="admin-panel">
      <div class="admin-panel__head users-panel-head">
        <div>
          <p class="admin-section-label">Users</p>
          <h3 class="admin-panel__title">账户列表</h3>
          <p class="admin-panel__desc">
            新增教师和学生只是创建登录账号，不会自动加入班级、小组。学生升为教师必须填写有效邀请码。
          </p>
        </div>
        <div class="head-actions">
          <button type="button" class="admin-btn-secondary" :disabled="loading" @click="refreshUsers">
            {{ loading ? '刷新中...' : '刷新列表' }}
          </button>
          <button type="button" class="admin-btn" @click="openCreateDialog">新增账户</button>
        </div>
      </div>

      <div class="admin-table-wrap">
        <table class="admin-table users-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>姓名</th>
              <th>角色</th>
              <th>邮箱</th>
              <th>仪式编号</th>
              <th>状态</th>
              <th>删除</th>
              <th>创建时间</th>
              <th>管理范围</th>
              <th class="sticky-action-col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredUsers" :key="item.id">
              <td>{{ item.id }}</td>
              <td>{{ item.name || '-' }}</td>
              <td>{{ getRoleLabel(item.role) }}</td>
              <td>{{ item.email || '-' }}</td>
              <td>{{ getCeremonyCode(item) }}</td>
              <td>
                <span class="admin-status-badge" :class="getStatusTone(item.status)">
                  {{ getStatusLabel(item.status) }}
                </span>
              </td>
              <td>
                <span class="admin-status-badge" :class="Number(item.isDeleted) === 1 ? 'danger' : 'neutral'">
                  {{ Number(item.isDeleted) === 1 ? '已删除' : '正常' }}
                </span>
              </td>
              <td>{{ formatDateTime(item.createdAt) }}</td>
              <td class="action-note">{{ getUserActionNote(item) }}</td>
              <td class="sticky-action-col">
                <div class="user-actions">
                  <button
                    type="button"
                    class="admin-link-btn"
                    :disabled="!canManageUser(item) || actionLoadingId === item.id"
                    @click="openEditDialog(item)"
                  >
                    编辑
                  </button>
                  <button
                    type="button"
                    class="admin-link-btn"
                    :disabled="Number(item.isDeleted) === 1 || actionLoadingId === item.id"
                    @click="resetPassword(item)"
                  >
                    重置密码
                  </button>
                  <button
                    type="button"
                    class="admin-link-btn"
                    :disabled="!canManageUser(item) || actionLoadingId === item.id"
                    @click="toggleStatus(item)"
                  >
                    {{ Number(item.status) === 1 ? '禁用' : '启用' }}
                  </button>
                  <button
                    type="button"
                    class="admin-link-btn"
                    :disabled="!canManageUser(item) || actionLoadingId === item.id"
                    @click="changeUserRole(item)"
                  >
                    {{ item.role === 'student' ? '改教师' : '改学生' }}
                  </button>
                  <button
                    v-if="canDeleteUser(item)"
                    type="button"
                    class="admin-link-btn danger"
                    :disabled="actionLoadingId === item.id"
                    @click="removeUser(item)"
                  >
                    删除
                  </button>
                  <button
                    v-else-if="canRestoreUser(item)"
                    type="button"
                    class="admin-link-btn"
                    :disabled="actionLoadingId === item.id"
                    @click="restoreUser(item)"
                  >
                    恢复
                  </button>
                  <span v-else class="muted-action">受保护</span>
                </div>
              </td>
            </tr>
            <tr v-if="!filteredUsers.length">
              <td colspan="10" class="empty">当前筛选条件下没有账户记录。</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="admin-pager">
        <button type="button" class="admin-btn-secondary" :disabled="pagination.page <= 1 || loading" @click="prevPage">
          上一页
        </button>
        <span>第 {{ pagination.page }} / {{ pagination.pages || 1 }} 页，共 {{ pagination.total }} 条</span>
        <button type="button" class="admin-btn-secondary" :disabled="!pagination.hasNext || loading" @click="nextPage">
          下一页
        </button>
      </div>
    </section>

    <div v-if="isDialogOpen" class="dialog-mask" @click.self="closeDialog">
      <section class="user-dialog" role="dialog" aria-modal="true" aria-label="账户表单">
        <header class="dialog-head">
          <div>
            <p class="admin-section-label">{{ isEditing ? 'Edit user' : 'Create user' }}</p>
            <h3>{{ isEditing ? '编辑账户' : '新增账户' }}</h3>
          </div>
          <button type="button" class="dialog-close" :disabled="saving" @click="closeDialog">×</button>
        </header>

        <div class="dialog-grid">
          <label class="admin-field">
            <span>角色</span>
            <select v-model="userForm.role" :disabled="isEditing">
              <option value="teacher">教师</option>
              <option value="student">学生</option>
            </select>
          </label>
          <label class="admin-field">
            <span>状态</span>
            <select v-model.number="userForm.status">
              <option :value="1">启用</option>
              <option :value="0">禁用</option>
            </select>
          </label>
          <label class="admin-field">
            <span>姓名</span>
            <input v-model.trim="userForm.name" type="text" maxlength="50" placeholder="建议使用真实姓名" />
          </label>
          <label class="admin-field">
            <span>邮箱</span>
            <input v-model.trim="userForm.email" type="email" placeholder="用于登录" />
          </label>
          <label class="admin-field">
            <span>手机号</span>
            <input v-model.trim="userForm.phone" type="tel" placeholder="可选" />
          </label>
          <label v-if="!isEditing" class="admin-field">
            <span>初始密码</span>
            <input v-model="userForm.password" type="password" :placeholder="PASSWORD_RULE_TEXT" />
          </label>
        </div>

        <p class="dialog-note">
          管理员端暂不新增管理员账号。新增教师不消耗邀请码；已有学生升为教师时需要单独执行“改教师”并填写邀请码。
        </p>

        <footer class="dialog-actions">
          <button type="button" class="admin-btn-secondary" :disabled="saving" @click="closeDialog">取消</button>
          <button type="button" class="admin-btn" :disabled="saving" @click="submitUserForm">
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </footer>
      </section>
    </div>
  </div>
</template>

<style scoped>
.users-filter-row {
  width: 100%;
}

.users-panel-head,
.head-actions,
.dialog-actions {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.head-actions,
.dialog-actions {
  align-items: center;
  flex-wrap: wrap;
}

.users-table {
  min-width: 1260px;
  table-layout: fixed;
}

.users-table th:nth-child(1),
.users-table td:nth-child(1) {
  width: 64px;
}

.users-table th:nth-child(2),
.users-table td:nth-child(2) {
  width: 92px;
}

.users-table th:nth-child(3),
.users-table td:nth-child(3) {
  width: 76px;
}

.users-table th:nth-child(4),
.users-table td:nth-child(4) {
  width: 230px;
}

.users-table th:nth-child(5),
.users-table td:nth-child(5) {
  width: 118px;
}

.users-table th:nth-child(6),
.users-table td:nth-child(6),
.users-table th:nth-child(7),
.users-table td:nth-child(7) {
  width: 92px;
}

.users-table th:nth-child(8),
.users-table td:nth-child(8) {
  width: 150px;
}

.users-table th:nth-child(9),
.users-table td:nth-child(9) {
  width: 220px;
}

.action-note {
  max-width: 220px;
  color: var(--admin-text-secondary);
  font-size: 12px;
  line-height: 1.55;
}

.sticky-action-col {
  position: sticky;
  right: 0;
  z-index: 2;
  width: 190px;
  min-width: 190px;
  background: var(--admin-surface);
  box-shadow: -10px 0 18px color-mix(in oklab, var(--admin-surface) 80%, transparent);
}

th.sticky-action-col {
  z-index: 3;
}

.user-actions {
  display: grid;
  grid-template-columns: repeat(3, minmax(48px, max-content));
  align-items: center;
  justify-content: end;
  gap: 8px 10px;
  min-width: 128px;
}

.user-actions .admin-link-btn,
.user-actions .muted-action {
  white-space: nowrap;
}

.muted-action {
  color: var(--admin-text-tertiary);
  font-size: 12px;
  font-weight: 700;
}

.danger {
  color: var(--admin-danger);
}

.empty {
  text-align: center;
  color: var(--admin-text-secondary);
  padding: 30px 12px;
}

.dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: grid;
  place-items: center;
  padding: 24px;
  background: color-mix(in oklab, var(--admin-text-primary) 28%, transparent);
}

.user-dialog {
  width: min(720px, 100%);
  max-height: calc(100vh - 48px);
  overflow: auto;
  padding: 22px;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  background: var(--admin-surface);
  box-shadow: var(--admin-shadow);
}

.dialog-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.dialog-head h3 {
  margin: 6px 0 0;
  color: var(--admin-text-primary);
  font-size: 22px;
}

.dialog-close {
  width: 34px;
  height: 34px;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-control);
  background: var(--admin-btn-secondary-bg);
  color: var(--admin-text-secondary);
  cursor: pointer;
  font-size: 20px;
  line-height: 1;
}

.dialog-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.dialog-note {
  margin: 16px 0 0;
  color: var(--admin-text-secondary);
  line-height: 1.6;
}

.dialog-actions {
  margin-top: 18px;
  justify-content: flex-end;
}

@media (max-width: 760px) {
  .users-panel-head {
    flex-direction: column;
  }

  .dialog-grid {
    grid-template-columns: 1fr;
  }
}
</style>
