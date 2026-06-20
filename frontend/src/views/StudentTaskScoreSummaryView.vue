<script setup>
import { computed, inject, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchStudentGroupScoreSummary } from '../services/student'
import { STUDENT_TASK_PAGE_KEY } from '../composables/studentTaskPageContext'
import { buildStudentTaskDetailLocation } from '../utils/studentTaskNavigation'
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

function openAppealsTab() {
  if (typeof pageCtx?.setStudentTab === 'function') {
    pageCtx.setStudentTab('appeals')
    return
  }
  router.push({
    path: `/student/classes/${classId}/tasks/${taskId}`,
    query: { ...route.query, tab: 'appeals' },
  })
}

const loading = ref(true)
const message = ref('')
const messageType = ref('info')
const summary = ref(null)
const detail = ref(null)
const detailLoadError = ref('')
const scoreLoadError = ref('')

const detailLocation = computed(() => buildStudentTaskDetailLocation(classId, taskId, route.query))
const resolvedGroupId = computed(() => resolvePositiveId(detail.value?.groupId))
const canUseGroup = computed(() => Boolean(resolvedGroupId.value) && detail.value?.groupJoinStatus !== '待审批')
const groupName = computed(
  () => detail.value?.groupName || (resolvedGroupId.value ? `小组 ${resolvedGroupId.value}` : '未加入小组'),
)
const normalizedSummary = computed(() => {
  if (!summary.value) return null
  const peerAverage = pickNumber(summary.value, ['peerReviewAverage', 'peerAverageOn100', 'peerAverage', 'peerScore'])
  const teacherScore = pickNumber(summary.value, ['teacherScore', 'teacherScoreOn100'])
  const maxScore = pickNumber(summary.value, ['peerReviewMaxScore', 'maxScore'], 100)
  const scoreDifference = calculateScoreDifference(peerAverage, teacherScore)

  return {
    peerAverage,
    teacherScore,
    scoreDifference,
    maxScore,
  }
})
const hasAnyScore = computed(() => {
  const item = normalizedSummary.value
  if (!item) return false
  return [item.peerAverage, item.teacherScore].some((value) => value !== null)
})
const appealStatus = computed(() => {
  if (!canUseGroup.value) {
    return {
      label: '暂不可用',
      hint: '加入小组后才可提交申诉',
      canAppeal: false,
    }
  }
  if (!hasAnyScore.value) {
    return {
      label: '等待成绩',
      hint: '有互评或教师评分后可申诉',
      canAppeal: false,
    }
  }
  return {
    label: '可申诉',
    hint: '对成绩或互评有异议时提交',
    canAppeal: true,
  }
})

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

function pickNumber(source, keys, fallback = null) {
  for (const key of keys) {
    if (source?.[key] === null || source?.[key] === undefined || source?.[key] === '') continue
    const parsed = Number(source[key])
    if (!Number.isNaN(parsed)) return parsed
  }
  return fallback
}

function fmt(value, digits = 1) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '-'
  return Number(value).toFixed(digits).replace(/\.0$/, '')
}

function calculateScoreDifference(peerScore, teacherScore) {
  if (
    peerScore === null ||
    peerScore === undefined ||
    teacherScore === null ||
    teacherScore === undefined
  ) {
    return null
  }
  const parsedPeer = Number(peerScore)
  const parsedTeacher = Number(teacherScore)
  if (Number.isNaN(parsedPeer) || Number.isNaN(parsedTeacher)) {
    return null
  }
  return Number((parsedTeacher - parsedPeer).toFixed(1))
}

function formatScoreDifference(value) {
  const formatted = fmt(value)
  if (formatted === '-') {
    return formatted
  }
  return Number(value) > 0 ? `+${formatted}` : formatted
}

