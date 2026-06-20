<script setup>
import { computed, ref, watch } from 'vue'
import { getTokenPayload, getActiveSession, clearTokenAndRemoveSession, updateActiveSessionUser } from '../../utils/auth'
import { getUserProfile, updateUserProfile, uploadAvatar, deleteAccount, sendEmailCode } from '../../services/auth'
import { resolveMediaUrl } from '../../utils/mediaUrl'
import IconSystem from '../common/IconSystem.vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  role: { type: String, default: 'student' },
})

const emit = defineEmits(['close', 'openChangePassword'])

const loading = ref(false)
const error = ref('')
const successMsg = ref('')
const loaded = ref(false)

const profile = ref({ id: '', name: '', email: '', studentId: '', avatarUrl: '', inviteCode: '' })
const editName = ref('')
const editStudentId = ref('')
const nameEditing = ref(false)
const avatarUploading = ref(false)
const avatarLoadFailed = ref(false)

const showDeleteFlow = ref(false)
const deleteStep = ref('confirm')
const deleteCode = ref('')
const deleteCodeCountdown = ref(0)
const deleteError = ref('')
const deleteLoading = ref(false)

const payload = computed(() => getTokenPayload() || {})
const sessionUser = computed(() => getActiveSession()?.user || {})
const displayAvatarUrl = computed(() => {
  if (avatarLoadFailed.value) return ''
  return resolveMediaUrl(profile.value.avatarUrl)
})

function maskEmail(email) {
  if (!email || !email.includes('@')) return email || '—'
  const [name, domain] = email.split('@')
  if (name.length <= 3) return name[0] + '***@' + domain
  return name.slice(0, 3) + '***@' + domain
}

function applyProfile(data) {
  profile.value = {
    id: data.id ?? '',
    name: data.name ?? '',
    email: data.email ?? '',
    studentId: data.studentId ?? '',
    avatarUrl: data.avatarUrl ?? '', inviteCode: data.inviteCode ?? '',
  }
  editName.value = data.name ?? ''
  editStudentId.value = data.studentId ?? ''
  avatarLoadFailed.value = false
  loaded.value = true
}

function buildFallbackProfile() {
  const su = sessionUser.value || {}
  const p = payload.value || {}
  return {
    id: su.id ?? p.sub ?? p.subject ?? p.userId ?? '',
    name: su.name ?? p.name ?? '',
    email: su.email ?? p.email ?? '',
    studentId: su.studentId ?? p.studentId ?? '',
    avatarUrl: su.avatarUrl ?? p.avatarUrl ?? '',
    inviteCode: su.inviteCode ?? p.inviteCode ?? '',
  }
}

async function loadProfile() {
  try {
    const res = await getUserProfile()
    const data = res?.data?.data
    if (data) {
      applyProfile(data)
      updateActiveSessionUser({
        name: data.name,
        email: data.email,
        studentId: data.studentId,
        avatarUrl: data.avatarUrl,
      })
      return
    }
  } catch (e) {
    console.warn('Profile API failed, using session:', e.message)
  }
  const fallback = buildFallbackProfile()
  if (fallback.id || fallback.name || fallback.email || fallback.avatarUrl) {
    applyProfile(fallback)
  }
}

watch(() => props.visible, async (v) => {
  if (v) {
    await loadProfile()
    error.value = ''
    successMsg.value = ''
    nameEditing.value = false
    resetDeleteFlow()
  }
}, { immediate: true })

function startEditName() {
  editName.value = profile.value.name
  nameEditing.value = true
  error.value = ''
}

async function saveName() {
  const val = editName.value.trim()
  if (!val) { error.value = '姓名不能为空'; return }
  loading.value = true
  error.value = ''
  try {
    const res = await updateUserProfile({ name: val, studentId: editStudentId.value.trim() || null })
    const data = res?.data?.data
    if (data) {
      applyProfile({ ...profile.value, ...data })
      updateActiveSessionUser({ name: data.name, studentId: data.studentId, avatarUrl: data.avatarUrl })
      successMsg.value = '保存成功'
      setTimeout(() => successMsg.value = '', 2000)
    }
    nameEditing.value = false
  } catch (err) {
    error.value = err?.message || '保存失败'
  } finally {
    loading.value = false
  }
}

