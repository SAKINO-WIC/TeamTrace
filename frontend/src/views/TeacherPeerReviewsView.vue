<script setup>
import EmptyState from '../components/common/EmptyState.vue'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TeacherSubviewShell from '../components/teacher/TeacherSubviewShell.vue'
import { useTeacherLocale } from '../composables/useTeacherLocale'
import { fetchTeacherClassGroups, fetchTeacherGroupPeerReviews } from '../services/teacher'
import { buildTeacherTaskDetailLocation } from '../utils/teacherTaskNavigation'
import { formatDateTime } from '../utils/teacher'

const { t, tm } = useTeacherLocale()

const route = useRoute()
const router = useRouter()
const classId = computed(() => route.params.classId)
const taskId = computed(() => route.params.taskId)

const groups = ref([])
const selectedGroupId = ref('')
const reviews = ref([])
const loading = ref(false)
const loadingGroups = ref(false)
const message = ref('')
const messageType = ref('info')
const keyword = ref('')
const sortMode = ref('score_desc')
const showComments = ref(true)

function goBackToTaskDetail() {
  router.push(buildTeacherTaskDetailLocation(classId.value, taskId.value, route.query, route.query?.from || ''))
}

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

function formatNumber(value, digits = 1) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }

  const parsed = Number(value)
  if (Number.isNaN(parsed)) {
    return '-'
  }

  return parsed.toFixed(digits).replace(/\.0+$/, '').replace(/(\.\d*[1-9])0+$/, '$1')
}

function normalizeGroup(raw) {
  const memberIds = Array.isArray(raw?.memberStudentIds) ? raw.memberStudentIds : []
  return {
    groupId: raw?.groupId ? String(raw.groupId) : '',
    name: raw?.name ?? raw?.groupName ?? t('未命名小组', 'Unnamed group'),
    memberCount: raw?.memberCount ?? memberIds.length,
  }
}

function normalizeReview(raw, index) {
  return {
    id: raw?.id ?? `${raw?.reviewerAlias || 'reviewer'}-${raw?.revieweeAlias || 'reviewee'}-${index}`,
    reviewerId: raw?.reviewerId ?? null,
    revieweeId: raw?.revieweeId ?? null,
    reviewerName: raw?.reviewerName ?? '',
    revieweeName: raw?.revieweeName ?? '',
    reviewerAlias: raw?.reviewerAlias ?? '-',
    revieweeAlias: raw?.revieweeAlias ?? '-',
    score: raw?.score,
    comment: raw?.comment ?? '',
    submittedAt: raw?.submittedAt ?? '',
  }
}

const selectedGroupMeta = computed(() => {
  return groups.value.find((item) => item.groupId === selectedGroupId.value) || null
})

const expectedReviewCount = computed(() => {
  const count = Number(selectedGroupMeta.value?.memberCount || 0)
  return count > 1 ? count * (count - 1) : 0
})

async function loadGroups() {
  loadingGroups.value = true
  try {
    const { data } = await fetchTeacherClassGroups(classId.value)
    const normalized = Array.isArray(data?.data) ? data.data.map(normalizeGroup) : []
    groups.value = normalized

    const hasCurrentSelection = normalized.some((item) => item.groupId === selectedGroupId.value)
    if (!hasCurrentSelection) {
      selectedGroupId.value = normalized[0]?.groupId || ''
    }

    if (!normalized.length) {
      setMessage('')
    }
  } catch (error) {
    groups.value = []
    selectedGroupId.value = ''
    reviews.value = []
    setMessage(error.message || t('加载小组列表失败，请稍后重试。', 'Failed to load the group list. Please try again later.'), 'error')
  } finally {
    loadingGroups.value = false
  }
}

