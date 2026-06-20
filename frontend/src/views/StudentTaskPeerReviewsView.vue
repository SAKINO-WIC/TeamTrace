<script setup>
import { computed, inject, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  fetchStudentClassGroups,
  fetchStudentGroupPeerReviews,
  fetchStudentTaskDetail,
  submitStudentPeerReview,
} from '../services/student'
import { STUDENT_TASK_PAGE_KEY } from '../composables/studentTaskPageContext'
import { getCurrentUserId } from '../utils/auth'
import { resolvePositiveId } from '../utils/routeIds'

defineProps({
  embedded: {
    type: Boolean,
    default: false,
  },
})

const route = useRoute()
const router = useRouter()
const classId = resolvePositiveId(route.params.classId)
const taskId = resolvePositiveId(route.params.taskId)
const pageCtx = inject(STUDENT_TASK_PAGE_KEY, null)

const loading = ref(true)
const submitting = ref(false)
const message = ref('')
const messageType = ref('info')
const reviews = ref([])
const detail = ref(null)
const taskDetail = ref(null)
const groups = ref([])
const submittedRevieweeIds = ref(new Set())
const detailLoadError = ref('')
const taskLoadError = ref('')
const groupLoadError = ref('')
const reviewLoadError = ref('')

const reviewForm = reactive({
  revieweeId: '',
  score: '',
  comment: '',
})