async function saveStudentId() {
  loading.value = true
  error.value = ''
  try {
    const res = await updateUserProfile({ name: profile.value.name, studentId: editStudentId.value.trim() || null })
    const data = res?.data?.data
    if (data) {
      applyProfile({ ...profile.value, ...data })
      updateActiveSessionUser({ studentId: data.studentId })
      successMsg.value = '学号绑定成功'
      setTimeout(() => successMsg.value = '', 2000)
    }
  } catch (err) {
    error.value = err?.message || '绑定失败'
  } finally {
    loading.value = false
  }
}

function onAvatarClick() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/jpeg,image/png,image/gif,image/bmp,image/webp'
  input.onchange = async (e) => {
    const file = e.target.files?.[0]
    if (!file) return
    if (file.size > 10 * 1024 * 1024) {
      error.value = '图片不能超过10MB'
      return
    }
    avatarUploading.value = true
    error.value = ''
    try {
      const res = await uploadAvatar(file)
      const url = res?.data?.data?.url
      if (url) {
        profile.value.avatarUrl = url
        avatarLoadFailed.value = false
        updateActiveSessionUser({ avatarUrl: url })
        successMsg.value = '头像更新成功'
        setTimeout(() => successMsg.value = '', 2000)
      }
    } catch (err) {
      error.value = err?.message || '上传失败'
    } finally {
      avatarUploading.value = false
    }
  }
  input.click()
}

function handleAvatarError() {
  avatarLoadFailed.value = true
}

function resetDeleteFlow() {
  showDeleteFlow.value = false
  deleteStep.value = 'confirm'
  deleteCode.value = ''
  deleteCodeCountdown.value = 0
  deleteError.value = ''
}

function startDeleteFlow() {
  showDeleteFlow.value = true
  deleteStep.value = 'confirm'
  deleteCode.value = ''
  deleteCodeCountdown.value = 0
  deleteError.value = ''
  error.value = ''
}

async function sendDeleteCode() {
  deleteLoading.value = true
  deleteError.value = ''
  try {
    await sendEmailCode({ email: profile.value.email, purpose: 'delete_account' })
    deleteCodeCountdown.value = 60
    const timer = setInterval(() => { deleteCodeCountdown.value--; if (deleteCodeCountdown.value <= 0) clearInterval(timer) }, 1000)
    deleteStep.value = 'verify'
  } catch (err) {
    deleteError.value = err?.message || '发送失败'
  } finally {
    deleteLoading.value = false
  }
}

async function confirmDelete() {
  if (!deleteCode.value || deleteCode.value.length !== 6) {
    deleteError.value = '请输入6位验证码'
    return
  }
  deleteLoading.value = true
  deleteError.value = ''
  try {
    await deleteAccount({ verifyCode: deleteCode.value })
    deleteStep.value = 'done'
    setTimeout(() => { clearTokenAndRemoveSession(); window.location.replace('/auth') }, 1500)
  } catch (err) {
    deleteError.value = err?.message || '注销失败'
  } finally {
    deleteLoading.value = false
  }
}
</script>