async function loadReviews() {
  if (!selectedGroupId.value) {
    reviews.value = []
    return
  }

  loading.value = true
  try {
    const { data } = await fetchTeacherGroupPeerReviews(classId.value, taskId.value, selectedGroupId.value)
    reviews.value = Array.isArray(data?.data) ? data.data.map((item, index) => normalizeReview(item, index)) : []
    setMessage('')
  } catch (error) {
    reviews.value = []
    setMessage(error.message || t('加载互评记录失败，请稍后重试。', 'Failed to load peer review records. Please try again later.'), 'error')
  } finally {
    loading.value = false
  }
}

async function refreshAll() {
  reviews.value = []
  await loadGroups()
  if (selectedGroupId.value) {
    await loadReviews()
  }
}

const filteredReviews = computed(() => {
  const term = keyword.value.trim().toLowerCase()
  let list = reviews.value
  if (term) {
    list = list.filter((item) => {
      return (
        String(item.reviewerName || '').toLowerCase().includes(term) ||
        String(item.revieweeName || '').toLowerCase().includes(term) ||
        String(item.reviewerAlias || '').toLowerCase().includes(term) ||
        String(item.revieweeAlias || '').toLowerCase().includes(term) ||
        String(item.comment || '').toLowerCase().includes(term)
      )
    })
  }

  const sorted = [...list]
  const getScoreNumber = (value) => {
    const parsed = Number(value)
    return Number.isNaN(parsed) ? null : parsed
  }

  if (sortMode.value === 'score_desc') {
    sorted.sort((a, b) => (getScoreNumber(b.score) ?? -Infinity) - (getScoreNumber(a.score) ?? -Infinity))
  } else if (sortMode.value === 'score_asc') {
    sorted.sort((a, b) => (getScoreNumber(a.score) ?? Infinity) - (getScoreNumber(b.score) ?? Infinity))
  } else if (sortMode.value === 'reviewer') {
    sorted.sort((a, b) => String(a.reviewerName || a.reviewerAlias).localeCompare(String(b.reviewerName || b.reviewerAlias), 'zh-CN'))
  } else if (sortMode.value === 'reviewee') {
    sorted.sort((a, b) => String(a.revieweeName || a.revieweeAlias).localeCompare(String(b.revieweeName || b.revieweeAlias), 'zh-CN'))
  } else if (sortMode.value === 'submitted_desc') {
    sorted.sort((a, b) => new Date(b.submittedAt).getTime() - new Date(a.submittedAt).getTime())
  }

  return sorted
})

const summary = computed(() => {
  const scores = filteredReviews.value
    .map((item) => Number(item.score))
    .filter((value) => !Number.isNaN(value))

  const latestSubmittedAt = filteredReviews.value.length
    ? formatDateTime(
        [...filteredReviews.value]
          .map((item) => item.submittedAt)
          .filter(Boolean)
          .sort((left, right) => new Date(right).getTime() - new Date(left).getTime())[0],
      )
    : '-'

  return {
    count: filteredReviews.value.length,
    scoredCount: scores.length,
    avg: scores.length ? formatNumber(scores.reduce((total, current) => total + current, 0) / scores.length) : '-',
    lowCount: scores.filter((value) => value < 60).length,
    expected: expectedReviewCount.value,
    pending: Math.max(expectedReviewCount.value - reviews.value.length, 0),
    completion: expectedReviewCount.value ? `${Math.min(100, Math.round((reviews.value.length / expectedReviewCount.value) * 100))}%` : '-',
    latestSubmittedAt,
  }
})

watch([classId, taskId], () => {
  selectedGroupId.value = ''
  groups.value = []
  reviews.value = []
  refreshAll()
}, { immediate: true })
</script>

