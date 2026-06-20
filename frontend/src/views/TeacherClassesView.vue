<script setup>
import WorkspaceDialogMask from '../components/common/WorkspaceDialogMask.vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTeacherLocale } from '../composables/useTeacherLocale'
import {
  createTeacherClass,
  fetchTeacherArchivedClasses,
  fetchTeacherClasses,
  generateTeacherClassInviteCode,
  invalidateTeacherClassCache,
  invalidateTeacherClassesListCache,
  restoreTeacherClass,
} from '../services/teacher'
import { readSessionCache, writeSessionCache } from '../utils/sessionCache'
import { formatSemesterLabel } from '../utils/teacher'
import { notifyTeacherClassesChanged } from '../utils/teacherWorkspace'

const route = useRoute()
const router = useRouter()
const { tm, locale } = useTeacherLocale()

const loading = ref(false)
const creating = ref(false)
const restoring = ref(false)
const classes = ref([])
const errorMessage = ref('')
const createDialogOpen = ref(false)
const restoreDialogOpen = ref(false)
const restoringClass = ref(null)
const RESTORE_WINDOW_DAYS = 30

const SEMESTER_PRESET_VALUES = [
  '2024-2025学年度 第2学期',
  '2025-2026学年度 第1学期',
  '2025-2026学年度 第2学期',
  '2026-2027学年度 第1学期',
  '2026-2027学年度 第2学期',
]

const semesterPresetOptions = computed(() =>
  SEMESTER_PRESET_VALUES.map((value) => ({
    value,
    label: formatSemesterLabel(value, locale.value),
  })),
)

function resolveStatusFilter(value) {
  const raw = String(value || 'all')
  if (raw === 'archived' || raw === '已归档') return 'archived'
  if (raw === 'active' || raw === '进行中') return 'active'
  return 'all'
}

const filters = reactive({
  keyword: '',
  semester: 'all',
  status: resolveStatusFilter(route.query.status),
})

const form = reactive({
  name: '',
  semester: '2025-2026学年度 第2学期',
})

function normalizeClass(raw) {
  const semester = raw?.semester ?? '-'
  const groupingLocked = Number(raw?.groupingLocked) === 1 || raw?.groupingLocked === true
  const deletedAt = raw?.deletedAt || null
  const statusKey = deletedAt ? 'archived' : 'active'
  return {
    id: String(raw?.classId ?? raw?.id ?? '-'),
    code: raw?.classCode ?? raw?.code ?? '-',
    name: raw?.name ?? raw?.className ?? '-',
    semester,
    semesterLabel: formatSemesterLabel(semester, locale.value),
    students: Number(raw?.studentCount ?? raw?.memberCount ?? 0),
    groupCount: Number(raw?.groupCount ?? 0),
    statusKey,
    groupingLocked,
    deletedAt,
    inviteCode: raw?.activeInviteCode ?? raw?.classInviteCode ?? raw?.inviteCode ?? '',
    inviteExpireAt: raw?.inviteExpireAt || '',
  }
}

function classStatusLabel(statusKey) {
  return statusKey === 'archived' ? tm('classes.filterArchived') : tm('classes.filterActive')
}

const displayClasses = computed(() =>
  classes.value.map((item) => ({
    ...item,
    statusLabel: classStatusLabel(item.statusKey),
    semesterLabel: formatSemesterLabel(item.semester, locale.value),
  })),
)

function calcRemainingDays(deletedAt) {
  if (!deletedAt) return null
  const deleted = new Date(deletedAt)
  const now = new Date()
  const diffMs = deleted.getTime() + RESTORE_WINDOW_DAYS * 86400000 - now.getTime()
  return Math.max(0, Math.ceil(diffMs / 86400000))
}

function formatDeletedAt(deletedAt) {
  if (!deletedAt) return '-'
  const d = new Date(deletedAt)
  const dateLocale = locale.value === 'en-US' ? 'en-US' : 'zh-CN'
  return d.toLocaleDateString(dateLocale, { year: 'numeric', month: '2-digit', day: '2-digit' })
    + ' ' + d.toLocaleTimeString(dateLocale, { hour: '2-digit', minute: '2-digit' })
}

function formatRemainingDays(deletedAt) {
  const days = calcRemainingDays(deletedAt)
  if (days > 0) return tm('classes.remainingDays', { days })
  return tm('classes.permanentDeleteSoon')
}

const semesterOptions = computed(() => {
  const map = new Map()
  displayClasses.value.forEach((item) => {
    if (!map.has(item.semester)) {
      map.set(item.semester, item.semesterLabel)
    }
  })
  return Array.from(map.entries()).map(([value, label]) => ({ value, label }))
})

const summary = computed(() => ({
  total: displayClasses.value.length,
  active: displayClasses.value.filter((item) => item.statusKey === 'active').length,
  archived: displayClasses.value.filter((item) => item.statusKey === 'archived').length,
}))

const copiedClassId = ref('')
const generatingInviteClassId = ref('')
let copyHintTimer = null

function isInviteValid(item) {
  const code = String(item?.inviteCode || '').trim()
  if (!code || code === '—') {
    return false
  }
  if (!item?.inviteExpireAt) {
    return true
  }
  const expireMs = new Date(item.inviteExpireAt).getTime()
  if (Number.isNaN(expireMs)) {
    return true
  }
  return expireMs > Date.now()
}