<template>
  <div class="account-body">
    <!-- Avatar + Role -->
    <div class="account-header">
      <div class="avatar-wrap" @click="onAvatarClick" :title="avatarUploading ? '上传中...' : '点击更换头像'">
        <img v-if="displayAvatarUrl" :src="displayAvatarUrl" class="avatar-img" @error="handleAvatarError" />
        <span v-else class="avatar-placeholder">{{ (profile.name || '?').slice(0, 1) }}</span>
        <div class="avatar-overlay">
          <IconSystem name="camera" :size="16" />
        </div>
      </div>
      <div class="account-meta">
        <p class="account-role">{{ role === 'student' ? '学生' : role === 'teacher' ? '教师' : '管理员' }}</p>
      </div>
    </div>

    <!-- Fields -->
    <div class="account-fields">
      <!-- 学生：学号（可编辑） -->
      <div v-if="role === 'student'" class="field-row">
        <span class="field-label">学号</span>
        <div class="field-control">
          <input v-model="editStudentId" class="field-input" placeholder="未绑定" @keyup.enter="saveStudentId" />
          <button class="field-btn" :disabled="loading" @click="saveStudentId">绑定</button>
        </div>
      </div>
      <!-- 教师：邀请码（只读） -->
      <div v-if="role === 'teacher'" class="field-row">
        <span class="field-label">邀请码</span>
        <div class="field-control read-only">
          <span class="field-value">{{ profile.inviteCode || '暂无' }}</span>
        </div>
      </div>

      <div class="field-row">
        <span class="field-label">姓名</span>
        <div class="field-control">
          <input v-if="nameEditing" v-model="editName" class="field-input" maxlength="50" @keyup.enter="saveName" />
          <span v-else class="field-value">{{ profile.name || '—' }}</span>
          <button v-if="!nameEditing" class="field-btn" @click="startEditName">编辑</button>
          <template v-else>
            <button class="field-btn primary" :disabled="loading" @click="saveName">保存</button>
            <button class="field-btn" @click="nameEditing = false; error = ''">取消</button>
          </template>
        </div>
      </div>

      <div class="field-row">
        <span class="field-label">邮箱</span>
        <div class="field-control read-only">
          <span class="field-value muted">{{ maskEmail(profile.email) }}</span>
          <IconSystem name="lock" :size="14" class="lock-icon" />
        </div>
      </div>
    </div>

    <p v-if="error" class="acct-error">{{ error }}</p>
    <p v-if="successMsg" class="acct-success">{{ successMsg }}</p>

    <div class="account-actions">
      <button class="panel-btn" type="button" @click="emit('openChangePassword')">
        <IconSystem name="lock" :size="14" /> 修改密码
      </button>
      <button class="panel-btn danger" type="button" @click="startDeleteFlow">
        <IconSystem name="trash" :size="14" /> 注销账号
      </button>
    </div>

    <!-- Delete flow -->
    <div v-if="showDeleteFlow" class="delete-overlay" @click.self="resetDeleteFlow">
      <div class="delete-panel">
        <template v-if="deleteStep === 'confirm'">
          <h3>确认注销账号？</h3>
          <p>注销后所有数据将被永久删除，无法恢复。</p>
          <p class="delete-email-hint">验证码将发送至 <strong>{{ maskEmail(profile.email) }}</strong></p>
          <p v-if="deleteError" class="acct-error">{{ deleteError }}</p>
          <div class="delete-btns">
            <button class="field-btn" @click="resetDeleteFlow">取消</button>
            <button class="field-btn danger" :disabled="deleteLoading" @click="sendDeleteCode">
              {{ deleteLoading ? '发送中...' : '获取验证码' }}
            </button>
          </div>
        </template>
        <template v-if="deleteStep === 'verify'">
          <h3>输入验证码</h3>
          <p>已发送至 {{ maskEmail(profile.email) }}</p>
          <div class="delete-code-row">
            <input v-model="deleteCode" class="field-input code-input" maxlength="6" placeholder="6位验证码" @keyup.enter="confirmDelete" />
            <button class="field-btn" :disabled="deleteCodeCountdown > 0" @click="sendDeleteCode">
              {{ deleteCodeCountdown > 0 ? `${deleteCodeCountdown}s` : '重新发送' }}
            </button>
          </div>
          <p v-if="deleteError" class="acct-error">{{ deleteError }}</p>
          <div class="delete-btns">
            <button class="field-btn" @click="resetDeleteFlow">取消</button>
            <button class="field-btn danger" :disabled="deleteLoading" @click="confirmDelete">
              {{ deleteLoading ? '验证中...' : '确认注销' }}
            </button>
          </div>
        </template>
        <template v-if="deleteStep === 'done'">
          <h3>账号已注销</h3>
          <p>即将跳转到登录页...</p>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.account-body { display: flex; flex-direction: column; gap: 16px; }
