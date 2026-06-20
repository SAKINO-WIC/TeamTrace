<script setup>
import '../styles/admin-workspace.css'
import { computed, reactive, ref } from 'vue'
import { changeAdminOwnPassword } from '../services/admin'

const loading = ref(false)
const message = ref('')
const messageType = ref('info')
const showPassword = reactive({
  old: false,
  next: false,
  confirm: false,
})

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

function getErrorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || fallback
}

const passwordStrength = computed(() => {
  const value = form.newPassword.trim()
  let score = 0
  if (value.length >= 8) score += 1
  if (value.length >= 12) score += 1
  if (/[A-Z]/.test(value) && /[a-z]/.test(value)) score += 1
  if (/\d/.test(value)) score += 1
  if (/[^A-Za-z0-9]/.test(value)) score += 1

  if (score <= 2) return { label: '较弱', tone: 'warn', width: '36%' }
  if (score <= 4) return { label: '良好', tone: 'ok', width: '72%' }
  return { label: '很强', tone: 'strong', width: '100%' }
})

async function submitPassword() {
  const oldPassword = form.oldPassword.trim()
  const newPassword = form.newPassword.trim()
  const confirmPassword = form.confirmPassword.trim()

  if (!oldPassword) {
    setMessage('请输入当前密码。', 'error')
    return
  }

  if (newPassword.length < 8 || newPassword.length > 20) {
    setMessage('新密码长度需要在 8 到 20 个字符之间。', 'error')
    return
  }

  if (!/[A-Za-z]/.test(newPassword) || !/\d/.test(newPassword)) {
    setMessage('新密码需要同时包含字母和数字。', 'error')
    return
  }

  if (newPassword !== confirmPassword) {
    setMessage('两次输入的新密码不一致。', 'error')
    return
  }

  loading.value = true
  try {
    await changeAdminOwnPassword(oldPassword, newPassword)
    form.oldPassword = ''
    form.newPassword = ''
    form.confirmPassword = ''
    setMessage('管理员密码修改成功。', 'success')
  } catch (error) {
    setMessage(getErrorMessage(error, '管理员密码修改失败'), 'error')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="admin-page">
    <section class="admin-hero">
      <div class="admin-hero__meta">
        <p class="admin-eyebrow">Security</p>
        <span class="admin-chip">Sensitive action</span>
      </div>
      <h2 class="admin-hero__title">安全设置</h2>
      <p class="admin-hero__desc">
        当前页面只处理管理员自己的登录密码。涉及其他用户密码时，请在账户管理里按单个账户操作。
      </p>
    </section>

    <section class="security-layout">
      <article class="admin-panel">
        <div class="admin-panel__head">
          <div>
            <p class="admin-section-label">Password</p>
            <h3 class="admin-panel__title">修改我的密码</h3>
            <p class="admin-panel__desc">密码需要 8-20 位，并且同时包含字母和数字。</p>
          </div>
        </div>

        <div class="field-list">
          <label class="admin-field">
            <span>当前密码</span>
            <div class="password-row">
              <input
                v-model="form.oldPassword"
                :type="showPassword.old ? 'text' : 'password'"
                placeholder="请输入当前密码"
              />
              <button type="button" class="admin-btn-ghost toggle-btn" @click="showPassword.old = !showPassword.old">
                {{ showPassword.old ? '隐藏' : '显示' }}
              </button>
            </div>
          </label>

          <label class="admin-field">
            <span>新密码</span>
            <div class="password-row">
              <input
                v-model="form.newPassword"
                :type="showPassword.next ? 'text' : 'password'"
                placeholder="请输入 8-20 位新密码"
              />
              <button type="button" class="admin-btn-ghost toggle-btn" @click="showPassword.next = !showPassword.next">
                {{ showPassword.next ? '隐藏' : '显示' }}
              </button>
            </div>
          </label>

          <label class="admin-field">
            <span>确认新密码</span>
            <div class="password-row">
              <input
                v-model="form.confirmPassword"
                :type="showPassword.confirm ? 'text' : 'password'"
                placeholder="请再次输入新密码"
              />
              <button type="button" class="admin-btn-ghost toggle-btn" @click="showPassword.confirm = !showPassword.confirm">
                {{ showPassword.confirm ? '隐藏' : '显示' }}
              </button>
            </div>
          </label>
        </div>

        <div class="strength-card">
          <div class="strength-head">
            <span>密码强度</span>
            <strong :class="passwordStrength.tone">{{ passwordStrength.label }}</strong>
          </div>
          <div class="strength-track">
            <div class="strength-bar" :class="passwordStrength.tone" :style="{ width: passwordStrength.width }" />
          </div>
        </div>

        <div class="admin-actions submit-row">
          <button type="button" class="admin-btn" :disabled="loading" @click="submitPassword">
            {{ loading ? '提交中...' : '确认修改' }}
          </button>
        </div>
        <p v-if="message" class="admin-message" :class="messageType">{{ message }}</p>
      </article>
    </section>
  </div>
</template>

<style scoped>
.security-layout {
  display: grid;
  grid-template-columns: minmax(0, 640px);
}

.field-list {
  display: grid;
  gap: 14px;
}

.password-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
}

.toggle-btn {
  min-width: 84px;
}

.strength-card {
  margin-top: 18px;
  border: 1px solid var(--admin-border-strong);
  border-radius: var(--admin-radius-card);
  padding: 16px;
}

.strength-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  color: var(--admin-text-primary);
}

.strength-track {
  margin-top: 12px;
  width: 100%;
  height: 10px;
  border-radius: 999px;
  background: var(--admin-neutral-soft);
  overflow: hidden;
}

.strength-bar {
  height: 100%;
  border-radius: 999px;
  transition: width 0.2s ease;
}

.strength-bar.warn,
.warn {
  background: var(--admin-warning);
  color: var(--admin-warning);
}

.strength-bar.ok,
.ok {
  background: var(--admin-neutral-strong);
  color: var(--admin-neutral-strong);
}

.strength-bar.strong,
.strong {
  background: var(--admin-success);
  color: var(--admin-success);
}

.submit-row {
  margin-top: 18px;
}

@media (max-width: 920px) {
  .security-layout {
    grid-template-columns: 1fr;
  }

  .password-row {
    grid-template-columns: 1fr;
  }
}
</style>