function isInviteExpired(item) {
  const code = String(item?.inviteCode || '').trim()
  if (!code || code === '—' || !item?.inviteExpireAt) {
    return false
  }
  const expireMs = new Date(item.inviteExpireAt).getTime()
  return !Number.isNaN(expireMs) && expireMs <= Date.now()
}

function inviteCodeDisplay(item) {
  if (generatingInviteClassId.value === item.id) {
    return tm('classes.generating')
  }
  if (!isInviteValid(item)) {
    return '—'
  }
  return item.inviteCode
}

function inviteCodeHint(item) {
  if (generatingInviteClassId.value === item.id) {
    return tm('classes.generating')
  }
  if (copiedClassId.value === item.id) {
    return tm('classes.copied')
  }
  if (isInviteValid(item)) {
    return tm('classes.clickToCopy')
  }
  if (isInviteExpired(item)) {
    return tm('classes.clickToRefresh')
  }
  return tm('classes.clickToGenerate')
}

const filteredClasses = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return displayClasses.value.filter((item) => {
    const keywordHit =
      !keyword ||
      [item.name, item.code, item.id].some((field) => String(field || '').toLowerCase().includes(keyword))
    const semesterHit = filters.semester === 'all' || item.semester === filters.semester
    const statusHit = filters.status === 'all' || item.statusKey === filters.status
    return keywordHit && semesterHit && statusHit
  })
})

function classesCacheKey() {
  return `teacher:classes:${filters.status}`
}

function applyClassesPayload(payload) {
  classes.value = Array.isArray(payload) ? payload.map(normalizeClass) : []
}

async function refreshClassesFromNetwork(cacheKey, hadCache) {
  try {
    const isArchived = filters.status === 'archived'
    const fetchFn = isArchived ? fetchTeacherArchivedClasses : fetchTeacherClasses
    const { data } = await fetchFn()
    const payload = data?.data || []
    applyClassesPayload(payload)
    writeSessionCache(cacheKey, payload)
    errorMessage.value = ''
  } catch (error) {
    if (!hadCache) {
      classes.value = []
      errorMessage.value = error.message || tm('classes.loadFailed')
    }
  } finally {
    loading.value = false
  }
}

async function loadClasses(options = {}) {
  const cacheKey = classesCacheKey()
  const cached = options.force ? null : readSessionCache(cacheKey, 180000)
  const hadCache = Boolean(cached?.length)
  if (cached && Array.isArray(cached)) {
    applyClassesPayload(cached)
    loading.value = false
    errorMessage.value = ''
    if (!options.force) {
      void refreshClassesFromNetwork(cacheKey, hadCache)
      return
    }
  } else {
    loading.value = true
    errorMessage.value = ''
  }

  await refreshClassesFromNetwork(cacheKey, hadCache)
}

function openCreateDialog() {
  form.name = ''
  form.semester = '2025-2026学年度 第2学期'
  errorMessage.value = ''
  createDialogOpen.value = true
}

function closeCreateDialog() {
  if (creating.value) return
  createDialogOpen.value = false
}

function normalizeInvitePayload(raw) {
  return {
    code: raw?.code || raw?.inviteCode || raw?.activeInviteCode || '',
    expireAt: raw?.expireAt || raw?.inviteExpireAt || '',
  }
}

async function refreshCreatedClassInvite(classId) {
  if (!classId || classId === '-') {
    return
  }
  try {
    const inviteResponse = await generateTeacherClassInviteCode(classId)
    applyInviteToClass(classId, inviteResponse?.data?.data || {})
    invalidateTeacherClassCache(classId)
  } catch {
    // 邀请码可在班级详情中再次生成
  }
}

async function submitCreateClass() {
  if (!form.name.trim() || !form.semester) {
    errorMessage.value = tm('classes.nameSemesterRequired')
    return
  }

  creating.value = true
  try {
    const { data } = await createTeacherClass({
      name: form.name.trim(),
      semester: form.semester,
      groupSizeMin: 1,
      groupSizeMax: 10,
    })
    const created = normalizeClass({
      ...(data?.data || {}),
      studentCount: 0,
    })
    createDialogOpen.value = false
    errorMessage.value = ''

    invalidateTeacherClassesListCache()

    filters.status = 'all'
    filters.keyword = ''
    filters.semester = 'all'

    if (created.id && created.id !== '-') {
      classes.value = [created, ...classes.value.filter((item) => item.id !== created.id)]
    }

    if (route.path !== '/teacher/classes') {
      await router.push('/teacher/classes')
    }

    await loadClasses({ force: true })
    notifyTeacherClassesChanged()
    void refreshCreatedClassInvite(created.id)
  } catch (error) {
    errorMessage.value = error.message || tm('classes.createFailed')
  } finally {
    creating.value = false
  }
}

function goToDetail(item) {
  if (!item?.id || item.id === '-') {
    return
  }
  router.push(`/teacher/classes/${item.id}`)
}

function applyInviteToClass(classId, payload) {
  const normalized = normalizeInvitePayload(payload)
  const index = classes.value.findIndex((item) => item.id === classId)
  if (index < 0 || !normalized.code) {
    return
  }
  classes.value[index] = {
    ...classes.value[index],
    inviteCode: normalized.code,
    inviteExpireAt: normalized.expireAt || classes.value[index].inviteExpireAt || '',
  }
}

