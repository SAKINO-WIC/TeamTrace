<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { deleteBatchStudentNotifications, deleteStudentNotification, fetchStudentNotifications, markAllStudentNotificationsRead, markStudentNotificationRead } from '../services/student'
import { useStudentLocale } from '../composables/useStudentLocale'

const { tm, locale } = useStudentLocale()

const loading = ref(true)
const actionLoading = ref('')
const notifications = ref([])
const unreadCount = ref(0)
const filter = ref('all')
const activeId = ref('')
const message = ref('')
const messageType = ref('info')
const loadError = ref('')
const NOTIFICATION_REFRESH_INTERVAL_MS = 30000
let refreshTimer = null

const typeMeta = computed(() => ({
  new_appeal: { label: tm('notifications.typeAppeal'), tone: 'warning' },
  appeal_resolved: { label: tm('notifications.typeAppealResolved'), tone: 'success' },
  subtask_pending_review: { label: tm('notifications.typePendingReview'), tone: 'warning' },
  subtask_review_result: { label: tm('notifications.typeReviewResult'), tone: 'success' },
  subtask_sent_back: { label: tm('notifications.typeSentBack'), tone: 'danger' },
  subtask_deadline_soon: { label: tm('notifications.typeDeadlineSoon'), tone: 'warning' },
}))

const filters = computed(() => [
  { key: 'all', label: tm('notifications.filterAll'), count: notifications.value.length },
  { key: 'unread', label: tm('notifications.filterUnread'), count: unreadCount.value },
  { key: 'action', label: tm('notifications.filterPending'), count: actionItems.value.length },
])

const actionTypes = new Set(['subtask_pending_review', 'subtask_sent_back', 'subtask_deadline_soon', 'appeal_resolved'])

const actionItems = computed(() => notifications.value.filter((item) => actionTypes.has(item.type) && !item.read))

const filteredNotifications = computed(() => {
  if (filter.value === 'unread') return notifications.value.filter((item) => !item.read)
  if (filter.value === 'action') return actionItems.value
  return notifications.value
})

const activeNotification = computed(() => {
  return filteredNotifications.value.find((item) => String(item.id) === String(activeId.value)) || filteredNotifications.value[0] || null
})

const summaryCards = computed(() => [
  { label: tm('notifications.statUnread'), value: unreadCount.value },
  { label: tm('notifications.statPending'), value: actionItems.value.length },
  { label: tm('notifications.statTotal'), value: notifications.value.length },
])

const emptyQueueTitle = computed(() => {
  if (filter.value === 'unread') return tm('notifications.emptyUnread')
  if (filter.value === 'action') return tm('notifications.emptyPending')
  return tm('notifications.empty')
})

function typeLabel(type) {
  return typeMeta.value[type]?.label || type || tm('notifications.typeSystem')
}

function typeTone(type) {
  return typeMeta.value[type]?.tone || 'default'
}

function normalizeNotification(raw, index) {
  const readValue = raw?.read ?? raw?.isRead ?? false
  return {
    id: raw?.id ?? raw?.notificationId ?? `${index}`,
    type: raw?.type || 'system',
    title: raw?.title || tm('notifications.defaultTitle'),
    content: raw?.content || '',
    relatedId: raw?.relatedId ?? null,
    read: readValue === true || readValue === 1 || readValue === '1',
    createdAt: raw?.createdAt || raw?.createdTime || '',
  }
}

function formatTime(value) {
  if (!value) return '—'
  const dateLocale = locale.value === 'en-US' ? 'en-US' : 'zh-CN'
  return new Date(value).toLocaleString(dateLocale)
}

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
  if (type === 'success') {
    setTimeout(() => {
      if (message.value === text) message.value = ''
    }, 2600)
  }
}

function pickActive() {
  if (!filteredNotifications.value.length) {
    activeId.value = ''
    return
  }
  if (!filteredNotifications.value.some((item) => String(item.id) === String(activeId.value))) {
    activeId.value = String(filteredNotifications.value[0].id)
  }
}

