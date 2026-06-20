<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  deleteStudentAccount,
  fetchStudentProfile,
  updateStudentProfile,
} from '../services/student'
import { clearToken, updateActiveSessionUser } from '../utils/auth'

const router = useRouter()

const loading = ref(true)
const saving = ref(false)
const deleting = ref(false)
const profile = ref(null)
const message = ref('')
const messageType = ref('info')

const profileForm = reactive({
  name: '',
})

const deleteForm = reactive({
  password: '',
  confirm: false,
})

const displayName = computed(() => profile.value?.name || '同学')
const avatarInitial = computed(() => displayName.value.slice(0, 1).toUpperCase())
const roleLabel = computed(() => profile.value?.role || '学生')
const statusLabel = computed(() => {
  const raw = profile.value?.status
  if (raw === undefined || raw === null || raw === '') return '正常'
  return Number(raw) === 1 ? '正常' : '禁用'
})
const statusTone = computed(() => (statusLabel.value === '正常' ? 'success' : 'danger'))
const createdAtText = computed(() => formatDate(profile.value?.createdAt))

const accountFacts = computed(() => [
  { label: '用户 ID', value: profile.value?.userId || profile.value?.userUuid || '—' },
  { label: '邮箱', value: profile.value?.email || '—' },
  { label: '当前角色', value: roleLabel.value },
  { label: '注册时间', value: createdAtText.value },
])

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
  if (type === 'success') {
    setTimeout(() => {
      if (message.value === text) message.value = ''
    }, 2800)
  }
}

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

function normalizeProfile(raw) {
  if (!raw) return null
  return {
    userId: raw.userId ?? raw.id ?? null,
    userUuid: raw.userUuid ?? null,
    name: raw.name || '',
    email: raw.email || '',
    role: raw.role || '学生',
    status: raw.status,
    createdAt: raw.createdAt || '',
  }
}

function syncStoredStudentName(name) {
  updateActiveSessionUser({ name })
  window.dispatchEvent(new CustomEvent('teamtrace-session-updated', { detail: { role: 'student' } }))
}

