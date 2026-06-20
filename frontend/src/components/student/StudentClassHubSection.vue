<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { joinStudentClass } from '../../services/student'
import { useStudentLocale } from '../../composables/useStudentLocale'

const props = defineProps({
  embedded: {
    type: Boolean,
    default: false,
  },
  classes: {
    type: Array,
    default: () => [],
  },
  loading: {
    type: Boolean,
    default: false,
  },
  loadError: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['refresh', 'message'])

const route = useRoute()
const router = useRouter()
const { tm } = useStudentLocale()

const joining = ref(false)
const showJoinDialog = ref(false)
const joinCode = ref('')
const inviteCodeError = ref('')
const newlyJoinedId = ref('')

const filters = reactive({
  keyword: '',
  semester: 'all',
  status: 'all',
})

const summary = computed(() => ({
  total: props.classes.length,
}))

const semesterOptions = computed(() => {
  const map = new Map()
  props.classes.forEach((item) => {
    if (!map.has(item.semester)) {
      map.set(item.semester, item.semester)
    }
  })
  return Array.from(map.entries()).map(([value, label]) => ({ value, label }))
})

const filteredClasses = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()

  return props.classes.filter((item) => {
    const keywordHit =
      !keyword ||
      [item.name, item.code, item.teacherName, item.semester].some((field) =>
        String(field || '').toLowerCase().includes(keyword),
      )

    const semesterHit = filters.semester === 'all' || item.semester === filters.semester
    const statusHit = filters.status === 'all' || item.status === filters.status

    return keywordHit && semesterHit && statusHit
  })
})

function formatGroupStatus(item) {
  if (item.groupName) return item.groupName
  if (item.groupJoinStatus) return item.groupJoinStatus
  return tm('classes.noGroup')
}

function formatClassStatus(status) {
  return status === 'ended' ? tm('classes.statusEnded') : tm('classes.statusActive')
}

function clearFilters() {
  filters.keyword = ''
  filters.semester = 'all'
  filters.status = 'all'
}

function validateInviteCode(code) {
  if (!code) return tm('classes.inviteRequired')
  if (!/^[A-Za-z0-9]{6,8}$/.test(code)) return tm('classes.inviteInvalid')
  return ''
}

function normalizeInviteCodeInput(value) {
  return String(value || '')
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '')
    .slice(0, 8)
}

function handleJoinCodeInput() {
  joinCode.value = normalizeInviteCodeInput(joinCode.value)
  inviteCodeError.value = ''
}

function handleJoinError(error) {
  const code = error?.response?.data?.code || ''
  const map = {
    INVITE_CODE_NOT_FOUND: tm('classes.inviteNotFound'),
    INVITE_CODE_EXPIRED: tm('classes.inviteExpired'),
    INVITE_CODE_ALREADY_USED: tm('classes.inviteUsed'),
    CLASS_DELETED: tm('classes.classDeleted'),
    ALREADY_CLASS_MEMBER: tm('classes.alreadyMember'),
  }
  return map[code] || error?.message || tm('classes.joinFailed')
}

async function submitJoin() {
  const code = normalizeInviteCodeInput(joinCode.value)
  joinCode.value = code
  const err = validateInviteCode(code)
  if (err) {
    inviteCodeError.value = err
    return
  }

  inviteCodeError.value = ''
  joining.value = true
  try {
    const { data } = await joinStudentClass(code)
    const payload = data?.data || {}
    newlyJoinedId.value = String(payload.classId || '')
    closeJoinDialog()
    emit('refresh')
    const joinedName = payload.className || payload.name || ''
    emit(
      'message',
      joinedName ? tm('classes.joinSuccessNamed', { name: joinedName }) : tm('classes.joinSuccess'),
      'success',
    )
    setTimeout(() => {
      newlyJoinedId.value = ''
    }, 3000)
  } catch (error) {
    emit('message', handleJoinError(error), 'error')
  } finally {
    joining.value = false
  }
}