async function copyInviteCode(item, event) {
  event?.stopPropagation?.()
  const code = String(item?.inviteCode || '').trim()
  if (!isInviteValid(item)) {
    return
  }
  try {
    await navigator.clipboard.writeText(code)
    copiedClassId.value = item.id
    if (copyHintTimer) {
      clearTimeout(copyHintTimer)
    }
    copyHintTimer = window.setTimeout(() => {
      copiedClassId.value = ''
      copyHintTimer = null
    }, 1800)
  } catch {
    copiedClassId.value = ''
  }
}

async function generateInviteCodeForCard(item, event) {
  event?.stopPropagation?.()
  if (!item?.id || item.id === '-' || generatingInviteClassId.value === item.id) {
    return
  }

  generatingInviteClassId.value = item.id
  copiedClassId.value = ''
  try {
    const inviteResponse = await generateTeacherClassInviteCode(item.id)
    applyInviteToClass(item.id, inviteResponse?.data?.data || {})
    invalidateTeacherClassCache(item.id)
  } catch (error) {
    errorMessage.value = error.message || tm('classes.inviteGenerateFailed')
  } finally {
    if (generatingInviteClassId.value === item.id) {
      generatingInviteClassId.value = ''
    }
  }
}

async function handleInviteCodeAction(item, event) {
  if (isInviteValid(item)) {
    await copyInviteCode(item, event)
    return
  }
  await generateInviteCodeForCard(item, event)
}

function setStatusFilter(status) {
  filters.status = status
}

function cardAccentClass(index) {
  return `accent-${index % 4}`
}

function openRestoreDialog(item, event) {
  event.stopPropagation()
  restoringClass.value = item
  restoreDialogOpen.value = true
}

function closeRestoreDialog() {
  if (restoring.value) return
  restoringClass.value = null
  restoreDialogOpen.value = false
}

async function confirmRestoreClass() {
  if (!restoringClass.value) return
  restoring.value = true
  try {
    await restoreTeacherClass(restoringClass.value.id)
    restoreDialogOpen.value = false
    restoringClass.value = null
    await loadClasses()
  } catch (error) {
    errorMessage.value = error.message || tm('classes.restoreFailed')
  } finally {
    restoring.value = false
  }
}

watch(() => filters.status, () => {
  loadClasses()
})

onMounted(() => {
  if (route.query.create === '1') {
    filters.status = 'all'
    openCreateDialog()
    const nextQuery = { ...route.query }
    delete nextQuery.create
    delete nextQuery.status
    router.replace({ path: route.path, query: nextQuery })
  }
  loadClasses()
})
</script>

