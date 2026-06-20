<script setup>
import '../styles/admin-workspace.css'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  fetchAdminUsers,
  fetchWelcomeEmailSummary,
  resendPendingWelcomeEmails,
  resendSelectedWelcomeEmails,
  sendAdminEmail,
} from '../services/admin'

const EMAIL_PATTERN = /^[\w.+-]+@(qq\.com|foxmail\.com|163\.com|126\.com|yeah\.net|188\.com|sina\.com|sina\.cn|sohu\.com|139\.com|gmail\.com|outlook\.com|hotmail\.com|live\.cn|live\.com|icloud\.com|([\w-]+\.)+edu\.cn)$/i
const MAX_SUBJECT_LENGTH = 120
const MAX_BODY_LENGTH = 5000
const MAX_RECIPIENTS = 50
const USER_PICKER_PAGE_SIZE = 8

const loadingEstimate = ref(false)
const sending = ref(false)
const welcomeLoading = ref(false)
const welcomeSending = ref(false)
const message = ref('')
const messageType = ref('info')
const sendResult = ref(null)
const welcomeResult = ref(null)
const userPickerLoading = ref(false)
const selectedUserMap = reactive({})

const welcomeSummary = reactive({
  pendingCount: 0,
  batchLimit: 30,
})

const userPicker = reactive({
  role: 'all',
  keyword: '',
  page: 1,
  total: 0,
  pages: 1,
  list: [],
})

const form = reactive({
  recipientScope: 'teacher',
  manualEmailsText: '',
  subject: '',
  body: '',
})

const estimates = reactive({
  teacher: 0,
  student: 0,
})

const scopeOptions = [
  { value: 'teacher', label: '全部教师', note: '启用且未删除的教师账号' },
  { value: 'student', label: '全部学生', note: '启用且未删除的学生账号' },
  { value: 'all', label: '全部教师和学生', note: '不包含管理员账号' },
  { value: 'selected', label: '已选邮箱', note: '使用右侧勾选的邮箱列表' },
  { value: 'manual', label: '手动邮箱', note: '逐行或用逗号分隔填写邮箱' },
]

const manualEmails = computed(() => {
  if (!form.manualEmailsText.trim()) return []
  return Array.from(new Set(
    form.manualEmailsText
      .split(/[\s,;，；]+/)
      .map((item) => item.trim().toLowerCase())
      .filter(Boolean),
  ))
})

const invalidManualEmails = computed(() => manualEmails.value.filter((email) => !EMAIL_PATTERN.test(email)))
const selectedUsers = computed(() => Object.values(selectedUserMap))
const selectedEmails = computed(() => Array.from(new Set(
  selectedUsers.value
    .map((item) => normalizeUserEmail(item))
    .filter((email) => EMAIL_PATTERN.test(email)),
)))
const selectedWelcomeUserIds = computed(() => selectedUsers.value
  .filter((item) => canSendWelcomeToUser(item))
  .map((item) => Number(item.id)))
const selectedWelcomeLimitExceeded = computed(() => selectedWelcomeUserIds.value.length > welcomeSummary.batchLimit)

const estimatedRecipients = computed(() => {
  if (form.recipientScope === 'teacher') return estimates.teacher
  if (form.recipientScope === 'student') return estimates.student
  if (form.recipientScope === 'all') return estimates.teacher + estimates.student
  if (form.recipientScope === 'selected') return selectedEmails.value.length
  return manualEmails.value.length - invalidManualEmails.value.length
})

const selectedScope = computed(() => scopeOptions.find((item) => item.value === form.recipientScope) || scopeOptions[0])
const exceedsRecipientLimit = computed(() => estimatedRecipients.value > MAX_RECIPIENTS)
const subjectCount = computed(() => form.subject.trim().length)
const bodyCount = computed(() => form.body.trim().length)
const canSend = computed(() => {
  if (sending.value) return false
  if (exceedsRecipientLimit.value) return false
  if (!form.recipientScope) return false
  if (!form.subject.trim() || !form.body.trim()) return false
  if (subjectCount.value > MAX_SUBJECT_LENGTH || bodyCount.value > MAX_BODY_LENGTH) return false
  if (form.recipientScope === 'manual') return manualEmails.value.length > 0 && invalidManualEmails.value.length === 0
  if (form.recipientScope === 'selected') return selectedEmails.value.length > 0
  return estimatedRecipients.value > 0
})

