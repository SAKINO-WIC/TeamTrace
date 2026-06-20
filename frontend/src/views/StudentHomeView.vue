<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  fetchStudentAllAppeals,
  fetchStudentAllTasks,
  fetchStudentClasses,
  joinStudentClass,
} from '../services/student'
import { useStudentLocale } from '../composables/useStudentLocale'
import StudentClassHubSection from '../components/student/StudentClassHubSection.vue'
import { getActiveSession, getCurrentUserId } from '../utils/auth'
import { readSessionCache, writeSessionCache } from '../utils/sessionCache'

const router = useRouter()
const route = useRoute()
const classHubRef = ref(null)
const { t, tm, isEn } = useStudentLocale()
const loading = ref(true)
const message = ref('')
const messageType = ref('info')
const loadError = ref('')
const classListError = ref('')
const classes = ref([])
const tasks = ref([])
const appeals = ref([])

const dismissed = ref(new Set(JSON.parse(localStorage.getItem('home_dismissed') || '[]')))
function closeTodo(type) {
  const s = new Set(dismissed.value)
  s.add(type)
  dismissed.value = s
  localStorage.setItem('home_dismissed', JSON.stringify([...s]))
}
const appealLastSeen = Number(localStorage.getItem('appeals_last_seen') || 0)

/* ── user info ── */
const studentName = ref('')
function loadStudentName() {
  studentName.value = getActiveSession()?.user?.name || ''
}

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return tm('home.greetingNight')
  if (hour < 12) return tm('home.greetingMorning')
  if (hour < 14) return tm('home.greetingNoon')
  if (hour < 18) return tm('home.greetingAfternoon')
  return tm('home.greetingEvening')
})
const displayName = computed(() => studentName.value || tm('common.student'))
const studentAvatarUrl = computed(() => getActiveSession()?.user?.avatarUrl || '')
const studentInitial = computed(() => displayName.value.slice(0, 1).toUpperCase())

/* ── derived ── */
const isEmpty = computed(() => !loading.value && !loadError.value && classes.value.length === 0)
const hasData = computed(() => !loading.value && !loadError.value && classes.value.length > 0)

/* ── todo items ── */
const urgentItems = computed(() => {
  isEn.value
  const items = []
  const now = Date.now()

  const pendingTasks = tasks.value.filter((t) => Number(t.status) === 1)
  if (pendingTasks.length > 0) {
    const overdue = pendingTasks.filter((t) => t.deadline && new Date(t.deadline).getTime() < now)
    items.push({
      priority: overdue.length > 0 ? 'warning' : 'notice',
      type: 'tasks',
      title: tm('home.tasksPending', { count: pendingTasks.length }),
      detail: overdue.length > 0
        ? tm('home.overdueHint', { overdue: overdue.length })
        : tm('home.continueGroupWork'),
      actionLabel: tm('home.goCompleteTasks'),
      action: () => router.push('/student/tasks'),
    })
  }

  const reviewTasks = tasks.value.filter((t) => Number(t.status) === 2)
  if (reviewTasks.length > 0) {
    const firstReview = reviewTasks[0]
    const reviewClassId = firstReview.classId || firstReview.class_id
    const reviewTaskId = firstReview.id || firstReview.taskId
    items.push({
      priority: 'notice',
      type: 'reviews',
      title: tm('home.peerReviewPending', { count: reviewTasks.length }),
      detail: tm('home.scoreTeammates'),
      actionLabel: tm('home.goPeerReview'),
      action: () => router.push({
        path: `/student/classes/${reviewClassId}/tasks/${reviewTaskId}`,
        query: { tab: 'peer' },
      }),
    })
  }

  const resolvedAppeals = appeals.value.filter((a) => {
    const raw = String(a.status ?? '').trim().toLowerCase()
    if (!['2','3','rejected','approved','resolved'].includes(raw)) return false
    const ts = a.updatedAt ?? a.handledAt ?? a.resolvedAt ?? a.createdAt
    return !ts || new Date(ts).getTime() > appealLastSeen || appealLastSeen === 0
  })
  if (resolvedAppeals.length > 0) {
    items.push({
      priority: 'notice',
      type: 'appeals',
      title: tm('home.appealResolved', { count: resolvedAppeals.length }),
      detail: tm('home.viewTeacherReply'),
      actionLabel: tm('home.viewAppeals'),
      action: () => {
        localStorage.setItem('appeals_last_seen', String(Date.now()))
        router.push('/student/appeals')
      },
    })
  }

  return items.filter(i => !dismissed.value.has(i.type))
})