<template>
  <div class="teacher-page classes-page">
    <header class="page-hero">
      <div class="hero-top">
        <div class="title-block">
          <p class="eyebrow">{{ tm('classes.eyebrow') }}</p>
          <h2>{{ tm('classes.title') }}</h2>
        </div>
        <div class="top-actions">
          <button class="primary-btn hero-create-btn" type="button" @click="openCreateDialog">
            <span class="btn-icon" aria-hidden="true">+</span>
            {{ tm('classes.createClass') }}
          </button>
        </div>
      </div>

      <div class="hero-stats" role="tablist" :aria-label="tm('classes.statusFilterAria')">
        <button
          class="stat-chip"
          :class="{ active: filters.status === 'all' }"
          type="button"
          role="tab"
          :aria-selected="filters.status === 'all'"
          @click="setStatusFilter('all')"
        >
          <span class="stat-label">{{ tm('classes.filterAll') }}</span>
          <strong class="stat-value">{{ summary.total }}</strong>
        </button>
        <button
          class="stat-chip"
          :class="{ active: filters.status === 'active' }"
          type="button"
          role="tab"
          :aria-selected="filters.status === 'active'"
          @click="setStatusFilter('active')"
        >
          <span class="stat-label">{{ tm('classes.filterActive') }}</span>
          <strong class="stat-value">{{ summary.active }}</strong>
        </button>
        <button
          class="stat-chip"
          :class="{ active: filters.status === 'archived' }"
          type="button"
          role="tab"
          :aria-selected="filters.status === 'archived'"
          @click="setStatusFilter('archived')"
        >
          <span class="stat-label">{{ tm('classes.filterArchived') }}</span>
          <strong class="stat-value">{{ summary.archived }}</strong>
        </button>
      </div>
    </header>

    <p v-if="errorMessage" class="message error">{{ errorMessage }}</p>

    <section class="toolbar-panel">
      <div class="toolbar-inner">
        <div class="toolbar-filters">
          <label class="search-control">
            <span class="sr-only">{{ tm('classes.searchAria') }}</span>
            <svg class="search-icon" width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <circle cx="7" cy="7" r="4.5" stroke="currentColor" stroke-width="1.5" />
              <path d="M10.5 10.5L14 14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
            </svg>
            <input
              v-model.trim="filters.keyword"
              type="search"
              name="teacher-class-search"
              autocomplete="off"
              :placeholder="tm('classes.searchPlaceholder')"
            />
            <button
              v-if="filters.keyword"
              class="search-clear"
              type="button"
              :aria-label="tm('classes.clearSearchAria')"
              @click="filters.keyword = ''"
            >
              ×
            </button>
          </label>

          <span class="toolbar-divider" aria-hidden="true"></span>

          <label class="semester-control">
            <span class="control-prefix">{{ tm('classes.semester') }}</span>
            <select v-model="filters.semester">
              <option value="all">{{ tm('classes.allSemesters') }}</option>
              <option v-for="item in semesterOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
            <svg class="select-chevron" width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
              <path d="M3.5 5.5L7 9l3.5-3.5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </label>
        </div>

        <div class="toolbar-meta-pill" :class="{ loading: loading }">
          <template v-if="loading">
            <span class="meta-dot" aria-hidden="true"></span>
            {{ tm('classes.loading') }}
          </template>
          <template v-else>
            <span class="meta-label">{{ tm('classes.showing') }}</span>
            <strong class="meta-highlight">{{ filteredClasses.length }}</strong>
            <span class="meta-sep">/</span>
            <span class="meta-total">{{ classes.length }}</span>
            <span class="meta-suffix">{{ tm('classes.classUnit') }}</span>
          </template>
        </div>
      </div>
    </section>

    <section v-if="loading" class="class-grid skeleton-grid" aria-busy="true" :aria-label="tm('classes.loadingAria')">
      <article v-for="n in 4" :key="n" class="class-card-skeleton" />
    </section>

    <section v-else class="class-grid teacher-page-scroll">
      <article
        v-for="(item, index) in filteredClasses"
        :key="item.id"
        class="class-card"
        :class="[
          cardAccentClass(index),
          { archived: item.statusKey === 'archived', active: item.statusKey === 'active' },
        ]"
        role="button"
        tabindex="0"
        @click="goToDetail(item)"
        @keydown.enter.prevent="goToDetail(item)"
        @keydown.space.prevent="goToDetail(item)"
      >
        <div class="card-top">
          <div class="card-title-block">
            <p class="class-name">{{ item.name }}</p>
            <p class="class-meta">{{ item.semesterLabel }}</p>
          </div>
          <div class="card-top-right">
            <span class="status-badge" :class="{ archive: item.statusKey === 'archived' }">{{ item.statusLabel }}</span>
            <button
              v-if="item.statusKey === 'archived'"
              class="restore-btn"
              type="button"
              @click="openRestoreDialog(item, $event)"
            >{{ tm('classes.restore') }}</button>
          </div>
        </div>

        <div class="card-middle">
          <div class="metric-box metric-box--code">
            <span class="metric-label">{{ tm('classes.classCode') }}</span>
            <strong class="metric-value compact">{{ item.code }}</strong>
          </div>
          <div class="metric-box metric-box--students">
            <span class="metric-label">{{ tm('classes.studentCount') }}</span>
            <strong class="metric-value">{{ item.students }}</strong>
          </div>
          <div class="metric-box metric-box--groups">
            <span class="metric-label">{{ tm('classes.groupCount') }}</span>
            <strong class="metric-value">{{ tm('classes.groupsUnit', { count: item.groupCount }) }}</strong>
          </div>
          <div v-if="item.statusKey === 'archived'" class="metric-box metric-box--muted">
            <span class="metric-label">{{ tm('classes.dissolvedAt') }}</span>
            <strong class="metric-value compact">{{ formatDeletedAt(item.deletedAt) }}</strong>
          </div>
          <div v-if="item.statusKey === 'archived'" class="metric-box metric-box--muted">
            <span class="metric-label">{{ tm('classes.remainingRestore') }}</span>
            <strong class="metric-value" :class="{ 'warn-text': calcRemainingDays(item.deletedAt) <= 7, 'danger-text': calcRemainingDays(item.deletedAt) === 0 }">
              {{ formatRemainingDays(item.deletedAt) }}
            </strong>
          </div>
          <div
            v-if="item.statusKey !== 'archived'"
            class="metric-box metric-box--invite invite-code-box"
            :class="{
              copied: copiedClassId === item.id,
              generating: generatingInviteClassId === item.id,
              'needs-invite': !isInviteValid(item),
            }"
            @click.stop="handleInviteCodeAction(item, $event)"
          >
            <span class="metric-label">{{ tm('classes.inviteCode') }}</span>
            <strong class="metric-value compact invite-code-value">
              {{ inviteCodeDisplay(item) }}
              <span class="copy-hint">{{ inviteCodeHint(item) }}</span>
            </strong>
          </div>
        </div>

      </article>

      <article v-if="!filteredClasses.length" class="empty-card">
        <h3>{{ tm('classes.emptyTitle') }}</h3>
        <p v-if="filters.status === 'archived'">{{ tm('classes.emptyArchived') }}</p>
        <p v-else-if="filters.keyword || filters.semester !== 'all'">{{ tm('classes.emptyFiltered') }}</p>
        <p v-else>{{ tm('classes.emptyDefault') }}</p>
        <button
          v-if="filters.status !== 'archived' && !filters.keyword && filters.semester === 'all'"
          class="primary-btn empty-action-btn"
          type="button"
          @click="openCreateDialog"
        >
          {{ tm('classes.createClass') }}
        </button>
      </article>
    </section>

    <WorkspaceDialogMask :open="createDialogOpen" @close="closeCreateDialog">
      <section class="dialog-panel">
        <div class="dialog-head">
          <h3>{{ tm('classes.createDialogTitle') }}</h3>
        </div>

        <div class="dialog-grid">
          <label class="full-width">
            <span>{{ tm('classes.className') }}</span>
            <input v-model.trim="form.name" type="text" :placeholder="tm('classes.classNamePlaceholder')" />
          </label>

          <label class="full-width">
            <span>{{ tm('classes.semester') }}</span>
            <select v-model="form.semester">
              <option value="" disabled>{{ tm('classes.selectSemester') }}</option>
              <option v-for="item in semesterPresetOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>
        </div>

        <div class="dialog-actions create-dialog-actions">
          <button class="secondary-btn" type="button" @click="closeCreateDialog">{{ tm('classes.cancelCreate') }}</button>
          <button class="primary-btn" type="button" :disabled="creating" @click="submitCreateClass">
            {{ creating ? tm('classes.creatingWithInvite') : tm('classes.createWithInvite') }}
          </button>
        </div>
      </section>
    </WorkspaceDialogMask>

    <WorkspaceDialogMask :open="restoreDialogOpen" @close="closeRestoreDialog">
      <section class="dialog-panel">
        <div class="dialog-head">
          <h3>{{ tm('classes.restoreDialogTitle') }}</h3>
          <button class="secondary-btn" type="button" @click="closeRestoreDialog">{{ tm('common.cancel') }}</button>
        </div>
        <div class="dialog-grid">
          <div class="restore-notice">
            <p><strong>{{ tm('classes.restoreNoticeTitle') }}</strong></p>
            <p>{{ tm('classes.restoreNoticeInvite') }}</p>
            <p>{{ tm('classes.restoreNoticeWindow') }}</p>
          </div>
          <article class="dialog-summary" v-if="restoringClass">
            <p class="summary-name">{{ restoringClass.name || tm('common.unnamedClass') }}</p>
            <p class="summary-note">{{ restoringClass.semesterLabel || '-' }} · {{ tm('classes.studentUnit', { count: restoringClass.students || 0 }) }}</p>
          </article>
        </div>
        <div class="dialog-actions">
          <button class="primary-btn restore-confirm-btn" type="button" :disabled="restoring" @click="confirmRestoreClass">
            {{ restoring ? tm('classes.restoring') : tm('classes.restoreConfirm') }}
          </button>
        </div>
      </section>
    </WorkspaceDialogMask>
  </div>
