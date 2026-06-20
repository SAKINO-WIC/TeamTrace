<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TeacherSubviewShell from '../components/teacher/TeacherSubviewShell.vue'
import { useTeacherLocale } from '../composables/useTeacherLocale'
import { loadTeacherAppealsWorkspace } from '../services/teacherAppeals'
import { readSessionCache, writeSessionCache } from '../utils/sessionCache'
import { resolveTeacherTaskAppeal } from '../services/teacher'
import { formatAppealStatus, formatDateTime } from '../utils/teacher'
import AttachmentFileBadge from '../components/common/AttachmentFileBadge.vue'
import { resolveMediaUrl } from '../utils/mediaUrl'

const route = useRoute()
const router = useRouter()
const { t, tm, locale } = useTeacherLocale()

const loading = ref(false)
const actionLoading = ref(false)
const message = ref('')
const messageType = ref('info')
const appeals = ref([])
const selectedAppealId = ref('')
const activeTab = ref('all')
const searchQuery = ref('')

const resolveForm = reactive({
  teacherResponse: '',
})

const sourceClassId = computed(() => String(route.query.classId || ''))
const sourceTaskId = computed(() => String(route.query.taskId || ''))
const sourceAppealId = computed(() => String(route.query.appealId || ''))
const fromClassTaskPage = computed(() => route.query.from === 'class-task' && sourceClassId.value && sourceTaskId.value)
const classTaskReturnPath = computed(() => {
  if (!fromClassTaskPage.value) {
    return ''
  }
  return `/teacher/classes/${sourceClassId.value}/tasks`
})

const queueTabs = [
  { key: 'all', label: t('全部', 'All') },
  { key: 'pending', label: t('待处理', 'Pending') },
  { key: 'resolved', label: t('已处理', 'Resolved') },
]

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

