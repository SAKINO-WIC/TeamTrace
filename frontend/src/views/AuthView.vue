<script setup>
import { nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login, registerStudent, registerTeacher, sendEmailCode, resetPassword } from '../services/auth'
import {
  clearRememberedEmail,
  clearRememberedPassword,
  getRememberedEmail,
  getRememberedPassword,
  rememberEmail,
  rememberPassword,
  saveSession,
} from '../utils/auth'
import {
  ALLOWED_EMAIL_HINT,
  EMAIL_REGISTER_PLACEHOLDER,
  EMAIL_RESET_PLACEHOLDER,
  isAllowedEmail,
} from '../utils/email'

const tabs = [
  { key: 'login', label: '登录' },
  { key: 'register', label: '注册' },
]

const router = useRouter()
const route = useRoute()
const authStage = ref('intro')
const activeTab = ref('login')
const loading = ref(false)
const message = ref('')
const messageType = ref('info')
const forgotPasswordVisible = ref(false)
const forgotFormKey = ref(0)
const loginPasswordVisible = ref(false)
const registerPasswordVisible = ref(false)
const registerConfirmPasswordVisible = ref(false)
const resetPasswordVisible = ref(false)
const resetConfirmPasswordVisible = ref(false)
const authTraceCanvas = ref(null)
let authTraceAnimationId = 0
let authTraceResizeHandler = null

function showAuthForm(tab = 'login') {
  activeTab.value = tab
  authStage.value = 'form'
  message.value = ''
}

function openHiddenMoyuPage() {
  router.push({ path: '/moyu-terminator', query: { returnTo: route.fullPath || '/auth' } })
}

function openProductIntro() {
  router.push({ path: '/product', query: { returnTo: route.fullPath || '/auth' } })
}

function setupAuthTraceCanvas() {
  const canvas = authTraceCanvas.value
  const context = canvas?.getContext('2d')
  if (!canvas || !context) return

  let particles = []
  const particleCount = window.matchMedia('(max-width: 640px)').matches ? 34 : 64

  function resize() {
    const rect = canvas.getBoundingClientRect()
    const ratio = window.devicePixelRatio || 1
    canvas.width = Math.floor(rect.width * ratio)
    canvas.height = Math.floor(rect.height * ratio)
    context.setTransform(ratio, 0, 0, ratio, 0, 0)
    particles = Array.from({ length: particleCount }, () => ({
      x: Math.random() * rect.width,
      y: Math.random() * rect.height,
      vx: (Math.random() - 0.5) * 0.28,
      vy: (Math.random() - 0.5) * 0.28,
      r: Math.random() * 1.5 + 0.7,
    }))
  }

  function tick() {
    const rect = canvas.getBoundingClientRect()
    context.clearRect(0, 0, rect.width, rect.height)
    particles.forEach((particle, index) => {
      particle.x += particle.vx
      particle.y += particle.vy
      if (particle.x < 0 || particle.x > rect.width) particle.vx *= -1
      if (particle.y < 0 || particle.y > rect.height) particle.vy *= -1

      context.beginPath()
      context.arc(particle.x, particle.y, particle.r, 0, Math.PI * 2)
      context.fillStyle = 'oklch(0.82 0.15 190 / 0.4)'
      context.fill()

      for (let j = index + 1; j < particles.length; j += 1) {
        const other = particles[j]
        const distance = Math.hypot(particle.x - other.x, particle.y - other.y)
        if (distance < 125) {
          context.beginPath()
          context.moveTo(particle.x, particle.y)
          context.lineTo(other.x, other.y)
          context.strokeStyle = `oklch(0.82 0.15 190 / ${0.13 * (1 - distance / 125)})`
          context.lineWidth = 1
          context.stroke()
        }
      }
    })
    authTraceAnimationId = requestAnimationFrame(tick)
  }

  resize()
  authTraceResizeHandler = resize
  window.addEventListener('resize', authTraceResizeHandler)
  tick()
}

function teardownAuthTraceCanvas() {
  if (authTraceAnimationId) {
    cancelAnimationFrame(authTraceAnimationId)
    authTraceAnimationId = 0
  }
  if (authTraceResizeHandler) {
    window.removeEventListener('resize', authTraceResizeHandler)
    authTraceResizeHandler = null
  }
}

const loginForm = reactive({
  email: '',
  password: '',
  rememberAccount: true,
  rememberPassword: false,
})

const registerForm = reactive({
  name: '',
  email: '',
  verifyCode: '',
  password: '',
  confirmPassword: '',
  inviteCode: '',
})

const resetForm = reactive({
  email: '',
  verifyCode: '',
  newPassword: '',
  confirmPassword: '',
})
const resetCodeCooldown = ref(0)
const resetCodeSending = ref(false)
let resetCodeCooldownTimer = null

const codeCooldown = ref(0)
const codeSending = ref(false)
let codeCooldownTimer = null

function validateVerifyCode(code) {
  return /^\d{6}$/.test(code)
}

function clearCodeCooldownTimer() {
  if (codeCooldownTimer) {
    clearInterval(codeCooldownTimer)
    codeCooldownTimer = null
  }
}

function startCodeCooldown(seconds = 60) {
  clearCodeCooldownTimer()
  codeCooldown.value = seconds
  codeCooldownTimer = setInterval(() => {
    if (codeCooldown.value <= 1) {
      codeCooldown.value = 0
      clearCodeCooldownTimer()
      return
    }
    codeCooldown.value -= 1
  }, 1000)
}

async function sendRegisterCode() {
  if (!validateEmail(registerForm.email)) {
    setMessage(`请先输入有效邮箱（${ALLOWED_EMAIL_HINT}）`, 'error')
    return
  }
  if (codeCooldown.value > 0 || codeSending.value) {
    return
  }

  codeSending.value = true
  try {
    await sendEmailCode({ email: registerForm.email.trim(), purpose: 'register' })
    startCodeCooldown(60)
    setMessage('验证码已发送至邮箱，请查收（含垃圾箱）。', 'success')
  } catch (error) {
    setMessage(error.message || '验证码发送失败，请稍后重试。', 'error')
  } finally {
    codeSending.value = false
  }
}

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

function validateEmail(email) {
  return isAllowedEmail(email)
}

/** 用户界面只展示邮箱；11 位手机号仍可用于内部测试旧账号（不在 UI 中提示） */
function validateLoginAccount(account) {
  const trimmed = String(account || '').trim()
  return validateEmail(trimmed) || /^\d{11}$/.test(trimmed)
}

function validatePassword(password) {
  return /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^\w\s]).{8,24}$/.test(password)
}

function validateName(name) {
  const normalizedName = name?.trim() || ''
  return normalizedName.length > 0 && normalizedName.length <= 50
}


function extractToken(responseData) {
  return (
    responseData?.data?.token ||
    responseData?.data?.accessToken ||
    responseData?.data?.jwt ||
    responseData?.token ||
    responseData?.accessToken ||
    null
  )
}

function extractRole(responseData) {
  return responseData?.data?.role || responseData?.role || null
}

function redirectByRole(role) {
  if (role === 'teacher') {
    router.replace('/teacher')
    return
  }
  if (role === 'student') {
    router.replace('/student')
    return
  }
  if (role === 'admin') {
    router.replace('/admin')
    return
  }
  router.replace('/dashboard')
}

function clearRegisterForm() {
  registerForm.name = ''
  registerForm.email = ''
  registerForm.verifyCode = ''
  registerForm.password = ''
  registerForm.confirmPassword = ''
  registerForm.inviteCode = ''
  codeCooldown.value = 0
  clearCodeCooldownTimer()
}

