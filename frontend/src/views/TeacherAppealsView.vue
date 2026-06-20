<script setup>
import EmptyState from '../components/common/EmptyState.vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTeacherLocale } from '../composables/useTeacherLocale'
import { fetchTeacherTaskAppeals, resolveTeacherTaskAppeal } from '../services/teacher'
import { buildTeacherTaskDetailLocation } from '../utils/teacherTaskNavigation'
import { formatAppealStatus, formatDateTime } from '../utils/teacher'

const route = useRoute()
const router = useRouter()
const { t, isEn } = useTeacherLocale()

const classId = computed(() => String(route.params.classId || ''))
const taskId = computed(() => String(route.params.taskId || ''))

const loading = ref(false)
const appeals = ref([])
const message = ref('')
const messageType = ref('info')

const showResolve = ref(false)
const resolveItem = ref(null)
const resolveOutcome = ref('approve')
const resolveResponse = ref('')
const resolveAdjustedScore = ref('')
const resolveBusy = ref(false)
const resolveError = ref('')

function typeLabel(type) {
  const map = {
    teacher_score: t('教师评分', 'Teacher score'),
    peer_review: t('互评', 'Peer review'),
    task_review: t('任务评审', 'Task review'),
  }
  return map[type] || type || t('教师评分', 'Teacher score')
}

function goBack() {
  router.push(buildTeacherTaskDetailLocation(classId.value, taskId.value, route.query, ''))
}

async function loadAppeals() {
  loading.value = true
  try {
    const res = await fetchTeacherTaskAppeals(classId.value, taskId.value)
    const list = res?.data?.data || []
    appeals.value = list.map((a) => ({
      id: a?.appealId || a?.id,
      studentId: a?.studentId,
      type: a?.type || 'teacher_score',
      reason: a?.reason || '',
      status: a?.status ?? 0,
      teacherResponse: a?.teacherResponse || '',
      handledAt: a?.handledAt || '',
      createdAt: a?.createdAt || '',
      createdAtText: formatDateTime(a?.createdAt),
    }))
  } catch {
    message.value = t('加载申诉失败', 'Failed to load appeals')
    messageType.value = 'error'
  } finally {
    loading.value = false
  }
}

function openResolve(item) {
  resolveItem.value = item
  resolveOutcome.value = 'approve'
  resolveResponse.value = ''
  resolveAdjustedScore.value = ''
  resolveError.value = ''
  showResolve.value = true
}

function closeResolve() {
  showResolve.value = false
  resolveItem.value = null
}

async function submitResolve() {
  if (!resolveItem.value) return
  resolveBusy.value = true
  resolveError.value = ''
  try {
    const payload = {
      outcome: resolveOutcome.value,
      teacherResponse: resolveResponse.value,
    }
    if (resolveOutcome.value === 'approve' && resolveAdjustedScore.value) {
      payload.adjustedScore = Number(resolveAdjustedScore.value)
    }
    await resolveTeacherTaskAppeal(classId.value, taskId.value, resolveItem.value.id, payload)
    closeResolve()
    await loadAppeals()
  } catch (e) {
    resolveError.value = e?.response?.data?.message || e?.message || t('处理失败', 'Failed to resolve')
  } finally {
    resolveBusy.value = false
  }
}

watch([classId, taskId], ([nextClassId, nextTaskId], [prevClassId, prevTaskId]) => {
  if (
    (nextClassId && nextClassId !== prevClassId) ||
    (nextTaskId && nextTaskId !== prevTaskId)
  ) {
    appeals.value = []
    loadAppeals()
  }
})
watch(isEn, loadAppeals)
onMounted(loadAppeals)
</script>