async function loadData() {
  loading.value = true
  setMessage('', 'info')
  detailLoadError.value = ''
  scoreLoadError.value = ''
  summary.value = null
  try {
    const sharedGroup = pageCtx?.groupContext?.value
    if (resolvePositiveId(sharedGroup?.groupId)) {
      detail.value = {
        groupId: resolvePositiveId(sharedGroup.groupId),
        groupName: sharedGroup.groupName,
        groupJoinStatus: sharedGroup.groupJoinStatus,
      }
    } else {
      const { fetchStudentClassDetail } = await import('../services/student')
      const detailRes = await fetchStudentClassDetail(classId)
      detail.value = detailRes?.data?.data || null
    }

    if (!detail.value) {
      throw new Error('未找到班级信息，或你暂时没有访问该班级的权限。')
    }
  } catch (error) {
    detail.value = null
    detailLoadError.value = error.message || '成绩信息加载失败，请稍后重试。'
    loading.value = false
    return
  }

  if (!canUseGroup.value) {
    loading.value = false
    return
  }

  try {
    const { data } = await fetchStudentGroupScoreSummary(classId, taskId, resolvedGroupId.value)
    summary.value = data?.data || null
  } catch (error) {
    summary.value = null
    scoreLoadError.value = error.message || '成绩汇总暂时加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

function goClassGroups() {
  router.push(`/student/classes/${classId}/groups`)
}

onMounted(loadData)
</script>

<template>
  <div :class="embedded ? 'score-composition-embedded' : 'student-page score-summary'">
    <header v-if="!embedded" class="card score-hero">
      <div>
        <p class="eyebrow">成绩汇总</p>
        <h1>我的任务成绩</h1>
      </div>
      <div class="hero-actions">
        <button class="secondary-btn" type="button" :disabled="loading" @click="loadData">
          {{ loading ? '刷新中...' : '刷新成绩' }}
        </button>
        <button class="back-btn" type="button" @click="router.push(detailLocation)">返回任务详情</button>
      </div>
    </header>

    <p v-if="message" class="message" :class="messageType">{{ message }}</p>

    <section v-if="!detailLoadError" class="card score-overview-card">
      <div class="score-overview-head">
        <div>
          <span class="section-kicker">成绩摘要</span>
          <h2>本任务评分概览</h2>
        </div>
        <p>当前小组：{{ groupName }}</p>
      </div>

      <div class="score-overview-grid">
        <article class="score-metric">
          <span>教师评分</span>
          <strong>{{ fmt(normalizedSummary?.teacherScore) }}</strong>
          <small>教师对本任务的一次评分</small>
        </article>
        <article class="score-metric">
          <span>互评均分</span>
          <strong>{{ fmt(normalizedSummary?.peerAverage) }}</strong>
          <small>组内成员互评平均分</small>
        </article>
        <article class="score-metric score-metric--difference">
          <span>评分差值</span>
          <strong>{{ formatScoreDifference(normalizedSummary?.scoreDifference) }}</strong>
          <small>教师评分 - 互评均分</small>
        </article>
        <article class="score-metric appeal-metric">
          <span>是否可申诉</span>
          <strong>{{ appealStatus.label }}</strong>
          <small>{{ appealStatus.hint }}</small>
          <button
            v-if="appealStatus.canAppeal"
            class="summary-action-btn"
            type="button"
            @click="openAppealsTab"
          >
            前往申诉
          </button>
        </article>
      </div>
    </section>

    <section class="card score-panel">
      <div class="student-section-bar">
        <h2>数据说明</h2>
      </div>

      <div v-if="loading" class="loading-state">
        <div class="skeleton-card" />
      </div>

      <div v-else-if="detailLoadError" class="empty-state error-state">
        <h3>成绩信息加载失败</h3>
        <p>{{ detailLoadError }}</p>
      </div>

      <div v-else-if="!canUseGroup" class="empty-state">
        <h3>{{ detail?.groupJoinStatus === '待审批' ? '小组申请待审批' : '先加入小组' }}</h3>
        <button class="primary-btn" type="button" @click="goClassGroups">前往班级小组</button>
      </div>

      <div v-else-if="scoreLoadError" class="empty-state error-state">
        <h3>成绩汇总暂时不可用</h3>
        <p>{{ scoreLoadError }}</p>
      </div>

      <div v-else-if="!embedded && !hasAnyScore" class="empty-state">
        <h3>当前还没有成绩数据</h3>
        <p>互评结果和教师评分会在完成后显示在这里；系统不自动生成最终成绩。</p>
      </div>

      <template v-else>
        <ul class="score-explain-list">
          <li class="score-row">
            <div class="score-row-main">
              <span class="score-row-label">互评均分</span>
              <p>来自组内成员互评结果的平均值，只用于辅助判断个人贡献。</p>
            </div>
          </li>
          <li class="score-row">
            <div class="score-row-main">
              <span class="score-row-label">教师评分</span>
              <p>教师对本任务提交结果的一次评分，不等同于课程最终成绩。</p>
            </div>
          </li>
          <li class="score-row score-row--difference">
            <div class="score-row-main">
              <span class="score-row-label">评分差值</span>
              <p>计算公式为“教师评分 - 互评均分”，用于提示教师评价和组内评价之间的分歧。</p>
            </div>
          </li>
        </ul>

        <div v-if="!embedded" class="formula-panel">
          <div>
            <span>成绩说明</span>
            <strong>系统只记录评分，不自动计算最终成绩</strong>
          </div>
          <p>
            互评均分、教师评分和评分差值作为任务评价依据展示；评分差值只提示评价分歧，最终课程成绩由教师结合任务证据、互评反馈和课程规则自行确定。
          </p>
        </div>
      </template>
    </section>
  </div>
</template>

<style scoped>
.score-composition-embedded,
.score-summary {
  display: grid;
  gap: 14px;
}

.card {
  background: var(--tt-surface);
  border: 1px solid var(--tt-border-subtle);
  border-radius: 24px;
  box-shadow: var(--student-shadow);
}

.score-hero,
.hero-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
}

.score-hero {
  padding: 20px;
}

.hero-actions {
  justify-content: flex-end;
  flex-wrap: wrap;
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

h1 {
  margin-top: 8px;
  font-size: 24px;
}

h2 {
  font-size: 18px;
}

.hero-copy,
.section-title,
.message,
.score-overview-head p,
.score-metric small,
.score-row p,
.formula-panel p,
.empty-state p {
  margin: 8px 0 0;
  color: var(--student-text-tertiary);
  font-size: 13px;
  line-height: 1.65;
}

.score-overview-card {
  padding: 18px;
  display: grid;
  gap: 16px;
}

.score-overview-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.score-overview-head p {
  margin: 0;
  text-align: right;
  word-break: break-word;
}

.section-kicker,
.score-metric span {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--student-text-secondary);
}

.section-kicker {
  display: block;
  margin-bottom: 6px;
}

.score-overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.score-metric {
  min-width: 0;
  min-height: 138px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  border: 1px solid var(--tt-border-subtle);
  border-radius: 18px;
  background: var(--tt-surface-muted);
}

.score-metric strong {
  display: block;
  margin: 0;
  font-size: 30px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: 0;
  color: var(--student-text-primary);
  font-variant-numeric: tabular-nums;
  word-break: break-word;
}

.score-metric small {
  margin: 0;
  line-height: 1.5;
}

.score-metric--difference {
  background: color-mix(in srgb, var(--student-accent) 6%, var(--tt-surface-muted));
}

.appeal-metric strong {
  font-size: 24px;
}

.summary-action-btn {
  min-height: 34px;
  margin-top: auto;
  padding: 0 12px;
  border: 1px solid color-mix(in srgb, var(--student-accent) 42%, var(--student-border));
  border-radius: 12px;
  background: color-mix(in srgb, var(--student-accent) 10%, var(--student-surface));
  color: var(--student-accent);
  cursor: pointer;
  font-family: inherit;
  font-size: 13px;
  font-weight: 700;
}

.score-panel {
  padding: 18px;
  display: grid;
  gap: 14px;
}

.score-panel .student-section-bar {
  margin-bottom: 0;
}

.score-explain-list {
  margin: 0;
  padding: 0;
  list-style: none;
  border: 1px solid var(--tt-border-subtle);
  border-radius: 16px;
  overflow: hidden;
  background: var(--tt-surface-muted);
}

.score-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--tt-border-subtle);
}