function moveToLoginAfterRegister(roleLabel) {
  clearRegisterForm()
  activeTab.value = 'login'
  loginForm.email = ''
  loginForm.password = ''
  loginForm.rememberAccount = true
  loginForm.rememberPassword = false
  setMessage(`${roleLabel}注册成功，请返回登录页使用邮箱和密码登录。`, 'success')
}

function finishLoginSuccess(responseData, successText, fallbackUser = null) {
  const token = extractToken(responseData)
  const role = extractRole(responseData)

  if (!token || !role) {
    return false
  }

  saveSession(role, token, responseData?.data?.user || fallbackUser)
  setMessage(successText, 'success')
  redirectByRole(role)
  return true
}

function clearResetForm() {
  resetForm.email = ''
  resetForm.verifyCode = ''
  resetForm.newPassword = ''
  resetForm.confirmPassword = ''
  resetCodeCooldown.value = 0
  if (resetCodeCooldownTimer) {
    clearInterval(resetCodeCooldownTimer)
    resetCodeCooldownTimer = null
  }
}

async function openForgotPasswordModal() {
  clearResetForm()
  forgotFormKey.value += 1
  forgotPasswordVisible.value = true
  await nextTick()
  clearResetForm()
}

function closeForgotPasswordModal() {
  forgotPasswordVisible.value = false
  clearResetForm()
}

function startResetCodeCooldown(seconds = 60) {
  if (resetCodeCooldownTimer) clearInterval(resetCodeCooldownTimer)
  resetCodeCooldown.value = seconds
  resetCodeCooldownTimer = setInterval(() => {
    if (resetCodeCooldown.value <= 1) {
      resetCodeCooldown.value = 0
      clearInterval(resetCodeCooldownTimer)
      resetCodeCooldownTimer = null
      return
    }
    resetCodeCooldown.value -= 1
  }, 1000)
}

async function sendResetCode() {
  if (!validateEmail(resetForm.email)) {
    setMessage(`请先输入有效邮箱（${ALLOWED_EMAIL_HINT}）`, 'error')
    return
  }
  if (resetCodeCooldown.value > 0 || resetCodeSending.value) return

  resetCodeSending.value = true
  try {
    await sendEmailCode({ email: resetForm.email.trim(), purpose: 'reset_password' })
    startResetCodeCooldown(60)
    setMessage('重置验证码已发送，请查收邮箱。', 'success')
  } catch (error) {
    setMessage(error.message || '验证码发送失败', 'error')
  } finally {
    resetCodeSending.value = false
  }
}

async function submitResetPassword() {
  if (!validateEmail(resetForm.email)) {
    setMessage(`邮箱格式不正确（${ALLOWED_EMAIL_HINT}）`, 'error')
    return
  }
  if (!validateVerifyCode(resetForm.verifyCode)) {
    setMessage('请输入 6 位验证码。', 'error')
    return
  }
  if (!validatePassword(resetForm.newPassword)) {
    setMessage('密码需 8-24 位，且包含大小写字母、数字和特殊字符。', 'error')
    return
  }
  if (resetForm.newPassword !== resetForm.confirmPassword) {
    setMessage('两次输入的新密码不一致。', 'error')
    return
  }

  loading.value = true
  try {
    await resetPassword({
      email: resetForm.email.trim(),
      verifyCode: resetForm.verifyCode.trim(),
      newPassword: resetForm.newPassword,
    })
    closeForgotPasswordModal()
    setMessage('密码已重置，请使用新密码登录。', 'success')
  } catch (error) {
    setMessage(error.message || '重置失败', 'error')
  } finally {
    loading.value = false
  }
}

async function submitLogin() {
  if (!validateLoginAccount(loginForm.email)) {
    setMessage('请输入有效邮箱。', 'error')
    return
  }
  if (!loginForm.password) {
    setMessage('请输入密码。', 'error')
    return
  }

  loading.value = true
  try {
    const { data } = await login({
      email: loginForm.email.trim(),
      password: loginForm.password,
    })

    if (loginForm.rememberAccount) {
      rememberEmail(loginForm.email.trim())
      if (loginForm.rememberPassword) {
        rememberPassword(loginForm.email.trim(), loginForm.password)
      } else {
        clearRememberedPassword(loginForm.email.trim())
      }
    } else {
      clearRememberedEmail()
      clearRememberedPassword(loginForm.email.trim())
    }

    if (!finishLoginSuccess(data, '登录成功，正在进入系统首页。', { email: loginForm.email.trim() })) {
      setMessage('登录成功，但后端未返回完整的 token/role 字段，请和后端确认返回结构。', 'warn')
      return
    }
  } catch (error) {
    setMessage(error.message || '登录失败', 'error')
  } finally {
    loading.value = false
  }
}

async function submitRegister() {
  if (!validateName(registerForm.name)) {
    setMessage('请输入昵称，且长度不能超过 50 个字符。', 'error')
    return
  }
  if (!validateEmail(registerForm.email)) {
    setMessage(`邮箱格式不正确（${ALLOWED_EMAIL_HINT}）`, 'error')
    return
  }
  if (!validateVerifyCode(registerForm.verifyCode)) {
    setMessage('请输入 6 位邮箱验证码。', 'error')
    return
  }
  if (!validatePassword(registerForm.password)) {
    setMessage('密码需 8-24 位，且包含大小写字母、数字和特殊字符。', 'error')
    return
  }
  if (registerForm.password !== registerForm.confirmPassword) {
    setMessage('两次输入的密码不一致。', 'error')
    return
  }

  const inviteCode = registerForm.inviteCode.trim()

  loading.value = true
  try {
    if (inviteCode) {
      const { data } = await registerTeacher({
        name: registerForm.name.trim(),
        email: registerForm.email.trim(),
        verifyCode: registerForm.verifyCode.trim(),
        password: registerForm.password,
        inviteCode,
      })
      if (finishLoginSuccess(data, '教师注册成功，正在进入系统。')) {
        return
      }
      moveToLoginAfterRegister('教师')
      return
    }

    const { data } = await registerStudent({
      name: registerForm.name.trim(),
      email: registerForm.email.trim(),
      verifyCode: registerForm.verifyCode.trim(),
      password: registerForm.password,
    })
    if (finishLoginSuccess(data, '注册成功，正在进入系统。')) {
      return
    }
    moveToLoginAfterRegister('学生')
  } catch (error) {
    setMessage(error.message || '注册失败', 'error')
  } finally {
    loading.value = false
  }
}

onUnmounted(() => {
  clearCodeCooldownTimer()
  teardownAuthTraceCanvas()
})

watch(
  () => loginForm.email,
  (nextEmail, previousEmail) => {
    const normalized = String(nextEmail || '').trim()
    const previous = String(previousEmail || '').trim()
    if (!normalized) {
      loginForm.password = ''
      loginForm.rememberPassword = false
      return
    }
    if (normalized === previous) return
    const rememberedPw = getRememberedPassword(normalized)
    if (rememberedPw) {
      loginForm.password = rememberedPw
      loginForm.rememberPassword = true
      return
    }
    loginForm.password = ''
    loginForm.rememberPassword = false
  },
)

onMounted(() => {
  const remembered = getRememberedEmail()
  if (remembered) {
    loginForm.email = remembered
    loginForm.rememberAccount = true
    const rememberedPw = getRememberedPassword(remembered)
    if (rememberedPw) {
      loginForm.password = rememberedPw
      loginForm.rememberPassword = true
    }
  }
  setupAuthTraceCanvas()
})
</script>