function emitNotificationsChanged() {
  window.dispatchEvent(new CustomEvent('teamtrace-notifications-changed'))
}

async function loadNotifications(options = {}) {
  const silent = options.silent === true
  if (!silent) {
    loading.value = true
    loadError.value = ''
    setMessage('', 'info')
  }
  try {
    const { data } = await fetchStudentNotifications({ page: 1, size: 80 })
    const payload = data?.data || {}
    const list = payload.items || payload.list || payload.content || []
    notifications.value = Array.isArray(list) ? list.map(normalizeNotification) : []
    unreadCount.value = Number(payload.unreadCount ?? notifications.value.filter((item) => !item.read).length)
    pickActive()
  } catch (error) {
    if (!silent) {
      notifications.value = []
      unreadCount.value = 0
      activeId.value = ''
      loadError.value = error.message || tm('notifications.loadErrorFallback')
    }
  } finally {
    if (!silent) loading.value = false
  }
}

async function selectNotification(item) {
  activeId.value = String(item.id)
  if (!item.read) {
    await markRead(item.id, false)
  }
}

async function markRead(id, showSuccess = true) {
  if (!id) return
  actionLoading.value = String(id)
  try {
    await markStudentNotificationRead(id)
    notifications.value = notifications.value.map((item) => String(item.id) === String(id) ? { ...item, read: true } : item)
    unreadCount.value = notifications.value.filter((item) => !item.read).length
    emitNotificationsChanged()
    if (showSuccess) setMessage(tm('notifications.markedRead'), 'success')
  } catch (error) {
    setMessage(error.message || tm('notifications.actionFailed'), 'error')
  } finally {
    actionLoading.value = ''
  }
}

async function markAllRead() {
  actionLoading.value = 'all'
  try {
    await markAllStudentNotificationsRead()
    notifications.value = notifications.value.map((item) => ({ ...item, read: true }))
    unreadCount.value = 0
    emitNotificationsChanged()
    setMessage(tm('notifications.allMarkedRead'), 'success')
    pickActive()
  } catch (error) {
    setMessage(error.message || tm('notifications.actionFailed'), 'error')
  } finally {
    actionLoading.value = ''
  }
}

async function deleteNotification(id) {
  if (!id) return
  actionLoading.value = `delete-${id}`
  try {
    await deleteStudentNotification(id)
    notifications.value = notifications.value.filter((item) => String(item.id) !== String(id))
    unreadCount.value = notifications.value.filter((item) => !item.read).length
    emitNotificationsChanged()
    setMessage(tm('notifications.deleted'), 'success')
    pickActive()
  } catch (error) {
    setMessage(error.message || tm('notifications.deleteFailed'), 'error')
  } finally {
    actionLoading.value = ''
  }
}

async function clearReadNotifications() {
  const readIds = notifications.value.filter((item) => item.read).map((item) => item.id)
  if (!readIds.length) {
    setMessage(tm('notifications.noReadToClear'), 'info')
    return
  }
  actionLoading.value = 'clear-read'
  try {
    await deleteBatchStudentNotifications(readIds)
    notifications.value = notifications.value.filter((item) => !item.read)
    unreadCount.value = notifications.value.filter((item) => !item.read).length
    emitNotificationsChanged()
    setMessage(tm('notifications.cleared', { count: readIds.length }), 'success')
    pickActive()
  } catch (error) {
    setMessage(error.message || tm('notifications.clearFailed'), 'error')
  } finally {
    actionLoading.value = ''
  }
}

function setFilter(nextFilter) {
  filter.value = nextFilter
  pickActive()
}

watch(filteredNotifications, pickActive)

onMounted(() => {
  loadNotifications()
  refreshTimer = window.setInterval(() => loadNotifications({ silent: true }), NOTIFICATION_REFRESH_INTERVAL_MS)
})