function openJoinDialog() {
  joinCode.value = ''
  inviteCodeError.value = ''
  showJoinDialog.value = true
}

function closeJoinDialog() {
  if (joining.value) return
  showJoinDialog.value = false
  inviteCodeError.value = ''
  joinCode.value = ''
  if (route.path === '/student' && route.query.action === 'join') {
    router.replace({ path: '/student' })
  }
}

function goDetail(item) {
  if (!item?.id || item.id === '-') return
  router.push(`/student/classes/${item.id}`)
}

watch(
  () => route.query.action,
  (action) => {
    if (action === 'join' && route.path === '/student') {
      openJoinDialog()
    }
  },
  { immediate: true },
)

defineExpose({ openJoinDialog })
</script>

<template>
  <section class="student-class-hub student-page" :class="{ embedded }">
    <header v-if="!embedded" class="topbar">
      <div class="title-block">
        <p class="eyebrow">{{ tm('shell.classes') }}</p>
        <h2 class="page-title">{{ tm('classes.title') }}</h2>
      </div>
      <div class="top-actions">
        <button class="primary-btn join-head-btn" type="button" @click="openJoinDialog">+ {{ tm('classes.joinClass') }}</button>
      </div>
    </header>

    <header v-else class="hub-embedded-head">
      <div class="student-section-bar hub-head-copy">
        <h3 class="section-title">{{ tm('home.myClasses') }}</h3>
        <span class="section-count">{{ tm('home.classCount', { count: summary.total }) }}</span>
      </div>
      <button class="primary-btn join-head-btn" type="button" @click="openJoinDialog">+ {{ tm('classes.joinClass') }}</button>
    </header>

    <section class="filter-panel">
      <label>
        <span>{{ tm('classes.search') }}</span>
        <input v-model.trim="filters.keyword" type="text" :placeholder="tm('classes.searchPlaceholder')" />
      </label>
      <label>
        <span>{{ tm('classes.semester') }}</span>
        <select v-model="filters.semester">
          <option value="all">{{ tm('classes.allSemesters') }}</option>
          <option v-for="item in semesterOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
      </label>
      <label>
        <span>{{ tm('classes.status') }}</span>
        <select v-model="filters.status">
          <option value="all">{{ tm('classes.allStatuses') }}</option>
          <option value="active">{{ tm('classes.statusActive') }}</option>
          <option value="ended">{{ tm('classes.statusEnded') }}</option>
        </select>
      </label>
    </section>

    <section v-if="loading" class="class-grid loading-grid">
      <div v-for="i in 4" :key="i" class="class-skeleton" />
    </section>

    <section v-else-if="loadError" class="empty-card error-card">
      <h3>{{ tm('classes.loadFailedTitle') }}</h3>
      <p>{{ loadError }}</p>
      <button class="primary-btn empty-action-btn" type="button" @click="emit('refresh')">{{ tm('common.retry') }}</button>
    </section>

    <section v-else class="class-grid">
      <button
        v-for="item in filteredClasses"
        :key="item.id"
        class="class-card"
        :class="{ active: item.status === 'active', ended: item.status === 'ended', newlyJoined: newlyJoinedId === item.id }"
        type="button"
        @click="goDetail(item)"
      >
        <div class="card-top">
          <div class="card-title-block">
            <p class="class-name">{{ item.name }}</p>
            <p class="class-meta">{{ item.semester }}</p>
          </div>
          <span class="status-badge" :class="{ ended: item.status === 'ended' }">{{ formatClassStatus(item.status) }}</span>
        </div>

        <div class="card-middle">
          <div class="metric-box metric-box--code">
            <span class="metric-label">{{ tm('classes.classCode') }}</span>
            <strong class="metric-value compact">{{ item.code }}</strong>
          </div>
          <div class="metric-box metric-box--teacher">
            <span class="metric-label">{{ tm('classes.teacher') }}</span>
            <strong class="metric-value compact">{{ item.teacherName }}</strong>
          </div>
          <div class="metric-box metric-box--group">
            <span class="metric-label">{{ tm('classes.myGroup') }}</span>
            <strong class="metric-value compact">{{ formatGroupStatus(item) }}</strong>
          </div>
          <div class="metric-box metric-box--tasks">
            <span class="metric-label">{{ tm('classes.taskCount') }}</span>
            <strong class="metric-value">{{ item.taskCount }}</strong>
          </div>
        </div>

        <div class="card-bottom">
          <span class="enter-btn">{{ newlyJoinedId === item.id ? tm('classes.enteredClass') : tm('classes.enterClass') }}</span>
        </div>
      </button>

      <article v-if="!filteredClasses.length" class="empty-card">
        <h3>{{ tm('classes.emptyTitle') }}</h3>
        <button v-if="classes.length" class="secondary-btn empty-action-btn" type="button" @click="clearFilters">
          {{ tm('classes.clearFilters') }}
        </button>
        <button v-if="!classes.length" class="primary-btn empty-action-btn" type="button" @click="openJoinDialog">
          {{ tm('classes.enterInviteJoin') }}
        </button>
      </article>
    </section>

    <Teleport to="body">
      <div v-if="showJoinDialog" class="dialog-overlay" @click.self="closeJoinDialog">
        <section class="dialog-panel join-class-dialog" role="dialog" aria-labelledby="hub-join-title">
          <div class="join-dialog-hero">
            <span class="join-dialog-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
                <path d="M12 3 4 7v6c0 5 3.5 8.5 8 10 4.5-1.5 8-5 8-10V7l-8-4Z" stroke-linejoin="round" />
                <path d="M9 12 11 14l4-4" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </span>
            <div class="join-dialog-head-copy">
              <h3 id="hub-join-title" class="dialog-title">{{ tm('joinClass.title') }}</h3>
            </div>
          </div>

          <div class="dialog-body join-dialog-body">
            <label class="field-label" for="hub-join-code">{{ tm('joinClass.codeLabel') }}</label>
            <input
              id="hub-join-code"
              v-model.trim="joinCode"
              type="text"
              :placeholder="tm('joinClass.placeholder')"
              class="dialog-input join-code-input"
              :class="{ error: inviteCodeError }"
              maxlength="8"
              autocomplete="off"
              autocapitalize="characters"
              spellcheck="false"
              @keyup.enter="submitJoin"
              @input="handleJoinCodeInput"
            />
            <p v-if="inviteCodeError" class="field-error">{{ inviteCodeError }}</p>
          </div>

          <footer class="dialog-actions join-dialog-actions">
            <button class="secondary-btn" type="button" :disabled="joining" @click="closeJoinDialog">{{ tm('common.cancel') }}</button>
            <button class="primary-btn" type="button" :disabled="joining" @click="submitJoin">
              {{ joining ? tm('common.joining') : tm('joinClass.confirmJoin') }}
            </button>
          </footer>
        </section>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.student-class-hub,