<template>
  <div class="auth-page" :class="{ 'is-intro': authStage === 'intro' }">
    <canvas ref="authTraceCanvas" class="auth-trace-canvas" aria-hidden="true"></canvas>
    <div class="auth-ambient" aria-hidden="true">
      <span class="auth-orb auth-orb--1"></span>
      <span class="auth-orb auth-orb--2"></span>
      <span class="auth-orb auth-orb--3"></span>
      <span class="auth-orb auth-orb--4"></span>
      <span class="auth-float auth-float--1">班级闭环</span>
      <span class="auth-float auth-float--2">任务追踪</span>
      <span class="auth-float auth-float--3">过程评价</span>
      <span class="auth-float auth-float--4">申诉复盘</span>
      <span class="auth-float auth-float--5">小组协作</span>
      <span class="auth-float auth-float--6">成绩留痕</span>
    </div>

    <section v-if="authStage === 'intro'" class="entry-shell">
      <div class="entry-hero entry-hero--minimal">
        <div class="entry-copy">
          <div class="entry-brand">
            <button class="brand-mark brand-mark--logo" type="button" aria-label="打开摸鱼终结者隐藏主页" @click="openHiddenMoyuPage">
              <img src="/TeamTraceLogo.png" alt="TeamTrace" />
            </button>
            <div>
              <h1>TeamTrace</h1>
              <p class="entry-tagline">班级协作过程追踪系统</p>
            </div>
          </div>

          <div class="entry-actions">
            <button type="button" class="primary-entry-btn" @click="showAuthForm('login')">登录</button>
            <button type="button" class="secondary-entry-btn" @click="showAuthForm('register')">注册</button>
          </div>
          <div class="entry-meta-links" aria-label="TeamTrace 介绍链接">
            <button type="button" class="entry-meta-link" @click="openProductIntro">了解 TeamTrace</button>
            <span aria-hidden="true">·</span>
            <a class="entry-meta-link" href="/dev-trace.html">开发历程与技术栈</a>
          </div>
        </div>
      </div>
    </section>

    <div v-else v-show="!forgotPasswordVisible" class="auth-card">
      <div class="header">
        <p class="tag">TeamTrace</p>
        <h1>注册与登录</h1>
      </div>

      <div class="tab-row">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          class="tab-btn"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </div>

      <p v-if="message" class="message form-message" :class="messageType">
        {{ message }}
      </p>

      <form v-if="activeTab === 'login'" class="form" autocomplete="off" @submit.prevent="submitLogin">
        <input class="ghost-field" type="text" name="ghost-login-username" autocomplete="username" tabindex="-1" />
        <input class="ghost-field" type="password" name="ghost-login-password" autocomplete="current-password" tabindex="-1" />
        <label>邮箱</label>
        <input
          v-model.trim="loginForm.email"
          type="text"
          name="login-email"
          autocomplete="username"
          inputmode="email"
          placeholder="请输入邮箱"
        />
        <label>密码</label>
        <div class="password-field">
          <input
            v-model="loginForm.password"
            :type="loginPasswordVisible ? 'text' : 'password'"
            name="login-password"
            autocomplete="current-password"
            placeholder="请输入密码"
          />
          <button
            class="password-toggle"
            :class="{ visible: loginPasswordVisible }"
            type="button"
            :aria-label="loginPasswordVisible ? '隐藏密码' : '显示密码'"
            @click="loginPasswordVisible = !loginPasswordVisible"
          />
        </div>
        <div class="login-options-row">
          <div class="remember-options">
            <label class="checkbox-row">
              <input v-model="loginForm.rememberAccount" type="checkbox" />
              记住账户
            </label>
            <label class="checkbox-row">
              <input v-model="loginForm.rememberPassword" type="checkbox" class="remember-pw" />
              记住密码
            </label>
          </div>
          <button type="button" class="text-btn forgot-password-btn" @click="openForgotPasswordModal">忘记密码</button>
        </div>
        <button type="submit" class="submit-btn" :disabled="loading">{{ loading ? '提交中...' : '立即登录' }}</button>
      </form>

      <form v-else class="form" autocomplete="off" @submit.prevent="submitRegister">
        <input class="ghost-field" type="text" name="ghost-username" autocomplete="username" tabindex="-1" />
        <input class="ghost-field" type="password" name="ghost-password" autocomplete="new-password" tabindex="-1" />
        <label>昵称</label>
        <input
          v-model.trim="registerForm.name"
          type="text"
          name="register-name"
          autocomplete="off"
          placeholder="建议使用正式姓名"
        />
        <label>邮箱</label>
        <input
          v-model.trim="registerForm.email"
          type="email"
          name="register-email"
          autocomplete="off"
          :placeholder="EMAIL_REGISTER_PLACEHOLDER"
        />
        <label>验证码</label>
        <div class="verify-inline">
          <input
            v-model.trim="registerForm.verifyCode"
            class="verify-inline-code"
            type="text"
            name="register-verify-code"
            inputmode="numeric"
            autocomplete="one-time-code"
            maxlength="6"
            placeholder="6 位验证码"
          />
          <button
            type="button"
            class="verify-inline-action"
            :disabled="codeSending || codeCooldown > 0"
            @click="sendRegisterCode"
          >
            {{ codeCooldown > 0 ? `${codeCooldown}s` : codeSending ? '发送中' : '获取验证码' }}
          </button>
        </div>
        <label>密码</label>
        <div class="password-field">
          <input
            v-model="registerForm.password"
            :type="registerPasswordVisible ? 'text' : 'password'"
            name="register-password"
            autocomplete="new-password"
            placeholder="8-24 位，含大小写字母+数字+特殊字符"
          />
          <button
            class="password-toggle"
            :class="{ visible: registerPasswordVisible }"
            type="button"
            :aria-label="registerPasswordVisible ? '隐藏密码' : '显示密码'"
            @click="registerPasswordVisible = !registerPasswordVisible"
          />
        </div>
        <label>确认密码</label>
        <div class="password-field">
          <input
            v-model="registerForm.confirmPassword"
            :type="registerConfirmPasswordVisible ? 'text' : 'password'"
            name="register-password-confirm"
            autocomplete="new-password"
            placeholder="请再次输入密码"
          />
          <button
            class="password-toggle"
            :class="{ visible: registerConfirmPasswordVisible }"
            type="button"
            :aria-label="registerConfirmPasswordVisible ? '隐藏密码' : '显示密码'"
            @click="registerConfirmPasswordVisible = !registerConfirmPasswordVisible"
          />
        </div>
        <label>教师邀请码（选填）</label>
        <input
          v-model="registerForm.inviteCode"
          type="text"
          name="teacher-invite-code"
          autocomplete="off"
          placeholder="选填，填写则注册为教师"
        />
        <button type="submit" class="submit-btn" :disabled="loading">{{ loading ? '提交中...' : '立即注册' }}</button>
      </form>
    </div>

    <div v-if="forgotPasswordVisible" class="modal-mask" @click.self="closeForgotPasswordModal">
      <div class="auth-card forgot-modal" role="dialog" aria-labelledby="forgot-password-title">
        <div class="header">
          <h1 id="forgot-password-title">重置密码</h1>
        </div>
        <p v-if="message" class="message form-message forgot-modal__message" :class="messageType">
          {{ message }}
        </p>
        <form
          :key="forgotFormKey"
          class="form forgot-reset-form"
          autocomplete="off"
          @submit.prevent="submitResetPassword"
        >
          <input class="ghost-field" type="text" name="forgot-decoy-username" autocomplete="username" tabindex="-1" />
          <input class="ghost-field" type="password" name="forgot-decoy-password" autocomplete="current-password" tabindex="-1" />
          <label>邮箱</label>
          <input
            v-model.trim="resetForm.email"
            type="email"
            name="forgot-reset-email"
            autocomplete="off"
            :placeholder="EMAIL_RESET_PLACEHOLDER"
          />
          <label>验证码</label>
          <div class="verify-inline">
            <input
              v-model.trim="resetForm.verifyCode"
              class="verify-inline-code"
              type="text"
              name="forgot-reset-verify-code"
              inputmode="numeric"
              autocomplete="one-time-code"
              maxlength="6"
              placeholder="6 位验证码"
            />
            <button
              type="button"
              class="verify-inline-action"
              :disabled="resetCodeSending || resetCodeCooldown > 0"
              @click="sendResetCode"
            >
              {{ resetCodeCooldown > 0 ? `${resetCodeCooldown}s` : resetCodeSending ? '发送中...' : '获取验证码' }}
            </button>
          </div>
          <label>新密码</label>
          <div class="password-field">
            <input
              v-model="resetForm.newPassword"
              :type="resetPasswordVisible ? 'text' : 'password'"
              name="forgot-reset-new-password"
              autocomplete="new-password"
              placeholder="8-24 位，含大小写字母+数字+特殊字符"
            />
            <button
              class="password-toggle"
              :class="{ visible: resetPasswordVisible }"
              type="button"
              :aria-label="resetPasswordVisible ? '隐藏密码' : '显示密码'"
              @click="resetPasswordVisible = !resetPasswordVisible"
            />
          </div>
          <label>确认新密码</label>
          <div class="password-field">
            <input
              v-model="resetForm.confirmPassword"
              :type="resetConfirmPasswordVisible ? 'text' : 'password'"
              name="forgot-reset-confirm-password"
              autocomplete="new-password"
              placeholder="请再次输入新密码"
            />
            <button
              class="password-toggle"
              :class="{ visible: resetConfirmPasswordVisible }"
              type="button"
              :aria-label="resetConfirmPasswordVisible ? '隐藏密码' : '显示密码'"
              @click="resetConfirmPasswordVisible = !resetConfirmPasswordVisible"
            />
          </div>
          <div class="forgot-modal__actions">
            <button type="button" class="forgot-modal__cancel" @click="closeForgotPasswordModal">取消</button>
            <button type="submit" class="submit-btn forgot-modal__submit" :disabled="loading">
              {{ loading ? '提交中...' : '确认重置' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
:global(:root) {
  --tt-accent: oklch(0.56 0.16 256);
  --tt-accent-strong: oklch(0.46 0.18 256);
  --tt-danger: oklch(0.58 0.2 28);
  --tt-bg: oklch(0.97 0.01 248);
  --tt-surface: oklch(0.995 0.003 248);
  --tt-surface-muted: oklch(0.955 0.015 248);
  --tt-border: oklch(0.88 0.018 248);
  --tt-text-primary: oklch(0.22 0.025 248);
  --tt-text-secondary: oklch(0.48 0.025 248);
  --tt-text-tertiary: oklch(0.62 0.02 248);
  --tt-shadow: 0 24px 70px rgba(37, 54, 93, 0.14);
}

:global(*),
:global(*::before),
:global(*::after) {
  box-sizing: border-box;
}

:global(body) {
  margin: 0;
  font-family: 'SF Pro SC', 'PingFang SC', 'SF Pro Text', -apple-system, BlinkMacSystemFont, 'Segoe UI',
    Roboto, sans-serif;
  background: var(--tt-bg);
  color: var(--tt-text-primary);
}

.auth-page {
  position: relative;
  min-height: 100vh;
  display: grid;
  place-items: center;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 18px;
  background:
    radial-gradient(circle at 10% 12%, oklch(0.62 0.16 190 / 0.18), transparent 28rem),
    radial-gradient(circle at 88% 0%, oklch(0.61 0.16 282 / 0.16), transparent 30rem),
    linear-gradient(135deg, oklch(0.145 0.035 252), oklch(0.105 0.035 260));
}

.auth-page.is-intro {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 24px 18px;
}

.auth-page::before {
  position: fixed;
  inset: 0;
  z-index: 0;
  content: '';
  background-image:
    linear-gradient(oklch(1 0 0 / 0.035) 1px, transparent 1px),
    linear-gradient(90deg, oklch(1 0 0 / 0.035) 1px, transparent 1px);
  background-size: 72px 72px;
  mask-image: linear-gradient(to bottom, black, transparent 84%);
  pointer-events: none;
}

.auth-page::after {
  position: fixed;
  inset: -15%;
  z-index: 0;
  content: '';
  background:
    linear-gradient(112deg, transparent 8%, oklch(0.82 0.15 190 / 0.1) 38%, transparent 62%),
    linear-gradient(200deg, transparent 20%, oklch(0.68 0.14 285 / 0.1) 48%, transparent 72%);
  pointer-events: none;
  animation: entryLightDrift 16s ease-in-out infinite alternate;
}

.auth-trace-canvas {
  position: fixed;
  inset: 0;
  z-index: 0;
  width: 100%;
  height: 100%;
  opacity: 0.72;
  pointer-events: none;
}

.auth-ambient {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}

.auth-page.is-intro .entry-shell {
  position: relative;
  z-index: 2;
}

.auth-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(56px);
  opacity: 0.8;
  animation: orbFloat 18s cubic-bezier(0.45, 0, 0.55, 1) infinite;
}

.auth-orb--1 {
  width: min(48vw, 480px);
  height: min(48vw, 480px);
  top: -8%;
  left: -6%;
  background: oklch(0.62 0.16 190 / 0.28);
  animation-duration: 14s;
}

.auth-orb--2 {
  width: min(42vw, 420px);
  height: min(42vw, 420px);
  top: 10%;
  right: -12%;
  background: oklch(0.61 0.16 282 / 0.24);
  animation-delay: -4s;
  animation-duration: 16s;
}

.auth-orb--3 {
  width: min(38vw, 380px);
  height: min(38vw, 380px);
  bottom: -8%;
  left: 14%;
  background: oklch(0.68 0.17 248 / 0.2);
  animation-delay: -8s;
  animation-duration: 20s;
}

.auth-orb--4 {
  width: min(30vw, 300px);
  height: min(30vw, 300px);
  bottom: 20%;
  right: 8%;
  background: oklch(0.77 0.16 62 / 0.14);
  animation-delay: -2s;
  animation-duration: 14s;
}

.auth-page:not(.is-intro) .auth-float {
  display: none;
}

.auth-float {
  position: absolute;
  padding: 11px 20px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(16px);
  color: rgba(235, 244, 255, 0.78);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.06em;
  white-space: nowrap;
  box-shadow: 0 14px 36px rgba(0, 0, 0, 0.22);
  animation: chipFloat 11s cubic-bezier(0.45, 0, 0.55, 1) infinite;
  will-change: transform;
}

.auth-float--1 { top: 10%; left: 5%; animation-delay: 0s; }
.auth-float--2 { top: 18%; right: 6%; animation-delay: -2s; }
.auth-float--3 { bottom: 24%; left: 4%; animation-delay: -4s; }
.auth-float--4 { bottom: 14%; right: 5%; animation-delay: -6s; }
.auth-float--5 { top: 38%; left: 10%; animation-delay: -3s; opacity: 0.72; }
.auth-float--6 { top: 44%; right: 10%; animation-delay: -7s; opacity: 0.72; }

@media (max-width: 720px) {
  .auth-float {
    display: none;
  }

  .auth-orb--1,
  .auth-orb--2 {
    opacity: 0.55;
  }
}

.entry-shell {
  position: relative;
  box-sizing: border-box;
  width: min(100%, 540px);
  margin: 0 auto;
  flex-shrink: 0;
  border: 1px solid rgba(210, 225, 255, 0.24);
  border-radius: 30px;
  overflow: hidden;
  background:
    linear-gradient(160deg, rgba(6, 14, 34, 0.96), rgba(10, 30, 72, 0.9)),
    radial-gradient(ellipse at 70% 20%, rgba(102, 174, 255, 0.32), transparent 52%);
  box-shadow:
    0 28px 80px rgba(3, 10, 28, 0.48),
    0 0 0 1px rgba(120, 180, 255, 0.06) inset;
  animation:
    shellReveal 0.82s cubic-bezier(0.16, 1, 0.3, 1) both,
    cardBreath 9s cubic-bezier(0.45, 0, 0.55, 1) 0.9s infinite;
  will-change: transform;
}

.entry-shell::before {
  position: absolute;
  inset: 0;
  z-index: 0;
  content: '';
  background:
    linear-gradient(160deg, rgba(255, 255, 255, 0.1), transparent 36%),
    repeating-linear-gradient(90deg, rgba(225, 238, 255, 0.04) 0 1px, transparent 1px 72px);
  pointer-events: none;
}

.entry-shell::after {
  position: absolute;
  inset: 0;
  z-index: 0;
  content: '';
  background: linear-gradient(
    105deg,
    transparent 28%,
    rgba(224, 241, 255, 0.14) 46%,
    transparent 62%
  );
  opacity: 0;
  pointer-events: none;
  will-change: opacity, transform;
  animation: shellSweep 6.5s cubic-bezier(0.16, 1, 0.3, 1) 1s infinite;
}

.entry-brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.entry-brand .brand-mark {
  width: 68px;
  height: 68px;
  border-radius: 20px;
  font-size: 22px;
  animation: markPulse 5s ease-in-out infinite;
}

.brand-mark {
  border: 0;
  display: inline-grid;
  place-items: center;
  background: linear-gradient(145deg, oklch(0.65 0.16 251), oklch(0.5 0.16 256));
  color: oklch(0.98 0.006 248);
  font-weight: 800;
  letter-spacing: 0.02em;
  box-shadow: 0 10px 30px rgba(39, 111, 217, 0.36);
}

.brand-mark--logo {
  overflow: hidden;
  cursor: pointer;
  background: oklch(0.985 0.006 248);
  box-shadow:
    0 18px 44px oklch(0.08 0.04 252 / 0.38),
    0 0 0 1px oklch(1 0 0 / 0.3) inset;
}

.brand-mark--logo:focus-visible {
  outline: 3px solid oklch(0.78 0.14 218);
  outline-offset: 5px;
}

.brand-mark--logo img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
  object-position: center;
}