onBeforeUnmount(() => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<template>
  <div class="student-page student-notifications">
    <section class="card notification-hero">
      <p class="eyebrow">{{ tm('notifications.eyebrow') }}</p>
      <div class="hero-actions">
        <button
          class="secondary-btn"
          type="button"
          :disabled="!notifications.some((n) => n.read) || actionLoading === 'clear-read'"
          @click="clearReadNotifications"
        >
          {{ actionLoading === 'clear-read' ? tm('notifications.clearing') : tm('notifications.clearRead') }}
        </button>
        <button
          class="primary-btn"
          type="button"
          :disabled="unreadCount === 0 || actionLoading === 'all'"
          @click="markAllRead"
        >
          {{ actionLoading === 'all' ? tm('notifications.processing') : tm('notifications.markAllRead') }}
        </button>
      </div>
    </section>

    <p v-if="message" class="message" :class="messageType">{{ message }}</p>

    <section v-if="loadError" class="card error-state">
      <div>
        <p class="eyebrow">{{ tm('notifications.loadErrorEyebrow') }}</p>
        <h3>{{ tm('notifications.loadErrorTitle') }}</h3>
        <p>{{ loadError }}</p>
      </div>
      <button class="primary-btn" type="button" :disabled="loading" @click="loadNotifications">
        {{ loading ? tm('notifications.reloading') : tm('notifications.reload') }}
      </button>
    </section>

    <template v-else>
      <section class="summary-strip">
        <article v-for="item in summaryCards" :key="item.label" class="card summary-card">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </article>
      </section>

      <section class="notification-workspace">
        <aside class="card notification-queue">
          <div class="queue-head">
            <p class="eyebrow">{{ tm('notifications.queueTitle') }}</p>
          </div>

          <div class="filter-tabs">
            <button
              v-for="item in filters"
              :key="item.key"
              class="tab-btn"
              :class="{ active: filter === item.key }"
              type="button"
              @click="setFilter(item.key)"
            >
              <span>{{ item.label }}</span>
              <strong>{{ item.count }}</strong>
            </button>
          </div>

          <div v-if="loading" class="loading-state">
            <div v-for="i in 5" :key="i" class="skeleton-row" />
          </div>

          <div v-else-if="!filteredNotifications.length" class="empty-state compact">
            <h3>{{ emptyQueueTitle }}</h3>
          </div>

          <div v-else class="queue-list">
            <button
              v-for="item in filteredNotifications"
              :key="item.id"
              class="notification-row"
              :class="{ active: String(activeNotification?.id) === String(item.id), unread: !item.read }"
              type="button"
              @click="selectNotification(item)"
            >
              <span class="type-pill" :class="typeTone(item.type)">{{ typeLabel(item.type) }}</span>
              <strong>{{ item.title }}</strong>
              <span class="row-time">{{ formatTime(item.createdAt) }}</span>
            </button>
          </div>
        </aside>

        <article class="card notification-detail" :class="{ 'is-empty': !activeNotification }">
          <template v-if="activeNotification">
            <div class="detail-head">
              <div>
                <span class="type-pill" :class="typeTone(activeNotification.type)">{{ typeLabel(activeNotification.type) }}</span>
                <h3>{{ activeNotification.title }}</h3>
                <p>{{ formatTime(activeNotification.createdAt) }}</p>
              </div>
              <span class="read-badge" :class="{ unread: !activeNotification.read }">
                {{ activeNotification.read ? tm('notifications.readBadge') : tm('notifications.unreadBadge') }}
              </span>
            </div>

            <div class="detail-body">
              <p>{{ activeNotification.content || tm('notifications.noContent') }}</p>
            </div>

            <div class="detail-actions">
              <button
                v-if="!activeNotification.read"
                class="primary-btn"
                type="button"
                :disabled="actionLoading === String(activeNotification.id)"
                @click="markRead(activeNotification.id)"
              >
                {{ actionLoading === String(activeNotification.id) ? tm('notifications.processing') : tm('notifications.markRead') }}
              </button>
              <button
                class="danger-btn"
                type="button"
                :disabled="actionLoading === `delete-${activeNotification.id}`"
                @click="deleteNotification(activeNotification.id)"
              >
                {{ actionLoading === `delete-${activeNotification.id}` ? tm('notifications.deleting') : tm('notifications.delete') }}
              </button>
            </div>
          </template>

          <div v-else class="empty-state detail-empty">
            <h3>{{ tm('notifications.selectHint') }}</h3>
          </div>
        </article>
      </section>
    </template>
  </div>
</template>

<style scoped>
.student-notifications,
.summary-strip,
.notification-workspace,
.notification-queue,
.notification-detail,
.queue-list,
.filter-tabs,
.loading-state {
  display: grid;
  gap: 14px;
}

.card {
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 24px;
  box-shadow: var(--student-shadow);
  backdrop-filter: blur(18px);
}

.notification-hero,
.hero-actions,
.detail-head,
.detail-actions,
.queue-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
}