</template>

<style scoped>
.classes-page,
.class-grid,
.dialog-grid,
.summary-grid {
  display: grid;
  gap: 18px;
}

.classes-page {
  gap: 20px;
}

.page-hero {
  display: grid;
  gap: 18px;
  padding: 22px 24px;
  border-radius: 22px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 14%, var(--tt-border));
  background:
    radial-gradient(circle at 0% 0%, color-mix(in srgb, var(--tt-accent) 14%, transparent), transparent 38%),
    radial-gradient(circle at 100% 0%, rgba(124, 58, 237, 0.1), transparent 34%),
    linear-gradient(165deg, var(--tt-surface), var(--tt-surface-muted));
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.04),
    0 14px 36px rgba(37, 99, 235, 0.08);
}

.hero-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
}

.title-block {
  flex: 1;
  min-width: 220px;
}

.title-block h2 {
  margin: 0;
  font-size: 30px;
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1.15;
  color: var(--tt-text);
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 10px;
  margin: 0 0 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--tt-accent);
  background: var(--tt-accent-soft);
  border: 1px solid var(--tt-accent-border);
}

.page-note {
  font-size: 14px;
  line-height: 1.55;
  color: var(--tt-text-secondary);
  margin: 8px 0 0;
  max-width: 52ch;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.stat-chip {
  display: grid;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid var(--tt-border-subtle);
  background: var(--tt-surface-muted);
  text-align: left;
  cursor: pointer;
  transition:
    transform 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    background 0.18s ease;
}

.stat-chip:hover {
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--tt-accent) 22%, transparent);
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.1);
}

.stat-chip.active {
  border-color: color-mix(in srgb, var(--tt-accent) 32%, transparent);
  background: linear-gradient(160deg, var(--tt-surface), color-mix(in srgb, var(--tt-accent) 10%, var(--tt-surface-muted)));
  box-shadow:
    0 0 0 1px color-mix(in srgb, var(--tt-accent) 18%, transparent),
    0 12px 28px rgba(37, 99, 235, 0.14);
}

.stat-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--tt-text-secondary);
}

.stat-chip.active .stat-label {
  color: var(--tt-accent);
}

.stat-value {
  font-size: 28px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.03em;
  color: var(--tt-text);
}

.stat-chip.active .stat-value {
  color: var(--tt-accent);
}

.top-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-shrink: 0;
}

.hero-create-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 0 20px;
  border-radius: 12px;
  font-size: 14px;
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.22);
}

.btn-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.22);
  font-size: 18px;
  font-weight: 700;
  line-height: 1;
}

.toolbar-panel {
  position: relative;
  overflow: hidden;
  padding: 0;
  border-radius: 20px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 14%, var(--tt-border));
  background:
    radial-gradient(circle at 100% 0%, color-mix(in srgb, var(--tt-accent) 8%, transparent), transparent 48%),
    linear-gradient(165deg, var(--tt-surface), color-mix(in srgb, var(--tt-accent) 4%, var(--tt-surface-muted)));
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.04),
    0 10px 28px rgba(37, 99, 235, 0.07);
}

.toolbar-panel::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(
    90deg,
    var(--tt-accent),
    color-mix(in srgb, var(--tt-accent) 45%, #7c3aed)
  );
  opacity: 0.9;
}

.toolbar-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  padding: 12px 14px;
}

.toolbar-filters {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: min(100%, 320px);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.search-control,
.semester-control {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 12px;
  border-radius: 12px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border));
  background: var(--tt-surface-muted);
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    background 0.18s ease;
}

.search-control {
  flex: 1;
  min-width: 0;
  max-width: 420px;
}

