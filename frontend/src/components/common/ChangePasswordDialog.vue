<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { changePassword, resetPassword as resetPasswordApi, sendEmailCode } from '../../services/auth'
import { isAllowedEmail } from '../../utils/email'

const HISTORY_STATE_KEY = 'ttChangePasswordDialog'
const POINTER_MOVE_THRESHOLD = 8

const props = defineProps({
  visible: { type: Boolean, default: false },
  userEmail: { type: String, default: '' },
  isEn: { type: Boolean, default: false },
})

const emit = defineEmits(['close'])

const mode = ref('change')
const step = ref(1)
const formSession = ref(0)

const form = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
  email: '',
  code: '',
})

const loading = ref(false)
const error = ref('')
const codeCountdown = ref(0)
const showOldPassword = ref(false)
const showNewPassword = ref(false)
const showConfirmPassword = ref(false)
const showResetPassword = ref(false)
const showResetConfirmPassword = ref(false)

const historyArmed = ref(false)
const overlayPointerId = ref(null)
const overlayPointerStart = ref(null)
const backPointerStart = ref(null)

function resetForm() {
  mode.value = 'change'
  step.value = 1
  form.value = {
    oldPassword: '',
    newPassword: '',
    confirmPassword: '',
    email: props.userEmail,
    code: '',
  }
  error.value = ''
  codeCountdown.value = 0
  showOldPassword.value = false
  showNewPassword.value = false
  showConfirmPassword.value = false
  showResetPassword.value = false
  showResetConfirmPassword.value = false
  formSession.value += 1
}

function stepBack() {
  error.value = ''
  if (mode.value === 'forgot') {
    mode.value = 'change'
    step.value = 1
    return true
  }
  if (step.value > 1) {
    step.value = 1
    return true
  }
  return false
}

function armDialogHistory() {
  if (historyArmed.value) return
  history.pushState({ [HISTORY_STATE_KEY]: true }, '')
  historyArmed.value = true
  window.addEventListener('popstate', handlePopState)
}

function disarmDialogHistory() {
  window.removeEventListener('popstate', handlePopState)
  if (historyArmed.value && history.state?.[HISTORY_STATE_KEY]) {
    historyArmed.value = false
    history.back()
    return
  }
  historyArmed.value = false
}

function handlePopState() {
  if (!props.visible) return
  historyArmed.value = false
  if (stepBack()) {
    armDialogHistory()
    return
  }
  resetForm()
  emit('close')
}

function resetOverlayPointer() {
  overlayPointerId.value = null
  overlayPointerStart.value = null
}

function onOverlayPointerDown(event) {
  if (event.target !== event.currentTarget) return
  overlayPointerId.value = event.pointerId
  overlayPointerStart.value = { x: event.clientX, y: event.clientY }
}

function onOverlayPointerUp(event) {
  if (event.pointerId !== overlayPointerId.value) return
  if (event.target !== event.currentTarget) {
    resetOverlayPointer()
    return
  }
  const start = overlayPointerStart.value
  if (start) {
    const dx = Math.abs(event.clientX - start.x)
    const dy = Math.abs(event.clientY - start.y)
    if (dx > POINTER_MOVE_THRESHOLD || dy > POINTER_MOVE_THRESHOLD) {
      resetOverlayPointer()
      return
    }
  }
  resetOverlayPointer()
  close()
}

function onOverlayPointerCancel(event) {
  if (event.pointerId === overlayPointerId.value) {
    resetOverlayPointer()
  }
}

function onBackPointerDown(event) {
  backPointerStart.value = { x: event.clientX, y: event.clientY }
}

function onBackPointerUp(event) {
  const start = backPointerStart.value
  backPointerStart.value = null
  if (start) {
    const dx = Math.abs(event.clientX - start.x)
    const dy = Math.abs(event.clientY - start.y)
    if (dx > POINTER_MOVE_THRESHOLD || dy > POINTER_MOVE_THRESHOLD) return
  }
  goBack()
}

watch(
  () => props.visible,
  async (open, wasOpen) => {
    if (open) {
      if (document.activeElement instanceof HTMLElement) {
        document.activeElement.blur()
      }
      resetForm()
      armDialogHistory()
      await nextTick()
      const panel = document.querySelector('.pw-panel')
      const firstInput = panel?.querySelector('input')
      firstInput?.focus()
      return
    }
    if (wasOpen) {
      disarmDialogHistory()
    }
    resetForm()
  },
)

onBeforeUnmount(() => {
  window.removeEventListener('popstate', handlePopState)
})

function close() {
  disarmDialogHistory()
  resetForm()
  emit('close')
}

function validatePassword(password) {
  return /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z\d\s]).{8,64}$/.test(password)
}