function normalizeClass(raw, taskCount = 0) {
  const statusRaw = raw?.statusLabel ?? raw?.status
  const active = !(statusRaw === 0 || statusRaw === '已结束')

  return {
    id: String(raw?.classId ?? raw?.id ?? '-'),
    code: raw?.classCode ?? raw?.code ?? '-',
    name: raw?.name ?? raw?.className ?? '-',
    semester: raw?.semester ?? '-',
    teacherName: raw?.teacherName ?? '—',
    groupName: raw?.groupName ?? '',
    groupJoinStatus: raw?.groupJoinStatus ?? '',
    taskCount: Number(taskCount),
    status: active ? 'active' : 'ended',
    active,
  }
}

function handleHubMessage(text, type = 'info') {
  setMessage(text, type)
}

function openJoinDialogFromHub() {
  classHubRef.value?.openJoinDialog?.()
}

/* ── join class dialog (empty state inline) ── */
const showJoinDialog = ref(false)
const inviteCode = ref('')
const joinLoading = ref(false)
const joinError = ref('')

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
  if (type === 'success') {
    setTimeout(() => {
      if (message.value === text) message.value = ''
    }, 3000)
  }
}

function normalizeInviteCodeInput(value) {
  return String(value || '')
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '')
    .slice(0, 8)
}

function handleInviteCodeInput() {
  inviteCode.value = normalizeInviteCodeInput(inviteCode.value)
  joinError.value = ''
}

function validateInviteCode(code) {
  if (!code) return '请输入邀请码'
  if (!/^[A-Z0-9]{6,8}$/.test(code)) return '邀请码格式错误'
  return ''
}

function openJoinDialog() {
  inviteCode.value = ''
  joinError.value = ''
  showJoinDialog.value = true
}

function closeJoinDialog() {
  showJoinDialog.value = false
  inviteCode.value = ''
  joinError.value = ''
}

async function joinClass() {
  const code = normalizeInviteCodeInput(inviteCode.value)
  inviteCode.value = code
  const validation = validateInviteCode(code)
  if (validation) {
    joinError.value = validation
    return
  }
  joinLoading.value = true
  joinError.value = ''
  try {
    const res = await joinStudentClass(code)
    const data = res?.data?.data || res?.data
    const joinedName = data?.className || data?.name || '新班级'
    setMessage(`已成功加入「${joinedName}」，现在可以进入班级。`, 'success')
    closeJoinDialog()
    await loadData({ force: true })
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || '加入失败'
    if (msg.includes('过期') || msg.includes('expired')) {
      joinError.value = '邀请码已过期'
    } else if (msg.includes('已使用') || msg.includes('used')) {
      joinError.value = '该邀请码已被使用'
    } else if (msg.includes('无效') || msg.includes('invalid') || msg.includes('不存在')) {
      joinError.value = '无效的邀请码，请检查后重新输入'
    } else {
      joinError.value = msg
    }
  } finally {
    joinLoading.value = false
  }
}

function getHomeCacheKey() {
  return `student:${getCurrentUserId() || 'anonymous'}:home`
}