const summaryCards = computed(() => [
  { title: '教师收件人', value: String(estimates.teacher), note: '启用且未删除' },
  { title: '学生收件人', value: String(estimates.student), note: '启用且未删除' },
  { title: '当前范围', value: String(estimatedRecipients.value), note: selectedScope.value.label },
])

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

function getErrorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || fallback
}

function normalizeUserEmail(user) {
  return String(user?.email || '').trim().toLowerCase()
}

function isManagedUser(user) {
  return user?.role === 'teacher' || user?.role === 'student'
}

function canSelectUser(user) {
  return isManagedUser(user)
    && Number(user?.status) === 1
    && Number(user?.isDeleted) === 0
    && EMAIL_PATTERN.test(normalizeUserEmail(user))
}

function canSendWelcomeToUser(user) {
  return canSelectUser(user) && !user?.welcomeEmailSentAt
}

function userStatusText(user) {
  if (!isManagedUser(user)) return '非教师/学生'
  if (Number(user?.isDeleted) === 1) return '已删除'
  if (Number(user?.status) !== 1) return '已停用'
  if (!EMAIL_PATTERN.test(normalizeUserEmail(user))) return '无有效邮箱'
  return user?.welcomeEmailSentAt ? '欢迎邮件已发送' : '待欢迎邮件'
}

function userRoleText(role) {
  if (role === 'teacher') return '教师'
  if (role === 'student') return '学生'
  return '其他'
}

function isUserSelected(user) {
  return !!selectedUserMap[String(user?.id)]
}

function toggleSelectedUser(user) {
  if (!canSelectUser(user)) return
  const key = String(user.id)
  if (selectedUserMap[key]) {
    delete selectedUserMap[key]
    return
  }
  selectedUserMap[key] = {
    id: user.id,
    role: user.role,
    name: user.name,
    email: user.email,
    status: user.status,
    isDeleted: user.isDeleted,
    welcomeEmailSentAt: user.welcomeEmailSentAt,
    welcomeEmailLastError: user.welcomeEmailLastError,
  }
}

function clearSelectedUsers() {
  Object.keys(selectedUserMap).forEach((key) => {
    delete selectedUserMap[key]
  })
}

async function loadEstimate() {
  loadingEstimate.value = true
  try {
    const [teacherRes, studentRes] = await Promise.all([
      fetchAdminUsers({ page: 1, size: 1, role: 'teacher', status: 1, isDeleted: 0 }),
      fetchAdminUsers({ page: 1, size: 1, role: 'student', status: 1, isDeleted: 0 }),
    ])
    estimates.teacher = Number(teacherRes?.data?.data?.total) || 0
    estimates.student = Number(studentRes?.data?.data?.total) || 0
  } catch (error) {
    estimates.teacher = 0
    estimates.student = 0
    setMessage(getErrorMessage(error, '加载收件人数量失败'), 'error')
  } finally {
    loadingEstimate.value = false
  }
}

async function loadWelcomeSummary() {
  welcomeLoading.value = true
  try {
    const { data } = await fetchWelcomeEmailSummary()
    welcomeSummary.pendingCount = Number(data?.data?.pendingCount) || 0
    welcomeSummary.batchLimit = Number(data?.data?.batchLimit) || 30
  } catch (error) {
    setMessage(getErrorMessage(error, '加载欢迎邮件统计失败'), 'error')
  } finally {
    welcomeLoading.value = false
  }
}

async function loadUserPicker(page = userPicker.page) {
  userPickerLoading.value = true
  try {
    const keyword = userPicker.keyword.trim()
    const params = {
      page,
      size: USER_PICKER_PAGE_SIZE,
      status: 1,
      isDeleted: 0,
    }
    if (userPicker.role !== 'all') params.role = userPicker.role
    if (keyword) {
      if (keyword.includes('@')) {
        params.email = keyword
      } else {
        params.name = keyword
      }
    }
    const { data } = await fetchAdminUsers(params)
    const payload = data?.data || {}
    userPicker.list = Array.isArray(payload.list) ? payload.list : []
    userPicker.list.forEach((item) => {
      const key = String(item?.id)
      if (selectedUserMap[key]) {
        selectedUserMap[key] = { ...selectedUserMap[key], ...item }
      }
    })
    userPicker.page = Number(payload.page) || page
    userPicker.total = Number(payload.total) || 0
    userPicker.pages = Math.max(1, Number(payload.pages) || 1)
  } catch (error) {
    userPicker.list = []
    setMessage(getErrorMessage(error, '加载邮箱选择列表失败'), 'error')
  } finally {
    userPickerLoading.value = false
  }
}