.account-header { display: flex; align-items: center; gap: 16px; padding-bottom: 16px; border-bottom: 1px solid var(--tt-divider); }
.avatar-wrap { width: 64px; height: 64px; border-radius: 50%; overflow: hidden; cursor: pointer; position: relative; flex-shrink: 0; background: var(--tt-accent-gradient, linear-gradient(135deg, #3b82f6, #60a5fa)); }
.avatar-img { width: 100%; height: 100%; object-fit: cover; }
.avatar-placeholder { width: 100%; height: 100%; display: inline-flex; align-items: center; justify-content: center; color: #fff; font-size: 24px; font-weight: 700; }
.avatar-overlay { position: absolute; inset: 0; background: rgba(0, 0, 0, 0.4); display: flex; align-items: center; justify-content: center; opacity: 0; transition: opacity 150ms; color: #fff; }
.avatar-wrap:hover .avatar-overlay { opacity: 1; }
.account-meta { display: flex; flex-direction: column; gap: 2px; }
.account-role { margin: 0; font-size: 13px; color: var(--tt-text-tertiary); }
.account-fields { display: flex; flex-direction: column; gap: 12px; }
.field-row { display: flex; align-items: center; gap: 12px; }
.field-label { width: 56px; font-size: 13px; font-weight: 600; color: var(--tt-text-secondary); flex-shrink: 0; }
.field-control { flex: 1; min-width: 0; display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: center; gap: 8px; }
.field-control.read-only { grid-template-columns: minmax(0, 1fr) auto; }
.field-value { min-height: 34px; display: inline-flex; align-items: center; min-width: 0; font-size: 14px; color: var(--tt-text); }
.field-value.muted { color: var(--tt-text-tertiary); }
.field-input { width: 100%; min-width: 0; height: 34px; padding: 0 10px; border: 1px solid var(--tt-border); border-radius: 8px; background: var(--tt-surface); color: var(--tt-text); font-family: inherit; font-size: 14px; }
.field-input:focus { outline: none; border-color: var(--tt-accent); }
.field-btn { min-width: 56px; height: 34px; padding: 0 14px; border: 1px solid var(--tt-border); border-radius: 8px; background: var(--tt-surface); color: var(--tt-text); font-family: inherit; font-size: 13px; cursor: pointer; white-space: nowrap; transition: all 150ms; }
.field-btn:hover { background: var(--tt-surface-hover); }
.field-btn.primary { background: var(--tt-accent); color: #fff; border-color: transparent; }
.field-btn.danger { color: #fff; background: #dc2626; border-color: #dc2626; }
.field-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.lock-icon { color: var(--tt-text-tertiary); flex-shrink: 0; }
.acct-error { margin: 0; color: #dc2626; font-size: 13px; text-align: center; }
.acct-success { margin: 0; color: #16a34a; font-size: 13px; text-align: center; }
.account-actions { display: flex; flex-direction: column; gap: 6px; padding-top: 8px; border-top: 1px solid var(--tt-divider); }
.panel-btn { display: inline-flex; align-items: center; gap: 8px; min-height: 36px; padding: 0 14px; border-radius: 8px; background: var(--tt-bg-elevated); color: var(--tt-text); border: 1px solid var(--tt-border); cursor: pointer; font-family: inherit; font-size: 13px; font-weight: 500; transition: all 150ms; }
.panel-btn:hover { background: var(--tt-surface-hover); }
.panel-btn.danger { color: var(--tt-danger); }
.panel-btn.danger:hover { background: var(--tt-danger-soft); }
.delete-overlay { position: fixed; inset: 0; background: rgba(0, 0, 0, 0.45); display: flex; align-items: center; justify-content: center; z-index: 200; backdrop-filter: blur(4px); }
.delete-panel { width: 92%; max-width: 380px; border-radius: 16px; background: var(--tt-bg-elevated, #fff); box-shadow: 0 16px 48px rgba(0, 0, 0, 0.15); border: 1px solid var(--tt-border); padding: 24px; display: flex; flex-direction: column; gap: 12px; }
.delete-panel h3 { margin: 0; font-size: 17px; font-weight: 700; color: var(--tt-text); }
.delete-panel p { margin: 0; font-size: 13px; color: var(--tt-text-secondary); line-height: 1.5; }
.delete-email-hint { padding: 8px 12px; background: var(--tt-surface); border-radius: 8px; font-size: 12px !important; }
.delete-code-row { display: flex; gap: 8px; align-items: center; }
.code-input { flex: 1; }
.delete-btns { display: flex; gap: 8px; justify-content: flex-end; }
</style>