const currentUserId = computed(() => getCurrentUserId())
const resolvedGroupId = computed(() => resolvePositiveId(detail.value?.groupId))
const canUseGroup = computed(() => Boolean(resolvedGroupId.value) && detail.value?.groupJoinStatus !== '待审批')
const currentGroup = computed(() => {
  if (!resolvedGroupId.value) return null
  return (
    groups.value.find(
      (item) => resolvePositiveId(item?.groupId ?? item?.id) === resolvedGroupId.value,
    ) || null
  )
})
const memberIds = computed(() => {
  const ids = currentGroup.value?.memberStudentIds
  return Array.isArray(ids) ? ids.map((item) => Number(item)).filter((item) => !Number.isNaN(item)) : []
})
const myReviewByRevieweeId = computed(() => {
  const map = new Map()
  reviews.value.forEach((item) => {
    if (item.revieweeId !== null && item.revieweeId !== undefined && item.revieweeId !== '') {
      map.set(String(item.revieweeId), item)
    }
  })
  return map
})
const revieweeOptions = computed(() =>
  memberIds.value
    .filter((id) => currentUserId.value === null || Number(id) !== Number(currentUserId.value))
    .map((id, index) => {
      const existing = myReviewByRevieweeId.value.get(String(id))
      return {
        id,
        label: (() => { const nm = (currentGroup.value?.memberNames || {})[id]; return Number(currentGroup.value?.leaderId) === Number(id) ? `组长 ${nm || id}` : nm || `组员 ${index + 1}`; })(),
        caption: `成员 ID ${id}`,
        sessionSubmitted: submittedRevieweeIds.value.has(String(id)) || Boolean(existing),
      }
    }),
)
const pendingRevieweeOptions = computed(() => revieweeOptions.value.filter((item) => !item.sessionSubmitted))
const maxScore = computed(() => Number(taskDetail.value?.peerReviewMaxScore ?? 100) || 100)
const taskDeadlineText = computed(() => formatDateTime(pickDateValue(taskDetail.value, ['deadlineValue', 'deadlineAt', 'deadline'])))
const peerReviewDeadlineText = computed(() =>
  formatDateTime(pickDateValue(taskDetail.value, ['peerReviewDeadlineValue', 'peerReviewDeadlineAt', 'peerReviewDeadline'])),
)
const peerPhaseLabel = computed(() => {
  const phase = String(taskDetail.value?.peerReviewPhase || '').toLowerCase()
  const map = {
    disabled: '未开启互评',
    not_started: '未到互评阶段',
    open: '互评进行中',
    closed: '互评已关闭',
  }
  return map[phase] || (taskDetail.value?.enablePeerReview ? '互评已开启' : '未开启互评')
})
const peerPhaseTone = computed(() => {
  const phase = String(taskDetail.value?.peerReviewPhase || '').toLowerCase()
  if (phase === 'open') return 'open'
  if (phase === 'closed') return 'closed'
  if (phase === 'not_started') return 'waiting'
  return 'disabled'
})
const reviewWorkspaceAvailable = computed(() => {
  return (
    canUseGroup.value &&
    Boolean(taskDetail.value?.enablePeerReview) &&
    Boolean(taskDetail.value?.canPeerReviewNow) &&
    memberIds.value.length >= 2 &&
    revieweeOptions.value.length > 0
  )
})
const canSubmitPeerReview = computed(() => reviewWorkspaceAvailable.value && pendingRevieweeOptions.value.length > 0)
const submitBlockReason = computed(() => {
  if (!canUseGroup.value) {
    return detail.value?.groupJoinStatus === '待审批'
      ? '小组申请通过后才能提交互评。'
      : '加入或创建小组后才能参与互评。'
  }
  if (!taskDetail.value) return '任务信息加载后才能提交互评。'
  if (!taskDetail.value.enablePeerReview) return '该任务未开启互评。'
  if (memberIds.value.length < 2) return '当前小组只有一名成员，不需要互评。'
  if (!taskDetail.value.canPeerReviewNow) return `${peerPhaseLabel.value}，暂时不能提交互评。`
  if (!pendingRevieweeOptions.value.length && revieweeOptions.value.length) return '本次打开页面后已完成所有可评价组员的互评。'
  if (!revieweeOptions.value.length) return '当前没有可评价的组员。'
  return ''
})
const expectedReviewCount = computed(() => Math.max(memberIds.value.length * Math.max(memberIds.value.length - 1, 0), 0))
const reviewStats = computed(() => {
  const scores = reviews.value
    .map((item) => Number(item.score))
    .filter((item) => !Number.isNaN(item))
  const total = expectedReviewCount.value
  return {
    submitted: reviews.value.length,
    expected: total,
    pending: Math.max(total - reviews.value.length, 0),
    average: scores.length
      ? (scores.reduce((sum, item) => sum + item, 0) / scores.length).toFixed(1).replace(/\.0$/, '')
      : '-',
  }
})
const peerStatusTitle = computed(() => {
  if (loading.value) return '正在确认互评状态'
  if (!taskDetail.value) return '互评状态待确认'
  if (!taskDetail.value.enablePeerReview) return '该任务未开启互评'
  if (!canUseGroup.value) return detail.value?.groupJoinStatus === '待审批' ? '小组申请待审批' : '先加入小组'
  if (memberIds.value.length < 2) return '一人成组无需互评'
  if (taskDetail.value.canPeerReviewNow) return '互评正在进行'
  if (String(taskDetail.value.peerReviewPhase || '').toLowerCase() === 'not_started') return '互评将在任务截止后开放'
  if (String(taskDetail.value.peerReviewPhase || '').toLowerCase() === 'closed') return '互评已截止'
  return peerPhaseLabel.value
})
const peerStatusDescription = computed(() => {
  if (!taskDetail.value) return '任务信息加载完成后会显示互评开放时间和提交要求。'
  if (!taskDetail.value.enablePeerReview) return '教师发布该任务时未开启互评，本页只保留互评记录查看。'
  if (!canUseGroup.value) return '只有通过审批并加入当前班级小组后，才能评价同组成员。'
  if (memberIds.value.length < 2) return '互评需要至少两名小组成员。'
  if (taskDetail.value.canPeerReviewNow) return `请在 ${peerReviewDeadlineText.value} 前完成对同组成员的评价。`
  if (String(taskDetail.value.peerReviewPhase || '').toLowerCase() === 'not_started') return `任务截止时间为 ${taskDeadlineText.value}，截止后开放互评。`
  if (String(taskDetail.value.peerReviewPhase || '').toLowerCase() === 'closed') return `互评截止时间为 ${peerReviewDeadlineText.value}，现在只能查看已提交记录。`
  return submitBlockReason.value || '当前暂时不能提交互评。'
})
const selectedReviewee = computed(() => revieweeOptions.value.find((item) => String(item.id) === String(reviewForm.revieweeId)) || null)
const selectedMyReview = computed(() => {
  if (!reviewForm.revieweeId) return null
  return myReviewByRevieweeId.value.get(String(reviewForm.revieweeId)) || null
})
const selectedReviewIsSubmitted = computed(() => Boolean(selectedMyReview.value))
const canEditSelectedReview = computed(() => reviewWorkspaceAvailable.value && Boolean(selectedReviewee.value))
const editorReadOnly = computed(() => Boolean(selectedReviewee.value) && !reviewWorkspaceAvailable.value)
const canShowReviewEditor = computed(() => Boolean(selectedReviewee.value) && (reviewWorkspaceAvailable.value || selectedReviewIsSubmitted.value))
const submitButtonLabel = computed(() => {
  if (submitting.value) return '提交中...'
  if (!canEditSelectedReview.value) return '暂不可提交'
  if (selectedReviewIsSubmitted.value) return '保存修改'
  return selectedReviewee.value ? `提交给 ${selectedReviewee.value.label}` : '提交互评'
})
const fatalLoadError = computed(() => detailLoadError.value || taskLoadError.value)

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
  if (type === 'success') {
    setTimeout(() => {
      if (message.value === text) message.value = ''
    }, 3000)
  }
}

function formatDateTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function pickDateValue(source, keys) {
  if (!source) return ''
  for (const key of keys) {
    const value = source?.[key]
    if (!value) continue
    const raw = String(value)
    if (raw && raw !== '-' && raw !== '—') return value
  }
  return ''
}

function normalizeTaskDetail(raw) {
  if (!raw) return null
  return {
    ...raw,
    deadlineValue: pickDateValue(raw, ['deadlineValue', 'deadlineAt', 'deadline']),
    peerReviewDeadlineValue: pickDateValue(raw, [
      'peerReviewDeadlineValue',
      'peerReviewDeadlineAt',
      'peerReviewDeadline',
    ]),
    peerReviewMaxScore: raw.peerReviewMaxScore ?? 100,
    peerReviewPhase: raw.peerReviewPhaseRaw ?? raw.peerReviewPhase,
  }
}

function formatScore(value) {
  const parsed = Number(value)
  if (Number.isNaN(parsed)) return '-'
  return parsed.toFixed(1).replace(/\.0$/, '')
}

function scoreTone(value) {
  const parsed = Number(value)
  if (Number.isNaN(parsed)) return 'neutral'
  if (parsed >= 90) return 'strong'
  if (parsed >= 75) return 'steady'
  return 'low'
}

function normalizeReview(raw, index) {
  return {
    id: raw?.id ?? raw?.reviewId ?? `${raw?.reviewerAlias || 'r'}-${raw?.revieweeAlias || 'e'}-${index}`,
    reviewerId: raw?.reviewerId ?? null,
    revieweeId: raw?.revieweeId ?? null,
    reviewerAlias: raw?.reviewerAlias || '匿名评价者',
    revieweeAlias: raw?.revieweeAlias || '匿名成员',
    reviewerName: raw?.reviewerName || '',
    revieweeName: raw?.revieweeName || '',
    score: raw?.score ?? raw?.rating ?? null,
    comment: raw?.comment || raw?.content || '',
    submittedAt: raw?.submittedAt || '',
  }
}

function syncSubmittedRevieweeIds(list) {
  const ids = list
    .map((item) => item.revieweeId)
    .filter((id) => id !== null && id !== undefined && id !== '')
    .map((id) => String(id))
  submittedRevieweeIds.value = new Set(ids)
}

function syncReviewFormForSelected() {
  if (!reviewForm.revieweeId) {
    reviewForm.score = ''
    reviewForm.comment = ''
    return
  }
  const existing = myReviewByRevieweeId.value.get(String(reviewForm.revieweeId))
  if (existing) {
    reviewForm.score = Number(existing.score)
    reviewForm.comment = existing.comment || ''
    return
  }
  reviewForm.score = ''
  reviewForm.comment = ''
}