function resetForm() {
  form.recipientScope = 'teacher'
  form.manualEmailsText = ''
  form.subject = ''
  form.body = ''
  sendResult.value = null
  setMessage('')
}

async function submitEmail() {
  if (!canSend.value) {
    setMessage(exceedsRecipientLimit.value ? `单次最多发送 ${MAX_RECIPIENTS} 封，请缩小收件范围。` : '请检查收件范围、标题和正文。', 'error')
    return
  }

  const scopeLabel = selectedScope.value.label
  if (!window.confirm(`确认向「${scopeLabel}」发送这封邮件吗？预计 ${estimatedRecipients.value} 位收件人。`)) return

  sending.value = true
  sendResult.value = null
  try {
    const payload = {
      recipientScope: form.recipientScope,
      subject: form.subject.trim(),
      body: form.body.trim(),
    }
    if (form.recipientScope === 'manual') payload.manualEmails = manualEmails.value
    if (form.recipientScope === 'selected') {
      payload.recipientScope = 'manual'
      payload.manualEmails = selectedEmails.value
    }
    const { data } = await sendAdminEmail(payload)
    sendResult.value = data?.data || null
    const sent = Number(sendResult.value?.sentCount) || 0
    const failed = Number(sendResult.value?.failedCount) || 0
    setMessage(`发送完成：成功 ${sent} 封，失败 ${failed} 封。`, failed > 0 ? 'warn' : 'success')
    await loadEstimate()
  } catch (error) {
    setMessage(getErrorMessage(error, '发送邮件失败'), 'error')
  } finally {
    sending.value = false
  }
}

async function submitWelcomeResend() {
  if (welcomeSending.value || welcomeSummary.pendingCount <= 0) return
  if (!window.confirm(`确认补发未发送的欢迎邮件吗？本次最多发送 ${welcomeSummary.batchLimit} 封。`)) return

  welcomeSending.value = true
  welcomeResult.value = null
  try {
    const { data } = await resendPendingWelcomeEmails()
    welcomeResult.value = data?.data || null
    welcomeSummary.pendingCount = Number(welcomeResult.value?.remainingCount) || 0
    const sent = Number(welcomeResult.value?.sentCount) || 0
    const failed = Number(welcomeResult.value?.failedCount) || 0
    setMessage(`欢迎邮件补发完成：成功 ${sent} 封，失败 ${failed} 封。`, failed > 0 ? 'warn' : 'success')
  } catch (error) {
    setMessage(getErrorMessage(error, '补发欢迎邮件失败'), 'error')
  } finally {
    welcomeSending.value = false
    await loadWelcomeSummary()
  }
}

async function submitSelectedWelcomeResend() {
  if (welcomeSending.value || selectedWelcomeUserIds.value.length <= 0) return
  if (!window.confirm(`确认给已选的 ${selectedWelcomeUserIds.value.length} 位用户补发欢迎邮件吗？`)) return

  welcomeSending.value = true
  welcomeResult.value = null
  try {
    const { data } = await resendSelectedWelcomeEmails(selectedWelcomeUserIds.value)
    welcomeResult.value = data?.data || null
    welcomeSummary.pendingCount = Number(welcomeResult.value?.remainingCount) || 0
    const sent = Number(welcomeResult.value?.sentCount) || 0
    const failed = Number(welcomeResult.value?.failedCount) || 0
    setMessage(`已选欢迎邮件补发完成：成功 ${sent} 封，失败 ${failed} 封。`, failed > 0 ? 'warn' : 'success')
    await loadUserPicker()
  } catch (error) {
    setMessage(getErrorMessage(error, '补发已选欢迎邮件失败'), 'error')
  } finally {
    welcomeSending.value = false
    await loadWelcomeSummary()
  }
}

watch(() => form.recipientScope, () => {
  sendResult.value = null
  setMessage('')
})