.semester-control {
  flex-shrink: 0;
  min-width: 168px;
  padding-right: 10px;
}

.search-control:focus-within,
.semester-control:focus-within {
  border-color: color-mix(in srgb, var(--tt-accent) 38%, var(--tt-border));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--tt-accent) 12%, transparent);
  background: var(--tt-surface);
}

.search-icon {
  flex-shrink: 0;
  color: var(--tt-text-tertiary);
}

.search-control:focus-within .search-icon {
  color: var(--tt-accent);
}

.search-control input {
  flex: 1;
  min-width: 0;
  height: 40px;
  padding: 0;
  border: 0;
  font-size: 14px;
  color: var(--tt-text);
  background: transparent;
  outline: none;
}

.search-control input::placeholder {
  color: var(--tt-text-tertiary);
}

.search-control input::-webkit-search-cancel-button {
  display: none;
}

.search-clear {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 6px;
  background: rgba(15, 23, 42, 0.06);
  color: var(--tt-text-secondary);
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.search-clear:hover {
  background: color-mix(in srgb, var(--tt-accent) 12%, transparent);
  color: var(--tt-accent);
}

.toolbar-divider {
  flex-shrink: 0;
  width: 1px;
  height: 24px;
  background: color-mix(in srgb, var(--tt-accent) 12%, var(--tt-border));
}

.control-prefix {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 700;
  color: var(--tt-text-tertiary);
  letter-spacing: 0.02em;
}

.semester-control select {
  flex: 1;
  min-width: 0;
  height: 40px;
  padding: 0;
  padding-right: 4px;
  border: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--tt-text);
  background: transparent;
  outline: none;
  cursor: pointer;
  appearance: none;
}

.select-chevron {
  flex-shrink: 0;
  color: var(--tt-text-tertiary);
  pointer-events: none;
}

.toolbar-meta-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  min-height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 16%, transparent);
  background: color-mix(in srgb, var(--tt-accent) 7%, var(--tt-surface));
  font-size: 13px;
  font-weight: 600;
  color: var(--tt-text-secondary);
  white-space: nowrap;
}

.toolbar-meta-pill.loading {
  color: var(--tt-text-tertiary);
}

.meta-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--tt-accent);
  animation: meta-pulse 1s ease-in-out infinite;
}

@keyframes meta-pulse {
  0%,
  100% {
    opacity: 0.35;
  }
  50% {
    opacity: 1;
  }
}

.meta-label,
.meta-suffix {
  font-weight: 600;
  color: var(--tt-text-tertiary);
}

.meta-highlight {
  font-size: 15px;
  font-weight: 800;
  color: var(--tt-accent);
  letter-spacing: -0.02em;
}

.meta-sep {
  color: var(--tt-text-tertiary);
  font-weight: 500;
}

.meta-total {
  font-weight: 700;
  color: var(--tt-text);
}

.skeleton-grid {
  pointer-events: none;
}

.class-card-skeleton {
  min-height: 220px;
  border-radius: 20px;
  border: 1px solid var(--tt-border-subtle);
  background: linear-gradient(
    110deg,
    var(--tt-surface-muted) 8%,
    var(--tt-surface-hover) 18%,
    var(--tt-surface-muted) 33%
  );
  background-size: 200% 100%;
  animation: class-skeleton-shine 1.4s ease-in-out infinite;
}

@keyframes class-skeleton-shine {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

.secondary-btn {
  min-height: 40px;
  padding: 0 16px;
  border-radius: 10px;
  border: 1px solid var(--tt-border);
  background: var(--tt-surface);
  color: var(--tt-text);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.18s ease;
}

.secondary-btn:hover {
  border-color: color-mix(in srgb, var(--tt-accent) 24%, var(--tt-border));
  background: var(--tt-surface-hover);
}

.primary-btn {
  min-height: 40px;
  padding: 0 16px;
  border-radius: 10px;
  border: 1px solid var(--tt-accent);
  background: linear-gradient(160deg, var(--tt-accent), color-mix(in srgb, var(--tt-accent) 82%, #1d4ed8));
  color: var(--tt-text-inverse);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.18s ease;
}

.primary-btn:hover {
  filter: brightness(1.04);
  transform: translateY(-1px);
}

.primary-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}

.message.error {
  padding: 12px 16px;
  border-radius: 14px;
  background: var(--tt-danger-soft);
  border: 1px solid rgba(220, 38, 38, 0.28);
  color: var(--tt-danger);
  font-size: 13px;
  font-weight: 600;
}

.summary-grid {
  grid-template-columns: repeat(3, 1fr);
}

.summary-card {
  background: var(--tt-surface);
  border: 1px solid var(--tt-border);
  border-radius: var(--teacher-radius-card);
  padding: 16px 18px;
  box-shadow: var(--tt-shadow-xs);
  transition: all 0.15s;
}

.summary-card.clickable {
  cursor: pointer;
}

.summary-card.clickable:hover {
  border-color: var(--tt-border-strong);
  box-shadow: var(--tt-shadow-sm);
}

.summary-card.active {
  border-color: var(--tt-accent);
  background: var(--tt-accent-soft);
}

.summary-label {
  font-size: 12px;
  color: var(--tt-text-tertiary);
  margin: 0 0 6px;
}

.summary-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--tt-text);
  letter-spacing: -0.5px;
}

.summary-note {
  font-size: 12px;
  color: var(--tt-text-tertiary);
  margin: 4px 0 0;
}