.entry-meta-links {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 18px;
  color: rgba(226, 238, 255, 0.74);
  font-size: 13px;
  font-weight: 800;
}

.entry-meta-link {
  border: 0;
  border-radius: 999px;
  padding: 7px 10px;
  background: transparent;
  color: inherit;
  font: inherit;
  text-decoration: none;
  cursor: pointer;
  transition:
    color 0.18s ease,
    background 0.18s ease;
}

.entry-meta-link:hover {
  background: rgba(255, 255, 255, 0.1);
  color: rgba(250, 253, 255, 0.96);
}

.entry-tagline {
  margin: 10px 0 0;
  color: rgba(235, 244, 255, 0.8);
  font-size: 15px;
  font-weight: 500;
  line-height: 1.5;
}

.entry-hero.entry-hero--minimal h1 {
  max-width: 100%;
  margin: 0;
  font-size: clamp(32px, 5.5vw, 42px);
  line-height: 1.12;
  word-break: keep-all;
}

.entry-hero.entry-hero--minimal .entry-actions {
  justify-content: center;
  width: 100%;
  max-width: 380px;
  margin-top: 40px;
  gap: 14px;
}

.entry-hero {
  position: relative;
  z-index: 2;
  height: calc(100% - 76px);
  max-width: none;
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(380px, 0.82fr);
  gap: clamp(28px, 4vw, 46px);
  align-items: center;
  margin: 0;
  padding: clamp(28px, 4.8vw, 58px) clamp(34px, 7vw, 82px) 48px;
}