onMounted(() => {
  loadEstimate()
  loadWelcomeSummary()
  loadUserPicker()
})
</script>

<template>
  <section class="admin-page email-page">
    <section class="admin-hero email-hero">
      <div class="admin-hero__meta">
        <span class="admin-chip">Email Center</span>
        <p class="admin-eyebrow">ADMIN BROADCAST</p>
      </div>
      <h2 class="admin-hero__title">管理员邮件通知</h2>
      <p class="admin-hero__desc">向系统内教师、学生或指定邮箱发送一次性通知。当前版本不保存邮件历史，单次最多发送 {{ MAX_RECIPIENTS }} 封。</p>
      <div class="admin-actions">
        <button class="admin-btn-secondary" type="button" :disabled="loadingEstimate" @click="loadEstimate">
          {{ loadingEstimate ? '刷新中...' : '刷新收件人数' }}
        </button>
        <button class="admin-btn-ghost" type="button" :disabled="sending" @click="resetForm">清空表单</button>
      </div>
    </section>

    <section class="admin-stats-grid">
      <article v-for="item in summaryCards" :key="item.title" class="admin-stat-card">
        <p class="admin-stat-card__label">{{ item.title }}</p>
        <p class="admin-stat-card__value">{{ item.value }}</p>
        <p class="admin-stat-card__note">{{ item.note }}</p>
      </article>
    </section>

    <p v-if="message" class="admin-message" :class="messageType">{{ message }}</p>
    <p v-if="exceedsRecipientLimit" class="admin-message warn">当前范围预计 {{ estimatedRecipients }} 位收件人，超过单次上限 {{ MAX_RECIPIENTS }} 封，请改用手动邮箱或缩小范围。</p>

    <section class="admin-panel recipient-picker-panel">
      <div class="recipient-picker-head">
        <div>
          <p class="admin-section-label">PICKER</p>
          <h3 class="admin-panel__title">选择邮箱</h3>
        </div>
        <button class="mini-refresh-btn" type="button" :disabled="userPickerLoading" @click="loadUserPicker(1)">
          {{ userPickerLoading ? '加载中' : '查询' }}
        </button>
      </div>

      <div class="recipient-picker-strip">
        <div class="recipient-picker-tools">
          <select v-model="userPicker.role" @change="loadUserPicker(1)">
            <option value="all">教师和学生</option>
            <option value="teacher">仅教师</option>
            <option value="student">仅学生</option>
          </select>
          <input
            v-model="userPicker.keyword"
            placeholder="搜索姓名或邮箱"
            @keyup.enter="loadUserPicker(1)"
          />
        </div>

        <div class="selected-summary">
          <span>已选 {{ selectedUsers.length }} 人</span>
          <span>有效邮箱 {{ selectedEmails.length }}</span>
          <span>可补发 {{ selectedWelcomeUserIds.length }}</span>
          <button type="button" :disabled="selectedUsers.length === 0" @click="clearSelectedUsers">清空</button>
        </div>

        <div class="picker-pager">
          <button type="button" :disabled="userPicker.page <= 1 || userPickerLoading" @click="loadUserPicker(userPicker.page - 1)">上一页</button>
          <span>{{ userPicker.page }} / {{ userPicker.pages }}</span>
          <button type="button" :disabled="userPicker.page >= userPicker.pages || userPickerLoading" @click="loadUserPicker(userPicker.page + 1)">下一页</button>
        </div>
      </div>

      <div class="recipient-list" :class="{ loading: userPickerLoading }">
        <button
          v-for="user in userPicker.list"
          :key="user.id"
          class="recipient-row"
          :class="{ selected: isUserSelected(user), disabled: !canSelectUser(user) }"
          type="button"
          :disabled="!canSelectUser(user)"
          @click="toggleSelectedUser(user)"
        >
          <span class="recipient-check">{{ isUserSelected(user) ? '✓' : '' }}</span>
          <span class="recipient-main">
            <strong>{{ user.name || '未命名用户' }}</strong>
            <small>{{ normalizeUserEmail(user) || '未绑定邮箱' }}</small>
          </span>
          <span class="recipient-meta">
            <em>{{ userRoleText(user.role) }}</em>
            <small>{{ userStatusText(user) }}</small>
          </span>
        </button>
        <p v-if="!userPickerLoading && userPicker.list.length === 0" class="picker-empty">没有匹配的用户邮箱</p>
      </div>
    </section>

    <section class="email-grid">
      <article class="admin-panel email-compose-panel">
        <div class="admin-panel__head">
          <div>
            <p class="admin-section-label">COMPOSE</p>
            <h3 class="admin-panel__title">邮件内容</h3>
          </div>
          <span class="email-limit">{{ subjectCount }}/{{ MAX_SUBJECT_LENGTH }}</span>
        </div>

        <div class="email-form-stack">
          <label class="admin-field email-field">
            邮件标题
            <input v-model="form.subject" maxlength="120" placeholder="请输入邮件标题" />
          </label>
          <label class="admin-field email-field">
            邮件正文
            <textarea v-model="form.body" maxlength="5000" placeholder="请输入需要发送给用户的正文内容" />
          </label>
          <div class="email-compose-footer">
            <span :class="{ over: bodyCount > MAX_BODY_LENGTH }">{{ bodyCount }}/{{ MAX_BODY_LENGTH }}</span>
            <button class="admin-btn" type="button" :disabled="!canSend" @click="submitEmail">
              {{ sending ? '发送中...' : '发送邮件' }}
            </button>
          </div>
        </div>
      </article>

      <aside class="admin-panel email-side-panel">
        <div class="admin-panel__head">
          <div>
            <p class="admin-section-label">RECIPIENTS</p>
            <h3 class="admin-panel__title">收件范围</h3>
          </div>
        </div>

        <div class="scope-list">
          <label v-for="item in scopeOptions" :key="item.value" class="scope-option" :class="{ active: form.recipientScope === item.value }">
            <input v-model="form.recipientScope" type="radio" :value="item.value" />
            <span>
              <strong>{{ item.label }}</strong>
              <small>{{ item.note }}</small>
            </span>
          </label>
        </div>

        <label v-if="form.recipientScope === 'manual'" class="admin-field manual-email-field">
          手动邮箱
          <textarea v-model="form.manualEmailsText" placeholder="每行一个邮箱，或用逗号分隔" />
        </label>
        <p v-if="invalidManualEmails.length" class="admin-message error">
          不支持的邮箱：{{ invalidManualEmails.join('、') }}
        </p>

        <div class="welcome-resend-panel">
          <div class="welcome-resend-head">
            <div>
              <p class="admin-section-label">WELCOME</p>
              <h4>欢迎邮件补发</h4>
            </div>
            <button class="mini-refresh-btn" type="button" :disabled="welcomeLoading" @click="loadWelcomeSummary">
              {{ welcomeLoading ? '刷新中' : '刷新' }}
            </button>
          </div>
          <div class="welcome-resend-metric">
            <span>待补发</span>
            <strong>{{ welcomeSummary.pendingCount }}</strong>
          </div>
          <p class="welcome-resend-note">
            仅面向已有仪式编号但尚未发送欢迎邮件的教师和学生。单次最多 {{ welcomeSummary.batchLimit }} 封。
          </p>
          <div class="welcome-actions">
            <button
              class="welcome-resend-btn"
              type="button"
              :disabled="welcomeSending || welcomeSummary.pendingCount <= 0"
              @click="submitWelcomeResend"
            >
              {{ welcomeSending ? '补发中...' : '一键补发未发送' }}
            </button>
            <button
              class="welcome-resend-btn secondary"
              type="button"
              :disabled="welcomeSending || selectedWelcomeUserIds.length <= 0 || selectedWelcomeLimitExceeded"
              @click="submitSelectedWelcomeResend"
            >
              补发已选用户
            </button>
          </div>
          <p v-if="selectedWelcomeLimitExceeded" class="welcome-resend-note warn">
            已选待补发用户超过 {{ welcomeSummary.batchLimit }} 位，请减少选择后再补发。
          </p>
          <div v-if="welcomeResult" class="welcome-result">
            <span>本次请求 {{ welcomeResult.requestedCount }}</span>
            <span>成功 {{ welcomeResult.sentCount }}</span>
            <span>失败 {{ welcomeResult.failedCount }}</span>
            <span>剩余 {{ welcomeResult.remainingCount }}</span>
          </div>
          <div v-if="welcomeResult?.failures?.length" class="failure-list compact">
            <p v-for="item in welcomeResult.failures" :key="item.userId || item.email">{{ item.email }}：{{ item.reason }}</p>
          </div>
        </div>

        <div v-if="sendResult" class="send-result">
          <p class="admin-section-label">RESULT</p>
          <div class="result-grid">
            <span>请求</span><strong>{{ sendResult.requestedCount }}</strong>
            <span>成功</span><strong>{{ sendResult.sentCount }}</strong>
            <span>失败</span><strong>{{ sendResult.failedCount }}</strong>
          </div>
          <div v-if="sendResult.failures?.length" class="failure-list">
            <p v-for="item in sendResult.failures" :key="item.email">{{ item.email }}：{{ item.reason }}</p>
          </div>
        </div>
      </aside>
    </section>
  </section>