function parseJsonSafely(raw) {
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function normalizeAttachmentType(type, value = '') {
  const raw = String(type || '').trim().toLowerCase()
  if (raw === 'image' || raw === 'img') return 'image'
  if (raw === 'document' || raw === 'doc' || raw === 'file' || raw === 'pdf') return 'document'
  if (raw === 'link' || raw === 'url') return 'link'

  const lowerValue = String(value || '').trim().toLowerCase()
  if (lowerValue.startsWith('/uploads/')) {
    if (/\.(png|jpe?g|gif|webp|bmp|svg)(\?|$)/.test(lowerValue)) return 'image'
    return 'document'
  }
  if (/^https?:\/\//.test(lowerValue)) {
    if (/\.(png|jpe?g|gif|webp|bmp|svg)(\?|$)/.test(lowerValue)) return 'image'
    if (/\.(pdf|docx?|xlsx?|pptx?|txt|zip|rar)(\?|$)/.test(lowerValue)) return 'document'
    return 'link'
  }

  return 'other'
}

function parseAttachments(raw) {
  if (!raw) {
    return []
  }

  const source =
    Array.isArray(raw)
      ? raw
      : typeof raw === 'string'
        ? (() => {
            const trimmed = raw.trim()
            if (!trimmed) return []
            if (trimmed.startsWith('[') || trimmed.startsWith('{')) {
              const parsed = parseJsonSafely(trimmed)
              if (Array.isArray(parsed)) return parsed
              if (parsed && typeof parsed === 'object') return [parsed]
            }
            if (/^https?:\/\//i.test(trimmed)) {
              return [{ type: 'link', value: trimmed, name: '附件链接' }]
            }
            return []
          })()
        : []

  return source
    .map((item, index) => {
      const href = String(item?.url ?? item?.value ?? item?.href ?? '').trim()
      if (!href) {
        return null
      }

      const type = normalizeAttachmentType(item?.type, href)
      return {
        id: String(item?.id ?? `${type}-${index}`),
        type,
        name: String(item?.name ?? item?.title ?? `附件 ${index + 1}`),
        href,
      }
    })
    .filter(Boolean)
}

function normalizeAppealType(type) {
  const raw = String(type || '').trim()
  if (!raw) {
    return 'teacher_score'
  }

  const normalized = raw.toLowerCase()
  if (
    normalized === 'teacher_score' ||
    normalized === 'teacher-score' ||
    normalized === 'teacherscore' ||
    normalized === 'teacher'
  ) {
    return 'teacher_score'
  }
  if (
    normalized === 'peer_review' ||
    normalized === 'peer-review' ||
    normalized === 'peerreview' ||
    normalized === 'peer'
  ) {
    return 'peer_review'
  }
  if (
    normalized === 'subtask' ||
    normalized === 'task_review' ||
    normalized === 'task-review' ||
    normalized === 'subtask_review' ||
    normalized === 'subtask-review' ||
    normalized === 'progress'
  ) {
    return 'subtask'
  }

  return normalized
}

function formatAppealType(type) {
  const normalized = normalizeAppealType(type)
  if (normalized === 'teacher_score') return t('教师评分', 'Teacher score')
  if (normalized === 'peer_review') return t('互评异常', 'Peer review issue')
  if (normalized === 'subtask') return t('子任务误判', 'Subtask misjudgment')
  return t('申诉', 'Appeal')
}

function formatTeacherAppealStatus(status) {
  if (status === 3 || status === '3') {
    return t('已处理', 'Resolved')
  }
  return formatAppealStatus(status, locale.value)
}

function formatAttemptLabel(attemptNumber) {
  if (!attemptNumber) return t('第 1 次申诉', 'Appeal #1')
  return t('第 {n} 次申诉', 'Appeal #{n}').replace('{n}', String(attemptNumber))
}

function getStatusPriority(statusLabel) {
  if (statusLabel === t('待处理', 'Pending')) return 0
  if (statusLabel === t('处理中', 'Processing')) return 1
  return 2
}

function openAttachment(attachment) {
  const href = resolveMediaUrl(attachment?.href)
  if (!href) {
    return
  }
  window.open(href, '_blank', 'noopener,noreferrer')
}

const normalizedAppeals = computed(() => {
  const withMeta = appeals.value.map((item) => {
    const classId = String(item?.classId ?? '')
    const taskId = String(item?.taskId ?? '')
    const studentId = String(item?.studentId ?? '')
    const studentName = String(item?.studentName || '').trim()
    const studentDisplayName = studentName || (studentId ? `${t('学生', 'Student')} ${studentId}` : '-')
    const createdAt = item?.createdAt ?? ''
    const createdAtMs = new Date(createdAt).getTime()
    const typeKey = normalizeAppealType(item?.type)
    const attachments = parseAttachments(item?.attachments)
    const groupKey = `${studentId}::${taskId}`
    const statusLabel = formatTeacherAppealStatus(item?.status)

    return {
      appealId: String(item?.appealId ?? item?.id ?? ''),
      classId,
      className: item?.className ?? t('未命名班级', 'Unnamed class'),
      taskId,
      taskName: item?.taskName ?? t('未命名任务', 'Unnamed task'),
      studentId,
      studentName,
      studentDisplayName,
      subtaskId: item?.subtaskId ? String(item.subtaskId) : '',
      typeKey,
      typeLabel: formatAppealType(item?.type),
      reason: item?.reason ?? '',
      attachments,
      hasAttachments: attachments.length > 0,
      primaryAttachment: attachments[0] || null,
      attachmentCount: attachments.length,
      attachmentKinds: Array.from(new Set(attachments.map((attachment) => attachment.type))),
      status: item?.status,
      statusLabel,
      createdAt,
      createdAtText: formatDateTime(createdAt, locale.value),
      createdAtMs: Number.isNaN(createdAtMs) ? 0 : createdAtMs,
      handledAt: item?.handledAt ?? '',
      handledAtText: formatDateTime(item?.handledAt, locale.value),
      teacherResponse: item?.teacherResponse ?? '',
      groupKey,
      isPending: statusLabel === t('待处理', 'Pending') || statusLabel === t('处理中', 'Processing'),
      isResolved: item?.status === 2 || item?.status === '2' || item?.status === 3 || item?.status === '3',
      isTeacherScoreType: typeKey === 'teacher_score',
      isPeerReviewType: typeKey === 'peer_review',
      isSubtaskType: typeKey === 'subtask',
    }
  })

  const countsByGroup = new Map()
  const attemptsByAppealId = new Map()

  const chronologicalRows = [...withMeta].sort((left, right) => left.createdAtMs - right.createdAtMs)
  chronologicalRows.forEach((row) => {
    const nextCount = (countsByGroup.get(row.groupKey) || 0) + 1
    countsByGroup.set(row.groupKey, nextCount)
    attemptsByAppealId.set(row.appealId, nextCount)
  })

  return withMeta
    .map((row) => {
      const historyCount = countsByGroup.get(row.groupKey) || 1
      const attemptNumber = attemptsByAppealId.get(row.appealId) || 1
      return {
        ...row,
        historyCount,
        attemptNumber,
        isRepeated: attemptNumber > 1,
        attemptLabel: formatAttemptLabel(attemptNumber),
      }
    })
    .sort((left, right) => {
      const statusDiff = getStatusPriority(left.statusLabel) - getStatusPriority(right.statusLabel)
      if (statusDiff !== 0) return statusDiff
      return right.createdAtMs - left.createdAtMs
    })
})

const summaryCards = computed(() => {
  const total = normalizedAppeals.value.length
  const pending = normalizedAppeals.value.filter((item) => item.isPending).length
  const resolved = normalizedAppeals.value.filter((item) => item.isResolved).length
  const repeated = normalizedAppeals.value.filter((item) => item.isRepeated).length

  return [
    { title: t('申诉总数', 'Total appeals'), value: total },
    { title: t('待处理', 'Pending'), value: pending },
    { title: t('已处理', 'Resolved'), value: resolved },
    { title: t('重复申诉', 'Repeated appeals'), value: repeated },
  ]
})

const filteredAppeals = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase()
  return normalizedAppeals.value.filter((item) => {
    const tabHit =
      activeTab.value === 'all' ||
      (activeTab.value === 'pending' && item.isPending) ||
      (activeTab.value === 'resolved' && item.isResolved)

    const keywordHit =
      !keyword ||
      [
        item.appealId,
        item.studentId,
        item.studentName,
        item.studentDisplayName,
        item.className,
        item.taskName,
        item.reason,
        item.typeLabel,
        item.attemptLabel,
      ].some((field) => String(field || '').toLowerCase().includes(keyword))

    return tabHit && keywordHit
  })
})