/* 精简介绍页：必须写在 .entry-hero 之后，避免被旧双栏网格覆盖 */
.entry-hero.entry-hero--minimal {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: auto;
  min-height: 0;
  margin: 0;
  padding: clamp(44px, 8vw, 68px) clamp(28px, 6vw, 48px);
  grid-template-columns: none;
  gap: 0;
  isolation: isolate;
}

.entry-hero.entry-hero--minimal .entry-copy {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  box-sizing: border-box;
  padding: 0 4px;
  animation: none;
}

.entry-copy {
  max-width: 660px;
  animation: fadeUp 0.62s cubic-bezier(0.16, 1, 0.3, 1) 0.16s both;
}

.eyebrow {
  margin: 0 0 12px;
  color: oklch(0.86 0.08 238);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.entry-hero h1 {
  max-width: 12em;
  margin: 0;
  color: rgba(250, 253, 255, 0.99);
  font-size: clamp(38px, 4.8vw, 64px);
  line-height: 1.06;
  font-weight: 900;
  letter-spacing: 0;
  text-shadow: 0 18px 42px rgba(0, 14, 44, 0.5);
}

.entry-desc {
  max-width: 54ch;
  margin: 18px 0 0;
  color: rgba(239, 246, 255, 0.88);
  font-size: 15px;
  line-height: 1.72;
}

.entry-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 26px;
}

.entry-hero.entry-hero--minimal .primary-entry-btn,
.entry-hero.entry-hero--minimal .secondary-entry-btn {
  flex: 1;
  height: 54px;
  min-width: 0;
  border-radius: 16px;
  font-size: 15px;
}

.primary-entry-btn,
.secondary-entry-btn {
  position: relative;
  height: 48px;
  min-width: 138px;
  border-radius: 14px;
  overflow: hidden;
  padding: 0 22px;
  border: 1px solid transparent;
  cursor: pointer;
  font-size: 14px;
  font-weight: 800;
  transition:
    transform 0.18s ease,
    border-color 0.18s ease,
    background 0.18s ease;
}

.primary-entry-btn::before,
.secondary-entry-btn::before {
  position: absolute;
  inset: 0;
  content: '';
  background: linear-gradient(110deg, transparent 0 34%, rgba(255, 255, 255, 0.34) 48%, transparent 62%);
  opacity: 0;
  transform: translateX(-80%);
  transition:
    opacity 0.22s ease,
    transform 0.62s cubic-bezier(0.16, 1, 0.3, 1);
}

.primary-entry-btn {
  background: linear-gradient(135deg, oklch(0.68 0.16 246), oklch(0.56 0.16 256));
  color: oklch(0.98 0.006 248);
  box-shadow: 0 16px 38px rgba(48, 116, 226, 0.32);
}

.secondary-entry-btn {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(232, 240, 255, 0.26);
  color: rgba(245, 249, 255, 0.92);
  backdrop-filter: blur(14px);
}