</template>

<style scoped>
.email-page {
  max-width: 1180px;
}

.email-hero {
  grid-template-columns: minmax(0, 1fr);
}

.email-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 0.42fr);
  gap: 16px;
  align-items: start;
}

.email-compose-panel,
.email-side-panel {
  min-height: 520px;
}

.email-limit {
  color: var(--admin-text-tertiary);
  font-weight: 800;
}

.email-form-stack {
  display: grid;
  gap: 16px;
}

.email-field {
  min-width: 0;
}

.email-field textarea,
.manual-email-field textarea {
  width: 100%;
  border: 1px solid var(--admin-input-border);
  border-radius: var(--admin-radius-control);
  padding: 12px 14px;
  background: var(--admin-input-bg);
  color: var(--admin-text-primary);
  font: inherit;
  line-height: 1.6;
  resize: vertical;
}

.email-field textarea {
  min-height: 260px;
}

.manual-email-field {
  margin-top: 16px;
}

.manual-email-field textarea {
  min-height: 120px;
}

.email-compose-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  color: var(--admin-text-tertiary);
  font-weight: 700;
}

.email-compose-footer .over {
  color: var(--admin-danger);
}

.scope-list {
  display: grid;
  gap: 10px;
}

.scope-option {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 10px;
  padding: 13px;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  background: var(--admin-content-surface-strong);
  cursor: pointer;
}