.notification-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 22px 28px;
}

.hero-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
  flex-shrink: 0;
}

.eyebrow {
  margin: 0;
}

h2,
h3 {
  margin: 0;
  color: var(--student-text-primary);
}

h2 {
  margin-top: 8px;
  font-size: clamp(20px, 1.85vw, 24px);
  line-height: 1.28;
}

h3 {
  font-size: 17px;
}

.hero-note,
.detail-head p,
.detail-body p,
.empty-state p,
.message,
.summary-card span,
.row-time {
  margin: 8px 0 0;
  color: var(--student-text-secondary);
  font-size: 13px;
  line-height: 1.65;
}

.summary-strip {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.summary-card {
  padding: 18px;
}

.summary-card strong {
  display: block;
  margin-top: 10px;
  font-size: clamp(20px, 1.8vw, 24px);
  line-height: 1.1;
  color: var(--student-text-primary);
}

.notification-workspace {
  grid-template-columns: minmax(320px, 0.95fr) minmax(0, 1.05fr);
  align-items: start;
}

.notification-queue,
.notification-detail {
  padding: 18px;
}

.notification-detail {
  position: sticky;
  top: 18px;
  min-height: 420px;
  align-content: start;
}

.filter-tabs {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.tab-btn {
  min-height: 58px;
  padding: 10px 12px;
  border: 1px solid var(--student-border);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.74);
  color: var(--student-text-secondary);
  font-family: inherit;
  cursor: pointer;
  display: grid;
  gap: 5px;
  text-align: left;
}

.tab-btn.active {
  border-color: rgba(0, 122, 255, 0.25);
  background: rgba(0, 122, 255, 0.1);
  color: var(--student-accent);
}

.tab-btn strong {
  font-size: 18px;
  color: var(--student-text-primary);
}

.notification-row {
  position: relative;
  min-height: 92px;
  padding: 14px 16px;
  border: 1px solid var(--student-divider);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.68);
  text-align: left;
  font-family: inherit;
  cursor: pointer;
  display: grid;
  gap: 8px;
}

.notification-row.unread {
  background:
    radial-gradient(circle at top right, rgba(0, 122, 255, 0.12), transparent 34%),
    rgba(255, 255, 255, 0.76);
}

.notification-row.unread::before {
  content: '';
  position: absolute;
  top: 16px;
  right: 16px;
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--student-accent);
}

.notification-row.active {
  border-color: rgba(0, 122, 255, 0.28);
  box-shadow: 0 14px 30px rgba(0, 122, 255, 0.08);
}

.notification-row strong {
  color: var(--student-text-primary);
  padding-right: 14px;
}

.type-pill,
.read-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: fit-content;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.type-pill.default {
  background: rgba(15, 23, 42, 0.06);
  color: var(--student-text-secondary);
}