.primary-entry-btn:hover,
.secondary-entry-btn:hover {
  transform: translateY(-2px);
}

.primary-entry-btn:hover::before,
.secondary-entry-btn:hover::before {
  opacity: 1;
  transform: translateX(80%);
}

.entry-stats {
  display: flex;
  gap: 18px;
  margin-top: 28px;
}

.entry-stats div {
  min-width: 92px;
  padding-right: 18px;
  border-right: 1px solid rgba(226, 237, 255, 0.22);
}

.entry-stats div:last-child {
  border-right: 0;
}

.entry-stats strong {
  display: block;
  color: rgba(248, 251, 255, 0.96);
  font-size: 26px;
  line-height: 1;
}

.entry-stats span {
  display: block;
  margin-top: 6px;
  color: rgba(235, 244, 255, 0.78);
  font-size: 12px;
}

.entry-product {
  position: relative;
  height: min(460px, 100%);
  min-height: 410px;
  animation: fadeUp 0.7s cubic-bezier(0.16, 1, 0.3, 1) 0.24s both;
}

.product-window {
  position: absolute;
  z-index: 2;
  overflow: hidden;
  border: 1px solid rgba(232, 242, 255, 0.32);
  background: rgba(236, 245, 255, 0.17);
  color: rgba(250, 253, 255, 0.96);
  backdrop-filter: blur(22px);
  box-shadow: 0 24px 80px rgba(0, 8, 26, 0.34);
}

.product-window::after {
  position: absolute;
  inset: 0;
  content: '';
  border-radius: inherit;
  background: linear-gradient(125deg, rgba(255, 255, 255, 0.16), transparent 28% 72%, rgba(111, 183, 255, 0.14));
  pointer-events: none;
}

.main-window {
  z-index: 3;
  top: 6px;
  right: 4px;
  width: min(100%, 398px);
  border-radius: 26px;
  overflow: hidden;
  animation: panelFloat 6s ease-in-out infinite;
}

.floating-window {
  z-index: 4;
  right: 232px;
  bottom: 58px;
  width: 240px;
  border-radius: 22px;
  padding: 16px;
  animation: panelFloat 6.8s ease-in-out 0.4s infinite;
}

.score-window {
  z-index: 5;
  right: 0;
  bottom: 0;
  width: 230px;
  border-radius: 20px;
  padding: 18px;
  animation: panelFloat 7.2s ease-in-out 0.8s infinite;
}

.window-top {
  display: flex;
  gap: 7px;
  align-items: center;
  padding: 15px 18px;
  border-bottom: 1px solid rgba(229, 239, 255, 0.16);
}

.window-top span {
  width: 9px;
  height: 9px;
  border-radius: 999px;
}

.window-top span:nth-child(1) {
  background: oklch(0.66 0.19 29);
}

.window-top span:nth-child(2) {
  background: oklch(0.82 0.16 86);
}

.window-top span:nth-child(3) {
  background: oklch(0.73 0.18 150);
}

.window-top strong {
  margin-left: auto;
  color: rgba(250, 253, 255, 0.98);
  font-size: 12px;
}

.window-top.compact {
  padding: 0 0 14px;
}

.window-top.compact strong {
  margin-left: 0;
}

.window-body {
  display: grid;
  gap: 14px;
  padding: 18px;
}

.metric-stack {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.metric-stack article {
  min-height: 78px;
  display: grid;
  gap: 8px;
  align-content: center;
  padding: 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.14);
}

.metric-stack span,
.progress-row span,
.score-window p,
.mini-task-list span {
  color: rgba(239, 246, 255, 0.82);
  font-size: 12px;
}

.metric-stack strong {
  color: rgba(250, 253, 255, 0.98);
  font-size: 30px;
  line-height: 1;
}

.flow-line {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 7px;
}

.flow-line span,
.mini-task-list span {
  min-height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  text-align: center;
  color: rgba(242, 248, 255, 0.9);
  font-weight: 700;
}

.flow-line .done {
  background: rgba(109, 184, 255, 0.26);
  color: rgba(250, 253, 255, 0.98);
}

.flow-line .active {
  background: oklch(0.62 0.15 251);
  color: oklch(0.99 0.006 248);
}

.progress-preview {
  display: grid;
  gap: 9px;
  padding: 14px;
  border-radius: 18px;
  background: rgba(5, 13, 32, 0.28);
}

.progress-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.progress-row strong {
  color: rgba(248, 251, 255, 0.96);
}

.progress-row.muted {
  padding-top: 4px;
}

.progress-track {
  width: 100%;
  height: 9px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(229, 238, 255, 0.13);
}

.progress-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, oklch(0.74 0.13 223), oklch(0.62 0.15 251));
  animation: trackGlow 2.6s ease-in-out infinite;
}

.mini-task-list {
  display: grid;
  gap: 9px;
}

.score-window strong {
  display: block;
  color: rgba(250, 253, 255, 0.98);
  font-size: 17px;
}

.score-window p {
  margin: 8px 0 0;
  line-height: 1.6;
}

.connector {
  position: absolute;
  z-index: 0;
  border: solid rgba(154, 205, 255, 0.58);
  filter: drop-shadow(0 0 10px rgba(94, 166, 255, 0.62));
  pointer-events: none;
  animation: lineGlow 3.4s ease-in-out infinite;
}

.connector-one {
  top: 110px;
  right: 324px;
  width: 158px;
  height: 82px;
  border-width: 1px 0 0 1px;
}

.connector-two {
  right: 168px;
  bottom: 106px;
  width: 150px;
  height: 68px;
  border-width: 0 0 1px 1px;
}

.auth-card {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 560px;
  overflow: hidden;
  background:
    linear-gradient(150deg, oklch(1 0 0 / 0.82), oklch(0.96 0.018 246 / 0.68)),
    radial-gradient(circle at 18% 0, oklch(0.86 0.09 220 / 0.18), transparent 36rem) !important;
  border: 1px solid oklch(1 0 0 / 0.62);
  border-radius: 24px;
  padding: 30px 24px;
  box-shadow:
    0 28px 86px oklch(0.09 0.05 252 / 0.46),
    0 1px 0 oklch(1 0 0 / 0.78) inset,
    0 0 0 1px oklch(0.82 0.05 248 / 0.2) inset;
  color: oklch(0.18 0.018 250) !important;
  backdrop-filter: blur(24px) saturate(1.18);
  -webkit-backdrop-filter: blur(24px) saturate(1.18);
}

.auth-card::before {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    linear-gradient(115deg, oklch(1 0 0 / 0.42), transparent 34%),
    radial-gradient(circle at 84% 12%, oklch(0.78 0.12 218 / 0.14), transparent 18rem);
  content: '';
}

.auth-card > * {
  position: relative;
  z-index: 1;
}

.auth-card .tag {
  color: oklch(0.52 0.18 256);
}

.auth-card h1 {
  color: oklch(0.16 0.018 252) !important;
}

.auth-card .desc,
.auth-card label {
  color: oklch(0.34 0.026 252) !important;
}

.auth-card input,
.auth-card .verify-inline-code {
  color: oklch(0.2 0.018 252) !important;
  background: oklch(1 0 0 / 0.78) !important;
  -webkit-text-fill-color: oklch(0.2 0.018 252) !important;
  caret-color: #1c1c1e !important;
  border-color: oklch(0.76 0.026 248 / 0.72) !important;
}

