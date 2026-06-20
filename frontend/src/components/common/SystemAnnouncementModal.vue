<script setup>
import { computed, onMounted, ref } from 'vue'
import { acknowledgeSystemAnnouncement, fetchPendingSystemAnnouncements } from '../../services/systemAnnouncements'
import IconSystem from './IconSystem.vue'

const announcements = ref([])
const activeIndex = ref(0)
const loading = ref(false)
const submitting = ref(false)
const loadError = ref('')

const activeAnnouncement = computed(() => announcements.value[activeIndex.value] || null)
const hasAnnouncement = computed(() => Boolean(activeAnnouncement.value))
const isImportant = computed(() => Number(activeAnnouncement.value?.priority || 1) > 1)
const confirmLabel = computed(() => activeAnnouncement.value?.forceConfirm ? '我已知晓' : '知道了')
const progressText = computed(() => {
  if (!announcements.value.length) return ''
  return `${activeIndex.value + 1} / ${announcements.value.length}`
})

function formatTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

async function loadAnnouncements() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await fetchPendingSystemAnnouncements()
    announcements.value = Array.isArray(data?.data) ? data.data : []
    activeIndex.value = 0
  } catch (error) {
    loadError.value = error?.message || '系统公告加载失败'
  } finally {
    loading.value = false
  }
}