<template>
  <TeacherSubviewShell :title="tm('peerReviews.title')" :message="message" :message-type="messageType">
    <template #actions>
      <button class="back-btn" type="button" @click="goBackToTaskDetail">
        {{ tm('common.back') }}
      </button>
    </template>

    <section class="card panel">
      <div class="filters">
        <label>
          <span>{{ tm('common.group') }}</span>
          <select v-model="selectedGroupId" :disabled="loadingGroups || !groups.length" @change="loadReviews">
            <option value="" disabled>{{ t('请选择小组', 'Select a group') }}</option>
            <option v-for="g in groups" :key="g.groupId" :value="g.groupId">
              {{ g.name }}（{{ g.memberCount }} {{ t('人', 'members') }}）
            </option>
          </select>
        </label>

        <label>
          <span>{{ tm('common.search') }}</span>
          <input v-model.trim="keyword" type="text" :placeholder="t('姓名 / 代号 / 评语', 'Name / alias / comment')" />
        </label>

        <label>
          <span>{{ tm('common.status') }}</span>
          <select v-model="sortMode">
            <option value="score_desc">{{ t('分数从高到低', 'Score high to low') }}</option>
            <option value="score_asc">{{ t('分数从低到高', 'Score low to high') }}</option>
            <option value="submitted_desc">{{ t('按最近提交', 'Most recent submissions') }}</option>
            <option value="reviewer">{{ t('按评价人', 'By reviewer') }}</option>
            <option value="reviewee">{{ t('按被评人', 'By reviewee') }}</option>
          </select>
        </label>

        <button class="secondary-btn" type="button" :disabled="loading || !selectedGroupId" @click="loadReviews">
          {{ loading ? tm('common.loading') : tm('common.refresh') }}
        </button>

        <button class="ghost-btn" type="button" @click="showComments = !showComments">
          {{ showComments ? t('隐藏评语', 'Hide comments') : t('显示评语', 'Show comments') }}
        </button>
      </div>

      <div v-if="!groups.length && !loadingGroups" class="empty-state">
        {{ t('当前班级还没有学期小组，暂时无法查看组内互评。', 'There are no semester groups in this class yet, so peer reviews are unavailable.') }}
      </div>

      <div v-else-if="groups.length" class="peer-overview">
        <article class="stat">
          <p class="label">{{ t('当前小组', 'Current group') }}</p>
          <p class="value">{{ selectedGroupMeta?.name || '-' }}</p>
        </article>
        <article class="stat">
          <p class="label">{{ t('互评完成率', 'Completion') }}</p>
          <p class="value">{{ summary.completion }}</p>
          <small>{{ summary.count }} / {{ summary.expected || '-' }}</small>
        </article>
        <article class="stat">
          <p class="label">{{ t('待提交记录', 'Pending records') }}</p>
          <p class="value">{{ summary.pending }}</p>
        </article>
        <article class="stat">
          <p class="label">{{ tm('common.average') || t('平均分', 'Average score') }}</p>
          <p class="value">{{ summary.avg }}</p>
        </article>
        <article class="stat">
          <p class="label">{{ t('低分记录', 'Low-score records') }}</p>
          <p class="value">{{ summary.lowCount }}</p>
        </article>
        <article class="stat">
          <p class="label">{{ t('最近提交', 'Latest submitted') }}</p>
          <p class="value">{{ summary.latestSubmittedAt }}</p>
        </article>
      </div>

      <div v-if="groups.length" class="peer-note">
        <strong>{{ t('教师实名查看', 'Teacher identity view') }}</strong>
        <span>{{ t('教师端显示真实评价人和被评人，便于处理异常低分与后续申诉；学生端仍不会看到是谁评价了自己。', 'Teachers can see real reviewer and reviewee names for low-score checks and appeals. Students still cannot see who reviewed them.') }}</span>
      </div>

      <div v-if="groups.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ t('评价人', 'Reviewer') }}</th>
              <th>{{ t('被评人', 'Reviewee') }}</th>
              <th>{{ tm('common.score') }}</th>
              <th>{{ tm('common.createdAt') }}</th>
              <th v-if="showComments">{{ t('评语', 'Comment') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, idx) in filteredReviews" :key="item.id || idx">
              <td>
                <strong class="identity-name">{{ item.reviewerName || item.reviewerAlias || '-' }}</strong>
                <small v-if="item.reviewerAlias && item.reviewerName">{{ item.reviewerAlias }}</small>
              </td>
              <td>
                <strong class="identity-name">{{ item.revieweeName || item.revieweeAlias || '-' }}</strong>
                <small v-if="item.revieweeAlias && item.revieweeName">{{ item.revieweeAlias }}</small>
              </td>
              <td>{{ formatNumber(item.score) }}</td>
              <td>{{ formatDateTime(item.submittedAt) }}</td>
              <td v-if="showComments" class="comment">{{ item.comment || '-' }}</td>
            </tr>
            <tr v-if="!filteredReviews.length && !loading">
              <td :colspan="showComments ? 5 : 4" class="empty">
                {{ t('暂无互评数据，请尝试切换小组或调整关键词。', 'No peer review data yet. Try switching groups or adjusting the keyword.') }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </TeacherSubviewShell>
</template>

<style scoped>
.card { background: var(--teacher-surface); border-radius: var(--teacher-radius-card); box-shadow: var(--teacher-shadow); }
.secondary-btn,.back-btn,.ghost-btn { height: 40px; border-radius: 10px; padding: 0 14px; border: 0; cursor: pointer; font-weight: 600; }
.secondary-btn { background: var(--teacher-surface-muted); color: var(--teacher-text-primary); }
.ghost-btn { background: var(--teacher-accent-soft); color: var(--teacher-accent); }
.back-btn { background: var(--teacher-surface); border: 1px solid var(--teacher-border); color: var(--teacher-text-primary); }
.secondary-btn:disabled,.ghost-btn:disabled,.back-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.panel { margin-top: 14px; padding: 16px; }
.filters { display: flex; gap: 12px; align-items: end; flex-wrap: wrap; }
label { display: grid; gap: 8px; color: var(--teacher-text-secondary); font-size: 13px; min-width: 220px; }
select, input { height: 40px; border: 1px solid var(--teacher-border); border-radius: var(--teacher-radius-control); padding: 0 10px; background: var(--teacher-surface); color: var(--teacher-text-primary); }
.peer-overview {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
}
.stat,
.empty-state {
  border: 1px solid var(--teacher-divider);
  border-radius: 10px;
  padding: 12px;
}
.empty-state {
  margin-top: 12px;
  color: var(--teacher-text-tertiary);
}
.label { margin: 0; font-size: 12px; color: var(--teacher-text-tertiary); }
.value { margin: 8px 0 0; font-size: 18px; font-weight: 600; color: var(--teacher-text-primary); }
.stat small {
  display: block;
  margin-top: 4px;
  color: var(--teacher-text-tertiary);
  font-size: 12px;
}
.peer-note {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--teacher-divider);
  border-radius: 10px;
  background: var(--teacher-surface-muted);
  color: var(--teacher-text-secondary);
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
  line-height: 1.6;
}
.peer-note strong {
  flex: none;
  color: var(--teacher-text-primary);
}
.table-wrap { margin-top: 12px; overflow-x: auto; }
table { width: 100%; border-collapse: collapse; min-width: 720px; }
th,td { border-bottom: 1px solid var(--teacher-divider); text-align: left; padding: 12px 10px; font-size: 14px; vertical-align: top; }
th { color: var(--teacher-text-tertiary); font-weight: 600; }
.identity-name {
  display: block;
  color: var(--teacher-text-primary);
  font-weight: 700;
}
td small {
  display: block;
  margin-top: 4px;
  color: var(--teacher-text-tertiary);
  font-size: 12px;
}
.comment { max-width: 420px; color: var(--teacher-text-primary); white-space: pre-wrap; }
.empty { text-align: center; color: var(--teacher-text-tertiary); }

@media (max-width: 720px) {
  .peer-note {
    flex-direction: column;
  }
}
</style>