.auth-card input::placeholder,
.auth-card .verify-inline-code::placeholder {
  color: oklch(0.58 0.018 252 / 0.82) !important;
  -webkit-text-fill-color: oklch(0.58 0.018 252 / 0.82) !important;
}

.auth-card .verify-inline {
  background: transparent !important;
  border-color: transparent !important;
}

.auth-card .tab-btn:not(.active) {
  background: oklch(1 0 0 / 0.46) !important;
  color: oklch(0.42 0.022 252) !important;
  border-color: oklch(1 0 0 / 0.58) !important;
}

.auth-card .form-message {
  margin: 0 0 12px;
}

.header {
  margin-bottom: 18px;
}

.tag {
  margin: 0;
  color: var(--tt-accent);
  font-size: 14px;
  font-weight: 600;
}

h1 {
  margin: 8px 0 6px;
  font-size: 32px;
  line-height: 1.2;
  font-weight: 600;
  color: var(--tt-text-primary);
}

.desc {
  margin: 0;
  color: var(--tt-text-secondary);
  font-size: 14px;
}

.tab-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  margin-bottom: 16px;
}

.tab-btn {
  height: 44px;
  border: 1px solid oklch(0.8 0.02 248 / 0.82);
  border-radius: 14px;
  background: oklch(1 0 0 / 0.54);
  color: var(--tt-text-secondary);
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
  transition:
    transform 0.18s ease,
    border-color 0.18s ease,
    background 0.18s ease,
    box-shadow 0.18s ease;
}

.tab-btn.active {
  color: oklch(0.99 0.004 248);
  background: linear-gradient(135deg, oklch(0.64 0.18 252), oklch(0.55 0.19 258));
  border-color: oklch(0.66 0.18 252 / 0.88);
  box-shadow: 0 14px 30px oklch(0.46 0.19 258 / 0.32);
}

.tab-btn:hover {
  transform: translateY(-1px);
}

.form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ghost-field {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

label {
  margin-top: 4px;
  font-size: 13px;
  color: var(--tt-text-secondary);
  font-weight: 500;
}

input {
  width: 100%;
  height: 46px;
  border: 1px solid oklch(0.8 0.02 248 / 0.82);
  border-radius: 14px;
  padding: 0 14px;
  font-size: 14px;
  color: var(--tt-text-primary);
  background: oklch(1 0 0 / 0.78);
  outline: none;
  box-shadow:
    0 1px 0 oklch(1 0 0 / 0.58) inset,
    0 10px 24px oklch(0.12 0.04 252 / 0.05);
  transition:
    border-color 0.16s ease,
    background 0.16s ease,
    box-shadow 0.16s ease;
}

input::placeholder {
  color: #b2b2b8;
}

input:focus {
  border-color: oklch(0.58 0.18 256);
  background: oklch(1 0 0 / 0.9);
  box-shadow:
    0 0 0 4px oklch(0.65 0.17 252 / 0.16),
    0 12px 28px oklch(0.12 0.04 252 / 0.08);
}

.password-field {
  position: relative;
  display: flex;
  align-items: center;
}

.password-field input {
  padding-right: 46px;
}

.password-toggle {
  position: absolute;
  right: 8px;
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #7a7a84;
  cursor: pointer;
}

.password-toggle::before {
  position: absolute;
  left: 7px;
  top: 9px;
  width: 16px;
  height: 11px;
  border: 1.6px solid currentColor;
  border-radius: 50%;
  content: '';
}

.password-toggle::after {
  position: absolute;
  left: 12px;
  top: 13px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  content: '';
}

.password-toggle.visible {
  color: var(--tt-accent);
}

.password-toggle.visible::before {
  transform: rotate(-10deg);
}

.password-toggle:hover {
  background: rgba(0, 122, 255, 0.08);
}

.verify-inline {
  display: flex;
  align-items: center;
  gap: 8px;
}

.verify-inline-code {
  flex: 1;
  min-width: 0;
  text-align: left;
  letter-spacing: 0.08em;
  font-variant-numeric: tabular-nums;
}

.verify-inline-action {
  flex-shrink: 0;
  min-width: 96px;
  height: 46px;
  padding: 0 12px;
  border: 1px solid oklch(0.8 0.02 248 / 0.82);
  border-radius: 14px;
  background: oklch(1 0 0 / 0.58);
  color: oklch(0.5 0.18 256);
  font-size: 13px;
  font-weight: 800;
  white-space: nowrap;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background 0.15s ease,
    transform 0.15s ease;
}

.verify-inline-action:hover:not(:disabled) {
  border-color: oklch(0.58 0.18 256);
  background: oklch(1 0 0 / 0.8);
  transform: translateY(-1px);
}

.verify-inline-action:disabled {
  color: #aeaeb2;
  border-color: #e5e5ea;
  cursor: not-allowed;
}

.login-options-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 4px;
}

.remember-options {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  min-width: 0;
}

.checkbox-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-top: 0;
  color: var(--tt-text-secondary);
  font-size: 13px;
  white-space: nowrap;
}

.checkbox-row input {
  width: 16px;
  height: 16px;
  margin: 0;
  box-shadow: none;
}

.text-btn {
  border: 0;
  padding: 0;
  background: transparent;
  color: oklch(0.47 0.18 256);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}

.submit-btn {
  margin-top: 10px;
  height: 48px;
  border: 0;
  border-radius: 14px;
  background: linear-gradient(135deg, oklch(0.64 0.18 252), oklch(0.55 0.19 258));
  color: oklch(0.99 0.004 248);
  font-size: 15px;
  font-weight: 850;
  cursor: pointer;
  box-shadow: 0 18px 38px oklch(0.45 0.19 258 / 0.34);
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease;
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 22px 44px oklch(0.45 0.19 258 / 0.4);
}

.submit-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.message {
  margin: 14px 0 0;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 14px;
  line-height: 1.4;
}

.message.info {
  color: var(--tt-text-primary);
  background: #f2f2f7;
}

.message.success {
  color: #0f5132;
  background: #eaf8ef;
}

.message.warn {
  color: #664d03;
  background: #fff8db;
}

.message.error {
  color: #8a1c17;
  background: rgba(255, 59, 48, 0.12);
  border: 1px solid rgba(255, 59, 48, 0.28);
}


.modal-mask {
  position: fixed;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 18px;
  background: rgba(28, 28, 30, 0.34);
}

.forgot-modal {
  margin: 0;
  width: 100%;
  max-width: 480px;
  padding: 24px 24px 20px;
  animation: shellReveal 0.35s ease both;
}

.forgot-modal .header {
  margin-bottom: 14px;
}

.forgot-modal .header h1 {
  margin: 0;
  font-size: 26px;
  line-height: 1.25;
}

.forgot-reset-form {
  gap: 10px;
}

.forgot-modal__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
  padding-top: 16px;
  border-top: 1px solid #e8e8ed;
}

.forgot-modal__cancel {
  height: 44px;
  padding: 0 18px;
  border: 1px solid #d2d2d7;
  border-radius: 10px;
  background: #fff;
  color: #3a3a3c;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.forgot-modal__cancel:hover {
  border-color: #b8b8be;
  background: #f8f8fa;
}

.forgot-modal__submit {
  margin-top: 0;
  width: auto;
  min-width: 128px;
  padding: 0 22px;
}