.scope-option.active {
  border-color: var(--admin-accent);
  background: var(--admin-accent-soft);
}

.scope-option input {
  margin-top: 3px;
}

.scope-option strong,
.scope-option small {
  display: block;
}

.scope-option strong {
  color: var(--admin-text-primary);
}

.scope-option small {
  margin-top: 4px;
  color: var(--admin-text-secondary);
  line-height: 1.5;
}

.recipient-picker-panel {
  margin-bottom: 16px;
  padding: 16px;
}

.recipient-picker-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.recipient-picker-strip {
  display: grid;
  grid-template-columns: minmax(280px, 0.9fr) minmax(360px, 1fr) auto;
  gap: 12px;
  align-items: center;
  margin-top: 12px;
}

.recipient-picker-tools {
  display: grid;
  grid-template-columns: 118px minmax(0, 1fr);
  gap: 8px;
}

.recipient-picker-tools select,
.recipient-picker-tools input {
  height: 36px;
  border: 1px solid var(--admin-input-border);
  border-radius: var(--admin-radius-control);
  padding: 0 10px;
  background: var(--admin-input-bg);
  color: var(--admin-text-primary);
  font: inherit;
}

.selected-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) auto;
  gap: 8px;
  align-items: center;
  color: var(--admin-text-secondary);
  font-size: 12px;
  font-weight: 800;
}

.selected-summary span {
  min-width: 0;
  padding: 7px 8px;
  border: 1px solid var(--admin-border);
  border-radius: 999px;
  text-align: center;
}

.selected-summary button,
.picker-pager button {
  height: 30px;
  border: 1px solid var(--admin-border);
  border-radius: 999px;
  background: var(--admin-surface);
  color: var(--admin-text-secondary);
  font-weight: 800;
  cursor: pointer;
}

.selected-summary button {
  padding: 0 10px;
}

.selected-summary button:disabled,
.picker-pager button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.recipient-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(210px, 1fr));
  gap: 8px;
  max-height: 184px;
  overflow: auto;
  margin-top: 12px;
  padding-right: 2px;
}

.recipient-list.loading {
  opacity: 0.65;
}