function applyHomePayload(classPayload, taskPayload, appealPayload) {
  tasks.value = Array.isArray(taskPayload) ? taskPayload : []
  appeals.value = Array.isArray(appealPayload) ? appealPayload : []
  const taskCountMap = tasks.value.reduce((acc, item) => {
    const classId = String(item?.classId ?? '')
    if (!classId) return acc
    acc[classId] = (acc[classId] || 0) + 1
    return acc
  }, {})
  classes.value = (Array.isArray(classPayload) ? classPayload : []).map((item) =>
    normalizeClass(item, taskCountMap[String(item?.classId ?? item?.id ?? '')] || 0),
  )
}

function resolveHomeLoadError(error) {
  const status = Number(error?.status)
  const message = String(error?.message || '').trim()
  if (status === 502 || /502/.test(message)) {
    return '后端服务未连接，请确认后端已启动后点击重新加载。'
  }
  if (status === 503 || /503/.test(message)) {
    return '服务暂时不可用，请稍后重试。'
  }
  return message || '首页数据加载失败，请稍后重试。'
}

async function loadData(options = {}) {
  const cacheKey = getHomeCacheKey()
  const cached = options.force ? null : readSessionCache(cacheKey, 180000)
  if (cached && typeof cached === 'object') {
    applyHomePayload(cached.classes, cached.tasks, cached.appeals)
    loading.value = false
  } else {
    loading.value = true
  }
  loadError.value = ''
  classListError.value = ''
  try {
    const [classRes, taskRes, appealRes] = await Promise.allSettled([
      fetchStudentClasses(),
      fetchStudentAllTasks(),
      fetchStudentAllAppeals(),
    ])
    if (classRes.status === 'rejected') {
      throw classRes.reason
    }

    const classPayload = Array.isArray(classRes.value?.data?.data) ? classRes.value.data.data : []
    const taskPayload = taskRes.status === 'fulfilled' && Array.isArray(taskRes.value?.data?.data)
      ? taskRes.value.data.data
      : []
    const appealPayload = appealRes.status === 'fulfilled' && Array.isArray(appealRes.value?.data?.data)
      ? appealRes.value.data.data
      : []

    applyHomePayload(classPayload, taskPayload, appealPayload)
    writeSessionCache(cacheKey, {
      classes: classPayload,
      tasks: taskPayload,
      appeals: appealPayload,
    })
  } catch (e) {
    if (!cached) {
      classes.value = []
      tasks.value = []
      appeals.value = []
      classListError.value = resolveHomeLoadError(e)
      loadError.value = resolveHomeLoadError(e)
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadStudentName()
  loadData()
  if (route.query.action === 'join') {
    openJoinDialogFromHub()
  }
})
</script>

<template>
  <div class="student-home student-page">
    <p v-if="message" class="toast-message" :class="messageType">{{ message }}</p>

    <section v-if="loadError" class="error-state">
      <h2>{{ tm('home.loadFailedTitle') }}</h2>
      <p>{{ loadError }}</p>
      <button class="primary-btn" type="button" :disabled="loading" @click="loadData({ force: true })">
        {{ loading ? tm('common.loading') : tm('common.reload') }}
      </button>
    </section>

    <!-- ═══ EMPTY STATE ═══ -->
    <div v-if="isEmpty" class="empty-state">
      <div class="empty-avatar" aria-hidden="true">
        <img v-if="studentAvatarUrl" :src="studentAvatarUrl" alt="" />
        <span v-else>{{ studentInitial }}</span>
      </div>
      <h2 class="empty-heading">{{ tm('home.welcome', { name: displayName }) }}</h2>

      <div class="join-box">
        <div class="join-input-row">
          <input
            v-model="inviteCode"
            class="join-input"
            type="text"
            maxlength="8"
            :placeholder="tm('joinClass.placeholderShort')"
            autocomplete="off"
            @keyup.enter="joinClass"
            @input="handleInviteCodeInput"
          />
          <button
            class="join-btn"
            type="button"
            :disabled="joinLoading || !inviteCode.trim()"
            @click="joinClass"
          >
            {{ joinLoading ? tm('common.joining') : tm('home.joinClass') }}
          </button>
        </div>
        <p v-if="joinError" class="join-error">{{ joinError }}</p>
      </div>
    </div>

    <!-- ═══ DATA STATE ═══ -->
    <template v-if="hasData">
      <!-- Urgent todos -->
      <section v-if="urgentItems.length > 0" class="urgent-section">
        <div class="student-section-bar">
          <h3 class="section-title">{{ tm('home.todoSection') }}</h3>
        </div>
        <div class="urgent-list">
          <button
            v-for="item in urgentItems"
            :key="item.type"
            class="urgent-card"
            :class="`priority-${item.priority}`"
            type="button"
            @click="item.action()"
          >
            <div class="urgent-left">
              <span class="urgent-badge">
                {{ item.priority === 'warning' ? '警' : '办' }}
              </span>
              <div>
                <p class="urgent-title">{{ item.title }}</p>
                <p class="urgent-detail">{{ item.detail }}</p>
              </div>
            </div>
            <span class="urgent-arrow">{{ item.actionLabel }} →</span>
            <button class="urgent-close" type="button" @click.stop="closeTodo(item.type)" title="忽略">×</button>
          </button>
        </div>
      </section>

      <StudentClassHubSection
        ref="classHubRef"
        embedded
        :classes="classes"
        :loading="loading"
        :load-error="classListError"
        @refresh="loadData"
        @message="handleHubMessage"
      />
    </template>

    <!-- ═══ LOADING ═══ -->
    <div v-if="loading && !hasData && !isEmpty" class="loading-state">
      <div class="skeleton-card" v-for="i in 4" :key="i"></div>
    </div>

  </div>
</template>

<style scoped>
/* ═══ BASE ═══ */
.student-home {
  display: flex;
  flex-direction: column;
  gap: 22px;
  animation: tt-fade-up var(--tt-duration-slow) var(--tt-ease-ios) both;
}
h2, h3, p { margin: 0; }
button { cursor: pointer; border: none; font-family: inherit; }

.section-title {
  margin: 0;
}

.student-section-bar {
  margin-bottom: 14px;
}
.section-count {
  font-size: 13px;
  font-weight: 500;
  color: var(--tt-text-tertiary);
}
.toast-message {
  padding: 10px 16px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
}
.toast-message.success { background: rgba(52,199,89,0.1); color: #16a34a; }
.toast-message.error { background: rgba(255,59,48,0.1); color: #dc2626; }
.toast-message.info { background: rgba(0,122,255,0.08); color: var(--student-accent); }

.error-state {
  min-height: 260px;
  padding: 32px 24px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(255, 59, 48, 0.16);
  box-shadow: var(--student-shadow);
  display: grid;
  place-items: center;
  text-align: center;
  gap: 12px;
}

.error-state h2 {
  font-size: 22px;
  font-weight: 800;
  color: var(--student-text-primary);
}

.error-state p {
  max-width: 420px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--student-text-secondary);
}

.retry-btn {
  min-height: 42px;
  padding: 0 16px;
  border-radius: 14px;
  background: var(--student-accent-gradient);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
}

/* ═══ EMPTY STATE ═══ */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 54px 24px;
  gap: 16px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 24px;
  background:
    radial-gradient(circle at 50% 0%, rgba(0, 82, 255, 0.08), transparent 42%),
    rgba(255, 255, 255, 0.82);
  box-shadow: var(--student-shadow-lg);
}
.empty-avatar {
  width: 76px;
  height: 76px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--student-accent-gradient);
  color: var(--student-text-inverse);
  font-size: 26px;
  font-weight: 800;
  box-shadow: 0 16px 34px rgba(37, 99, 235, 0.2);
}
.empty-avatar img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}
.empty-heading {
  font-size: clamp(20px, 1.85vw, 24px);
  font-weight: 700;
  line-height: 1.28;
  color: var(--student-text-primary);
}
.empty-desc {
  max-width: 400px;
  font-size: 15px;
  line-height: 1.7;
  color: var(--student-text-secondary);
}
.join-box {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  width: 100%;
  max-width: 400px;
}
.join-input-row {
  display: flex;
  gap: 10px;
  width: 100%;
}
.join-input {
  flex: 1;
  height: 48px;
  padding: 0 16px;
  border-radius: 14px;
  border: 1px solid var(--student-border);
  background: var(--student-surface);
  font-size: 16px;
  font-family: inherit;
  color: var(--student-text-primary);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  outline: none;
}
.join-input:focus {
  border-color: var(--student-accent);
  box-shadow: 0 0 0 3px rgba(0, 82, 255, 0.12);
}
.join-input::placeholder {
  text-transform: none;
  letter-spacing: normal;
  color: var(--student-text-tertiary);
}
.join-btn {
  height: 48px;
  padding: 0 24px;
  border-radius: 14px;
  font-size: 15px;
  font-weight: 700;
  background: var(--student-accent-gradient);
  color: #fff;
  box-shadow: var(--student-accent-shadow);
  white-space: nowrap;
}
.join-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.join-error {
  font-size: 13px;
  color: #dc2626;
}
.join-hint {
  font-size: 12px;
  color: var(--student-text-tertiary);
  line-height: 1.5;
}