@keyframes shellReveal {
  from {
    opacity: 0;
    transform: translateY(18px) scale(0.985);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes fadeUp {
  from {
    opacity: 0;
    transform: translateY(16px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes entryLightDrift {
  from {
    transform: translate3d(-5%, -3%, 0) rotate(0deg);
    opacity: 0.6;
  }

  to {
    transform: translate3d(5%, 4%, 0) rotate(2deg);
    opacity: 0.95;
  }
}

@keyframes cardBreath {
  0%,
  100% {
    transform: translateY(0);
    box-shadow:
      0 28px 80px rgba(3, 10, 28, 0.48),
      0 0 0 1px rgba(120, 180, 255, 0.06) inset;
  }

  50% {
    transform: translateY(-12px);
    box-shadow:
      0 40px 100px rgba(3, 10, 28, 0.55),
      0 0 0 1px rgba(140, 195, 255, 0.1) inset;
  }
}

@keyframes orbFloat {
  0%,
  100% {
    transform: translate3d(0, 0, 0) scale(1);
  }

  33% {
    transform: translate3d(28px, -22px, 0) scale(1.08);
  }

  66% {
    transform: translate3d(-18px, 14px, 0) scale(1.04);
  }
}

@keyframes chipFloat {
  0%,
  100% {
    transform: translate3d(0, 0, 0);
    opacity: 0.72;
  }

  50% {
    transform: translate3d(10px, -20px, 0);
    opacity: 0.92;
  }
}

@keyframes shellSweep {
  0%,
  58% {
    opacity: 0;
    transform: translateX(-46%);
  }

  70% {
    opacity: 0.75;
  }

  100% {
    opacity: 0;
    transform: translateX(54%);
  }
}

@keyframes markPulse {
  0%,
  100% {
    filter: drop-shadow(0 0 0 rgba(87, 156, 255, 0));
    transform: translateZ(0);
  }

  50% {
    filter: drop-shadow(0 0 14px rgba(117, 188, 255, 0.56));
    transform: translateY(-1px);
  }
}

@keyframes panelFloat {
  0%,
  100% {
    transform: translate3d(0, 0, 0);
  }

  50% {
    transform: translate3d(0, -8px, 0);
  }
}

@keyframes lineGlow {
  0%,
  100% {
    opacity: 0.48;
    filter: drop-shadow(0 0 7px rgba(94, 166, 255, 0.36));
  }

  50% {
    opacity: 0.88;
    filter: drop-shadow(0 0 16px rgba(128, 196, 255, 0.78));
  }
}

@keyframes trackGlow {
  0%,
  100% {
    filter: saturate(1);
  }

  50% {
    filter: saturate(1.35) brightness(1.18);
  }
}

@media (max-height: 760px) and (min-width: 981px) {
  .entry-nav {
    min-height: 56px;
    margin-top: 10px;
  }

  .entry-hero {
    height: calc(100% - 66px);
    padding: 22px clamp(30px, 6vw, 72px) 36px;
  }

  .entry-hero h1 {
    font-size: clamp(34px, 4.3vw, 54px);
  }

  .entry-desc {
    margin-top: 14px;
    font-size: 14px;
    line-height: 1.62;
  }

  .entry-actions {
    margin-top: 20px;
  }

  .entry-stats {
    margin-top: 20px;
  }

  .entry-product {
    height: 390px;
    min-height: 360px;
  }

  .main-window {
    width: min(100%, 364px);
  }

  .floating-window {
    right: 210px;
    bottom: 46px;
  }

  .score-window {
    width: 212px;
  }

  .window-body {
    gap: 12px;
    padding: 16px;
  }

  .metric-stack article {
    min-height: 68px;
  }
}

@media (max-width: 980px) {
  .auth-page.is-intro {
    height: auto;
    min-height: 100vh;
    overflow-y: auto;
  }

  .entry-shell {
    height: auto;
    min-height: auto;
  }

  .entry-nav {
    width: min(900px, calc(100% - 36px));
    grid-template-columns: 1fr auto;
  }

  .entry-nav-center {
    display: none;
  }

  .entry-hero:not(.entry-hero--minimal) {
    height: auto;
    grid-template-columns: 1fr;
    gap: 30px;
    padding: 44px 32px 70px;
  }

  .entry-hero:not(.entry-hero--minimal) h1 {
    max-width: 12em;
  }

  .entry-hero.entry-hero--minimal {
    padding: 32px 20px;
  }

  .entry-product {
    height: auto;
    min-height: 450px;
  }

  .main-window {
    right: 0;
    left: 0;
    width: min(100%, 560px);
    margin: 0 auto;
  }

  .floating-window {
    right: auto;
    left: 0;
    bottom: 58px;
  }

  .score-window {
    right: 0;
    bottom: 0;
  }

  .connector-one {
    right: auto;
    left: 86px;
  }

  .connector-two {
    right: 136px;
  }
}

@media (max-width: 640px) {
  .auth-page {
    padding: 14px;
  }

  .entry-shell {
    height: auto;
    min-height: auto;
    border-radius: 22px;
  }

  .entry-nav {
    width: calc(100% - 24px);
    min-height: auto;
    grid-template-columns: 1fr;
    gap: 14px;
    padding: 14px;
    border-radius: 18px;
  }

  .brand-mark {
    width: 38px;
    height: 38px;
    border-radius: 12px;
    font-size: 13px;
  }

  .brand-row strong {
    font-size: 13px;
  }

  .entry-hero:not(.entry-hero--minimal) {
    gap: 28px;
    padding: 34px 20px 62px;
  }

  .entry-hero.entry-hero--minimal {
    padding: 28px 16px;
  }

  .entry-hero.entry-hero--minimal h1 {
    font-size: 26px;
  }

  .entry-desc {
    font-size: 14px;
    line-height: 1.75;
  }

  .entry-actions {
    display: grid;
    grid-template-columns: 1fr;
    margin-top: 28px;
  }

  .primary-entry-btn,
  .secondary-entry-btn {
    width: 100%;
  }

  .entry-stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
    margin-top: 30px;
  }

  .entry-stats div {
    min-width: 0;
    padding-right: 0;
    border-right: 0;
  }

  .entry-stats strong {
    font-size: 26px;
  }

  .entry-product {
    min-height: auto;
    display: grid;
    gap: 12px;
  }

  .product-window {
    position: relative;
    inset: auto;
    width: 100%;
    animation: none;
  }

  .main-window,
  .floating-window,
  .score-window {
    margin: 0;
    border-radius: 20px;
  }

  .metric-stack,
  .flow-line {
    grid-template-columns: 1fr 1fr;
  }

  .connector {
    display: none;
  }

  .auth-card {
    padding: 20px 16px;
  }

  h1 {
    font-size: 28px;
  }

  .tab-row {
    grid-template-columns: 1fr;
  }

  .login-options-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .verify-inline-code {
    min-width: 0;
    padding: 0 10px;
    font-size: 13px;
    letter-spacing: 0.05em;
  }

  .verify-inline-action {
    min-width: 86px;
    padding: 0 10px;
    font-size: 12px;
  }

  .forgot-modal {
    max-width: 100%;
    padding: 20px 16px 18px;
  }

  .forgot-modal__actions {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }

  .forgot-modal__cancel,
  .forgot-modal__submit {
    width: 100%;
    min-width: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .auth-page.is-intro::before,
  .auth-page.is-intro::after,
  .auth-orb,
  .auth-float,
  .entry-shell,
  .entry-shell::after,
  .entry-nav,
  .brand-mark,
  .entry-copy,
  .entry-product,
  .main-window,
  .floating-window,
  .score-window,
  .connector,
  .progress-track i {
    animation: none;
  }

  .primary-entry-btn,
  .secondary-entry-btn {
    transition: none;
  }
}
</style>