function selectDefaultReviewee() {
  const current = revieweeOptions.value.find((item) => String(item.id) === String(reviewForm.revieweeId))
  if (current) {
    syncReviewFormForSelected()
    return
  }
  const next = pendingRevieweeOptions.value[0] || revieweeOptions.value[0]
  reviewForm.revieweeId = next ? String(next.id) : ''
  syncReviewFormForSelected()
}

async function loadReviews() {
  if (!canUseGroup.value) {
    reviews.value = []
    return
  }
  reviewLoadError.value = ''
  const { data } = await fetchStudentGroupPeerReviews(classId, taskId, resolvedGroupId.value)
  const payload = Array.isArray(data?.data) ? data.data : []
  reviews.value = payload.map(normalizeReview)
  syncSubmittedRevieweeIds(reviews.value)
}

async function loadData() {
  loading.value = true
  detailLoadError.value = ''
  taskLoadError.value = ''
  groupLoadError.value = ''
  reviewLoadError.value = ''
  setMessage('', 'info')
  try {
    const sharedGroup = pageCtx?.groupContext?.value
    const sharedTask = pageCtx?.detail?.value

    if (resolvePositiveId(sharedGroup?.groupId)) {
      detail.value = {
        groupId: resolvePositiveId(sharedGroup.groupId),
        groupName: sharedGroup.groupName,
        groupJoinStatus: sharedGroup.groupJoinStatus,
      }
    }

    if (sharedTask) {
      taskDetail.value = normalizeTaskDetail(sharedTask)
    }

    const pending = []
    if (!detail.value?.groupId) {
      pending.push(
        Promise.resolve().then(async () => {
          const { fetchStudentClassDetail } = await import('../services/student')
          const detailRes = await fetchStudentClassDetail(classId)
          detail.value = detailRes?.data?.data || null
        }),
      )
    }
    if (!taskDetail.value) {
      pending.push(
        fetchStudentTaskDetail(classId, taskId).then((taskRes) => {
          taskDetail.value = normalizeTaskDetail(taskRes?.data?.data || null)
        }),
      )
    }
    if (pending.length) {
      await Promise.allSettled(pending)
    }

    if (!detail.value) {
      detailLoadError.value = '班级小组身份加载失败，请稍后重试。'
    }
    if (!taskDetail.value) {
      taskLoadError.value = '任务互评设置加载失败，请稍后重试。'
    }

    if (fatalLoadError.value) {
      groups.value = []
      reviews.value = []
      return
    }

    if (!canUseGroup.value) {
      groups.value = []
      reviews.value = []
      return
    }

    const [groupRes, reviewRes] = await Promise.allSettled([
      fetchStudentClassGroups(classId),
      fetchStudentGroupPeerReviews(classId, taskId, resolvedGroupId.value),
    ])

    if (groupRes.status === 'fulfilled') {
      groups.value = groupRes.value?.data?.data || []
    } else {
      groups.value = []
      groupLoadError.value = groupRes.reason?.message || '小组成员列表暂时加载失败，请稍后重试。'
    }

    if (reviewRes.status === 'fulfilled') {
      const payload = Array.isArray(reviewRes.value?.data?.data) ? reviewRes.value.data.data : []
      reviews.value = payload.map(normalizeReview)
      syncSubmittedRevieweeIds(reviews.value)
    } else {
      reviews.value = []
      submittedRevieweeIds.value = new Set()
      reviewLoadError.value = reviewRes.reason?.message || '互评记录暂时加载失败，请稍后重试。'
    }
    selectDefaultReviewee()
  } catch (error) {
    groups.value = []
    reviews.value = []
    setMessage(error.message || '互评工作台暂时加载失败，请稍后重试。', 'error')
  } finally {
    loading.value = false
  }
}

function goClassGroups() {
  router.push(`/student/classes/${classId}/groups`)
}

function selectReviewee(id) {
  const target = revieweeOptions.value.find((item) => String(item.id) === String(id))
  if (!target) return
  reviewForm.revieweeId = String(id)
  syncReviewFormForSelected()
}