/* ═══ GREETING ═══ */
.greeting-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 22px 24px;
  border: 1px solid var(--tt-border-subtle);
  border-radius: 24px;
  background:
    radial-gradient(circle at 100% 0%, rgba(0, 82, 255, 0.08), transparent 34%),
    var(--tt-surface);
  box-shadow: var(--tt-shadow-sm);
  position: relative;
  overflow: hidden;
}
.greeting-bar::after {
  content: '';
  position: absolute;
  right: 24px;
  bottom: -44px;
  width: 180px;
  height: 180px;
  border: 1px dashed rgba(0, 82, 255, 0.16);
  border-radius: 999px;
  pointer-events: none;
}
.workbench-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid var(--tt-accent-border);
  background: var(--tt-accent-soft);
  color: var(--tt-accent);
  font-family: var(--tt-font-mono);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  margin-bottom: 12px;
}
.workbench-pill span {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: var(--tt-accent);
  animation: tt-pulse-dot 2.2s var(--tt-ease-ios) infinite;
}
.greeting-text {
  font-size: clamp(20px, 1.85vw, 24px);
  font-weight: 700;
  color: var(--tt-text);
}
.greeting-sub {
  margin-top: 4px;
  font-size: 14px;
  color: var(--tt-text-secondary);
}
.greeting-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.action-btn {
  min-height: 40px;
  padding: 0 16px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  border: none;
  font-family: inherit;
  transition: all 150ms;
}
.join-class-btn {
  background: var(--tt-accent);
  color: #fff;
  box-shadow: var(--tt-shadow-accent);
}
.join-class-btn:hover {
  background: var(--tt-accent-hover);
}
.refresh-btn {
  background: var(--tt-surface-muted);
  border: 1px solid var(--tt-border);
  color: var(--tt-text-secondary);
}
.refresh-btn:hover {
  background: var(--tt-surface-hover);
}
.refresh-btn:disabled { opacity: 0.5; }