.recipient-row {
  width: 100%;
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  min-height: 70px;
  padding: 10px;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  background: var(--admin-surface);
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.recipient-row.selected {
  border-color: var(--admin-accent);
  background: var(--admin-accent-soft);
}

.recipient-row.disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.recipient-check {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border: 1px solid var(--admin-border);
  border-radius: 6px;
  color: var(--admin-accent);
  font-weight: 900;
}

.recipient-main,
.recipient-meta {
  min-width: 0;
}

.recipient-main strong,
.recipient-main small,
.recipient-meta em,
.recipient-meta small {
  display: block;
}

.recipient-main strong {
  overflow: hidden;
  color: var(--admin-text-primary);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recipient-main small {
  overflow: hidden;
  margin-top: 3px;
  color: var(--admin-text-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recipient-meta {
  text-align: right;
}

.recipient-meta em {
  color: var(--admin-text-primary);
  font-size: 12px;
  font-style: normal;
  font-weight: 900;
}

.recipient-meta small {
  max-width: 92px;
  overflow: hidden;
  margin-top: 3px;
  color: var(--admin-text-tertiary);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picker-empty {
  margin: 0;
  padding: 18px 10px;
  border: 1px dashed var(--admin-border);
  border-radius: var(--admin-radius-card);
  color: var(--admin-text-secondary);
  text-align: center;
}

.picker-pager {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  color: var(--admin-text-secondary);
  font-size: 12px;
  font-weight: 900;
}

.picker-pager button {
  min-width: 76px;
  padding: 0 10px;
}

.welcome-resend-panel {
  margin-top: 18px;
  padding: 16px;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  background: var(--admin-surface);
}

.welcome-resend-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.welcome-resend-head h4 {
  margin: 4px 0 0;
  color: var(--admin-text-primary);
  font-size: 16px;
}

.mini-refresh-btn {
  height: 30px;
  padding: 0 10px;
  border: 1px solid var(--admin-border);
  border-radius: 999px;
  background: var(--admin-content-surface-strong);
  color: var(--admin-text-secondary);
  font-weight: 800;
  cursor: pointer;
}

.mini-refresh-btn:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.welcome-resend-metric {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-top: 14px;
  padding: 12px 0;
  border-top: 1px solid var(--admin-border);
  border-bottom: 1px solid var(--admin-border);
}

.welcome-resend-metric span {
  color: var(--admin-text-secondary);
  font-weight: 800;
}

.welcome-resend-metric strong {
  color: var(--admin-text-primary);
  font-size: 34px;
  line-height: 1;
}

.welcome-resend-note {
  margin: 12px 0;
  color: var(--admin-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.welcome-resend-note.warn {
  color: var(--admin-danger);
  font-weight: 800;
}

.welcome-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.welcome-resend-btn {
  width: 100%;
  height: 40px;
  border: 0;
  border-radius: var(--admin-radius-control);
  background: var(--admin-text-primary);
  color: var(--admin-surface);
  font-weight: 900;
  cursor: pointer;
}

.welcome-resend-btn.secondary {
  border: 1px solid var(--admin-border);
  background: var(--admin-surface);
  color: var(--admin-text-primary);
}

.welcome-resend-btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.welcome-result {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 12px;
  color: var(--admin-text-secondary);
  font-size: 13px;
  font-weight: 800;
}

.send-result {
  margin-top: 16px;
  padding: 14px;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  background: var(--admin-surface);
}

.result-grid {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px 14px;
  margin-top: 12px;
  color: var(--admin-text-secondary);
}

.result-grid strong {
  color: var(--admin-text-primary);
}

.failure-list {
  max-height: 130px;
  overflow: auto;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--admin-border);
  color: var(--admin-danger);
  font-size: 13px;
  line-height: 1.5;
}

.failure-list.compact {
  max-height: 92px;
  margin-top: 10px;
  font-size: 12px;
}

@media (max-width: 980px) {
  .recipient-picker-strip {
    grid-template-columns: 1fr;
  }

  .recipient-list {
    grid-template-columns: 1fr;
    max-height: 320px;
  }

  .email-grid {
    grid-template-columns: 1fr;
  }

  .email-compose-panel,
  .email-side-panel {
    min-height: auto;
  }
}
</style>
