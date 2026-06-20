<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TeacherSubviewShell from '../components/teacher/TeacherSubviewShell.vue'
import { useTeacherLocale } from '../composables/useTeacherLocale'
import { fetchTeacherClassGroups, fetchTeacherGroupScoreSummaries, saveTeacherScore } from '../services/teacher'
import { buildTeacherTaskDetailLocation } from '../utils/teacherTaskNavigation'

const { t, tm } = useTeacherLocale()

const route = useRoute()
const router = useRouter()
const classId = computed(() => route.params.classId)
const taskId = computed(() => route.params.taskId)

const groups = ref([])
const selectedGroupId = ref('')
const summaries = ref([])
const loading = ref(false)
const loadingGroups = ref(false)
const message = ref('')
const messageType = ref('info')
const keyword = ref('')
const sortMode = ref('teacher_desc')

function goBackToTaskDetail() {
  router.push(buildTeacherTaskDetailLocation(classId.value, taskId.value, route.query, route.query?.from || ''))
}

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

function formatNumber(value, digits = 2) {
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

function normalizeSummary(raw) {
  return {
    studentId: raw?.studentId ?? '-',
    peerReviewApplicable: Boolean(raw?.peerReviewApplicable),
    peerReviewWeight: raw?.peerReviewWeight,
    teacherScoreWeight: raw?.teacherScoreWeight,
    peerReviewMaxScore: raw?.peerReviewMaxScore,
    peerAverageReceived: raw?.peerAverageReceived,
    peerAverageOn100: raw?.peerAverageOn100,
    teacherScore: raw?.teacherScore,
    weightedTotal100: raw?.weightedTotal100,
  }
}

const selectedGroupMeta = computed(() => {
  return groups.value.find((item) => item.groupId === selectedGroupId.value) || null
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
    summaries.value = []
    setMessage(error.message || t('加载小组列表失败，请稍后重试。', 'Failed to load the group list. Please try again later.'), 'error')
  } finally {
    loadingGroups.value = false
  }
}

async function loadSummaries() {
  if (!selectedGroupId.value) {
    summaries.value = []
    return
  }

  loading.value = true
  try {
    const { data } = await fetchTeacherGroupScoreSummaries(classId.value, taskId.value, selectedGroupId.value)
    summaries.value = Array.isArray(data?.data) ? data.data.map(normalizeSummary) : []
    setMessage('')
  } catch (error) {
    summaries.value = []
    setMessage(error.message || t('加载成绩汇总失败，请稍后重试。', 'Failed to load score summaries. Please try again later.'), 'error')
  } finally {
    loading.value = false
  }
}

async function refreshAll() {
  summaries.value = []
  await loadGroups()
  if (selectedGroupId.value) {
    await loadSummaries()
  }
}

const filteredSummaries = computed(() => {
  const term = keyword.value.trim().toLowerCase()
  let list = summaries.value
  if (term) {
    list = list.filter((item) => String(item.studentId || '').toLowerCase().includes(term))
  }

  const getNumber = (value) => {
    const parsed = Number(value)
    return Number.isNaN(parsed) ? null : parsed
  }

  const sorted = [...list]
  if (sortMode.value === 'weighted_desc') {
    sorted.sort((a, b) => (getNumber(b.weightedTotal100) ?? -Infinity) - (getNumber(a.weightedTotal100) ?? -Infinity))
  } else if (sortMode.value === 'weighted_asc') {
    sorted.sort((a, b) => (getNumber(a.weightedTotal100) ?? Infinity) - (getNumber(b.weightedTotal100) ?? Infinity))
  } else if (sortMode.value === 'peer_desc') {
    sorted.sort((a, b) => (getNumber(b.peerAverageOn100) ?? -Infinity) - (getNumber(a.peerAverageOn100) ?? -Infinity))
  } else if (sortMode.value === 'teacher_desc') {
    sorted.sort((a, b) => (getNumber(b.teacherScore) ?? -Infinity) - (getNumber(a.teacherScore) ?? -Infinity))
  } else if (sortMode.value === 'student') {
    sorted.sort((a, b) => String(a.studentId).localeCompare(String(b.studentId), 'zh-CN'))
  }
  return sorted
})

const stats = computed(() => {
  const totals = filteredSummaries.value
    .map((item) => Number(item.weightedTotal100))
    .filter((value) => !Number.isNaN(value))
  const count = filteredSummaries.value.length

  const teacherCount = filteredSummaries.value.filter((item) => item.teacherScore !== null && item.teacherScore !== undefined).length
  const peerCount = filteredSummaries.value.filter((item) => item.peerAverageOn100 !== null && item.peerAverageOn100 !== undefined).length

  if (!totals.length) {
    return { count, avg: '-', min: '-', max: '-', teacherCount, peerCount }
  }

  const sum = totals.reduce((total, current) => total + current, 0)
  const min = Math.min(...totals)
  const max = Math.max(...totals)
  return {
    count,
    avg: formatNumber(sum / totals.length),
    min: formatNumber(min),
    max: formatNumber(max),
    teacherCount,
    peerCount,
  }
})

const scoreRuleText = computed(() => {
  const sample = filteredSummaries.value[0] || summaries.value[0]
  if (!sample) {
    return t('当前小组还没有可展示的成绩记录。', 'There are no score records to display yet.')
  }

  if (!sample.peerReviewApplicable) {
    return t('当前小组不适用互评，仅展示教师评分记录；系统不自动生成最终成绩。', 'Peer review is not applicable for this group; only teacher score records are displayed. The system does not generate final grades.')
  }

  return t(
    '互评均分与教师评分作为独立记录展示；参考汇总仅用于快速查看，不代表课程最终成绩。互评原始满分 {max}。',
    'Peer averages and teacher scores are shown as separate records. The reference summary is for quick review only and is not a final course grade. Original peer review max: {max}.',
    {
      max: sample.peerReviewMaxScore ?? '-',
    },
  )
})

/* ── inline score editing ── */
const editingId = ref('')
const editScore = ref('')
const savingScore = ref(false)
const savedId = ref('')

function startEdit(item) {
  editingId.value = item.studentId
  editScore.value = item.teacherScore !== null && item.teacherScore !== undefined ? String(item.teacherScore) : ''
}

function cancelEdit() {
  editingId.value = ''
  editScore.value = ''
}

async function submitScore(studentId) {
  const score = Number(editScore.value)
  if (Number.isNaN(score) || score < 0 || score > 100) return
  savingScore.value = true
  try {
    await saveTeacherScore(classId.value, taskId.value, { studentId, score })
    savedId.value = studentId
    setTimeout(() => { savedId.value = '' }, 1500)
    await loadSummaries()
    editingId.value = ''
  } catch (e) {
    setMessage(e?.response?.data?.message || tm('common.saveFailed'), 'error')
  } finally {
    savingScore.value = false
  }
}

watch([classId, taskId], () => {
  selectedGroupId.value = ''
  groups.value = []
  summaries.value = []
  refreshAll()
}, { immediate: true })
</script>

<template>
  <TeacherSubviewShell :title="tm('scoreSummaries.title')" :message="message" :message-type="messageType">
    <template #actions>
      <button class="back-btn" type="button" @click="goBackToTaskDetail">
        {{ tm('common.back') }}
      </button>
    </template>

    <section class="card panel">
      <div class="filters">
        <label>
          <span>{{ tm('common.group') }}</span>
          <select v-model="selectedGroupId" :disabled="loadingGroups || !groups.length" @change="loadSummaries">
            <option value="" disabled>{{ t('请选择小组', 'Select a group') }}</option>
            <option v-for="g in groups" :key="g.groupId" :value="g.groupId">
              {{ g.name }}（{{ g.memberCount }} {{ t('人', 'members') }}）
            </option>
          </select>
        </label>

        <label>
          <span>{{ t('学号 / ID 关键词', 'Student ID / keyword') }}</span>
          <input v-model.trim="keyword" type="text" :placeholder="t('例如：2026001', 'e.g. 2026001')" />
        </label>

        <label>
          <span>{{ tm('common.status') }}</span>
          <select v-model="sortMode">
            <option value="teacher_desc">{{ t('教师评分从高到低', 'Teacher score high to low') }}</option>
            <option value="peer_desc">{{ t('互评分从高到低', 'Peer score high to low') }}</option>
            <option value="weighted_desc">{{ t('参考汇总从高到低', 'Reference summary high to low') }}</option>
            <option value="weighted_asc">{{ t('参考汇总从低到高', 'Reference summary low to high') }}</option>
            <option value="student">{{ t('按学生 ID', 'By student ID') }}</option>
          </select>
        </label>

        <button class="secondary-btn" type="button" :disabled="loading || !selectedGroupId" @click="loadSummaries">
          {{ loading ? tm('common.loading') : tm('common.refresh') }}
        </button>
      </div>

      <div v-if="!groups.length && !loadingGroups" class="empty-state">
        {{ t('当前班级还没有学期小组，暂时无法查看成绩汇总。', 'There are no semester groups in this class yet, so score summaries are unavailable.') }}
      </div>

      <template v-else-if="groups.length">
        <article class="rule-card">
          <p class="label">{{ t('评分口径', 'Scoring approach') }}</p>
          <p class="rule-text">{{ scoreRuleText }}</p>
        </article>

        <div class="stats">
          <div class="stat">
            <p class="label">{{ t('当前小组', 'Current group') }}</p>
            <p class="value">{{ selectedGroupMeta?.name || '-' }}</p>
          </div>
          <div class="stat">
            <p class="label">{{ t('学生数', 'Students') }}</p>
            <p class="value">{{ stats.count }}</p>
          </div>
          <div class="stat">
            <p class="label">{{ t('平均参考汇总', 'Average reference summary') }}</p>
            <p class="value">{{ stats.avg }}</p>
          </div>
          <div class="stat">
            <p class="label">{{ t('参考汇总最低 / 最高', 'Reference min / max') }}</p>
            <p class="value">{{ stats.min }} / {{ stats.max }}</p>
          </div>
          <div class="stat">
            <p class="label">{{ t('有教师分 / 有互评分', 'With teacher / peer scores') }}</p>
            <p class="value">{{ stats.teacherCount }} / {{ stats.peerCount }}</p>
          </div>
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ tm('scoreSummaries.studentId') }}</th>
                <th>{{ t('互评适用', 'Peer review applicable') }}</th>
                <th>{{ t('收到互评均分', 'Average peer review received') }}</th>
                <th>{{ t('折算后互评分', 'Scaled peer score') }}</th>
                <th>{{ tm('common.teacherScore') }}</th>
                <th>{{ t('参考汇总', 'Reference summary') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, idx) in filteredSummaries" :key="item.studentId || idx">
                <td>{{ item.studentId ?? '-' }}</td>
                <td>{{ item.peerReviewApplicable ? tm('common.yes') : tm('common.no') }}</td>
                <td>
                  {{ formatNumber(item.peerAverageReceived) }}
                  <span class="suffix">/ {{ item.peerReviewMaxScore ?? '-' }}</span>
                </td>
                <td>{{ formatNumber(item.peerAverageOn100) }}</td>
                <td class="score-cell">
                  <template v-if="editingId === item.studentId">
                    <input
                      v-model="editScore"
                      class="score-input"
                      type="number"
                      min="0"
                      max="100"
                      :disabled="savingScore"
                      @keyup.enter="submitScore(item.studentId)"
                      @keyup.escape="cancelEdit()"
                      @blur="submitScore(item.studentId)"
                    />
                    <span v-if="savingScore" class="saving-hint">{{ tm('common.loading') }}</span>
                  </template>
                  <button
                    v-else
                    class="score-display"
                    type="button"
                    @click="startEdit(item)"
                    :title="item.teacherScore != null ? tm('scoreSummaries.clickEdit') : tm('scoreSummaries.clickScore')"
                  >
                    <span v-if="savedId === item.studentId" class="saved-check">✅</span>
                    {{ item.teacherScore != null ? formatNumber(item.teacherScore) : tm('scoreSummaries.clickScore') }}
                  </button>
                </td>
                <td>{{ formatNumber(item.weightedTotal100) }}</td>
              </tr>
              <tr v-if="!filteredSummaries.length && !loading">
                <td colspan="6" class="empty">{{ t('暂无成绩汇总数据，请尝试切换小组或调整关键词。', 'No score summaries yet. Try switching groups or adjusting the keyword.') }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </section>
  </TeacherSubviewShell>
</template>

<style scoped>
.card { background: var(--teacher-surface); border-radius: var(--teacher-radius-card); box-shadow: var(--teacher-shadow); }
.secondary-btn,.back-btn { height: 40px; border-radius: 10px; padding: 0 14px; border: 0; cursor: pointer; font-weight: 600; }
.secondary-btn { background: var(--teacher-surface-muted); color: var(--teacher-text-primary); }
.back-btn { background: var(--teacher-surface); border: 1px solid var(--teacher-border); color: var(--teacher-text-primary); }
.secondary-btn:disabled,.back-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.panel { margin-top: 14px; padding: 16px; }
.filters { display: flex; gap: 12px; align-items: end; flex-wrap: wrap; }
label { display: grid; gap: 8px; color: var(--teacher-text-secondary); font-size: 13px; min-width: 220px; }
select, input { height: 40px; border: 1px solid var(--teacher-border); border-radius: var(--teacher-radius-control); padding: 0 10px; background: var(--teacher-surface); color: var(--teacher-text-primary); }
.rule-card,
.stat,
.empty-state {
  border: 1px solid var(--teacher-divider);
  border-radius: 10px;
  padding: 12px;
}
.rule-card { margin-top: 12px; }
.rule-text { margin: 8px 0 0; color: var(--teacher-text-primary); line-height: 1.7; }
.stats {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}
.label { margin: 0; font-size: 12px; color: var(--teacher-text-tertiary); }
.value { margin: 8px 0 0; font-size: 18px; font-weight: 600; color: var(--teacher-text-primary); }
.suffix { font-size: 12px; color: var(--teacher-text-tertiary); }
.empty-state { margin-top: 12px; color: var(--teacher-text-tertiary); }
.table-wrap { margin-top: 12px; overflow-x: auto; }
table { width: 100%; border-collapse: collapse; min-width: 760px; }
th,td { border-bottom: 1px solid var(--teacher-divider); text-align: left; padding: 12px 10px; font-size: 14px; vertical-align: top; }
th { color: var(--teacher-text-tertiary); font-weight: 600; }
.empty { text-align: center; color: var(--teacher-text-tertiary); }

/* ═══ INLINE SCORE ═══ */
.score-cell { min-width: 100px; }
.score-display {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 8px;
  border: 1px dashed transparent;
  background: transparent;
  font-size: 14px;
  font-weight: 600;
  color: var(--teacher-text-primary);
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
  font-family: inherit;
}
.score-display:hover {
  border-color: var(--teacher-accent);
  background: rgba(36,86,173,0.04);
}
.score-input {
  width: 72px;
  height: 32px;
  padding: 0 8px;
  border: 2px solid var(--teacher-accent);
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--teacher-text-primary);
  background: #fff;
  outline: none;
  text-align: center;
  -moz-appearance: textfield;
}
.score-input::-webkit-inner-spin-button,
.score-input::-webkit-outer-spin-button { -webkit-appearance: none; margin: 0; }
.saving-hint { font-size: 12px; color: var(--teacher-text-tertiary); margin-left: 4px; }
.saved-check { font-size: 14px; }
</style>