.class-grid {
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 18px;
}

.class-card {
  position: relative;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 12%, var(--tt-border));
  border-radius: 20px;
  padding: 20px 22px 22px;
  background:
    radial-gradient(circle at 100% 0%, color-mix(in srgb, var(--card-accent, var(--tt-accent)) 14%, transparent), transparent 42%),
    linear-gradient(165deg, var(--tt-surface), color-mix(in srgb, var(--card-accent, var(--tt-accent)) 4%, var(--tt-surface-muted)));
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.04),
    0 10px 28px rgba(15, 23, 42, 0.06);
  cursor: pointer;
  transition:
    transform 0.2s var(--tt-ease-ios, ease),
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.class-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(
    90deg,
    var(--card-accent, var(--tt-accent)),
    color-mix(in srgb, var(--card-accent, var(--tt-accent)) 55%, white)
  );
  opacity: 0.92;
}

.class-card.accent-0 {
  --card-accent: #2563eb;
}

.class-card.accent-1 {
  --card-accent: #7c3aed;
}

.class-card.accent-2 {
  --card-accent: #0891b2;
}

.class-card.accent-3 {
  --card-accent: #059669;
}

.class-card.active:hover {
  border-color: color-mix(in srgb, var(--card-accent) 32%, var(--tt-border));
  box-shadow:
    0 4px 12px rgba(15, 23, 42, 0.06),
    0 18px 40px color-mix(in srgb, var(--card-accent) 16%, transparent);
  transform: translateY(-3px);
}

.class-card.archived {
  --card-accent: #94a3b8;
  opacity: 0.92;
  background:
    radial-gradient(circle at 100% 0%, rgba(148, 163, 184, 0.12), transparent 40%),
    linear-gradient(165deg, var(--tt-surface), var(--tt-surface-muted));
}

.class-card.archived::before {
  opacity: 0.45;
}

.class-card.archived:hover {
  opacity: 1;
  transform: translateY(-2px);
}

.card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
  padding-top: 4px;
}

.card-title-block {
  flex: 1;
  min-width: 0;
}

.class-name {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--tt-text);
  margin: 0 0 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  letter-spacing: -0.02em;
}

.class-meta {
  font-size: 13px;
  color: var(--tt-text-secondary);
  margin: 0;
}

.card-top-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  background: color-mix(in srgb, var(--card-accent, var(--tt-accent)) 14%, var(--tt-surface-muted));
  color: var(--card-accent, var(--tt-accent));
  border: 1px solid color-mix(in srgb, var(--card-accent, var(--tt-accent)) 22%, transparent);
  box-shadow: 0 4px 12px color-mix(in srgb, var(--card-accent, var(--tt-accent)) 10%, transparent);
}

.status-badge.archive {
  background: var(--tt-surface-muted);
  color: var(--tt-text-secondary);
  border-color: var(--tt-border);
  box-shadow: none;
}

.restore-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 6px;
  border: 1px solid var(--tt-success);
  background: var(--tt-success-soft);
  color: var(--tt-success);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  margin-left: 8px;
}

.restore-btn:hover {
  background: rgba(34, 197, 94, 0.16);
  border-color: var(--tt-success);
}

.restore-notice {
  background: var(--tt-success-soft);
  border: 1px solid rgba(34, 197, 94, 0.24);
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 12px;
}

.restore-notice p {
  margin: 0 0 4px;
  font-size: 13px;
  color: var(--tt-success);
}

.restore-notice p:last-child {
  margin-bottom: 0;
}

.restore-confirm-btn {
  background: var(--tt-success);
  border-color: var(--tt-success);
}

.restore-confirm-btn:hover {
  filter: brightness(1.05);
}