function validateForm() {
  if (!reviewForm.revieweeId) return '请选择要评价的组员。'
  if (!canEditSelectedReview.value) return submitBlockReason.value || '当前不能提交或修改互评。'
  if (reviewForm.score === '' || reviewForm.score === null || reviewForm.score === undefined) return '请输入互评分数。'
  const score = Number(reviewForm.score)
  if (Number.isNaN(score)) return '请输入互评分数。'
  if (score < 0 || score > maxScore.value) return `分数须在 0～${maxScore.value} 之间。`
  if (String(reviewForm.comment || '').trim().length > 500) return '评语最多 500 字。'
  return ''
}

async function submitReview() {
  const error = validateForm()
  if (error) {
    setMessage(error, 'error')
    return
  }

  submitting.value = true
  try {
    const wasSubmitted = selectedReviewIsSubmitted.value
    await submitStudentPeerReview(classId, taskId, resolvedGroupId.value, {
      revieweeId: Number(reviewForm.revieweeId),
      score: Number(reviewForm.score),
      comment: String(reviewForm.comment || '').trim() || null,
    })
    submittedRevieweeIds.value = new Set([...submittedRevieweeIds.value, String(reviewForm.revieweeId)])
    const selectedId = String(reviewForm.revieweeId)
    await loadReviews()
    reviewForm.revieweeId = selectedId
    syncReviewFormForSelected()
    setMessage(wasSubmitted ? '互评已保存。' : '互评已提交。', 'success')
  } catch (error) {
    setMessage(error.message || '提交互评失败，请稍后重试。', 'error')
  } finally {
    submitting.value = false
  }
}

if (pageCtx?.detail) {
  watch(
    () => pageCtx.detail.value,
    (nextDetail) => {
      if (nextDetail) {
        taskDetail.value = normalizeTaskDetail(nextDetail)
      }
    },
    { deep: true },
  )
}

if (pageCtx?.groupContext) {
  watch(
    () => pageCtx.groupContext.value,
    (nextGroup) => {
      if (resolvePositiveId(nextGroup?.groupId)) {
        detail.value = {
          groupId: resolvePositiveId(nextGroup.groupId),
          groupName: nextGroup.groupName,
          groupJoinStatus: nextGroup.groupJoinStatus,
        }
      }
    },
    { deep: true },
  )
}

onMounted(loadData)
</script>