const selectedAppeal = computed(() => {
  return (
    filteredAppeals.value.find((item) => item.appealId === selectedAppealId.value) ||
    normalizedAppeals.value.find((item) => item.appealId === selectedAppealId.value) ||
    null
  )
})

const selectedAppealHistory = computed(() => {
  if (!selectedAppeal.value) {
    return []
  }

  return normalizedAppeals.value
    .filter((item) => item.groupKey === selectedAppeal.value.groupKey)
    .sort((left, right) => right.createdAtMs - left.createdAtMs)
})

const selectedSnapshotItems = computed(() => {
  if (!selectedAppeal.value) {
    return []
  }

  const appeal = selectedAppeal.value
  if (appeal.isTeacherScoreType) {
    return [
      { label: t('申诉对象', 'Appeal target'), value: t('教师评分', 'Teacher score') },
      { label: t('处理方式', 'Resolution mode'), value: t('仅记录处理意见，分数调整请到评分中心完成', 'Record resolution only; adjust scores in the score center') },
      { label: t('处理时间', 'Handled at'), value: appeal.handledAt ? appeal.handledAtText : '-' },
      { label: t('关联任务 ID', 'Task ID'), value: appeal.taskId || '-' },
    ]
  }

  if (appeal.isPeerReviewType) {
    return [
      { label: t('申诉对象', 'Appeal target'), value: t('互评分数异常', 'Peer review anomaly') },
      { label: t('关联任务 ID', 'Task ID'), value: appeal.taskId || '-' },
      { label: t('学生 ID', 'Student ID'), value: appeal.studentId || '-' },
      { label: t('处理入口', 'Review entry'), value: t('互评明细', 'Peer review details') },
    ]
  }

  return [
    { label: t('申诉对象', 'Appeal target'), value: t('子任务误判 / 进度误判', 'Subtask / progress misjudgment') },
    { label: t('关联子任务 ID', 'Subtask ID'), value: appeal.subtaskId || '-' },
    { label: t('关联任务 ID', 'Task ID'), value: appeal.taskId || '-' },
    { label: t('处理入口', 'Entry point'), value: t('任务进度 / 子任务记录', 'Task progress / subtask records') },
  ]
})

const canSubmitPendingResolve = computed(() => {
  const a = selectedAppeal.value
  return Boolean(
    a?.isPending &&
      (a.isTeacherScoreType || a.isPeerReviewType || a.isSubtaskType),
  )
})

function isRejectedStatus(status) {
  const raw = status
  if (raw === 2 || raw === '2') return true
  const label = formatAppealStatus(raw, locale.value)
  return label === tm('common.rejected') || label === 'Rejected' || label === '已驳回'
}

function syncResolveForm(appeal) {
  resolveForm.teacherResponse = appeal?.teacherResponse || ''
}

watch(
  selectedAppeal,
  (appeal) => {
    if (appeal) {
      syncResolveForm(appeal)
    }
  },
  { immediate: true },
)

function selectAppeal(appealId) {
  selectedAppealId.value = appealId
}

function openRelatedWorkspace(target, appeal = selectedAppeal.value) {
  if (!appeal) {
    return
  }

  if (target === 'scores') {
    router.push(`/teacher/scores?classId=${appeal.classId}&taskId=${appeal.taskId}`)
    return
  }

  if (target === 'peer-reviews') {
    router.push(`/teacher/classes/${appeal.classId}/tasks/${appeal.taskId}/peer-reviews`)
    return
  }

  if (target === 'progress') {
    router.push(`/teacher/classes/${appeal.classId}/tasks/${appeal.taskId}/progress`)
    return
  }

  if (target === 'task-detail') {
    router.push(`/teacher/classes/${appeal.classId}/tasks/${appeal.taskId}`)
  }
}

const APPEALS_CACHE_KEY = 'teacher:appeals-center:v2'

async function loadAppealsCenter(options = {}) {
  const cached = options.force ? null : readSessionCache(APPEALS_CACHE_KEY, 120000)
  if (cached && Array.isArray(cached)) {
    appeals.value = cached
    loading.value = false
  } else {
    loading.value = true
  }
  try {
    const rows = await loadTeacherAppealsWorkspace({ force: options.force === true })
    appeals.value = rows
    writeSessionCache(APPEALS_CACHE_KEY, rows)

    const matchedById = sourceAppealId.value
      ? normalizedAppeals.value.find((item) => item.appealId === sourceAppealId.value)
      : null
    const sourceAppeal = matchedById || (sourceTaskId.value
      ? normalizedAppeals.value.find((item) => item.classId === sourceClassId.value && item.taskId === sourceTaskId.value)
      : null)

    if (sourceAppeal) {
      selectedAppealId.value = sourceAppeal.appealId
      activeTab.value = sourceAppealId.value ? 'all' : (sourceAppeal.isResolved ? 'resolved' : 'pending')
    } else if (!normalizedAppeals.value.some((item) => item.appealId === selectedAppealId.value)) {
      selectedAppealId.value = normalizedAppeals.value[0]?.appealId || ''
    }

    setMessage('')
  } catch (error) {
    if (!cached?.length) {
      appeals.value = []
      selectedAppealId.value = ''
    }
    setMessage(error.message || tm('appeals.loadFailed'), 'error')
  } finally {
    loading.value = false
  }
}