async function submitNewPassword() {
  if (!form.value.oldPassword) {
    error.value = props.isEn ? 'Please enter your current password' : '请输入旧密码'
    return
  }
  if (!form.value.newPassword) {
    error.value = props.isEn ? 'Please enter a new password' : '请输入新密码'
    return
  }
  if (!validatePassword(form.value.newPassword)) {
    error.value = props.isEn ? 'Password: 8+ chars, upper+lower+digit+special' : '密码需8位以上，含大写、小写、数字、特殊字符'
    return
  }
  if (form.value.newPassword !== form.value.confirmPassword) {
    error.value = props.isEn ? 'Passwords do not match' : '两次密码输入不一致'
    return
  }
  error.value = ''
  loading.value = true
  try {
    await changePassword({ oldPassword: form.value.oldPassword, newPassword: form.value.newPassword })
    close()
    alert(props.isEn ? 'Password changed successfully' : '密码修改成功')
  } catch (err) {
    error.value = err?.message || (props.isEn ? 'Failed to change password' : '密码修改失败')
  } finally {
    loading.value = false
  }
}

async function sendCode() {
  const email = String(form.value.email || '').trim()
  if (!isAllowedEmail(email)) {
    error.value = props.isEn ? 'Invalid email format' : '邮箱格式不正确，请检查后重试'
    return
  }
  error.value = ''
  loading.value = true
  try {
    await sendEmailCode({ email, purpose: 'reset_password' })
    codeCountdown.value = 60
    const timer = setInterval(() => {
      codeCountdown.value--
      if (codeCountdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch {
    error.value = props.isEn ? 'Failed to send code' : '发送验证码失败'
  } finally {
    loading.value = false
  }
}

async function resetPassword() {
  if (!isAllowedEmail(String(form.value.email || '').trim())) {
    error.value = props.isEn ? 'Invalid email format' : '邮箱格式不正确，请检查后重试'
    return
  }
  if (!String(form.value.code || '').trim()) {
    error.value = props.isEn ? 'Please enter the verification code' : '请输入验证码'
    return
  }
  if (!form.value.newPassword) {
    error.value = props.isEn ? 'Please enter a new password' : '请输入新密码'
    return
  }
  if (!validatePassword(form.value.newPassword)) {
    error.value = props.isEn ? 'Password: 8+ chars, upper+lower+digit+special' : '密码需8位以上，含大写、小写、数字、特殊字符'
    return
  }
  if (form.value.newPassword !== form.value.confirmPassword) {
    error.value = props.isEn ? 'Passwords do not match' : '两次密码输入不一致'
    return
  }
  error.value = ''
  loading.value = true
  try {
    await resetPasswordApi({
      email: String(form.value.email || '').trim(),
      verifyCode: String(form.value.code || '').trim(),
      newPassword: form.value.newPassword,
    })
    close()
    alert(props.isEn ? 'Password reset successfully' : '密码重置成功')
  } catch (err) {
    error.value = err?.message || (props.isEn ? 'Failed to reset password' : '密码重置失败')
  } finally {
    loading.value = false
  }
}

function goBack() {
  if (!stepBack()) {
    close()
  }
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="pw-overlay"
      @pointerdown="onOverlayPointerDown"
      @pointerup="onOverlayPointerUp"
      @pointercancel="onOverlayPointerCancel"
    >
      <div class="pw-panel" role="dialog" aria-modal="true" @click.stop @pointerdown.stop @pointerup.stop>
        <div class="pw-header">
          <button
            class="pw-back"
            type="button"
            :aria-label="isEn ? 'Back' : '返回'"
            @pointerdown="onBackPointerDown"
            @pointerup="onBackPointerUp"
            @click.prevent
          >
            ←
          </button>
          <h2 class="pw-title">
            {{
              mode === 'forgot'
                ? isEn
                  ? 'Reset Password'
                  : '重置密码'
                : isEn
                  ? 'Change Password'
                  : '修改密码'
            }}
          </h2>
          <button class="pw-close" type="button" @click="close">×</button>
        </div>

        <form class="pw-body" :key="formSession" autocomplete="off" @submit.prevent>
          <div v-if="mode === 'change'" class="pw-step">
            <div class="pw-field">
              <label class="pw-label">{{ isEn ? 'Current Password' : '当前密码' }}</label>
              <div class="pw-input-wrap">
                <input
                  v-model="form.oldPassword"
                  :type="showOldPassword ? 'text' : 'password'"
                  class="pw-input"
                  name="tt-current-password"
                  autocomplete="new-password"
                  :placeholder="isEn ? 'Enter current password' : '请输入当前密码'"
                  @keyup.enter="submitNewPassword"
                />
                <button
                  class="pw-toggle"
                  type="button"
                  tabindex="-1"
                  :aria-label="showOldPassword ? (isEn ? 'Hide password' : '隐藏密码') : (isEn ? 'Show password' : '显示密码')"
                  :class="{ visible: showOldPassword }"
                  @click="showOldPassword = !showOldPassword"
                />
              </div>
            </div>

            <div class="pw-field">
              <label class="pw-label">{{ isEn ? 'New Password' : '新密码' }}</label>
              <div class="pw-input-wrap">
                <input
                  v-model="form.newPassword"
                  :type="showNewPassword ? 'text' : 'password'"
                  class="pw-input"
                  name="tt-new-password"
                  autocomplete="new-password"
                  :placeholder="isEn ? '8+ chars, upper+lower+digit+special' : '8位以上，含大小写+数字+特殊字符'"
                />
                <button
                  class="pw-toggle"
                  type="button"
                  tabindex="-1"
                  :aria-label="showNewPassword ? (isEn ? 'Hide password' : '隐藏密码') : (isEn ? 'Show password' : '显示密码')"
                  :class="{ visible: showNewPassword }"
                  @click="showNewPassword = !showNewPassword"
                />
              </div>
            </div>

            <div class="pw-field">
              <label class="pw-label">{{ isEn ? 'Confirm Password' : '确认密码' }}</label>
              <div class="pw-input-wrap">
                <input
                  v-model="form.confirmPassword"
                  :type="showConfirmPassword ? 'text' : 'password'"
                  class="pw-input"
                  name="tt-confirm-password"
                  autocomplete="new-password"
                  :placeholder="isEn ? 'Re-enter new password' : '再次输入新密码'"
                  @keyup.enter="submitNewPassword"
                />
                <button
                  class="pw-toggle"
                  type="button"
                  tabindex="-1"
                  :aria-label="showConfirmPassword ? (isEn ? 'Hide password' : '隐藏密码') : (isEn ? 'Show password' : '显示密码')"
                  :class="{ visible: showConfirmPassword }"
                  @click="showConfirmPassword = !showConfirmPassword"
                />
              </div>
            </div>

            <p v-if="error" class="pw-error">{{ error }}</p>

            <button class="pw-btn primary" type="button" :disabled="loading" @click="submitNewPassword">
              {{ loading ? (isEn ? 'Saving...' : '保存中...') : isEn ? 'Reset Password' : '确定重置密码' }}
            </button>

            <button class="pw-link" type="button" @click="mode = 'forgot'; step = 1; error = ''">
              {{ isEn ? 'Forgot password?' : '忘记密码？' }}
            </button>
          </div>

          <div v-if="mode === 'forgot'" class="pw-step">
            <div class="pw-field">
              <label class="pw-label">{{ isEn ? 'Email' : '邮箱' }}</label>
              <input
                v-model="form.email"
                type="email"
                class="pw-input"
                name="tt-reset-email"
                autocomplete="off"
                :placeholder="isEn ? 'xxx@163.com / xxx@qq.com' : '请输入注册邮箱'"
              />
            </div>

            <div class="pw-field">
              <label class="pw-label">{{ isEn ? 'Verification Code' : '验证码' }}</label>
              <div class="pw-code-row">
                <input
                  v-model="form.code"
                  type="text"
                  maxlength="6"
                  class="pw-input code-input"
                  name="tt-reset-code"
                  autocomplete="one-time-code"
                  :placeholder="isEn ? 'Enter code' : '请输入验证码'"
                  @keyup.enter="resetPassword"
                />
                <button class="pw-btn small" type="button" :disabled="codeCountdown > 0 || loading" @click="sendCode">
                  {{ codeCountdown > 0 ? `${codeCountdown}s` : loading ? (isEn ? 'Sending...' : '发送中...') : isEn ? 'Send Code' : '发送验证码' }}
                </button>
              </div>
            </div>

            <div class="pw-field">
              <label class="pw-label">{{ isEn ? 'New Password' : '新密码' }}</label>
              <div class="pw-input-wrap">
                <input
                  v-model="form.newPassword"
                  :type="showResetPassword ? 'text' : 'password'"
                  class="pw-input"
                  name="tt-reset-new-password"
                  autocomplete="new-password"
                  :placeholder="isEn ? '8+ chars, upper+lower+digit+special' : '8位以上，含大小写+数字+特殊字符'"
                />
                <button
                  class="pw-toggle"
                  type="button"
                  tabindex="-1"
                  :aria-label="showResetPassword ? (isEn ? 'Hide password' : '隐藏密码') : (isEn ? 'Show password' : '显示密码')"
                  :class="{ visible: showResetPassword }"
                  @click="showResetPassword = !showResetPassword"
                />
              </div>
            </div>

            <div class="pw-field">
              <label class="pw-label">{{ isEn ? 'Confirm Password' : '确认密码' }}</label>
              <div class="pw-input-wrap">
                <input
                  v-model="form.confirmPassword"
                  :type="showResetConfirmPassword ? 'text' : 'password'"
                  class="pw-input"
                  name="tt-reset-confirm-password"
                  autocomplete="new-password"
                  :placeholder="isEn ? 'Re-enter new password' : '再次输入新密码'"
                  @keyup.enter="resetPassword"
                />
                <button
                  class="pw-toggle"
                  type="button"
                  tabindex="-1"
                  :aria-label="showResetConfirmPassword ? (isEn ? 'Hide password' : '隐藏密码') : (isEn ? 'Show password' : '显示密码')"
                  :class="{ visible: showResetConfirmPassword }"
                  @click="showResetConfirmPassword = !showResetConfirmPassword"
                />
              </div>
            </div>

            <p v-if="error" class="pw-error">{{ error }}</p>

            <button class="pw-btn primary" type="button" :disabled="loading" @click="resetPassword">
              {{ loading ? (isEn ? 'Saving...' : '保存中...') : isEn ? 'Reset Password' : '重置密码' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.pw-overlay {
  /* positioning handled by teacher-dialog.css */
  overscroll-behavior: contain;
  touch-action: manipulation;
}

.pw-panel {
  width: 92%;
  max-width: 420px;
  border-radius: 18px;
  background: var(--tt-bg-elevated, #fff);
  box-shadow: var(--tt-shadow-xl, 0 16px 48px rgba(0, 0, 0, 0.1));
  border: 1px solid var(--tt-border, #e5e7eb);
  overflow: hidden;
}

.pw-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 20px 24px 0;
}

.pw-back,
.pw-close {
  border: 0;
  background: transparent;
  color: var(--tt-text-secondary, #6b7280);
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  font-size: 18px;
  line-height: 1;
}

.pw-back:hover,
.pw-close:hover {
  background: var(--tt-surface-hover, #f9fafb);
  color: var(--tt-text, #111827);
}

.pw-title {
  flex: 1;
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--tt-text, #111827);
}

.pw-body {
  padding: 20px 24px 24px;
}

.pw-step {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.pw-hint {
  margin: 0;
  font-size: 13px;
  color: var(--tt-text-secondary, #6b7280);
  line-height: 1.5;
}

.pw-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.pw-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--tt-text, #111827);
}

.pw-input-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.pw-input {
  width: 100%;
  height: 40px;
  padding: 0 12px;
  border: 1px solid var(--tt-border, #e5e7eb);
  border-radius: 10px;
  background: var(--tt-surface, #fff);
  color: var(--tt-text, #111827);
  font-family: inherit;
  font-size: 14px;
  transition: border-color 150ms;
  touch-action: manipulation;
  user-select: text;
  -webkit-user-select: text;
}

.pw-input-wrap .pw-input {
  padding-right: 52px;
}

.pw-input:focus {
  outline: none;
  border-color: var(--tt-accent, #2563eb);
}

.pw-input::placeholder {
  color: var(--tt-text-tertiary, #9ca3af);
}

.pw-toggle {
  position: absolute;
  right: 8px;
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--tt-text-tertiary, #9ca3af);
  cursor: pointer;
  padding: 0;
}

.pw-toggle::before {
  position: absolute;
  left: 7px;
  top: 9px;
  width: 16px;
  height: 11px;
  border: 1.6px solid currentColor;
  border-radius: 50%;
  content: '';
}

.pw-toggle::after {
  position: absolute;
  left: 12px;
  top: 13px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  content: '';
}

.pw-toggle.visible {
  color: var(--tt-accent, #2563eb);
}

.pw-toggle:hover {
  background: var(--tt-surface-hover, #f9fafb);
}

.pw-code-row {
  display: flex;
  gap: 8px;
}

.code-input {
  flex: 1;
}

.pw-error {
  margin: 0;
  font-size: 13px;
  color: #dc2626;
  text-align: center;
}

.pw-btn {
  height: 40px;
  border: 1px solid var(--tt-border, #e5e7eb);
  border-radius: 10px;
  background: var(--tt-surface, #fff);
  color: var(--tt-text, #111827);
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 150ms;
}

.pw-btn:hover {
  background: var(--tt-surface-hover, #f9fafb);
}

.pw-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pw-btn.primary {
  background: var(--tt-accent, #2563eb);
  color: #fff;
  border-color: transparent;
}

.pw-btn.primary:hover {
  background: var(--tt-accent-hover, #1d4ed8);
}

.pw-btn.small {
  height: 40px;
  padding: 0 16px;
  font-size: 13px;
  flex-shrink: 0;
}

.pw-link {
  border: 0;
  background: transparent;
  color: var(--tt-accent, #2563eb);
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  padding: 0;
  text-align: center;
}

.pw-link:hover {
  color: var(--tt-accent-hover, #1d4ed8);
  text-decoration: underline;
}
</style>