async function acknowledge(action = 'dismiss') {
  if (!activeAnnouncement.value || submitting.value) return
  submitting.value = true
  try {
    const currentId = activeAnnouncement.value.id
    await acknowledgeSystemAnnouncement(currentId, action)
    const nextItems = announcements.value.filter((item) => item.id !== currentId)
    announcements.value = nextItems
    activeIndex.value = Math.min(activeIndex.value, Math.max(nextItems.length - 1, 0))
  } catch (error) {
    loadError.value = error?.message || '操作失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

function skipCurrent() {
  acknowledge(activeAnnouncement.value?.forceConfirm ? 'confirm' : 'dismiss')
}

onMounted(() => {
  loadAnnouncements()
})
</script>

<template>
  <Teleport to="body">
    <div v-if="hasAnnouncement" class="system-announcement-overlay">
      <section class="system-announcement-panel" :class="{ important: isImportant }">
        <div class="announcement-visual" aria-hidden="true">
          <div class="radar-orbit">
            <span class="radar-dot" />
          </div>
          <div class="announcement-icon">
            <img class="announcement-logo" src="/TeamTraceLogo.png" alt="TeamTrace" />
          </div>
        </div>

        <div class="announcement-body">
          <div class="announcement-head">
            <div>
              <p class="announcement-kicker">{{ isImportant ? '重要系统公告' : '系统公告' }}</p>
              <h2>{{ activeAnnouncement.title }}</h2>
            </div>
            <span class="announcement-progress">{{ progressText }}</span>
          </div>

          <div class="announcement-content">
            <p>{{ activeAnnouncement.content }}</p>
          </div>

          <div class="announcement-meta">
            <span v-if="activeAnnouncement.createdAt">发布时间 {{ formatTime(activeAnnouncement.createdAt) }}</span>
            <span v-if="activeAnnouncement.expiresAt">有效至 {{ formatTime(activeAnnouncement.expiresAt) }}</span>
          </div>

          <p v-if="loadError" class="announcement-error">{{ loadError }}</p>

          <div class="announcement-actions">
            <button
              v-if="!activeAnnouncement.forceConfirm"
              class="ghost-action"
              type="button"
              :disabled="submitting"
              @click="acknowledge('dismiss')"
            >
              稍后查看
            </button>
            <button class="primary-action" type="button" :disabled="submitting" @click="skipCurrent">
              <IconSystem name="check" :size="16" />
              <span>{{ submitting ? '处理中' : confirmLabel }}</span>
            </button>
          </div>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.system-announcement-overlay {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: grid;
  place-items: center;
  padding: 28px;
  background: rgba(9, 13, 22, 0.54);
  backdrop-filter: blur(10px);
}

.system-announcement-panel {
  width: min(760px, 100%);
  min-height: 360px;
  display: grid;
  grid-template-columns: 230px minmax(0, 1fr);
  overflow: hidden;
  border: 1px solid rgba(105, 129, 171, 0.22);
  border-radius: 22px;
  background:
    radial-gradient(circle at 20% 10%, rgba(255, 214, 150, 0.28), transparent 34%),
    linear-gradient(135deg, rgba(251, 252, 255, 0.98), rgba(241, 246, 255, 0.96));
  box-shadow: 0 28px 80px rgba(15, 23, 42, 0.24);
  color: #172033;
}

.system-announcement-panel.important {
  border-color: rgba(229, 121, 58, 0.4);
}

.announcement-visual {
  position: relative;
  display: grid;
  place-items: center;
  background:
    radial-gradient(circle at 50% 45%, rgba(45, 92, 255, 0.18), transparent 40%),
    linear-gradient(160deg, #eef5ff, #fff5df);
}

.radar-orbit {
  position: absolute;
  width: 150px;
  height: 150px;
  border: 1px dashed rgba(43, 74, 134, 0.24);
  border-radius: 999px;
  animation: announcement-spin 8s linear infinite;
}

.radar-dot {
  position: absolute;
  top: 12px;
  left: 50%;
  width: 12px;
  height: 12px;
  border-radius: 999px;
  background: #f2994a;
  box-shadow: 0 0 0 8px rgba(242, 153, 74, 0.18);
}

.announcement-icon {
  width: 118px;
  height: 118px;
  display: grid;
  place-items: center;
  border-radius: 34px;
  overflow: hidden;
  background: oklch(0.99 0.01 230);
  color: #172033;
  padding: 14px;
  box-shadow:
    0 18px 36px rgba(23, 32, 51, 0.18),
    inset 0 0 0 1px rgba(107, 128, 166, 0.14);
}

.announcement-logo {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: contain;
}

.announcement-body {
  padding: 34px 34px 28px;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.announcement-head {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
}

.announcement-kicker {
  margin: 0 0 8px;
  color: #486284;
  font-size: 13px;
  font-weight: 700;
}

.announcement-head h2 {
  margin: 0;
  color: #121a2a;
  font-size: 26px;
  line-height: 1.2;
}

.announcement-progress {
  flex: 0 0 auto;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(24, 42, 76, 0.08);
  color: #34415b;
  font-size: 12px;
  font-weight: 800;
}

.announcement-content {
  margin-top: 24px;
  max-height: 190px;
  overflow: auto;
  padding-right: 6px;
}

.announcement-content p {
  margin: 0;
  white-space: pre-wrap;
  color: #33415c;
  font-size: 15px;
  line-height: 1.75;
}

.announcement-meta {
  margin-top: 18px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: #66738d;
  font-size: 12px;
}

.announcement-error {
  margin: 16px 0 0;
  color: #b54708;
  font-size: 13px;
}

.announcement-actions {
  margin-top: auto;
  padding-top: 24px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.ghost-action,
.primary-action {
  height: 40px;
  border-radius: 12px;
  border: 0;
  padding: 0 16px;
  font-weight: 800;
  cursor: pointer;
}

.ghost-action {
  background: rgba(31, 45, 75, 0.08);
  color: #34415b;
}

.primary-action {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: #1f4fff;
  color: #f8fbff;
  box-shadow: 0 14px 28px rgba(31, 79, 255, 0.22);
}

.ghost-action:disabled,
.primary-action:disabled {
  opacity: 0.62;
  cursor: wait;
}

@keyframes announcement-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 720px) {
  .system-announcement-panel {
    grid-template-columns: 1fr;
  }

  .announcement-visual {
    min-height: 150px;
  }

  .announcement-body {
    padding: 24px;
  }
}
</style>
