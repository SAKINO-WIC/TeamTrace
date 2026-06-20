<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  deleteBatchStudentNotifications,
  deleteStudentNotification,
  fetchStudentNotifications,
  markStudentNotificationRead,
} from '../../../services/student'

const router = useRouter()

const loading = ref(false)
const actionLoading = ref('')
const notifications = ref([])
const activeFilter = ref('all')
const activeId = ref('')
const feedback = ref({ text: '', type: 'info' })

const freeTypes = new Set([
  'collaboration_task',
  'collaboration_review',
  'collaboration_space',
])
const actionTypes = new Set(['collaboration_task', 'collaboration_review'])

const freeNotifications = computed(() =>
  notifications.value.filter((item) => freeTypes.has(item.type)),
)

const unreadCount = computed(() => freeNotifications.value.filter((item) => !item.read).length)
const actionCount = computed(() => freeNotifications.value.filter((item) => item.isActionItem && !item.read).length)

const filters = computed(() => [
  { key: 'all', label: '全部协作通知', count: freeNotifications.value.length },
  { key: 'unread', label: '未读', count: unreadCount.value },
  { key: 'action', label: '待处理', count: actionCount.value },
])

const visibleNotifications = computed(() => {
  if (activeFilter.value === 'unread') return freeNotifications.value.filter((item) => !item.read)
  if (activeFilter.value === 'action') return freeNotifications.value.filter((item) => item.isActionItem && !item.read)
  return freeNotifications.value
})

const activeNotification = computed(() =>
  visibleNotifications.value.find((item) => String(item.id) === String(activeId.value))
  || visibleNotifications.value[0]
  || null,
)

const summaryCards = computed(() => [
  { label: '未读提醒', value: unreadCount.value, hint: '还没有处理的协作消息' },
  { label: '待处理', value: actionCount.value, hint: '需要你接收、查看或推进' },
  { label: '协作通知', value: freeNotifications.value.length, hint: '自由协作相关消息总数' },
])

function normalizeNotification(raw, index) {
  const readValue = raw?.read ?? raw?.isRead ?? false
  const type = raw?.type || 'system'
  return {
    id: raw?.id ?? raw?.notificationId ?? `${index}`,
    type,
    title: raw?.title || '协作提醒',
    content: raw?.content || '',
    relatedId: raw?.relatedId ?? raw?.targetId ?? raw?.businessId ?? null,
    read: readValue === true || readValue === 1 || readValue === '1',
    createdAt: raw?.createdAt || raw?.createdTime || '',
    isActionItem: actionTypes.has(type),
  }
}

function pickActive() {
  if (!visibleNotifications.value.length) {
    activeId.value = ''
    return
  }
  const hasActive = visibleNotifications.value.some((item) => String(item.id) === String(activeId.value))
  if (!hasActive) activeId.value = String(visibleNotifications.value[0].id)
}

function setFeedback(text, type = 'info') {
  feedback.value = { text, type }
  if (type === 'success') {
    window.setTimeout(() => {
      if (feedback.value.text === text) feedback.value = { text: '', type: 'info' }
    }, 2400)
  }
}

async function loadNotifications() {
  loading.value = true
  setFeedback('')
  try {
    const { data } = await fetchStudentNotifications({ page: 1, size: 100 })
    const payload = data?.data || {}
    const list = payload.items || payload.list || payload.content || []
    notifications.value = Array.isArray(list) ? list.map(normalizeNotification) : []
    pickActive()
  } catch (error) {
    notifications.value = []
    activeId.value = ''
    setFeedback(error.message || '协作通知加载失败，请稍后重试。', 'error')
  } finally {
    loading.value = false
  }
}

async function selectNotification(item) {
  activeId.value = String(item.id)
  if (!item.read) await markRead(item.id, false)
}

async function goToNotification(item) {
  if (!item) return
  const target = notificationTarget(item)
  if (!target) {
    setFeedback('这条通知没有可跳转的协作对象。')
    return
  }
  if (!item.read) await markRead(item.id, false)
  await router.push(target)
}

async function markRead(id, showSuccess = true) {
  if (!id) return
  actionLoading.value = String(id)
  try {
    await markStudentNotificationRead(id)
    notifications.value = notifications.value.map((item) =>
      String(item.id) === String(id) ? { ...item, read: true } : item,
    )
    window.dispatchEvent(new CustomEvent('teamtrace-notifications-changed'))
    if (showSuccess) setFeedback('已标记为已读。', 'success')
  } catch (error) {
    setFeedback(error.message || '操作失败，请稍后重试。', 'error')
  } finally {
    actionLoading.value = ''
  }
}

async function markAllRead() {
  const unreadIds = freeNotifications.value.filter((item) => !item.read).map((item) => item.id)
  if (!unreadIds.length) return
  actionLoading.value = 'all'
  try {
    await Promise.all(unreadIds.map((id) => markStudentNotificationRead(id)))
    notifications.value = notifications.value.map((item) =>
      unreadIds.some((id) => String(id) === String(item.id)) ? { ...item, read: true } : item,
    )
    window.dispatchEvent(new CustomEvent('teamtrace-notifications-changed'))
    setFeedback('全部通知已标记为已读。', 'success')
    pickActive()
  } catch (error) {
    setFeedback(error.message || '操作失败，请稍后重试。', 'error')
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
    window.dispatchEvent(new CustomEvent('teamtrace-notifications-changed'))
    setFeedback('通知已删除。', 'success')
    pickActive()
  } catch (error) {
    setFeedback(error.message || '删除失败，请稍后重试。', 'error')
  } finally {
    actionLoading.value = ''
  }
}