async function loadProfile() {
  loading.value = true
  setMessage('', 'info')
  try {
    const { data } = await fetchStudentProfile()
    profile.value = normalizeProfile(data?.data)
    profileForm.name = profile.value?.name || ''
  } catch (error) {
    profile.value = null
    setMessage(error.message || '个人资料加载失败，请稍后重试。', 'error')
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  const name = String(profileForm.name || '').trim()
  if (!name) {
    setMessage('姓名不能为空。', 'error')
    return
  }

  saving.value = true
  try {
    const { data } = await updateStudentProfile({ name })
    profile.value = normalizeProfile(data?.data) || { ...profile.value, name }
    profileForm.name = profile.value?.name || name
    syncStoredStudentName(profileForm.name)
    setMessage('个人资料已保存。', 'success')
  } catch (error) {
    setMessage(error.message || '保存失败，请稍后重试。', 'error')
  } finally {
    saving.value = false
  }
}

function resetProfileForm() {
  profileForm.name = profile.value?.name || ''
  setMessage('', 'info')
}

function logout() {
  clearToken()
  router.replace('/auth')
}

function startDeleteConfirm() {
  deleteForm.confirm = true
  setMessage('', 'info')
}

function cancelDeleteConfirm() {
  deleteForm.confirm = false
  deleteForm.password = ''
}

async function submitDeleteAccount() {
  const password = String(deleteForm.password || '').trim()
  if (!password) {
    setMessage('请输入登录密码以确认注销。', 'error')
    return
  }

  deleting.value = true
  try {
    await deleteStudentAccount(password)
    deleteForm.password = ''
    clearToken()
    await router.replace('/auth')
  } catch (error) {
    setMessage(error.message || '注销失败，请确认密码后重试。', 'error')
  } finally {
    deleting.value = false
  }
}

onMounted(() => {
  loadProfile()
})
</script>

<template>
  <div class="student-page student-profile">
    <div v-if="loading" class="loading-state">
      <div class="skeleton-card hero" />
      <div class="skeleton-grid">
        <div v-for="i in 2" :key="i" class="skeleton-card" />
      </div>
    </div>

    <template v-else>
      <section class="card profile-hero">
        <div class="hero-identity">
          <div class="profile-avatar">{{ avatarInitial }}</div>
          <div>
            <p class="eyebrow">个人中心</p>
            <h1>{{ displayName }}</h1>
            <p class="hero-note">{{ roleLabel }} · {{ profile?.email || '未绑定邮箱' }}</p>
          </div>
        </div>
        <div class="hero-actions">
          <button class="plain-danger-btn" type="button" @click="logout">退出登录</button>
        </div>
      </section>

      <p v-if="message" class="message" :class="messageType">{{ message }}</p>

      <div v-if="!profile" class="empty-state">
        <h3>暂时无法读取个人资料</h3>
        <p>请刷新页面或重新登录后再试。</p>
      </div>

      <template v-else>
        <section class="summary-strip">
          <article v-for="item in accountFacts" :key="item.label" class="card fact-card">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </article>
        </section>

        <section class="profile-workspace">
          <article class="card profile-panel">
            <div class="panel-head">
              <div>
                <p class="eyebrow">账户资料</p>
                <h2>基本信息</h2>
              </div>
              <span class="status-pill" :class="statusTone">{{ statusLabel }}</span>
            </div>

            <div class="form-grid">
              <label class="field">
                <span>姓名</span>
                <input v-model.trim="profileForm.name" type="text" maxlength="40" placeholder="请输入姓名" />
              </label>
              <label class="field readonly">
                <span>邮箱</span>
                <input :value="profile.email || '—'" type="text" disabled />
              </label>
            </div>

            <div class="panel-actions">
              <button class="primary-btn" type="button" :disabled="saving" @click="saveProfile">
                {{ saving ? '保存中...' : '保存个人资料' }}
              </button>
              <button class="secondary-btn" type="button" :disabled="saving" @click="resetProfileForm">恢复当前资料</button>
            </div>
          </article>

          <article class="card security-panel">
            <div class="panel-head">
              <div>
                <p class="eyebrow">安全操作</p>
                <h2>账号状态</h2>
              </div>
            </div>

            <div class="security-list">
              <div class="security-row">
                <div>
                  <strong>退出当前学生账号</strong>
                </div>
                <button class="secondary-btn" type="button" @click="logout">退出登录</button>
              </div>

              <div class="security-row danger">
                <div>
                  <strong>注销学生账号</strong>
                </div>
                <button v-if="!deleteForm.confirm" class="outline-danger-btn" type="button" @click="startDeleteConfirm">
                  准备注销
                </button>
              </div>
            </div>

            <div v-if="deleteForm.confirm" class="delete-confirm">
              <label class="field">
                <span>登录密码</span>
                <input
                  v-model="deleteForm.password"
                  type="password"
                  autocomplete="current-password"
                  placeholder="输入登录密码确认注销"
                />
              </label>
              <div class="panel-actions">
                <button class="outline-danger-btn" type="button" :disabled="deleting" @click="submitDeleteAccount">
                  {{ deleting ? '处理中...' : '确认注销账号' }}
                </button>
                <button class="secondary-btn" type="button" :disabled="deleting" @click="cancelDeleteConfirm">取消</button>
              </div>
            </div>
          </article>
        </section>
      </template>
    </template>
  </div>
</template>

<style scoped>
.student-profile,
.loading-state,
.profile-workspace,
.security-list,
.delete-confirm {
  display: grid;
  gap: 14px;
}

.card,
.empty-state {
  background: var(--tt-surface);
  border: 1px solid var(--tt-border-subtle);
  border-radius: var(--tt-radius-lg);
  box-shadow: var(--tt-shadow-sm);
}

.profile-hero,
.hero-identity,
.hero-actions,
.panel-head,
.panel-actions,
.security-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.profile-hero,
.hero-identity {
  align-items: center;
}

.panel-head,
.panel-actions,
.security-row {
  align-items: flex-start;
}

.profile-hero {
  padding: 22px;
}

.hero-identity {
  justify-content: flex-start;
  align-items: center;
}

.hero-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.profile-avatar {
  width: 72px;
  height: 72px;
  border-radius: 22px;
  background: var(--tt-accent-gradient);
  color: #f8fbff;
  font-size: 30px;
  font-weight: 800;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.eyebrow {
  margin: 0;
}

h1,
h2,
h3 {
  margin: 0;
  color: var(--tt-text);
}

h1 {
  margin-top: 8px;
  font-size: clamp(20px, 1.85vw, 24px);
  line-height: 1.28;
}

h2 {
  font-size: 20px;
}

.hero-note,
.message,
.fact-card span,
.security-row p,
.empty-state p {
  margin: 8px 0 0;
  color: var(--tt-text-secondary);
  font-size: 13px;
  line-height: 1.65;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.fact-card {
  padding: 16px 18px;
}

.fact-card strong {
  display: block;
  margin-top: 8px;
  color: var(--tt-text);
  font-size: 15px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.profile-workspace {
  grid-template-columns: minmax(0, 1fr) minmax(340px, 0.82fr);
  align-items: start;
}

.profile-panel,
.security-panel {
  padding: 18px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.field {
  display: grid;
  gap: 8px;
  color: var(--tt-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

input {
  width: 100%;
  min-height: 42px;
  box-sizing: border-box;
  border: 1px solid var(--tt-border-subtle);
  border-radius: 14px;
  padding: 0 12px;
  background: var(--tt-surface);
  color: var(--tt-text);
  font: inherit;
}

input:focus {
  outline: none;
  border-color: rgba(0, 122, 255, 0.42);
  box-shadow: 0 0 0 3px rgba(0, 122, 255, 0.08);
}

input:disabled {
  color: var(--tt-text-secondary);
  background: var(--tt-surface-muted);
  cursor: not-allowed;
}

.readonly {
  opacity: 0.9;
}

.panel-actions {
  justify-content: flex-start;
  flex-wrap: wrap;
  margin-top: 18px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.status-pill.success {
  background: var(--tt-success-soft);
  color: var(--tt-success);
}

.status-pill.danger {
  background: var(--tt-danger-soft);
  color: var(--tt-danger);
}

.security-row {
  padding: 14px;
  border: 1px solid var(--tt-border-subtle);
  border-radius: 18px;
  background: var(--tt-surface);
}

.security-row strong {
  color: var(--tt-text);
  font-size: 14px;
}

.security-row.danger {
  background: rgba(255, 59, 48, 0.06);
  border-color: rgba(255, 59, 48, 0.16);
}

.delete-confirm {
  padding: 14px;
  border-radius: 18px;
  background: rgba(255, 59, 48, 0.06);
  border: 1px solid rgba(255, 59, 48, 0.16);
}

.message {
  padding: 10px 14px;
  border-radius: 16px;
  background: var(--tt-surface);
}

.message.error {
  color: var(--tt-danger);
}

.message.success {
  color: var(--tt-success);
}

.primary-btn,
.secondary-btn,
.plain-danger-btn,
.outline-danger-btn {
  min-height: 42px;
  padding: 0 15px;
  border-radius: 14px;
  font-family: inherit;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.primary-btn {
  border: 0;
  background: var(--tt-accent);
  color: #f8fbff;
}

.secondary-btn {
  border: 1px solid var(--tt-border-subtle);
  background: var(--tt-surface-muted);
  color: var(--tt-text);
}

.plain-danger-btn,
.outline-danger-btn {
  border: 1px solid rgba(255, 59, 48, 0.35);
  background: rgba(255, 59, 48, 0.06);
  color: var(--tt-danger);
}

.primary-btn:hover:not(:disabled),
.secondary-btn:hover:not(:disabled),
.plain-danger-btn:hover:not(:disabled),
.outline-danger-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.08);
}

.primary-btn:disabled,
.secondary-btn:disabled,
.plain-danger-btn:disabled,
.outline-danger-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.empty-state {
  min-height: 260px;
  padding: 28px;
  display: grid;
  place-items: center;
  text-align: center;
}

.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.skeleton-card {
  min-height: 260px;
  border-radius: var(--tt-radius-lg);
  background: var(--tt-surface-muted);
  animation: pulse 1.5s ease-in-out infinite;
}

.skeleton-card.hero {
  min-height: 118px;
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

@media (max-width: 1040px) {
  .summary-strip,
  .profile-workspace,
  .skeleton-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .profile-hero,
  .hero-actions,
  .panel-head,
  .panel-actions,
  .security-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