<template>
  <div :class="embedded ? 'peer-review-embedded' : 'student-page peer-reviews'">
    <p v-if="message" class="message" :class="messageType">{{ message }}</p>

    <section v-if="fatalLoadError" class="card error-state">
      <div>
        <p class="eyebrow">互评加载失败</p>
        <h2>暂时无法确认互评条件</h2>
        <p>{{ fatalLoadError }}</p>
      </div>
      <button class="primary-btn" type="button" :disabled="loading" @click="loadData">
        {{ loading ? '重新加载中...' : '重新加载' }}
      </button>
    </section>

    <template v-else>
      <section class="card peer-status-card" :class="`phase-${peerPhaseTone}`">
        <div>
          <p class="eyebrow">互评状态</p>
          <h2>{{ peerStatusTitle }}</h2>
          <p>{{ peerStatusDescription }}</p>
        </div>
        <div class="peer-status-meta">
          <span>{{ peerPhaseLabel }}</span>
          <strong>{{ reviewStats.submitted }} / {{ reviewStats.expected || '-' }}</strong>
          <small>已提交 / 应提交</small>
        </div>
      </section>

      <section class="peer-workspace">
        <aside class="card review-queue-panel">
          <div class="panel-head">
            <div>
              <h2>互评名单</h2>
              <p class="panel-note">点击成员后，在右侧查看或填写互评。</p>
            </div>
            <span class="panel-count">待评价 {{ pendingRevieweeOptions.length }}</span>
          </div>

          <div v-if="loading" class="loading-state">
            <div v-for="i in 3" :key="i" class="skeleton-row" />
          </div>

          <div v-else-if="groupLoadError" class="empty-state compact error-state-inline">
            <h3>小组成员加载失败</h3>
            <p>{{ groupLoadError }}</p>
          </div>

          <div v-else-if="!revieweeOptions.length" class="empty-state compact">
            <h3>暂无可评价成员</h3>
            <p>{{ submitBlockReason || peerStatusDescription }}</p>
          </div>

          <div v-else class="review-queue-list">
            <button
              v-for="member in revieweeOptions"
              :key="member.id"
              class="review-queue-item"
              :class="{ active: String(reviewForm.revieweeId) === String(member.id), submitted: member.sessionSubmitted }"
              type="button"
              @click="selectReviewee(member.id)"
            >
              <span class="queue-member-name">{{ member.label }}</span>
              <small>{{ member.caption }}</small>
              <em>{{ member.sessionSubmitted ? '已提交' : '待评价' }}</em>
            </button>
          </div>
        </aside>

        <article class="card review-editor-panel">
          <div class="panel-head">
            <div>
              <h2>{{ selectedReviewee ? `评价 ${selectedReviewee.label}` : '填写互评' }}</h2>
              <p class="panel-note">互评期内可以修改已提交评价，截止后只读。</p>
            </div>
            <span class="panel-count">已提交 {{ reviewStats.submitted }} / {{ reviewStats.expected || '-' }}</span>
          </div>

          <div v-if="loading" class="loading-state">
            <div v-for="i in 3" :key="i" class="skeleton-row" />
          </div>

          <div v-else-if="!canShowReviewEditor" class="empty-state compact">
            <h3>暂时不能提交互评</h3>
            <p>{{ submitBlockReason || peerStatusDescription }}</p>
            <button v-if="!canUseGroup" class="primary-btn" type="button" @click="goClassGroups">前往班级小组</button>
          </div>

          <form v-else class="review-form" :class="{ readonly: editorReadOnly }" @submit.prevent="submitReview">
            <p v-if="editorReadOnly" class="readonly-banner">
              互评已截止，当前只能查看你提交给该成员的评价。
            </p>
            <label class="field">
              <span>互评分数（0～{{ maxScore }}）</span>
              <div class="score-editor">
                <input v-model.number="reviewForm.score" type="range" :min="0" :max="maxScore" step="1" :disabled="editorReadOnly" />
                <input v-model.number="reviewForm.score" class="score-input" type="number" :min="0" :max="maxScore" step="1" :disabled="editorReadOnly" />
              </div>
            </label>

            <label class="field">
              <span>评价说明</span>
              <textarea
                v-model.trim="reviewForm.comment"
                rows="5"
                maxlength="500"
                :readonly="editorReadOnly"
                placeholder="可以写清楚对方完成了哪些工作、协作是否及时、有什么改进建议。"
              ></textarea>
            </label>

            <div class="form-actions">
              <p class="submit-hint">请基于同组成员实际贡献填写，互评只作为记录与参考汇总。</p>
              <button v-if="!editorReadOnly" class="primary-btn" type="submit" :disabled="submitting || !canEditSelectedReview">
                {{ submitButtonLabel }}
              </button>
            </div>
          </form>
        </article>
      </section>
    </template>
  </div>
</template>

<style scoped>
.peer-review-embedded,
.peer-reviews,
.review-form,
.member-picker,
.field,
.loading-state,
.review-list,
.peer-status-meta {
  display: grid;
  gap: 14px;
}

.card {
  background: var(--tt-surface);
  border: 1px solid var(--tt-border-subtle);
  border-radius: 24px;
  box-shadow: var(--student-shadow);
}

.panel-head,
.review-row,
.review-route,
.form-actions {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
}

.eyebrow {
  margin: 0;
}

h1,
h2,
h3 {
  margin: 0;
  color: var(--student-text-primary);
}

h2 {
  font-size: 18px;
}

.section-title,
.message,
.panel-note,
.submit-hint,
.review-main p,
.review-main small,
.empty-state p,
.field span {
  margin: 8px 0 0;
  color: var(--student-text-tertiary);
  font-size: 13px;
  line-height: 1.65;
}