<template>
  <div class="appeals-page">
    <header class="page-head">
      <div>
        <button class="back-link" type="button" @click="goBack">← {{ t('返回任务详情', 'Back to task') }}</button>
        <h2>{{ t('任务申诉', 'Task appeals') }}</h2>
      </div>
    </header>

    <p v-if="message" class="status-msg" :class="messageType">{{ message }}</p>

    <div v-if="loading" class="loading-msg">{{ t('加载中…', 'Loading…') }}</div>

    <div v-else-if="appeals.length === 0" class="empty-msg">
      {{ t('暂无申诉记录', 'No appeals yet') }}
    </div>

    <div v-else class="appeals-table-wrap">
      <table class="appeals-table">
        <thead>
          <tr>
            <th>{{ t('学生', 'Student') }}</th>
            <th>{{ t('类型', 'Type') }}</th>
            <th>{{ t('申诉理由', 'Reason') }}</th>
            <th>{{ t('状态', 'Status') }}</th>
            <th>{{ t('提交时间', 'Submitted') }}</th>
            <th>{{ t('操作', 'Actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in appeals" :key="item.id">
            <td>{{ item.studentId }}</td>
            <td>{{ typeLabel(item.type) }}</td>
            <td class="reason-cell">{{ item.reason }}</td>
            <td>
              <span class="status-tag" :class="item.status === 0 ? 'pending' : 'done'">
                {{ formatAppealStatus(item.status) }}
              </span>
            </td>
            <td>{{ item.createdAtText }}</td>
            <td>
              <button
                v-if="item.status === 0"
                class="action-btn"
                type="button"
                @click="openResolve(item)"
              >{{ t('处理', 'Resolve') }}</button>
              <span v-else class="resolved-text">{{ item.teacherResponse || '—' }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <Teleport to="body">
      <div v-if="showResolve" class="dialog-overlay" @click.self="closeResolve">
        <div class="dialog-panel">
          <h3>{{ t('处理申诉', 'Resolve appeal') }}</h3>
          <p class="dialog-info">
            {{ t('类型', 'Type') }}：{{ typeLabel(resolveItem?.type) }} | {{ t('理由', 'Reason') }}：{{ resolveItem?.reason }}
          </p>

          <label class="field">
            <span>{{ t('处理结果', 'Outcome') }}</span>
            <select v-model="resolveOutcome" class="select">
              <option value="approve">{{ t('通过申诉', 'Approve') }}</option>
              <option value="reject">{{ t('驳回申诉', 'Reject') }}</option>
            </select>
          </label>

          <label v-if="resolveOutcome === 'approve'" class="field">
            <span>{{ t('调整后分数（可选）', 'Adjusted score (optional)') }}</span>
            <input v-model="resolveAdjustedScore" class="input" type="number" min="0" max="100" :placeholder="t('留空则不调整分数', 'Leave blank to keep score')" />
          </label>

          <label class="field">
            <span>{{ t('回复内容', 'Response') }}</span>
            <textarea v-model="resolveResponse" class="textarea" rows="3" :placeholder="t('请输入回复内容…', 'Enter your response…')"></textarea>
          </label>

          <p v-if="resolveError" class="error-msg">{{ resolveError }}</p>

          <div class="dialog-actions">
            <button class="btn-cancel" type="button" @click="closeResolve">{{ t('取消', 'Cancel') }}</button>
            <button class="btn-confirm" type="button" :disabled="resolveBusy" @click="submitResolve">
              {{ resolveBusy ? t('提交中…', 'Submitting…') : t('提交处理', 'Submit') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.appeals-page { display: flex; flex-direction: column; gap: 16px; }
h2 { margin: 0; font-size: 20px; font-weight: 800; color: var(--teacher-text-primary); }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.back-link { font-size: 13px; color: var(--teacher-accent); font-weight: 600; background: none; border: none; cursor: pointer; padding: 0; margin-bottom: 6px; }
.refresh-btn,.action-btn,.btn-cancel,.btn-confirm { font-family: inherit; cursor: pointer; border: none; font-weight: 600; border-radius: 10px; }
.refresh-btn { height: 34px; padding: 0 14px; font-size: 12px; background: var(--teacher-surface-muted); color: var(--teacher-text-secondary); border: 1px solid var(--teacher-border); }
.action-btn { padding: 6px 14px; font-size: 12px; background: var(--teacher-accent); color: var(--tt-text-inverse); }
.status-msg { font-size: 13px; padding: 8px 12px; border-radius: 8px; }
.status-msg.error { color: var(--teacher-danger); background: var(--tt-danger-soft); }
.loading-msg,.empty-msg { text-align: center; padding: 40px; color: var(--teacher-text-secondary); font-size: 14px; }
.appeals-table-wrap { overflow-x: auto; }
.appeals-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.appeals-table th { text-align: left; padding: 10px 12px; color: var(--teacher-text-tertiary); font-weight: 600; border-bottom: 2px solid var(--teacher-divider); white-space: nowrap; }
.appeals-table td { padding: 10px 12px; border-bottom: 1px solid var(--teacher-divider); color: var(--teacher-text-primary); }
.reason-cell { max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.status-tag { font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 8px; }
.status-tag.pending { color: var(--tt-danger); background: var(--tt-danger-soft); }
.status-tag.done { color: var(--tt-success); background: var(--tt-success-soft); }
.resolved-text { font-size: 12px; color: var(--teacher-text-secondary); }

.dialog-overlay { /* positioning handled by teacher-dialog.css */ }
.dialog-panel { width: 90%; max-width: 480px; padding: 24px; border-radius: 20px; background: var(--tt-bg-elevated); box-shadow: var(--tt-shadow-xl); border: 1px solid var(--tt-border); display: flex; flex-direction: column; gap: 14px; }
.dialog-panel h3 { margin: 0; font-size: 18px; font-weight: 800; }
.dialog-info { font-size: 13px; color: var(--teacher-text-secondary); line-height: 1.5; }
.field { display: flex; flex-direction: column; gap: 6px; font-size: 13px; font-weight: 600; color: var(--teacher-text-primary); }
.select,.input,.textarea { padding: 10px 12px; border: 1px solid var(--teacher-border); border-radius: 10px; font-size: 14px; font-family: inherit; color: var(--teacher-text-primary); background: var(--tt-surface-muted); }
.textarea { resize: vertical; min-height: 72px; }
.error-msg { font-size: 12px; color: var(--tt-danger); }
.dialog-actions { display: flex; justify-content: flex-end; gap: 10px; }
.btn-cancel { height: 38px; padding: 0 18px; background: var(--teacher-surface-muted); color: var(--teacher-text-secondary); border: 1px solid var(--teacher-border); }
.btn-confirm { height: 38px; padding: 0 18px; background: var(--teacher-accent); color: var(--tt-text-inverse); }
.btn-confirm:disabled { opacity: 0.5; }
</style>