async function submitResolve(outcome) {
  if (!selectedAppeal.value || !selectedAppeal.value.isPending) {
    return
  }

  actionLoading.value = true
  try {
    const payload = {
      outcome,
      teacherResponse: resolveForm.teacherResponse.trim() || '',
    }
    await resolveTeacherTaskAppeal(
      selectedAppeal.value.classId,
      selectedAppeal.value.taskId,
      selectedAppeal.value.appealId,
      payload,
    )

    setMessage(outcome === 3 ? t('申诉已标记为已处理。', 'Appeal marked as resolved.') : tm('appeals.appealRejected'), 'success')
    await loadAppealsCenter({ force: true })
  } catch (error) {
    setMessage(error.message || tm('appeals.resolveFailed'), 'error')
  } finally {
    actionLoading.value = false
  }
}

onMounted(loadAppealsCenter)
watch(() => route.query.appealId, () => {
  if (!normalizedAppeals.value.length) {
    return
  }
  const appealId = sourceAppealId.value
  if (!appealId) {
    return
  }
  const matched = normalizedAppeals.value.find((item) => item.appealId === appealId)
  if (matched) {
    selectedAppealId.value = matched.appealId
    activeTab.value = 'all'
  }
})
</script>

<template>
  <TeacherSubviewShell :title="tm('appeals.title')" :message="message" :message-type="messageType">
    <template #actions>
      <button v-if="fromClassTaskPage" class="ghost-btn" type="button" @click="router.push(classTaskReturnPath)">
        {{ tm('appeals.backToTaskPage') }}
      </button>
    </template>



    <section v-if="loading" class="card loading-panel">
      <p>{{ tm('common.loading') }}</p>
    </section>

    <section v-else class="workbench">
      <div class="workbench-body">
        <aside class="queue-panel">
          <header class="section-head">
            <h3>{{ tm('appeals.queueTitle') }}</h3>
            <span class="section-count">{{ filteredAppeals.length }}</span>
          </header>

          <div class="queue-search">
            <input
              v-model.trim="searchQuery"
              class="search-input"
              type="search"
              name="teacher-appeal-search"
              autocomplete="off"
              :placeholder="tm('appeals.searchPlaceholder')"
            />
          </div>

          <div class="queue-list">
            <button
              v-for="item in filteredAppeals"
              :key="item.appealId"
              class="queue-item"
              :class="{ active: item.appealId === selectedAppealId }"
              type="button"
              @click="selectAppeal(item.appealId)"
            >
              <div class="queue-item-head">
                <div class="queue-badges">
                  <span class="type-badge" :class="item.typeKey">{{ item.typeLabel }}</span>
                  <span v-if="item.isRepeated" class="repeat-badge">{{ item.attemptLabel }}</span>
                </div>
                <span class="status-badge" :class="{ pending: item.isPending, resolved: item.isResolved }">
                  {{ item.statusLabel }}
                </span>
              </div>

              <p class="queue-title">{{ item.className }}</p>
              <p class="queue-meta">{{ item.taskName }}</p>
              <p class="queue-meta">{{ t('学生', 'Student') }} {{ item.studentId }} · {{ item.createdAtText }}</p>

              <p class="queue-meta queue-student-name">{{ item.studentDisplayName }} · {{ item.createdAtText }}</p>

              <p class="queue-meta queue-student-name-fixed">{{ item.studentDisplayName }} &middot; {{ item.createdAtText }}</p>

              <div class="queue-foot">
                <span v-if="item.hasAttachments" class="mini-tag mini-tag-attachment">
                  <AttachmentFileBadge
                    size="sm"
                    :name="item.primaryAttachment?.name"
                    :url="item.primaryAttachment?.href"
                    :kind="item.primaryAttachment?.type"
                  />
                  <span>
                    {{
                      item.attachmentCount > 1
                        ? t('{n} 个附件', '{n} attachments').replace('{n}', String(item.attachmentCount))
                        : item.primaryAttachment?.name || t('附件', 'Attachment')
                    }}
                  </span>
                </span>
                <span v-if="item.historyCount > 1" class="mini-tag subtle">{{ t('历史', 'History') }} {{ item.historyCount }} {{ t('次', 'times') }}</span>
              </div>
            </button>

            <div v-if="!filteredAppeals.length" class="empty-state">
              <p>{{ tm('appeals.currentEmpty') }}</p>
            </div>
          </div>
        </aside>

        <section class="detail-panel">
          <template v-if="selectedAppeal">
            <header class="detail-header">
              <div class="detail-title-wrap">
                <h3>{{ selectedAppeal.className }}</h3>
                <p class="detail-path detail-student-name-fixed">{{ selectedAppeal.taskName }} &middot; {{ selectedAppeal.studentDisplayName }}</p>
                <p class="detail-path detail-student-name">{{ selectedAppeal.taskName }} 路 {{ selectedAppeal.studentDisplayName }}</p>
                <p class="detail-path">{{ selectedAppeal.taskName }} · {{ t('学生', 'Student') }} {{ selectedAppeal.studentId }}</p>
              </div>

              <div class="detail-head-actions">
                <span class="type-badge" :class="selectedAppeal.typeKey">{{ selectedAppeal.typeLabel }}</span>
                <span class="status-badge" :class="{ pending: selectedAppeal.isPending, resolved: selectedAppeal.isResolved }">
                  {{ selectedAppeal.statusLabel }}
                </span>
              </div>
            </header>

            <div class="detail-content">
              <section class="detail-sections">
                <article class="detail-card detail-card-wide reason-card">
                  <div class="section-head">
                    <h4>{{ tm('appeals.reasonTitle') }}</h4>
                  </div>
                  <div class="reason-box">{{ selectedAppeal.reason || tm('appeals.noReason') }}</div>
                </article>



                <article class="detail-card attachment-card">
                  <div class="section-head">
                    <h4>{{ tm('appeals.evidenceTitle') }}</h4>
                  </div>
                  <div v-if="selectedAppeal.attachments.length" class="attachment-grid">
                    <button
                      v-for="attachment in selectedAppeal.attachments"
                      :key="attachment.id"
                      class="attachment-item"
                      type="button"
                      @click="openAttachment(attachment)"
                    >
                      <AttachmentFileBadge
                        :name="attachment.name"
                        :url="attachment.href"
                        :kind="attachment.type"
                      />
                      <span class="attachment-name">{{ attachment.name }}</span>
                    </button>
                  </div>
                  <div v-else class="empty-inline attachment-empty">
                    <span class="attachment-empty-icon" aria-hidden="true">📄</span>
                    <span>{{ tm('appeals.noAttachment') }}</span>
                  </div>
                </article>

              </section>

              <section class="action-panel">
              <div class="action-grid">
                <div v-if="selectedAppeal.isTeacherScoreType" class="field-block resolution-note">
                  <span class="field-label">{{ t('成绩调整', 'Score adjustment') }}</span>
                  <p>
                    {{ t('申诉处理只记录教师意见；如需影响成绩，请在评分中心手动调整教师评分并填写备注。', 'Appeal resolution only records the teacher response. To affect grades, adjust the teacher score manually in the score center and add a note.') }}
                  </p>
                </div>

                <label class="field-block full-width">
                  <span class="field-label">{{ tm('appeals.processingOpinion') }}</span>
                  <textarea
                    v-model.trim="resolveForm.teacherResponse"
                    rows="3"
                    :readonly="!selectedAppeal.isPending"
                    :placeholder="tm('appeals.responsePlaceholder')"
                  ></textarea>
                </label>

                <div class="action-panel-foot">
                  <button
                    v-if="canSubmitPendingResolve"
                    class="primary-btn"
                    type="button"
                    :disabled="actionLoading"
                    @click="submitResolve(3)"
                  >
                    {{ actionLoading ? tm('appeals.submitting') : t('已处理', 'Resolved') }}
                  </button>
                  <button
                    v-if="canSubmitPendingResolve"
                    class="danger-btn"
                    type="button"
                    :disabled="actionLoading"
                    @click="submitResolve(2)"
                  >
                    {{ tm('appeals.reject') }}
                  </button>
                </div>
              </div>

              </section>
            </div>
          </template>

          <div v-else class="detail-empty">
            <p>{{ tm('appeals.detailEmpty') }}</p>
          </div>
        </section>
      </div>
    </section>
  </TeacherSubviewShell>
