<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import TeacherSubviewShell from '../components/teacher/TeacherSubviewShell.vue'
import { useTeacherLocale } from '../composables/useTeacherLocale'
import {
  changeTeacherAccountPassword,
  fetchTeacherAccountProfile,
  updateTeacherAccountProfile,
} from '../services/teacherLocal'
import { formatDateTime } from '../utils/teacher'

const { t, tm } = useTeacherLocale()

const loading = ref(false)
const savingProfile = ref(false)
const savingPassword = ref(false)
const message = ref('')
const messageType = ref('info')
const profile = ref({
  userId: '-',
  name: '',
  email: '',
  role: t('教师', 'Teacher'),
  createdAt: '',
})
const showPassword = reactive({
  old: false,
  next: false,
  confirm: false,
})

const profileForm = reactive({
  name: '',
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

const passwordStrength = computed(() => {
  const value = passwordForm.newPassword.trim()
  let score = 0
  if (value.length >= 6) score += 1
  if (value.length >= 10) score += 1
  if (/[A-Z]/.test(value) && /[a-z]/.test(value)) score += 1
  if (/\d/.test(value)) score += 1
  if (/[^A-Za-z0-9]/.test(value)) score += 1

  if (score <= 2) return { label: tm('profile.strengthWeak'), tone: 'warn', width: '34%' }
  if (score <= 4) return { label: tm('profile.strengthGood'), tone: 'ok', width: '72%' }
  return { label: tm('profile.strengthStrong'), tone: 'strong', width: '100%' }
})

async function loadProfile() {
  loading.value = true
  try {
    const nextProfile = await fetchTeacherAccountProfile()
    profile.value = { ...nextProfile, role: nextProfile.role || t('教师', 'Teacher') }
    profileForm.name = nextProfile.name || ''
    setMessage('')
  } catch (error) {
    setMessage(error.message || tm('profile.loadFailed'), 'error')
  } finally {
    loading.value = false
  }
}

async function submitProfile() {
  if (!profileForm.name.trim()) {
    setMessage(tm('profile.nameRequired'), 'error')
    return
  }

  savingProfile.value = true
  try {
    const nextProfile = await updateTeacherAccountProfile({
      name: profileForm.name,
    })
    profile.value = { ...nextProfile, role: nextProfile.role || t('教师', 'Teacher') }
    setMessage(tm('profile.profileUpdated'), 'success')
  } catch (error) {
    setMessage(error.message || tm('profile.saveProfileFailed'), 'error')
  } finally {
    savingProfile.value = false
  }
}

async function submitPassword() {
  if (!passwordForm.oldPassword.trim()) {
    setMessage(tm('profile.oldPasswordRequired'), 'error')
    return
  }
  if (passwordForm.newPassword.trim().length < 6 || passwordForm.newPassword.trim().length > 64) {
    setMessage(tm('profile.passwordLength'), 'error')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    setMessage(tm('profile.passwordMismatch'), 'error')
    return
  }

  savingPassword.value = true
  try {
    await changeTeacherAccountPassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    setMessage(tm('profile.passwordUpdated'), 'success')
  } catch (error) {
    setMessage(error.message || tm('profile.passwordFailed'), 'error')
  } finally {
    savingPassword.value = false
  }
}

onMounted(loadProfile)
</script>

<template>
  <TeacherSubviewShell :title="tm('profile.title')" :message="message" :message-type="messageType">
    <template #actions>
      <button class="secondary-btn" type="button" :disabled="loading" @click="loadProfile">
        {{ loading ? tm('common.refreshing') : tm('profile.refreshProfile') }}
      </button>
    </template>

    <section class="summary-grid">
      <article class="card summary-card">
        <p class="label">{{ tm('common.name') }}</p>
        <p class="value">{{ profile.name || '-' }}</p>
      </article>
      <article class="card summary-card">
        <p class="label">{{ tm('common.email') }}</p>
        <p class="value">{{ profile.email || '-' }}</p>
      </article>
      <article class="card summary-card">
        <p class="label">{{ tm('common.role') }}</p>
        <p class="value">{{ profile.role }}</p>
      </article>
      <article class="card summary-card">
        <p class="label">{{ tm('common.createdAt') }}</p>
        <p class="value small">{{ profile.createdAt ? formatDateTime(profile.createdAt) : '-' }}</p>
      </article>
    </section>

    <section class="layout">
      <article class="card panel">
        <p class="eyebrow">{{ tm('profile.accountInfo') }}</p>
        <h3>{{ tm('profile.personalInfo') }}</h3>
        <div class="info-grid">
          <article class="info-item">
            <p class="label">{{ tm('profile.userId') }}</p>
            <p class="detail-value">{{ profile.userId }}</p>
          </article>
          <article class="info-item">
            <p class="label">{{ tm('profile.currentRole') }}</p>
            <p class="detail-value">{{ profile.role }}</p>
          </article>
        </div>

        <div class="form-grid">
          <label>
            <span>{{ tm('common.name') }}</span>
            <input v-model.trim="profileForm.name" type="text" :placeholder="tm('profile.enterName')" />
          </label>

        </div>

        <button class="primary-btn" type="button" :disabled="savingProfile" @click="submitProfile">
          {{ savingProfile ? tm('common.saving') : tm('profile.saveProfile') }}
        </button>
      </article>

      <article class="card panel">
        <p class="eyebrow">{{ tm('profile.security') }}</p>
        <h3>{{ tm('profile.changePassword') }}</h3>

        <div class="field-list">
          <label class="field">
            <span>{{ tm('profile.currentPassword') }}</span>
            <div class="password-row">
              <input
                v-model="passwordForm.oldPassword"
                :type="showPassword.old ? 'text' : 'password'"
                :placeholder="tm('profile.enterCurrentPassword')"
              />
              <button type="button" class="toggle-btn" @click="showPassword.old = !showPassword.old">
                {{ showPassword.old ? tm('common.hide') : tm('common.show') }}
              </button>
            </div>
          </label>

          <label class="field">
            <span>{{ tm('profile.newPassword') }}</span>
            <div class="password-row">
              <input
                v-model="passwordForm.newPassword"
                :type="showPassword.next ? 'text' : 'password'"
                :placeholder="tm('profile.enterNewPassword')"
              />
              <button type="button" class="toggle-btn" @click="showPassword.next = !showPassword.next">
                {{ showPassword.next ? tm('common.hide') : tm('common.show') }}
              </button>
            </div>
          </label>

          <label class="field">
            <span>{{ tm('profile.confirmPassword') }}</span>
            <div class="password-row">
              <input
                v-model="passwordForm.confirmPassword"
                :type="showPassword.confirm ? 'text' : 'password'"
                :placeholder="tm('profile.reenterPassword')"
              />
              <button type="button" class="toggle-btn" @click="showPassword.confirm = !showPassword.confirm">
                {{ showPassword.confirm ? tm('common.hide') : tm('common.show') }}
              </button>
            </div>
          </label>
        </div>

        <div class="strength-card">
          <div class="strength-head">
            <span>{{ tm('profile.passwordStrength') }}</span>
            <strong :class="passwordStrength.tone">{{ passwordStrength.label }}</strong>
          </div>
          <div class="strength-track">
            <div class="strength-bar" :class="passwordStrength.tone" :style="{ width: passwordStrength.width }" />
          </div>
        </div>

        <button class="primary-btn" type="button" :disabled="savingPassword" @click="submitPassword">
          {{ savingPassword ? tm('common.submitting') : tm('profile.updatePassword') }}
        </button>
      </article>
    </section>
  </TeacherSubviewShell>
</template>

<style scoped>
.card {
  background: var(--teacher-surface);
  border-radius: var(--teacher-radius-card);
  box-shadow: var(--teacher-shadow);
}

.panel,
.summary-card {
  margin-top: 14px;
  padding: 18px;
}

.summary-grid,
.layout,
.info-grid,
.form-grid {
  display: grid;
  gap: 14px;
}

.summary-grid {
  margin-top: 14px;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.layout {
  grid-template-columns: minmax(0, 0.95fr) minmax(0, 1.05fr);
}

.info-grid {
  margin-top: 16px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.form-grid {
  margin-top: 18px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.eyebrow {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--teacher-text-tertiary);
}

h3 {
  margin: 10px 0 0;
}

.label,
.strength-note {
  margin: 0;
  color: var(--teacher-text-tertiary);
  font-size: 12px;
  line-height: 1.6;
}

.value,
.detail-value {
  margin: 8px 0 0;
  color: var(--teacher-text-primary);
  font-size: 18px;
  font-weight: 700;
}

.value.small {
  font-size: 15px;
}

.info-item {
  border: 1px solid var(--teacher-divider);
  border-radius: 16px;
  padding: 14px 16px;
}

label {
  display: grid;
  gap: 8px;
  color: var(--teacher-text-primary);
  font-weight: 600;
}

input {
  height: 44px;
  border: 1px solid var(--teacher-border);
  border-radius: 12px;
  padding: 0 14px;
  background: var(--teacher-surface-muted);
  color: var(--teacher-text-primary);
}

.primary-btn,
.secondary-btn,
.toggle-btn {
  border: 0;
  cursor: pointer;
  font-family: inherit;
}

.primary-btn,
.secondary-btn {
  height: 42px;
  border-radius: 12px;
  padding: 0 16px;
  font-weight: 600;
}

.primary-btn {
  margin-top: 18px;
  background: var(--teacher-accent);
  color: #fff;
}

.secondary-btn {
  background: var(--teacher-surface-muted);
  color: var(--teacher-text-primary);
}

.field-list {
  margin-top: 16px;
  display: grid;
  gap: 14px;
}

.field {
  display: grid;
  gap: 8px;
}

.password-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
}

.toggle-btn {
  height: 44px;
  border-radius: 12px;
  padding: 0 14px;
  background: var(--teacher-surface);
  border: 1px solid var(--teacher-border);
  color: var(--teacher-text-primary);
  font-weight: 700;
}

.strength-card {
  margin-top: 18px;
  border: 1px solid var(--teacher-divider);
  border-radius: 18px;
  padding: 16px;
}

.strength-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.strength-track {
  margin-top: 12px;
  width: 100%;
  height: 10px;
  border-radius: 999px;
  background: rgba(17, 24, 39, 0.08);
  overflow: hidden;
}

.strength-bar {
  height: 100%;
  border-radius: inherit;
}

.strength-bar.warn,
.warn {
  background: #e79637;
  color: #e79637;
}

.strength-bar.ok,
.ok {
  background: var(--teacher-accent);
  color: var(--teacher-accent);
}

.strength-bar.strong,
.strong {
  background: var(--teacher-success);
  color: var(--teacher-success);
}

.strength-note {
  margin-top: 10px;
}

@media (max-width: 960px) {
  .layout,
  .form-grid,
  .info-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .password-row {
    grid-template-columns: 1fr;
  }
}
</style>