/* ═══ URGENT ═══ */
.urgent-card::before,
.class-card::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(0, 82, 255, 0.045), transparent 48%);
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--tt-duration-fast) var(--tt-ease-ios);
}

.urgent-card:hover::before,
.class-card:hover::before {
  opacity: 1;
}

.urgent-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.urgent-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 20px;
  border-radius: 20px;
  text-align: left;
  background: var(--tt-surface);
  border: 1px solid var(--tt-border-subtle);
  box-shadow: var(--tt-shadow-sm);
  position: relative;
  overflow: hidden;
  transition: transform 160ms cubic-bezier(0.25,0.1,0.25,1);
}
.urgent-card:hover { transform: translateY(-2px); }
.priority-warning { border-left: 4px solid #ea580c; }
.priority-notice { border-left: 4px solid var(--student-accent); }
.urgent-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.urgent-badge {
  width: 30px;
  height: 30px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--student-accent-soft);
  color: var(--student-accent);
  font-size: 12px;
  font-weight: 800;
  flex-shrink: 0;
}
.urgent-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--student-text-primary);
}
.urgent-detail {
  margin: 2px 0 0;
  font-size: 13px;
  color: var(--tt-text-secondary);
}
.urgent-arrow {
  font-size: 13px;
  font-weight: 700;
  color: var(--tt-accent);
  white-space: nowrap;
}