</template>

<style scoped>
.card {
  background: var(--teacher-surface);
  border-radius: var(--teacher-radius-card);
  box-shadow: var(--teacher-shadow);
}

.loading-panel {
  margin-top: 16px;
  padding: 24px 16px;
  color: var(--teacher-text-secondary);
}

.summary-grid {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-card {
  position: relative;
  overflow: hidden;
  padding: 18px 20px 20px;
  min-height: 118px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  transition:
    transform var(--teacher-duration-fast) var(--teacher-ease-ios),
    box-shadow var(--teacher-duration-fast) var(--teacher-ease-ios);
}

.summary-accent {
  position: absolute;
  inset: 0 auto auto 0;
  width: 100%;
  height: 4px;
  background: linear-gradient(90deg, var(--teacher-accent), color-mix(in srgb, var(--teacher-accent) 20%, white));
}

.summary-label,
.info-label,
.field-label,
.queue-meta,
.detail-path,
.history-meta,
.empty-inline,
.empty-state p {
  margin: 0;
  color: var(--teacher-text-tertiary);
  font-size: 12px;
  line-height: 1.5;
}

.summary-value,
.info-value,
.queue-title,
.snapshot-value,
.history-attempt {
  margin: 0;
  color: var(--teacher-text-primary);
  font-weight: 700;
}

.summary-value {
  font-size: 28px;
  line-height: 1.1;
}

.workbench {
  margin-top: 12px;
  border: 1px solid var(--teacher-divider);
  border-radius: 22px;
  background: var(--teacher-surface);
  box-shadow: var(--teacher-shadow);
  overflow: hidden;
}

.workbench-body {
  display: grid;
  grid-template-columns: minmax(300px, 340px) minmax(0, 1fr);
  gap: 0;
  align-items: stretch;
  min-height: clamp(620px, calc(100vh - 280px), 760px);
}

.queue-panel,
.detail-panel {
  min-height: 0;
  display: grid;
  grid-template-rows: auto auto 1fr;
  gap: 0;
  background: transparent;
  border: 0;
  box-shadow: none;
  height: 100%;
}

.queue-panel {
  padding: 16px;
  gap: 12px;
  border-right: 1px solid var(--teacher-divider);
}

.detail-panel {
  padding: 16px;
  gap: 16px;
  grid-template-rows: auto 1fr;
}

.queue-search {
  padding: 0 2px;
}

.queue-search .search-input {
  width: 100%;
  min-height: 40px;
}

.queue-panel:hover,
.detail-panel:hover {
  transform: none;
  box-shadow: none;
}

.tab-row,
.toolbar-actions,
.detail-head-actions,
.action-links,
.action-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.tab-btn,
.ghost-btn,
.secondary-btn,
.primary-btn,
.danger-btn,
.segment-btn,
.attachment-item {
  border: 0;
  cursor: pointer;
  font: inherit;
  transition:
    transform var(--teacher-duration-fast) var(--teacher-ease-ios),
    box-shadow var(--teacher-duration-fast) var(--teacher-ease-ios),
    background var(--teacher-duration-fast) var(--teacher-ease-ios),
    border-color var(--teacher-duration-fast) var(--teacher-ease-ios);
}

.tab-btn,
.ghost-btn,
.secondary-btn,
.primary-btn,
.danger-btn,
.segment-btn {
  min-height: 42px;
  border-radius: 12px;
  padding: 0 16px;
  font-weight: 600;
}

.tab-btn {
  background: var(--teacher-surface);
  color: var(--teacher-text-secondary);
  box-shadow: inset 0 0 0 1px var(--teacher-border);
}

.tab-btn.active {
  background: var(--teacher-accent-soft);
  color: var(--teacher-accent);
  box-shadow:
    inset 0 0 0 1px color-mix(in srgb, var(--teacher-accent) 26%, transparent),
    0 10px 18px color-mix(in srgb, var(--teacher-accent) 16%, transparent);
}

.ghost-btn {
  background: var(--teacher-surface);
  color: var(--teacher-text-primary);
  box-shadow: inset 0 0 0 1px var(--teacher-border);
}

.secondary-btn {
  background: var(--teacher-surface);
  color: var(--teacher-text-primary);
  box-shadow: inset 0 0 0 1px var(--teacher-border);
}

.primary-btn {
  background: var(--teacher-accent);
  color: #fff;
}

.danger-btn {
  background: var(--teacher-surface);
  color: var(--teacher-danger);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--teacher-danger) 32%, var(--teacher-border));
}