.peer-status-card {
  margin-bottom: 14px;
  padding: 18px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  background: linear-gradient(135deg, rgba(32, 107, 255, 0.08), var(--tt-surface));
}

.peer-status-card h2,
.peer-status-card p {
  margin: 6px 0 0;
}

.peer-status-card p {
  color: var(--student-text-secondary);
  line-height: 1.7;
}

.peer-status-card.phase-open {
  border-color: rgba(52, 199, 89, 0.24);
}

.peer-status-card.phase-waiting {
  border-color: rgba(0, 122, 255, 0.22);
}

.peer-status-card.phase-closed,
.peer-status-card.phase-disabled {
  border-color: var(--tt-border-subtle);
  background: var(--tt-surface);
}

.peer-status-meta {
  min-width: 150px;
  justify-items: end;
  gap: 4px;
}

.peer-status-meta span,
.panel-count {
  min-height: 30px;
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 0 12px;
  background: var(--student-accent-soft);
  color: var(--student-accent);
  font-size: 12px;
  font-weight: 800;
}

.peer-status-meta strong {
  font-size: 24px;
  color: var(--student-text-primary);
}

.peer-status-meta small {
  color: var(--student-text-tertiary);
}

.panel-note {
  margin-top: 4px;
}

.panel-count {
  flex: none;
}

.peer-workspace {
  display: grid;
  grid-template-columns: minmax(260px, 0.42fr) minmax(0, 1fr);
  gap: 14px;
  align-items: stretch;
}

.review-queue-panel,
.review-editor-panel {
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
}

.review-queue-panel > :not(.panel-head),
.review-editor-panel > :not(.panel-head) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.review-queue-list {
  display: grid;
  align-content: start;
  gap: 10px;
  min-height: 0;
  overflow-y: auto;
  padding-right: 2px;
}

.review-queue-item {
  width: 100%;
  min-height: 76px;
  padding: 12px 14px;
  border: 1px solid var(--tt-border-subtle);
  border-radius: 16px;
  background: var(--tt-surface-muted);
  text-align: left;
  color: var(--student-text-primary);
  font-family: inherit;
  cursor: pointer;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 4px 10px;
  align-items: center;
  transition: border-color 0.16s ease, background 0.16s ease, box-shadow 0.16s ease;
}

.review-queue-item:disabled {
  cursor: not-allowed;
  opacity: 0.86;
}

.review-queue-item:hover:not(:disabled),
.review-queue-item.active {
  border-color: var(--tt-accent-border);
  background: var(--tt-accent-soft);
}

.review-queue-item.active {
  box-shadow: inset 0 0 0 1px var(--tt-accent-border);
}

.review-queue-item.submitted {
  background: rgba(52, 199, 89, 0.08);
  border-color: rgba(52, 199, 89, 0.2);
}

.queue-member-name,
.review-queue-item small,
.review-queue-item em {
  display: block;
}

.queue-member-name {
  min-width: 0;
  color: var(--student-text-primary);
  font-size: 15px;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.review-queue-item small {
  grid-column: 1 / 2;
  color: var(--student-text-secondary);
  font-size: 12px;
}

.review-queue-item em {
  grid-column: 2 / 3;
  grid-row: 1 / 3;
  min-height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  padding: 0 10px;
  background: var(--student-accent-soft);
  color: var(--student-accent);
  font-size: 12px;
  font-style: normal;
  font-weight: 800;
}

.review-queue-item.submitted em {
  background: rgba(52, 199, 89, 0.14);
  color: var(--student-success);
}

.review-editor-panel .empty-state {
  min-height: 360px;
}

.field {
  align-content: start;
}

.field span {
  color: var(--student-text-secondary);
}

.score-editor {
  display: grid;
  grid-template-columns: 1fr 88px;
  gap: 10px;
  align-items: center;
}

input[type='range'] {
  width: 100%;
  accent-color: var(--student-accent);
}

.score-input,
textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid var(--tt-border-subtle);
  border-radius: 16px;
  background: var(--tt-surface-muted);
  color: var(--student-text-primary);
  font-family: inherit;
}