.filter-panel,
.class-grid {
  display: grid;
  gap: 16px;
}

.student-class-hub {
  gap: 20px;
}

.student-class-hub.embedded {
  gap: 18px;
}

.hub-embedded-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 4px 2px 2px;
}

.hub-head-copy {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  min-width: 0;
  margin-bottom: 0;
}

.hub-embedded-head .section-title {
  margin: 0;
}

.section-count {
  margin: 0;
}

.join-head-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  flex-shrink: 0;
  min-height: 42px;
  padding: 0 18px;
  border-radius: 14px;
  font-size: 14px;
  font-weight: 700;
  line-height: 1;
  background: linear-gradient(135deg, #007aff, #0056d6);
  box-shadow: 0 8px 20px color-mix(in srgb, var(--tt-accent) 28%, transparent);
}

.topbar,
.top-actions,
.card-top,
.card-bottom,
.dialog-head,
.dialog-actions {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
}

.topbar {
  padding: 18px 20px;
  flex-wrap: wrap;
}

h2,
h3,
.class-name,
.metric-value {
  margin: 0;
}

.title-block > h2,
.page-title {
  font-size: clamp(24px, 2.2vw, 30px);
  font-weight: 800;
  line-height: 1.2;
  letter-spacing: -0.03em;
}

.title-block {
  display: grid;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.eyebrow {
  margin: 0;
}

.top-actions {
  flex-shrink: 0;
}

.filter-panel,
.empty-card {
  background: var(--student-surface);
  border-radius: 20px;
  box-shadow: var(--tt-shadow-xs);
}

.filter-panel {
  grid-template-columns: repeat(3, minmax(220px, 1fr));
  padding: 16px 18px;
  background: var(--tt-surface);
  border: 1px solid var(--tt-border-subtle);
}

label {
  display: grid;
  gap: 8px;
  color: var(--student-text-secondary);
  font-size: 13px;
}

input,
select {
  border: 1px solid var(--student-border);
  border-radius: 14px;
  background: var(--student-surface);
  color: var(--student-text-primary);
  padding: 0 12px;
  font-family: inherit;
  height: 44px;
}

.primary-btn,
.secondary-btn {
  min-height: 44px;
  border: 1px solid transparent;
  border-radius: 14px;
  padding: 0 16px;
  font-family: inherit;
  font-weight: 700;
  cursor: pointer;
  color: #fff;
}

.primary-btn {
  background: var(--student-accent);
}

.secondary-btn {
  background: var(--student-surface-muted);
  color: var(--student-text-primary);
  border-color: var(--student-border);
}

.class-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.loading-grid {
  align-items: stretch;
}

.class-skeleton {
  min-height: 168px;
  border-radius: 18px;
  background: var(--student-surface-muted);
  animation: pulse 1.5s ease-in-out infinite;
}

.class-card {
  min-height: 0;
  padding: 16px 18px;
  border-radius: 20px;
  border: 1px solid var(--tt-border-subtle);
  background: var(--student-tile-bg, var(--tt-surface));
  box-shadow: var(--tt-shadow-xs);
  display: grid;
  align-content: start;
  gap: 14px;
  text-align: left;
  cursor: pointer;
  transition:
    transform 160ms cubic-bezier(0.25, 0.1, 0.25, 1),
    border-color 160ms cubic-bezier(0.25, 0.1, 0.25, 1),
    box-shadow 160ms cubic-bezier(0.25, 0.1, 0.25, 1),
    background 220ms ease;
}

.class-card.active {
  border-color: color-mix(in srgb, var(--tt-accent) 18%, var(--tt-border-subtle));
  background: var(--student-class-card-active-bg, var(--student-tile-bg));
}

.class-card.ended {
  background: var(--student-class-card-ended-bg, var(--student-tile-bg));
  border-color: var(--tt-border-subtle);
  box-shadow: var(--tt-shadow-xs);
}

.class-card:hover {
  transform: translateY(-2px);
  border-color: color-mix(in srgb, var(--tt-accent) 24%, var(--tt-border));
  box-shadow: 0 10px 24px color-mix(in srgb, var(--tt-accent) 10%, transparent);
}

.class-card.newlyJoined {
  border-color: color-mix(in srgb, var(--tt-accent) 32%, var(--tt-border));
  box-shadow: 0 12px 30px color-mix(in srgb, var(--tt-accent) 16%, transparent);
}

.card-title-block {
  min-width: 0;
}

.class-name {
  font-size: 16px;
  line-height: 1.35;
  font-weight: 700;
  color: var(--student-text-primary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

.class-meta {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--student-text-secondary);
}

.status-badge {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--tt-accent-soft);
  color: var(--tt-accent);
  border: 1px solid var(--tt-accent-border);
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

.status-badge.ended {
  background: var(--tt-surface-muted);
  color: var(--tt-text-secondary);
  border-color: var(--tt-border-subtle);
}

.card-middle {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  min-width: 0;
}

.metric-box {
  min-height: 72px;
  border-radius: 14px;
  padding: 12px 14px;
  display: grid;
  gap: 8px;
  align-content: space-between;
  min-width: 0;
  overflow: hidden;
  background: var(--student-class-metric-tasks-bg, color-mix(in srgb, var(--tt-accent) 5%, #f8fbff));
  border: 1px solid var(--student-class-metric-tasks-border, color-mix(in srgb, var(--tt-accent) 11%, #eaf1fa));
  transition: background 220ms ease, border-color 220ms ease;
}

.metric-box--code {
  background: var(--student-class-metric-code-bg, color-mix(in srgb, var(--tt-accent) 11%, #ffffff));
  border-color: var(--student-class-metric-code-border, color-mix(in srgb, var(--tt-accent) 18%, #e3ebf7));
}

.metric-box--teacher {
  background: var(--student-class-metric-teacher-bg, color-mix(in srgb, var(--tt-accent) 7%, #ffffff));
  border-color: var(--student-class-metric-teacher-border, color-mix(in srgb, var(--tt-accent) 13%, #e8eef6));
}

.metric-box--group {
  background: var(--student-class-metric-group-bg, color-mix(in srgb, #38bdf8 8%, #ffffff));
  border-color: var(--student-class-metric-group-border, color-mix(in srgb, #38bdf8 14%, #e6f3fc));
}

.metric-box--tasks {
  background: var(--student-class-metric-tasks-bg, color-mix(in srgb, var(--tt-accent) 5%, #f8fbff));
  border-color: var(--student-class-metric-tasks-border, color-mix(in srgb, var(--tt-accent) 11%, #eaf1fa));
}

.metric-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--tt-text-secondary);
}

.metric-value {
  font-size: 20px;
  font-weight: 800;
  color: var(--tt-text);
  min-width: 0;
  line-height: 1.15;
  letter-spacing: -0.02em;
}

.metric-value.compact {
  font-size: 14px;
  line-height: 1.35;
  font-weight: 700;
  word-break: break-word;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-bottom {
  justify-content: flex-end;
  padding-top: 2px;
}

.enter-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 30px;
  padding: 0 16px;
  border-radius: var(--tt-radius-full, 9999px);
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, var(--tt-accent), color-mix(in srgb, var(--tt-accent) 82%, #0056d6));
  box-shadow: 0 3px 10px color-mix(in srgb, var(--tt-accent) 22%, transparent);
}

.class-card:hover .enter-btn {
  box-shadow: 0 6px 18px color-mix(in srgb, var(--tt-accent) 34%, transparent);
}

.class-card.ended .enter-btn {
  background: var(--tt-surface-muted);
  color: var(--tt-text-secondary);
  box-shadow: none;
  border: 1px solid var(--tt-border-subtle);
}

.empty-card {
  min-height: 220px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  text-align: center;
}

.error-card {
  border: 1px solid color-mix(in srgb, var(--tt-danger) 24%, var(--tt-border));
  background: var(--tt-surface-muted);
}

.empty-card p {
  margin: 0;
  color: var(--student-text-secondary);
}

.empty-action-btn {
  margin-top: 0;
  justify-self: center;
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

@media (max-width: 1280px) {
  .class-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .class-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .hub-embedded-head,
  .topbar,
  .top-actions,
  .card-top,
  .dialog-head,
  .dialog-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .join-head-btn,
  .top-actions .primary-btn {
    width: 100%;
  }

  .card-middle {
    grid-template-columns: 1fr;
  }

  .filter-panel {
    grid-template-columns: 1fr;
  }
}
</style>