.type-pill.warning {
  background: var(--student-warning-soft);
  color: var(--student-warning);
}

.type-pill.success {
  background: var(--student-success-soft);
  color: var(--student-success);
}

.type-pill.danger {
  background: var(--student-danger-soft);
  color: var(--student-danger);
}

.read-badge {
  background: rgba(15, 23, 42, 0.06);
  color: var(--student-text-secondary);
}

.read-badge.unread {
  background: var(--student-accent-soft);
  color: var(--student-accent);
}

.detail-head h3 {
  margin-top: 12px;
  font-size: clamp(16px, 1.6vw, 19px);
  line-height: 1.3;
}

.detail-body {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.68);
  border: 1px solid var(--student-divider);
}

.detail-body p {
  margin: 0;
  color: var(--student-text-primary);
  font-size: 15px;
}

.detail-actions {
  justify-content: flex-start;
}

.message {
  padding: 10px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
}

.message.error {
  color: var(--student-danger);
}

.message.success {
  color: var(--student-success);
}

.primary-btn,
.secondary-btn {
  min-height: 42px;
  padding: 0 15px;
  border: 0;
  border-radius: 14px;
  font-family: inherit;
  font-weight: 700;
  cursor: pointer;
}

.primary-btn {
  background: var(--student-accent);
  color: #fff;
}

.secondary-btn {
  background: var(--student-surface-muted);
  color: var(--student-text-primary);
  border: 1px solid var(--student-border);
}

.primary-btn:disabled,
.secondary-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.danger-btn {
  min-height: 42px;
  padding: 0 15px;
  border: 0;
  border-radius: 14px;
  font-family: inherit;
  font-weight: 700;
  cursor: pointer;
  background: #e74c3c;
  color: #fff;
}

.danger-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.empty-state {
  min-height: 220px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  text-align: center;
  border: 1px dashed var(--student-border);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.62);
}

.error-state {
  min-height: 260px;
  padding: 28px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  text-align: center;
  border-color: rgba(255, 59, 48, 0.16);
}

.error-state p {
  max-width: 460px;
  margin: 0;
  color: var(--student-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.empty-state.compact {
  min-height: 180px;
}

.detail-empty {
  min-height: 360px;
}

.skeleton-row {
  height: 82px;
  border-radius: 18px;
  background: var(--student-surface-muted);
  animation: pulse 1.5s ease-in-out infinite;
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

@media (max-width: 1080px) {
  .notification-workspace {
    grid-template-columns: 1fr;
  }

  .notification-detail {
    position: static;
  }
}

@media (max-width: 760px) {
  .notification-hero,
  .hero-actions,
  .detail-head,
  .queue-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .summary-strip,
  .filter-tabs {
    grid-template-columns: 1fr;
  }
}

/* Layout polish: keep notification list and detail aligned in a single workbench. */
.notification-workspace {
  grid-template-columns: minmax(360px, 1fr) minmax(0, 1fr);
  height: clamp(540px, calc(100vh - 270px), 700px);
  align-items: stretch;
}

.notification-queue,
.notification-detail {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.notification-queue {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
}

.notification-detail {
  position: static;
  align-content: start;
  overflow-y: auto;
}

.queue-list,
.loading-state,
.empty-state.compact {
  min-height: 0;
}

.queue-list {
  overflow-y: auto;
  align-content: start;
  grid-auto-rows: min-content;
}

/* Empty detail should fill the right card; notification rows still keep content height. */
.notification-detail.is-empty {
  display: flex;
}

.notification-detail.is-empty .detail-empty {
  flex: 1;
  width: 100%;
  height: 100%;
  min-height: 0;
  margin: 0;
}

@media (max-width: 1080px) {
  .notification-workspace {
    height: auto;
    grid-template-columns: 1fr;
  }

  .notification-queue,
  .notification-detail {
    height: auto;
  }
}
</style>