.tab-btn:disabled,
.ghost-btn:disabled,
.secondary-btn:disabled,
.primary-btn:disabled,
.danger-btn:disabled,
.segment-btn:disabled {
  opacity: 0.46;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.search-input,
input,
textarea {
  width: 100%;
  border: 1px solid var(--teacher-border);
  border-radius: 12px;
  background: var(--teacher-surface);
  color: var(--teacher-text-primary);
  padding: 10px 12px;
  font: inherit;
}

.search-input {
  width: 100%;
  min-height: 40px;
}

.queue-student-name-fixed,
.detail-student-name-fixed {
  display: block;
}

.queue-student-name,
.detail-student-name,
.queue-item > p.queue-meta:nth-of-type(3),
.detail-title-wrap > .detail-path:not(.detail-student-name):not(.detail-student-name-fixed) {
  display: none;
}

.action-panel-foot {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 44px;
}

.section-head h3,
.detail-header h3 {
  margin: 0;
  color: var(--teacher-text-primary);
  font-size: 18px;
  font-weight: 700;
}

.section-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 28px;
  border-radius: 999px;
  background: var(--teacher-surface);
  color: var(--teacher-text-primary);
  font-size: 12px;
  font-weight: 700;
}

.queue-list,
.history-list {
  min-height: 0;
  overflow: auto;
}

.queue-list {
  display: grid;
  gap: 14px;
  padding-right: 6px;
  align-content: start;
  grid-auto-rows: min-content;
}

.queue-item,
.info-card,
.history-item {
  background: var(--teacher-surface);
  border: 1px solid var(--teacher-divider);
  border-radius: 18px;
  transition:
    transform var(--teacher-duration-fast) var(--teacher-ease-ios),
    box-shadow var(--teacher-duration-fast) var(--teacher-ease-ios),
    border-color var(--teacher-duration-fast) var(--teacher-ease-ios),
    background var(--teacher-duration-fast) var(--teacher-ease-ios);
}

.queue-item {
  position: relative;
  overflow: hidden;
  min-height: 96px;
  padding: 14px;
  text-align: left;
  display: grid;
  gap: 8px;
}

.queue-item::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: transparent;
  transition: background var(--teacher-duration-fast) var(--teacher-ease-ios);
}

.queue-item.active {
  border-color: color-mix(in srgb, var(--teacher-accent) 34%, var(--teacher-divider));
  background: color-mix(in srgb, var(--teacher-accent) 5%, var(--teacher-surface));
  box-shadow: 0 16px 30px rgba(15, 23, 42, 0.08);
}

.queue-item.active::before {
  background: linear-gradient(180deg, var(--teacher-accent) 0%, color-mix(in srgb, var(--teacher-accent) 20%, white) 100%);
}

