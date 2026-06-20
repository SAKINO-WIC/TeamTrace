<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import TeacherSubviewShell from '../components/teacher/TeacherSubviewShell.vue'
import { useTeacherLocale } from '../composables/useTeacherLocale'
import {
  deleteBatchTeacherNotices,
  deleteTeacherNotice,
  fetchTeacherNoticeCenter,
  markAllTeacherNoticesRead,
  markTeacherNoticeRead,
} from '../services/teacherLocal'
import { formatDateTime } from '../utils/teacher'

const { t, tm } = useTeacherLocale()

const loading = ref(false)
const message = ref('')
const messageType = ref('info')
const notices = ref([])
const NOTIFICATION_REFRESH_INTERVAL_MS = 30000
let refreshTimer = null

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

const unreadCount = computed(() => notices.value.filter((item) => !item.isRead).length)

function emitNotificationsChanged() {
  window.dispatchEvent(new CustomEvent('teamtrace-notifications-changed'))
}

async function loadPage(options = {}) {
  const silent = options.silent === true
  if (!silent) loading.value = true
  try {
    const noticeCenter = await fetchTeacherNoticeCenter()
    notices.value = noticeCenter.list
    if (!silent) setMessage('')
  } catch (error) {
    if (!silent) {
      notices.value = []
      setMessage(error.message || tm('notifications.loadFailed'), 'error')
    }
  } finally {
    if (!silent) loading.value = false
  }
}

async function handleRead(id) {
  const noticeCenter = await markTeacherNoticeRead(id)
  notices.value = noticeCenter.list
  emitNotificationsChanged()
  setMessage(tm('notifications.markedRead'), 'success')
}

async function handleReadAll() {
  const noticeCenter = await markAllTeacherNoticesRead()
  notices.value = noticeCenter.list
  emitNotificationsChanged()
  setMessage(tm('notifications.allMarkedRead'), 'success')
}

async function handleDelete(id) {
  const result = await deleteTeacherNotice(id)
  notices.value = result.list
  emitNotificationsChanged()
  setMessage(tm('notifications.deleted'), 'success')
}

async function handleClearRead() {
  const readIds = notices.value.filter((item) => item.isRead).map((item) => item.id)
  if (!readIds.length) {
    setMessage(tm('notifications.noReadToClear'), 'info')
    return
  }
  const result = await deleteBatchTeacherNotices(readIds)
  notices.value = result.list
  emitNotificationsChanged()
  setMessage(tm('notifications.cleared', { count: readIds.length }), 'success')
}

onMounted(() => {
  loadPage()
  refreshTimer = window.setInterval(() => loadPage({ silent: true }), NOTIFICATION_REFRESH_INTERVAL_MS)
})

onBeforeUnmount(() => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<template>
  <TeacherSubviewShell :title="tm('notifications.title')" :message="message" :message-type="messageType">
    <template #actions>
      <button class="secondary-btn" type="button" @click="handleReadAll">{{ tm('notifications.markAllRead') }}</button>
      <button class="secondary-btn" type="button" @click="handleClearRead">{{ tm('notifications.clearRead') }}</button>
    </template>

    <section v-if="loading" class="card panel loading-panel">
      <p>{{ t('加载通知…', 'Loading notifications…') }}</p>
    </section>

    <template v-else>
    <section class="stats-grid">
      <article class="card stat-card">
        <p class="label">{{ tm('notifications.total') }}</p>
        <p class="value">{{ notices.length }}</p>
      </article>
      <article class="card stat-card">
        <p class="label">{{ tm('notifications.unread') }}</p>
        <p class="value">{{ unreadCount }}</p>
      </article>
    </section>

    <section class="card panel">
      <div class="notice-list">
        <article v-for="item in notices" :key="item.id" class="notice-item" :class="{ unread: !item.isRead }">
          <div class="notice-head">
            <div>
              <p class="tag">{{ item.tag || t('通知', 'Notice') }}</p>
              <h3>{{ item.title }}</h3>
            </div>
            <div class="notice-actions">
              <button v-if="!item.isRead" class="link-btn" type="button" @click="handleRead(item.id)">{{ tm('notifications.markRead') }}</button>
              <button class="link-btn danger" type="button" @click="handleDelete(item.id)">{{ t('删除', 'Delete') }}</button>
            </div>
          </div>
          <p class="desc">{{ item.content }}</p>
          <p class="meta">{{ formatDateTime(item.createdAt) }}</p>
        </article>
        <p v-if="!notices.length" class="empty">{{ tm('notifications.noNotices') }}</p>
      </div>
    </section>
    </template>
  </TeacherSubviewShell>
</template>

<style scoped>
.card {
  background: var(--tt-surface);
  border-radius: var(--tt-radius-lg);
  box-shadow: var(--tt-shadow-sm);
}

.panel,
.stat-card {
  margin-top: 14px;
  padding: 16px;
}

.loading-panel {
  margin-top: 14px;
  padding: 24px 16px;
  color: var(--tt-text-secondary);
}

.stats-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.secondary-btn,
.link-btn {
  border: 0;
  cursor: pointer;
  font-family: inherit;
}

.notice-list {
  display: grid;
  gap: 12px;
}

.notice-item {
  border: 1px solid var(--tt-border-subtle);
  border-radius: 16px;
  padding: 16px;
}

.notice-item.unread {
  border-color: rgba(0, 82, 255, 0.24);
  background: rgba(0, 82, 255, 0.04);
}

.notice-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.notice-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.link-btn {
  background: none;
  color: var(--tt-accent);
  font-weight: 600;
  font-size: 13px;
  padding: 4px 8px;
  border-radius: 6px;
}

.link-btn:hover {
  background: rgba(0, 82, 255, 0.08);
}

.link-btn.danger {
  color: var(--tt-danger);
}

.link-btn.danger:hover {
  background: rgba(255, 59, 48, 0.08);
}

.label,
.tag,
.desc,
.meta,
.empty {
  margin: 0;
  color: var(--tt-text-secondary);
}

.tag {
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

h3 {
  margin: 8px 0 0;
}

.desc {
  margin-top: 12px;
  line-height: 1.7;
}

.meta {
  margin-top: 8px;
  font-size: 13px;
}

.value {
  margin: 10px 0 0;
  font-size: 28px;
  font-weight: 700;
}

.secondary-btn {
  height: 40px;
  padding: 0 14px;
  border-radius: 12px;
  background: var(--tt-surface-muted);
  color: var(--tt-text);
}

@media (max-width: 760px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .notice-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