.card-middle {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.metric-box {
  min-height: 76px;
  display: grid;
  align-content: space-between;
  gap: 8px;
  border-radius: 14px;
  padding: 12px 14px;
  background: var(--tt-surface-muted);
  border: 1px solid var(--tt-border-subtle);
  backdrop-filter: blur(6px);
}

.metric-box--code {
  background: color-mix(in srgb, var(--card-accent, var(--tt-accent)) 6%, var(--tt-surface));
  border-color: color-mix(in srgb, var(--card-accent, var(--tt-accent)) 12%, transparent);
}

.metric-box--students .metric-value {
  color: color-mix(in srgb, var(--card-accent, #2563eb) 78%, #1e3a8a);
}

.metric-box--groups .metric-value {
  color: color-mix(in srgb, var(--card-accent, #7c3aed) 70%, #5b21b6);
}

.metric-box--invite {
  background: linear-gradient(145deg, var(--tt-surface-muted), color-mix(in srgb, var(--card-accent) 8%, var(--tt-surface)));
  border-color: color-mix(in srgb, var(--card-accent) 18%, transparent);
}

.metric-box--muted {
  background: var(--tt-surface-muted);
}

.metric-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--tt-text-secondary);
  margin-bottom: 0;
}

.metric-value {
  display: block;
  font-size: 22px;
  font-weight: 800;
  line-height: 1.2;
  color: var(--tt-text);
  letter-spacing: -0.02em;
}

.metric-value.compact {
  font-size: 15px;
  font-weight: 700;
  line-height: 1.35;
  word-break: break-all;
}

.status-text {
  color: var(--tt-success);
}

.invite-code-box {
  cursor: pointer;
  position: relative;
  transition: background 0.15s, border-color 0.15s;
}

.invite-code-box:hover {
  background: linear-gradient(145deg, var(--tt-surface), color-mix(in srgb, var(--card-accent) 12%, var(--tt-surface-muted)));
  border-color: color-mix(in srgb, var(--card-accent) 28%, transparent);
  box-shadow: 0 8px 20px color-mix(in srgb, var(--card-accent) 12%, transparent);
}

.invite-code-box.copied {
  background: linear-gradient(145deg, #f0fdf4, #ecfdf5);
  border-color: rgba(34, 197, 94, 0.35);
  box-shadow: 0 8px 20px rgba(34, 197, 94, 0.12);
}

.invite-code-box.needs-invite {
  border-style: dashed;
  border-color: color-mix(in srgb, var(--tt-accent) 32%, var(--tt-border));
}

.invite-code-box.needs-invite:hover {
  border-style: solid;
}

.invite-code-box.generating {
  pointer-events: none;
  opacity: 0.72;
}

.invite-code-box.needs-invite .copy-hint {
  opacity: 1;
  color: var(--tt-accent);
}

.invite-code-value {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.copy-hint {
  font-size: 11px;
  font-weight: 500;
  color: var(--tt-text-tertiary);
  opacity: 0;
  transition: opacity 0.15s;
}

.invite-code-box:hover .copy-hint,
.invite-code-box.copied .copy-hint {
  opacity: 1;
  color: var(--tt-accent);
}

.invite-code-box.copied .copy-hint {
  color: var(--tt-success);
}

.empty-card {
  grid-column: 1 / -1;
  text-align: center;
  padding: 48px 24px;
  background:
    radial-gradient(circle at 50% 0%, var(--tt-accent-soft), transparent 55%),
    var(--tt-surface);
  border: 1px dashed color-mix(in srgb, var(--tt-accent) 24%, var(--tt-border));
  border-radius: 20px;
  color: var(--tt-text-tertiary);
}

.empty-card h3 {
  font-size: 18px;
  font-weight: 700;
  color: var(--tt-text);
  margin: 0 0 8px;
}

.empty-card p {
  font-size: 14px;
  line-height: 1.55;
  color: var(--tt-text-secondary);
  margin: 0 0 16px;
  max-width: 36ch;
}

.empty-action-btn {
  min-width: 140px;
}

.dialog-mask {
  /* positioning handled by teacher-dialog.css */
}

.dialog-panel {
  background: var(--tt-surface);
  border: 1px solid color-mix(in srgb, var(--tt-accent) 12%, var(--tt-border));
  border-radius: 20px;
  width: 100%;
  max-width: 520px;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.16);
  overflow: hidden;
}

.dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 22px;
  border-bottom: 1px solid var(--tt-divider);
  background: linear-gradient(180deg, var(--tt-surface-muted), var(--tt-surface));
}

.dialog-head h3 {
  font-size: 18px;
  font-weight: 700;
  color: var(--tt-text);
  margin: 0;
  letter-spacing: -0.02em;
}

.dialog-grid {
  padding: 22px;
  display: grid;
  gap: 16px;
}

.dialog-grid label {
  display: grid;
  gap: 6px;
}

.dialog-grid label span {
  font-size: 12px;
  color: var(--tt-text-secondary);
  font-weight: 600;
}

.dialog-grid input,
.dialog-grid select {
  min-height: 44px;
  padding: 0 14px;
  border: 1px solid var(--tt-border);
  border-radius: 12px;
  font-size: 14px;
  color: var(--tt-text);
  background: var(--tt-surface);
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease;
}

.dialog-grid input:focus,
.dialog-grid input:focus-visible,
.dialog-grid select:focus,
.dialog-grid select:focus-visible,
.dialog-grid textarea:focus,
.dialog-grid textarea:focus-visible {
  outline: none;
  border-color: color-mix(in srgb, var(--tt-accent) 40%, var(--tt-border));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--tt-accent) 14%, transparent);
}

.dialog-grid .full-width {
  grid-column: 1 / -1;
}

.form-note {
  font-size: 12px;
  color: var(--tt-text-tertiary);
  margin: 0;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px 20px;
  border-top: 1px solid var(--tt-divider);
}

.create-dialog-actions {
  justify-content: space-between;
  align-items: center;
}

.dialog-summary {
  background: var(--tt-surface-muted);
  border: 1px solid var(--tt-border-subtle);
  border-radius: 8px;
  padding: 12px 16px;
}

.summary-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--tt-text);
  margin: 0 0 2px;
}

.summary-note {
  color: var(--tt-text-secondary);
}

.warn-text {
  color: var(--tt-warning);
}

.danger-text {
  color: var(--tt-danger);
}

@media (max-width: 1080px) {
  .hero-stats {
    grid-template-columns: 1fr;
  }

  .toolbar-inner {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar-filters {
    flex-direction: column;
    align-items: stretch;
    min-width: 0;
  }

  .search-control {
    max-width: none;
  }

  .semester-control {
    min-width: 0;
  }

  .toolbar-divider {
    display: none;
  }

  .toolbar-meta-pill {
    align-self: flex-start;
  }

}

@media (max-width: 720px) {
  .title-block h2 {
    font-size: 24px;
  }

  .hero-top {
    flex-direction: column;
  }

  .top-actions {
    width: 100%;
  }

  .hero-create-btn {
    width: 100%;
    justify-content: center;
  }
}

</style>