.queue-item-head,
.history-top,
.detail-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.queue-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.type-badge,
.repeat-badge,
.mini-tag,
.status-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.type-badge.teacher_score {
  background: color-mix(in srgb, var(--teacher-accent) 14%, var(--teacher-surface));
  color: var(--teacher-accent);
}

.type-badge.peer_review {
  background: color-mix(in srgb, var(--teacher-warning, #f59e0b) 14%, var(--teacher-surface));
  color: var(--teacher-warning, #b45309);
}

.type-badge.subtask {
  background: color-mix(in srgb, var(--teacher-success) 14%, var(--teacher-surface));
  color: var(--teacher-success);
}

.repeat-badge {
  background: color-mix(in srgb, var(--teacher-danger) 12%, var(--teacher-surface));
  color: var(--teacher-danger);
}

.status-badge.pending {
  background: color-mix(in srgb, var(--teacher-danger) 10%, var(--teacher-surface));
  color: var(--teacher-danger);
}

.status-badge.resolved {
  background: var(--teacher-surface-muted);
  color: var(--teacher-text-primary);
}

.queue-title {
  font-size: 16px;
  line-height: 1.4;
  word-break: break-word;
}

.queue-meta,
.detail-path,
.snapshot-value,
.attachment-name,
.history-reason {
  word-break: break-word;
}

.queue-foot {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.mini-tag {
  background: var(--teacher-surface-muted);
  color: var(--teacher-text-secondary);
}

.mini-tag.subtle {
  background: color-mix(in srgb, var(--teacher-accent) 8%, var(--teacher-surface));
  color: var(--teacher-accent);
}

.mini-tag-attachment {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
  padding: 4px 10px 4px 4px;
  border-radius: 999px;
}

.mini-tag-attachment > span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 44px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--teacher-divider);
}

.detail-title-wrap {
  min-width: 0;
}

.detail-head-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-shrink: 0;
}

.detail-content {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.info-card {
  min-height: 104px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.info-value {
  font-size: 18px;
  line-height: 1.2;
}

.info-value.small {
  font-size: 15px;
  font-weight: 600;
}

.detail-sections {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.detail-sections > article {
  min-height: 0;
  padding: 0;
}

.reason-card,
.snapshot-card {
  grid-column: 1 / -1;
}

.section-block {
  display: grid;
  gap: 12px;
}

.detail-card {
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
  transition: none;
}

.detail-card :deep(*) {
  box-sizing: border-box;
}

.section-head {
  padding-bottom: 2px;
}

.reason-box {
  border-radius: 16px;
  border: 1px solid var(--tt-accent-border);
  background: linear-gradient(
    145deg,
    var(--teacher-surface),
    color-mix(in srgb, var(--teacher-accent) 6%, #f8fafc)
  );
  padding: 16px;
  color: var(--teacher-text-primary);
  line-height: 1.7;
  white-space: pre-wrap;
  min-height: 132px;
  box-shadow: 0 8px 20px color-mix(in srgb, var(--teacher-accent) 5%, transparent);
}

.snapshot-grid,
.attachment-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.snapshot-item {
  min-height: 96px;
  border: 1px solid var(--teacher-border);
  border-radius: 16px;
  padding: 14px;
  background: var(--teacher-surface);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  transition:
    transform var(--teacher-duration-fast) var(--teacher-ease-ios),
    box-shadow var(--teacher-duration-fast) var(--teacher-ease-ios);
}

.attachment-card .attachment-grid {
  grid-template-columns: 1fr;
}

.attachment-card .attachment-item {
  min-height: 48px;
  height: 48px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-radius: 12px;
}

.attachment-card .attachment-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-card .attachment-empty {
  min-height: 48px;
  height: 48px;
  width: 100%;
  padding: 0 12px;
  gap: 8px;
  border-radius: 12px;
  font-size: 12px;
}

.attachment-empty-icon {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: var(--teacher-accent-soft);
  font-size: 14px;
  line-height: 1;
}

.attachment-item {
  min-height: 96px;
  padding: 14px;
  border-radius: 16px;
  background: var(--teacher-surface);
  border: 1px solid var(--teacher-border);
  display: grid;
  justify-items: start;
  gap: 10px;
  text-align: left;
}

.attachment-name {
  color: var(--teacher-text-primary);
  font-weight: 600;
  line-height: 1.5;
  word-break: break-word;
}

.history-list {
  display: grid;
  gap: 12px;
}

.history-item {
  padding: 14px;
}

.history-reason {
  margin: 8px 0 0;
  color: var(--teacher-text-primary);
  line-height: 1.6;
}

.action-panel {
  padding-top: 8px;
  border-top: 1px solid var(--teacher-divider);
  display: grid;
  gap: 16px;
}

.action-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.action-grid {
  display: grid;
  grid-template-columns: 240px minmax(220px, 260px) minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.field-block {
  display: grid;
  gap: 10px;
}

.field-block.full-width {
  grid-column: 1 / -1;
}

.resolution-note {
  padding: 12px 14px;
  border: 1px solid color-mix(in srgb, var(--teacher-accent) 16%, var(--teacher-border));
  border-radius: 14px;
  background: color-mix(in srgb, var(--teacher-accent) 7%, var(--teacher-surface));
}

.resolution-note p {
  margin: 0;
  color: var(--teacher-text-secondary);
  font-size: 13px;
  line-height: 1.65;
}

.segmented {
  display: flex;
  gap: 8px;
}

.segment-btn {
  flex: 1;
  background: var(--teacher-surface);
  color: var(--teacher-text-secondary);
  min-height: 44px;
  box-shadow: inset 0 0 0 1px var(--teacher-border);
}

.segment-btn.active {
  color: var(--teacher-accent);
  box-shadow:
    inset 0 0 0 1px color-mix(in srgb, var(--teacher-accent) 24%, transparent),
    0 10px 18px color-mix(in srgb, var(--teacher-accent) 14%, transparent);
}

.segment-btn:first-child.active {
  background: var(--teacher-accent-soft);
}

.segment-btn:last-child.active {
  background: var(--teacher-danger-soft);
  color: var(--teacher-danger);
  box-shadow:
    inset 0 0 0 1px color-mix(in srgb, var(--teacher-danger) 24%, transparent),
    0 10px 18px color-mix(in srgb, var(--teacher-danger) 12%, transparent);
}

textarea {
  min-height: 96px;
  resize: vertical;
}

.detail-empty,
.empty-state,
.empty-inline {
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 18px;
  background: var(--teacher-surface);
  color: var(--teacher-text-secondary);
  border: 1px dashed var(--teacher-border);
}

.detail-empty {
  min-height: 100%;
}

.empty-state {
  min-height: 120px;
}

@media (hover: hover) and (pointer: fine) {
  .queue-item:hover,
  .summary-card:hover,
  .info-card:hover,
  .history-item:hover,
  .snapshot-item:hover,
  .attachment-item:hover,
  .tab-btn:hover,
  .ghost-btn:hover,
  .secondary-btn:hover,
  .primary-btn:hover,
  .danger-btn:hover,
  .segment-btn:hover {
    transform: translateY(-2px);
    box-shadow: 0 14px 26px rgba(15, 23, 42, 0.08);
  }
}

@media (max-width: 1280px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workbench-body {
    grid-template-columns: 320px minmax(0, 1fr);
  }

  .info-grid,
  .snapshot-grid,
  .attachment-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-sections {
    grid-template-columns: 1fr;
  }

  .action-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 980px) {
  .workbench-toolbar,
  .detail-header,
  .action-panel-head {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar-actions {
    width: 100%;
  }

  .search-input {
    width: 100%;
  }

  .workbench-body {
    grid-template-columns: 1fr;
    height: auto;
  }

  .queue-panel,
  .detail-panel {
    height: auto;
    min-height: 0;
  }

  .info-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .action-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .summary-grid,
  .info-grid,
  .snapshot-grid,
  .attachment-grid,
  .action-grid {
    grid-template-columns: 1fr;
  }

  .segmented {
    flex-direction: column;
  }
}

/* Layout polish: aligned workbench with card-level scrolling. */
.workbench-body {
  height: clamp(600px, calc(100vh - 260px), 740px);
  min-height: 0;
  align-items: stretch;
}

.queue-panel,
.detail-panel {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.detail-panel {
  grid-template-rows: auto minmax(0, 1fr);
}

.detail-content {
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
  align-content: start;
}

.queue-list {
  min-height: 0;
  overflow-y: auto;
}

/* Appeal action area: response on the left, decisions and submit on the right. */
.action-grid {
  grid-template-columns: minmax(0, 1fr) minmax(150px, 180px);
  align-items: stretch;
  gap: 12px;
}

.field-block.full-width {
  grid-column: 1;
  grid-row: 1 / span 2;
}

.action-grid > .field-block:not(.full-width):not(.resolution-note) {
  grid-column: 2;
  grid-row: 1;
}

.resolution-note {
  display: none;
}

.action-grid textarea {
  min-height: 112px;
  height: 100%;
}

.action-panel-foot {
  grid-column: 2;
  grid-row: 2;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: stretch;
  gap: 10px;
  padding-top: 0;
}

.action-panel-foot .primary-btn,
.action-panel-foot .danger-btn {
  width: 100%;
  min-height: 40px;
  padding: 0 16px;
  border-radius: 12px;
  white-space: nowrap;
}

.segmented {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
}

.segment-btn {
  min-height: 38px;
  padding: 0 14px;
  font-size: 14px;
  box-shadow: inset 0 0 0 1px var(--teacher-border);
}

@media (max-width: 980px) {
  .workbench-body {
    height: auto;
  }

  .queue-panel,
  .detail-panel {
    height: auto;
    overflow: visible;
  }

  .detail-content {
    overflow: visible;
    padding-right: 0;
  }

  .action-grid {
    grid-template-columns: 1fr;
  }

  .field-block.full-width {
    grid-column: 1 / -1;
    grid-row: auto;
  }

  .action-grid > .field-block:not(.full-width):not(.resolution-note) {
    grid-column: 1 / -1;
    grid-row: auto;
  }

  .action-panel-foot {
    grid-column: 1 / -1;
    grid-row: auto;
    display: flex;
    flex-direction: column;
    align-items: stretch;
    justify-content: flex-end;
    gap: 10px;
  }

  .action-panel-foot .primary-btn,
  .action-panel-foot .danger-btn {
    width: 100%;
  }

  .segmented {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