/* ═══ ALL CLEAR ═══ */
.all-clear {
  padding: 24px;
  border-radius: 20px;
  background:
    radial-gradient(circle at 0% 0%, rgba(0, 82, 255, 0.06), transparent 34%),
    var(--tt-surface);
  border: 1px solid var(--tt-border-subtle);
  text-align: center;
}
.all-clear-text {
  font-size: 15px;
  color: var(--tt-text-secondary);
}

/* ═══ CLASS CARDS ═══ */
.class-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 14px;
}
.class-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
  padding: 18px 20px;
  border-radius: 20px;
  text-align: left;
  background: var(--tt-surface);
  border: 1px solid var(--tt-border-subtle);
  box-shadow: var(--tt-shadow-sm);
  position: relative;
  overflow: hidden;
  transition: transform 160ms cubic-bezier(0.25,0.1,0.25,1), border-color 160ms cubic-bezier(0.25,0.1,0.25,1), box-shadow 160ms cubic-bezier(0.25,0.1,0.25,1);
}
.class-card:hover {
  transform: translateY(-3px);
  border-color: var(--tt-accent-border);
  box-shadow: var(--tt-shadow-lg);
}
.class-card-top {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  width: 100%;
  min-width: 0;
  text-align: left;
}
.class-name {
  margin: 0;
  width: 100%;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.35;
  color: var(--student-text-primary);
  text-align: left;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}
.group-tag {
  margin: 0;
  width: 100%;
  max-width: 100%;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.35;
  padding: 0;
  border-radius: 0;
  background: transparent;
  color: var(--student-accent);
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.group-tag.no-group {
  color: #ea580c;
}
.class-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.progress-bar {
  flex: 1;
  height: 6px;
  border-radius: 3px;
  background: var(--tt-border-subtle);
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  border-radius: 3px;
  background: var(--student-accent-gradient);
  transition: width 0.3s ease;
}
.progress-text {
  font-size: 13px;
  font-weight: 700;
  color: var(--student-text-primary);
}
.class-stats {
  display: flex;
  gap: 16px;
  width: 100%;
  font-size: 13px;
  color: var(--student-text-secondary);
}
.stat-highlight {
  color: #ea580c;
  font-weight: 600;
}
.class-alert {
  font-size: 12px;
  color: #ea580c;
  font-weight: 600;
}

/* ═══ LOADING ═══ */
.loading-state {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
}
.skeleton-card {
  height: 160px;
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(15,23,42,0.04), rgba(15,23,42,0.02));
  animation: pulse 1.5s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 0.8; }
}

@media (max-width: 640px) {
  .greeting-bar {
    padding: 18px;
  }

  .greeting-actions,
  .join-input-row {
    width: 100%;
    flex-direction: column;
  }

  .action-btn,
  .join-btn {
    width: 100%;
  }

  .urgent-card {
    align-items: flex-start;
    flex-direction: column;
  }
}


.urgent-close {
  position: absolute;
  top: 6px;
  right: 8px;
  width: 22px;
  height: 22px;
  border: none;
  background: none;
  font-size: 16px;
  color: var(--tt-text-muted);
  cursor: pointer;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
}
.urgent-close:hover { background: var(--tt-surface-muted); color: var(--tt-text); }
</style>