.score-row:last-child {
  border-bottom: 0;
}

.score-row-main {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.score-row-label {
  font-size: 13px;
  font-weight: 700;
  color: var(--student-text-primary);
}

.score-row p {
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
}

.score-row--difference {
  background: color-mix(in srgb, var(--tt-accent) 4%, var(--tt-surface-muted));
}

.formula-panel {
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid var(--tt-border-subtle);
  background: var(--tt-surface-muted);
}

.formula-panel span {
  color: var(--student-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.formula-panel strong {
  display: block;
  margin-top: 6px;
  color: var(--student-text-primary);
  font-size: 18px;
}

.empty-state {
  min-height: 260px;
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

.empty-state.error-state {
  border-color: color-mix(in srgb, var(--tt-danger) 24%, var(--tt-border));
  background: color-mix(in srgb, var(--tt-danger) 8%, var(--tt-surface-muted));
}

.empty-state h3 {
  margin: 0;
}

.empty-state p {
  max-width: 420px;
  margin: 0;
}

.empty-actions {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
}

.loading-state {
  display: grid;
}

.skeleton-card {
  height: 220px;
  border-radius: 22px;
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

.primary-btn,
.secondary-btn,
.back-btn {
  min-height: 40px;
  border-radius: 16px;
  padding: 0 14px;
  border: 0;
  cursor: pointer;
  font-weight: 700;
  font-family: inherit;
}

.primary-btn {
  background: var(--student-accent);
  color: #fff;
}

.secondary-btn {
  background: var(--student-surface-muted);
  color: var(--student-text-primary);
}

.back-btn {
  background: var(--student-surface);
  border: 1px solid var(--student-border);
  color: var(--student-text-primary);
}

.primary-btn:disabled,
.secondary-btn:disabled,
.back-btn:disabled {
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

@media (max-width: 900px) {
  .score-overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .score-hero,
  .score-overview-head {
    flex-direction: column;
  }

  .hero-actions,
  .score-overview-head p {
    justify-content: flex-start;
    text-align: left;
  }
}

@media (max-width: 560px) {
  .score-overview-grid {
    grid-template-columns: 1fr;
  }

  .score-metric {
    min-height: auto;
  }
}
</style>