async function clearReadNotifications() {
  const readIds = freeNotifications.value.filter((item) => item.read).map((item) => item.id)
  if (!readIds.length) {
    setFeedback('没有已读协作通知可清理。')
    return
  }
  actionLoading.value = 'clear-read'
  try {
    await deleteBatchStudentNotifications(readIds)
    notifications.value = notifications.value.filter((item) => !readIds.some((id) => String(id) === String(item.id)))
    window.dispatchEvent(new CustomEvent('teamtrace-notifications-changed'))
    setFeedback(`已清理 ${readIds.length} 条已读协作通知。`, 'success')
    pickActive()
  } catch (error) {
    setFeedback(error.message || '清理失败，请稍后重试。', 'error')
  } finally {
    actionLoading.value = ''
  }
}

function setFilter(key) {
  activeFilter.value = key
  pickActive()
}

function formatTime(value) {
  if (!value) return '未记录'
  return new Date(value).toLocaleString('zh-CN')
}

function typeLabel(type) {
  if (String(type).includes('deadline')) return '时间风险'
  if (String(type).includes('review')) return '接收处理'
  if (String(type).includes('space')) return '空间消息'
  if (String(type).includes('task') || String(type).includes('subtask')) return '任务协作'
  return '系统通知'
}

function targetLabel(item) {
  if (!notificationTarget(item)) return '无可跳转对象'
  if (item.type === 'collaboration_space') return '进入协作空间'
  if (item.type === 'collaboration_review') return '前往接收处理'
  return '前往任务面板'
}

function notificationTarget(item) {
  const relatedId = normalizePositiveId(item?.relatedId)
  if (!relatedId) return null
  if (item.type === 'collaboration_space') {
    return { path: '/student/free/spaces', query: { spaceId: relatedId } }
  }
  if (item.type === 'collaboration_task' || item.type === 'collaboration_review') {
    return { path: '/student/free/tasks', query: { taskId: relatedId } }
  }
  return null
}

function normalizePositiveId(value) {
  const id = Number(value)
  return Number.isFinite(id) && id > 0 ? id : null
}

onMounted(loadNotifications)
</script>

<template>
  <section class="free-notification-layout">
    <div class="notification-overview">
      <article v-for="card in summaryCards" :key="card.label">
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
        <p>{{ card.hint }}</p>
      </article>
    </div>

    <p v-if="feedback.text" class="feedback-line" :class="{ error: feedback.type === 'error' }">
      {{ feedback.text }}
    </p>

    <section class="notification-panel">
      <aside class="notification-list-panel">
        <div class="section-head compact">
          <div>
            <span>协作通知队列</span>
            <h2>只显示自由协作相关消息</h2>
          </div>
          <button type="button" class="secondary-btn" :disabled="loading" @click="loadNotifications">
            {{ loading ? '刷新中' : '刷新' }}
          </button>
        </div>

        <div class="filter-tabs">
          <button
            v-for="filter in filters"
            :key="filter.key"
            type="button"
            :class="{ active: activeFilter === filter.key }"
            @click="setFilter(filter.key)"
          >
            {{ filter.label }} · {{ filter.count }}
          </button>
        </div>

        <div class="notification-actions">
          <button type="button" class="secondary-btn" :disabled="!unreadCount || actionLoading === 'all'" @click="markAllRead">
            全部已读
          </button>
          <button type="button" class="secondary-btn" :disabled="actionLoading === 'clear-read'" @click="clearReadNotifications">
            清理已读
          </button>
        </div>

        <div v-if="loading" class="empty-state large">正在读取协作通知。</div>
        <div v-else-if="!visibleNotifications.length" class="empty-state large">
          当前没有符合条件的协作通知。
        </div>
        <div v-else class="free-notification-list">
          <button
            v-for="item in visibleNotifications"
            :key="item.id"
            type="button"
            :class="{ active: activeNotification?.id === item.id, unread: !item.read }"
            @click="selectNotification(item)"
          >
            <span>{{ typeLabel(item.type) }}</span>
            <strong>{{ item.title }}</strong>
            <small>{{ formatTime(item.createdAt) }}</small>
          </button>
        </div>
      </aside>

      <article class="notification-detail-panel">
        <template v-if="activeNotification">
          <div class="section-head">
            <div>
              <span>{{ typeLabel(activeNotification.type) }}</span>
              <h2>{{ activeNotification.title }}</h2>
            </div>
            <strong class="read-state" :class="{ unread: !activeNotification.read }">
              {{ activeNotification.read ? '已读' : '未读' }}
            </strong>
          </div>
          <p class="notification-time">{{ formatTime(activeNotification.createdAt) }}</p>
          <div class="notification-content">
            {{ activeNotification.content || '这条通知没有更多内容。' }}
          </div>
          <div class="notification-target-meta">
            <span>关联对象</span>
            <strong>{{ activeNotification.relatedId || '未记录' }}</strong>
            <p>
              {{ notificationTarget(activeNotification) ? '可以直接跳转到对应自由协作位置。' : '这条通知只作为消息记录保留。' }}
            </p>
          </div>
          <div class="button-row">
            <button
              type="button"
              class="primary-btn"
              :disabled="!notificationTarget(activeNotification)"
              @click="goToNotification(activeNotification)"
            >
              {{ targetLabel(activeNotification) }}
            </button>
            <button
              v-if="!activeNotification.read"
              type="button"
              class="secondary-btn"
              :disabled="actionLoading === String(activeNotification.id)"
              @click="markRead(activeNotification.id)"
            >
              标记已读
            </button>
            <button
              type="button"
              class="danger-btn"
              :disabled="actionLoading === `delete-${activeNotification.id}`"
              @click="deleteNotification(activeNotification.id)"
            >
              删除通知
            </button>
          </div>
        </template>

        <div v-else class="empty-state large">选择一条协作通知查看详情。</div>
      </article>
    </section>
  </section>
</template>