.score-input {
  height: 42px;
  padding: 0 12px;
}

textarea {
  min-height: 118px;
  padding: 12px;
  resize: vertical;
}

.form-actions {
  justify-content: flex-end;
  align-items: center;
}

.submit-hint {
  margin: 0 auto 0 0;
  max-width: 420px;
}

.review-row {
  padding: 16px;
  border-radius: 20px;
  border: 1px solid var(--tt-border-subtle);
  background: var(--tt-surface-muted);
}

.review-main {
  min-width: 0;
}

.review-main small {
  display: block;
}

.review-route {
  justify-content: flex-start;
  align-items: center;
  flex-wrap: wrap;
}

.alias-pill,
.score-pill {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.alias-pill {
  background: var(--student-accent-soft);
  color: var(--student-accent);
}

.alias-pill.target {
  background: rgba(52, 199, 89, 0.12);
  color: var(--student-success);
}

.route-arrow {
  color: var(--student-text-tertiary);
}

.score-pill {
  justify-content: center;
  min-width: 56px;
  font-size: 18px;
}

.score-pill.strong {
  background: rgba(52, 199, 89, 0.14);
  color: var(--student-success);
}

.score-pill.steady {
  background: rgba(0, 122, 255, 0.12);
  color: var(--student-accent);
}

.score-pill.low {
  background: rgba(255, 149, 0, 0.14);
  color: #b35c00;
}

.score-pill.neutral {
  background: rgba(15, 23, 42, 0.06);
  color: var(--student-text-secondary);
}

.review-form,
.loading-state,
.review-list {
  flex: 1;
  min-height: 0;
}

.review-form.readonly input,
.review-form.readonly textarea {
  cursor: default;
}

.readonly-banner {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid var(--tt-border-subtle);
  border-radius: 16px;
  background: var(--tt-surface-muted);
  color: var(--student-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.empty-state {
  flex: 1;
  min-height: 280px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  text-align: center;
  padding: 24px;
  border: 1px dashed var(--tt-border);
  border-radius: 22px;
  background: var(--tt-surface-muted);
}

.empty-state.compact {
  min-height: 280px;
}

.empty-state h3 {
  margin: 0;
}

.empty-state p {
  max-width: 380px;
  margin: 0;
}

.error-state {
  min-height: 280px;
  padding: 30px;
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

.error-state-inline {
  border-color: rgba(255, 59, 48, 0.16);
}

.loading-state {
  min-height: 280px;
  align-content: start;
}

.skeleton-row {
  height: 74px;
  border-radius: 18px;
  background: var(--student-surface-muted);
  animation: pulse 1.5s ease-in-out infinite;
}

.message {
  padding: 10px 14px;
  border-radius: 16px;
  background: var(--tt-surface-muted);
  border: 1px solid var(--tt-border-subtle);
}

.message.error {
  color: var(--student-danger);
}

.message.success {
  color: var(--student-success);
}

.primary-btn {
  min-height: 40px;
  border-radius: 16px;
  padding: 0 18px;
  border: 0;
  cursor: pointer;
  font-weight: 700;
  font-family: inherit;
  background: var(--student-accent);
  color: #fff;
}

.primary-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
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

@media (max-width: 1040px) {
  .peer-workspace {
    grid-template-columns: 1fr;
  }

  .peer-status-card {
    align-items: flex-start;
  }
}

@media (max-width: 760px) {
  .peer-status-card,
  .panel-head,
  .review-row,
  .form-actions {
    flex-direction: column;
  }

  .peer-status-meta {
    justify-items: start;
  }

  .form-actions {
    align-items: stretch;
  }

  .form-actions .primary-btn {
    width: 100%;
  }
}

@media (max-width: 560px) {
  .score-editor {
    grid-template-columns: 1fr;
  }

  .review-queue-item {
    grid-template-columns: 1fr;
  }

  .review-queue-item em {
    grid-column: auto;
    grid-row: auto;
    justify-self: start;
  }
}
</style>
