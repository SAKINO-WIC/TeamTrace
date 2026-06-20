<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TeacherSubviewShell from '../components/teacher/TeacherSubviewShell.vue'
import { useTeacherLocale } from '../composables/useTeacherLocale'
import { fetchTeacherClassGroups, fetchTeacherGroupSubtaskProgress, fetchTeacherGroupSubtasks } from '../services/teacher'
import { canPreviewMedia, downloadMediaFile, resolveMediaUrl } from '../utils/mediaUrl'
import { parseSubmissionContent } from '../utils/submissionAttachments'
import { formatDateTime } from '../utils/teacher'
import { buildTeacherTaskDetailLocation } from '../utils/teacherTaskNavigation'

const { t, tm } = useTeacherLocale()

const route = useRoute()
const router = useRouter()

const loadingGroups = ref(false)
const loadingProgress = ref(false)
const loadingSubtasks = ref(false)
const message = ref('')
const messageType = ref('info')
const groups = ref([])
const selectedGroupId = ref('')
const progress = ref(null)

const selectedGroup = ref(null)
const selectedMember = ref(null)
const memberTasks = ref([])
const groupSubtasks = ref([])
const selectedSubtaskDetail = ref(null)

const classId = computed(() => route.params.classId)
const taskId = computed(() => route.params.taskId)

const statusMap = {
  done: t('已完成', 'Completed'),
  doing: t('进行中', 'In progress'),
  not_started: t('未开始', 'Not started'),
}

const subtaskStatusMap = {
  1: t('待认领', 'Unclaimed'),
  2: t('进行中', 'In progress'),
  3: t('待审批', 'Pending review'),
  4: t('已完成', 'Completed'),
}

function goBackToTaskDetail() {
  router.push(buildTeacherTaskDetailLocation(classId.value, taskId.value, route.query, route.query?.from || ''))
}

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

function formatNumber(value, digits = 2) {
  if (value === null || value === undefined || value === '') return '-'
  const parsed = Number(value)
  if (Number.isNaN(parsed)) return '-'
  return parsed.toFixed(digits).replace(/\.0+$/, '').replace(/(\.\d*[1-9])0+$/, '$1')
}

function formatPercent(value) {
  if (value === null || value === undefined || value === '') return '-'
  return `${formatNumber(value)}%`
}

function normalizeGroup(raw) {
  const memberIds = Array.isArray(raw?.memberStudentIds) ? raw.memberStudentIds : []
  return {
    groupId: raw?.groupId ? String(raw.groupId) : '',
    name: raw?.name ?? raw?.groupName ?? t('未命名小组', 'Unnamed group'),
    memberCount: raw?.memberCount ?? memberIds.length,
    memberIds: memberIds.map(String),
    risk: raw?.risk || (raw?.memberCount > 5 ? 'high' : 'medium'),
  }
}

function normalizeProgress(raw) {
  const members = Array.isArray(raw?.members)
    ? raw.members.map((item) => ({
        studentId: String(item?.studentId ?? '-'),
        name: item?.studentName || item?.name || String(item?.studentId ?? '-'),
        claimedSubtasks: Number(item?.claimedSubtasks ?? 0),
        completedSubtasks: Number(item?.completedSubtasks ?? 0),
        progressValue: Number(item?.progressPercent ?? 0),
        progressPercent: formatPercent(item?.progressPercent),
      }))
    : []

  return {
    groupId: raw?.groupId ? String(raw.groupId) : selectedGroupId.value,
    groupClaimedSubtasks: raw?.groupClaimedSubtasks ?? 0,
    groupCompletedSubtasks: raw?.groupCompletedSubtasks ?? 0,
    groupProgressPercent: formatPercent(raw?.groupProgressPercent),
    members,
  }
}

const selectedGroupMeta = computed(() => {
  return groups.value.find((item) => item.groupId === selectedGroupId.value) || null
})

const memberOptions = computed(() => {
  if (progress.value?.members?.length) {
    return progress.value.members.map((member) => ({
      ...member,
      id: member.studentId,
      taskTitle: `${member.completedSubtasks}/${member.claimedSubtasks} ${t('个子任务', 'subtasks')}`,
      taskStatus:
        member.claimedSubtasks > 0 && member.completedSubtasks === member.claimedSubtasks
          ? 'done'
          : member.claimedSubtasks > 0
            ? 'doing'
            : 'not_started',
    }))
  }
  return (selectedGroup.value?.memberIds || []).map((id) => ({
    id,
    studentId: id,
    name: `${t('学生', 'Student')} ${id}`,
    claimedSubtasks: 0,
    completedSubtasks: 0,
    progressValue: 0,
    progressPercent: '0%',
    taskTitle: t('暂无子任务', 'No subtasks'),
    taskStatus: 'not_started',
  }))
})

async function loadGroups() {
  loadingGroups.value = true
  try {
    const { data } = await fetchTeacherClassGroups(classId.value)
    const payload = Array.isArray(data?.data) ? data.data.map(normalizeGroup) : []
    groups.value = payload

    const hasCurrentSelection = payload.some((item) => item.groupId === selectedGroupId.value)
    if (!hasCurrentSelection && payload.length) {
      changeGroup(payload[0])
    }

    if (!payload.length) {
      setMessage('')
    }
  } catch (error) {
    groups.value = []
    selectedGroupId.value = ''
    selectedGroup.value = null
    progress.value = null
    setMessage(error.message || t('加载小组列表失败，请稍后重试。', 'Failed to load the group list. Please try again later.'), 'error')
  } finally {
    loadingGroups.value = false
  }
}

async function loadProgress() {
  if (!selectedGroupId.value) {
    progress.value = null
    return
  }

  loadingProgress.value = true
  try {
    const { data } = await fetchTeacherGroupSubtaskProgress(classId.value, taskId.value, selectedGroupId.value)
    progress.value = normalizeProgress(data?.data || {})
  } catch (error) {
    progress.value = null
    setMessage(error.message || t('加载小组子任务进度失败，请稍后重试。', 'Failed to load group subtask progress. Please try again later.'), 'error')
  } finally {
    loadingProgress.value = false
  }
}

function normalizeSubmissionHistory(raw, subtaskId) {
  const parsedSubmission = parseSubmissionContent(raw?.submissionContent)
  return {
    id: String(raw?.id ?? `${subtaskId}-${raw?.versionNo ?? ''}`),
    versionNo: Number(raw?.versionNo ?? 0),
    submittedAtText: formatDateTime(raw?.submittedAt),
    parsedSubmission,
    current: Boolean(raw?.current),
  }
}

function normalizeSubtask(raw) {
  const statusCode = Number(raw?.status ?? 0)
  const assigneeId = raw?.assigneeId != null ? String(raw.assigneeId) : ''
  const parsedSubmission = parseSubmissionContent(raw?.submissionContent)
  const histories = Array.isArray(raw?.submissionHistories)
    ? raw.submissionHistories.map((history) => normalizeSubmissionHistory(history, raw?.subtaskId ?? raw?.id ?? 'subtask'))
    : []
  const member = memberOptions.value.find((item) => String(item.studentId || item.id) === assigneeId)
  return {
    subtaskId: String(raw?.subtaskId ?? raw?.id ?? ''),
    name: raw?.name ?? t('未命名子任务', 'Unnamed subtask'),
    description: raw?.description ?? '',
    qualityRequirement: raw?.qualityRequirement ?? '',
    assigneeId,
    assigneeLabel: assigneeId ? (member?.name || assigneeId) : t('待认领', 'Unclaimed'),
    deadlineText: formatDateTime(raw?.deadline),
    submittedAtText: formatDateTime(raw?.submittedAt),
    reviewedAtText: formatDateTime(raw?.reviewedAt),
    statusCode,
    statusLabel: subtaskStatusMap[statusCode] || '-',
    parsedSubmission,
    submissionHistories: histories,
    reviewComment: raw?.reviewComment || '',
  }
}

function syncSelectedMemberTasks() {
  const selectedId = String(selectedMember.value?.studentId || selectedMember.value?.id || '')
  if (!selectedId) {
    memberTasks.value = []
    return
  }
  memberTasks.value = groupSubtasks.value.filter((item) => item.assigneeId === selectedId)
}

async function loadSubtasks() {
  if (!selectedGroupId.value || !classId.value || !taskId.value) {
    groupSubtasks.value = []
    memberTasks.value = []
    return
  }
  loadingSubtasks.value = true
  try {
    const { data } = await fetchTeacherGroupSubtasks(classId.value, taskId.value, selectedGroupId.value)
    const payload = Array.isArray(data?.data) ? data.data : []
    groupSubtasks.value = payload.map(normalizeSubtask)
    syncSelectedMemberTasks()
  } catch (error) {
    groupSubtasks.value = []
    memberTasks.value = []
    setMessage(error.message || t('加载子任务详情失败，请稍后重试。', 'Failed to load subtasks. Please try again later.'), 'error')
  } finally {
    loadingSubtasks.value = false
  }
}

// 切换小组
async function changeGroup(group) {
  selectedGroupId.value = group.groupId
  selectedGroup.value = group
  selectedMember.value = null
  memberTasks.value = []
  groupSubtasks.value = []
  selectedSubtaskDetail.value = null
  await loadProgress()
  await loadSubtasks()
}

// 选择组员
function selectMember(member) {
  selectedMember.value = member
  syncSelectedMemberTasks()
}

function openSubtaskDetail(task) {
  selectedSubtaskDetail.value = task
}

function closeSubtaskDetail() {
  selectedSubtaskDetail.value = null
}

function statusTagType(code) {
  const parsed = Number(code)
  if (parsed === 4) return 'success'
  if (parsed === 3) return 'warning'
  if (parsed === 2) return 'primary'
  return 'info'
}

function displayLinkText(value) {
  return String(value || '').trim() || t('链接', 'Link')
}

function canPreviewFile(file) {
  return canPreviewMedia(file?.value || file?.url)
}

function openFile(file) {
  const url = resolveMediaUrl(file?.value || file?.url)
  if (!url) return
  window.open(url, '_blank', 'noopener,noreferrer')
}

async function downloadFile(file) {
  try {
    await downloadMediaFile(file?.value || file?.url, file?.name || t('附件', 'Attachment'))
  } catch (error) {
    setMessage(error.message || t('文件下载失败，请稍后重试。', 'Download failed. Please try again later.'), 'error')
  }
}

watch([classId, taskId], () => {
  selectedGroupId.value = ''
  selectedGroup.value = null
  selectedMember.value = null
  memberTasks.value = []
  groupSubtasks.value = []
  selectedSubtaskDetail.value = null
  groups.value = []
  progress.value = null
  loadGroups()
}, { immediate: true })
</script>

<template>
  <TeacherSubviewShell :title="tm('taskProgress.title')" :message="message" :message-type="messageType">
    <template #actions>
      <button class="back-btn" type="button" @click="goBackToTaskDetail">
        {{ tm('common.back') }}
      </button>
    </template>

    <div class="progress-layout">
      <!-- 左侧：小组处理队列 -->
      <el-card class="queue-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <span>{{ t('小组处理队列', 'Group processing queue') }}</span>
            <el-tag size="small">{{ groups.length }} {{ t('组', 'groups') }}</el-tag>
          </div>
        </template>
        <div v-if="loadingGroups" class="loading-hint">{{ tm('common.loading') }}</div>
        <div v-else-if="!groups.length" class="empty-hint">{{ tm('common.noData') }}</div>
        <div v-else class="group-list">
          <div
            v-for="group in groups"
            :key="group.groupId"
            class="group-item"
            :class="{ active: selectedGroup?.groupId === group.groupId }"
            @click="changeGroup(group)"
          >
            <div class="group-item-main">
              <span class="group-name">{{ group.name }}</span>
              <span class="group-count">{{ group.memberCount }} {{ t('人', 'members') }}</span>
            </div>
            <el-tag :type="group.risk === 'high' ? 'danger' : 'warning'" size="small">
              {{ group.risk === 'high' ? t('高风险', 'High risk') : t('中风险', 'Medium risk') }}
            </el-tag>
          </div>
        </div>
      </el-card>

      <!-- 中间：组员任务状态 -->
      <el-card class="member-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <span>{{ selectedGroup ? `${selectedGroup.name} - ${t('组员任务状态', 'Member task status')}` : t('请选择一个小组', 'Please select a group') }}</span>
          </div>
        </template>
        <div v-if="selectedGroup" class="member-list">
          <div v-if="loadingProgress" class="loading-hint">{{ t('加载组员数据中...', 'Loading member data...') }}</div>
          <template v-else>
            <div
              v-for="member in memberOptions"
              :key="member.studentId || member.id"
              class="member-item"
              :class="{ active: String(selectedMember?.studentId || selectedMember?.id || '') === String(member.studentId || member.id) }"
              @click="selectMember(member)"
            >
              <el-avatar :size="28">{{ (member.name || member.studentId || '?')[0] }}</el-avatar>
              <div class="member-info">
                <span class="member-name">{{ member.name || member.studentId }}</span>
                <span class="member-task">{{ member.taskTitle }}</span>
              </div>
              <el-tag
                :type="member.taskStatus === 'done' ? 'success' : member.taskStatus === 'doing' ? 'primary' : 'info'"
                size="small"
              >
                {{ statusMap[member.taskStatus] || member.taskStatus }}
              </el-tag>
            </div>
            <div v-if="!memberOptions.length" class="empty-hint">
              {{ t('当前小组暂无成员', 'This group has no members yet') }}
            </div>
          </template>
        </div>
        <div v-else class="empty-hint">{{ t('请先从左侧选择一个小组', 'Select a group from the left first') }}</div>
      </el-card>

      <!-- 右侧：子任务详情 -->
      <el-card class="subtask-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <span>{{ selectedMember ? `${selectedMember.name || selectedMember.studentId} ${t('的子任务', 'subtasks')}` : t('请选择一位组员', 'Please select a member') }}</span>
          </div>
        </template>
        <div v-if="selectedMember" class="subtask-list">
          <div v-if="loadingSubtasks" class="loading-hint">{{ t('加载子任务中...', 'Loading subtasks...') }}</div>
          <div v-else-if="!memberTasks.length" class="empty-hint">{{ t('暂无子任务', 'No subtasks') }}</div>
          <div v-else class="subtask-table">
            <div class="subtask-header">
              <span>{{ t('子任务名称', 'Subtask name') }}</span>
              <span>{{ t('认领人', 'Assignee') }}</span>
              <span>{{ tm('common.status') }}</span>
              <span>{{ t('提交时间', 'Submitted') }}</span>
              <span>{{ tm('common.deadline') }}</span>
            </div>
            <button
              v-for="task in memberTasks"
              :key="task.subtaskId"
              class="subtask-row subtask-row-button"
              type="button"
              @click="openSubtaskDetail(task)"
            >
              <span class="subtask-name">{{ task.name }}</span>
              <span>{{ task.assigneeLabel }}</span>
              <el-tag
                :type="statusTagType(task.statusCode)"
                size="small"
              >
                {{ task.statusLabel }}
              </el-tag>
              <span>{{ task.submittedAtText || '-' }}</span>
              <span>{{ task.deadlineText || '-' }}</span>
            </button>
          </div>
        </div>
        <div v-else class="empty-hint">{{ t('请先从中间列选择一位组员', 'Select a member from the middle column first') }}</div>
      </el-card>
    </div>

    <Teleport to="body">
      <div v-if="selectedSubtaskDetail" class="dialog-overlay subtask-detail-overlay" @click.self="closeSubtaskDetail">
        <section class="subtask-detail-modal" role="dialog" aria-modal="true">
          <header class="subtask-detail-head">
            <div>
              <p>{{ t('子任务详情', 'Subtask detail') }}</p>
              <h3>{{ selectedSubtaskDetail.name }}</h3>
            </div>
            <button class="modal-close-btn" type="button" @click="closeSubtaskDetail">关闭</button>
          </header>

          <div class="subtask-detail-body">
            <dl class="detail-meta-grid">
              <div>
                <dt>{{ t('认领人', 'Assignee') }}</dt>
                <dd>{{ selectedSubtaskDetail.assigneeLabel }}</dd>
              </div>
              <div>
                <dt>{{ tm('common.status') }}</dt>
                <dd>
                  <el-tag :type="statusTagType(selectedSubtaskDetail.statusCode)" size="small">
                    {{ selectedSubtaskDetail.statusLabel }}
                  </el-tag>
                </dd>
              </div>
              <div>
                <dt>{{ tm('common.deadline') }}</dt>
                <dd>{{ selectedSubtaskDetail.deadlineText || '-' }}</dd>
              </div>
              <div>
                <dt>{{ t('提交时间', 'Submitted') }}</dt>
                <dd>{{ selectedSubtaskDetail.submittedAtText || '-' }}</dd>
              </div>
              <div>
                <dt>{{ t('审批时间', 'Reviewed') }}</dt>
                <dd>{{ selectedSubtaskDetail.reviewedAtText || '-' }}</dd>
              </div>
              <div>
                <dt>{{ t('提交记录', 'Submission records') }}</dt>
                <dd>{{ selectedSubtaskDetail.submissionHistories?.length || 0 }} {{ t('次', 'records') }}</dd>
              </div>
            </dl>

            <section class="detail-section">
              <h4>{{ t('子任务说明', 'Subtask description') }}</h4>
              <p>{{ selectedSubtaskDetail.description || t('暂无说明', 'No description') }}</p>
            </section>

            <section class="detail-section">
              <h4>{{ t('完成说明', 'Submission note') }}</h4>
              <p>{{ selectedSubtaskDetail.parsedSubmission.text || t('暂无完成说明', 'No submission note') }}</p>
            </section>

            <section
              v-if="selectedSubtaskDetail.parsedSubmission.files?.length || selectedSubtaskDetail.parsedSubmission.link"
              class="detail-section"
            >
              <h4>{{ t('提交附件', 'Submission attachments') }}</h4>
              <div class="detail-file-list">
                <div
                  v-for="(file, index) in selectedSubtaskDetail.parsedSubmission.files"
                  :key="`${file.value}-${index}`"
                  class="detail-file-row"
                >
                  <span>{{ file.name }}</span>
                  <div>
                    <button v-if="canPreviewFile(file)" type="button" @click="openFile(file)">查看</button>
                    <button type="button" @click="downloadFile(file)">下载</button>
                  </div>
                </div>
                <div v-if="selectedSubtaskDetail.parsedSubmission.link" class="detail-file-row">
                  <span>{{ displayLinkText(selectedSubtaskDetail.parsedSubmission.link) }}</span>
                  <div>
                    <a :href="resolveMediaUrl(selectedSubtaskDetail.parsedSubmission.link)" target="_blank" rel="noreferrer">
                      打开
                    </a>
                  </div>
                </div>
              </div>
            </section>

            <section v-if="selectedSubtaskDetail.reviewComment" class="detail-section">
              <h4>{{ t('审批备注', 'Review note') }}</h4>
              <p>{{ selectedSubtaskDetail.reviewComment }}</p>
            </section>

            <section v-if="selectedSubtaskDetail.submissionHistories?.length" class="detail-section">
              <h4>{{ t('操作痕迹', 'Activity history') }}</h4>
              <div class="history-list">
                <article
                  v-for="history in selectedSubtaskDetail.submissionHistories"
                  :key="history.id"
                  class="history-item"
                >
                  <div class="history-head">
                    <strong>{{ t('提交版本', 'Version') }} {{ history.versionNo || '-' }}</strong>
                    <span>{{ history.current ? t('当前版本', 'Current') : history.submittedAtText }}</span>
                  </div>
                  <p v-if="history.parsedSubmission.text">{{ history.parsedSubmission.text }}</p>
                  <div
                    v-if="history.parsedSubmission.files?.length || history.parsedSubmission.link"
                    class="detail-file-list compact"
                  >
                    <div
                      v-for="(file, index) in history.parsedSubmission.files"
                      :key="`${history.id}-${file.value}-${index}`"
                      class="detail-file-row"
                    >
                      <span>{{ file.name }}</span>
                      <div>
                        <button v-if="canPreviewFile(file)" type="button" @click="openFile(file)">查看</button>
                        <button type="button" @click="downloadFile(file)">下载</button>
                      </div>
                    </div>
                    <div v-if="history.parsedSubmission.link" class="detail-file-row">
                      <span>{{ displayLinkText(history.parsedSubmission.link) }}</span>
                      <div>
                        <a :href="resolveMediaUrl(history.parsedSubmission.link)" target="_blank" rel="noreferrer">
                          打开
                        </a>
                      </div>
                    </div>
                  </div>
                </article>
              </div>
            </section>
          </div>
        </section>
      </div>
    </Teleport>
  </TeacherSubviewShell>
</template>

<style scoped>
.progress-layout {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 16px;
  margin-top: 14px;
  align-items: start;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.group-list,
.member-list,
.subtask-list {
  max-height: calc(100vh - 300px);
  overflow-y: auto;
}

.group-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 14px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
  margin-bottom: 8px;
}

.group-item:hover {
  background: var(--teacher-surface-muted);
}

.group-item.active {
  background: var(--teacher-accent-soft, rgba(36, 86, 173, 0.08));
  border-color: var(--teacher-accent, #2456ad);
}

.group-item-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.group-name {
  font-weight: 600;
  font-size: 14px;
}

.group-count {
  font-size: 12px;
  color: var(--teacher-text-tertiary);
}

.member-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
  margin-bottom: 8px;
}

.member-item:hover {
  background: var(--teacher-surface-muted);
}

.member-item.active {
  background: var(--teacher-accent-soft, rgba(36, 86, 173, 0.08));
  border-color: var(--teacher-accent, #2456ad);
}

.member-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.member-name {
  font-weight: 600;
  font-size: 14px;
}

.member-task {
  font-size: 12px;
  color: var(--teacher-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.subtask-table {
  width: 100%;
}

.subtask-header {
  display: grid;
  grid-template-columns: 1.5fr 1fr 0.8fr 1fr 1fr;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid var(--teacher-divider);
  font-size: 12px;
  font-weight: 600;
  color: var(--teacher-text-tertiary);
}

.subtask-row {
  display: grid;
  grid-template-columns: 1.5fr 1fr 0.8fr 1fr 1fr;
  gap: 8px;
  width: 100%;
  padding: 10px 0;
  border-bottom: 1px solid var(--teacher-divider);
  border-left: 0;
  border-right: 0;
  border-top: 0;
  font-size: 13px;
  align-items: center;
  background: transparent;
  color: var(--teacher-text-primary);
  text-align: left;
}

.subtask-row-button {
  cursor: pointer;
}

.subtask-row-button:hover {
  background: var(--teacher-surface-muted);
}

.subtask-name {
  font-weight: 500;
}

.subtask-detail-overlay {
  z-index: 1000;
}

.subtask-detail-modal {
  width: min(880px, var(--tt-dialog-available-width, 100%));
  max-height: min(88vh, 780px);
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  overflow: hidden;
  border-radius: 18px;
  border: 1px solid var(--teacher-border);
  background: var(--teacher-surface);
  box-shadow: var(--tt-shadow-xl, 0 24px 56px rgba(15, 23, 42, 0.16));
}

.subtask-detail-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--teacher-divider);
}

.subtask-detail-head p {
  margin: 0 0 4px;
  color: var(--teacher-text-tertiary);
  font-size: 12px;
  font-weight: 700;
}

.subtask-detail-head h3 {
  margin: 0;
  color: var(--teacher-text-primary);
  font-size: 20px;
  line-height: 1.3;
}

.modal-close-btn {
  height: 36px;
  padding: 0 12px;
  border-radius: 10px;
  border: 1px solid var(--teacher-border);
  background: var(--teacher-surface);
  color: var(--teacher-text-primary);
  cursor: pointer;
}

.subtask-detail-body {
  display: grid;
  gap: 14px;
  min-height: 0;
  overflow-y: auto;
  padding: 18px 20px 22px;
}

.detail-meta-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.detail-meta-grid div,
.detail-section {
  border-radius: 14px;
  background: var(--teacher-surface-muted);
}

.detail-meta-grid div {
  padding: 12px;
}

.detail-meta-grid dt {
  margin: 0 0 4px;
  color: var(--teacher-text-tertiary);
  font-size: 12px;
}

.detail-meta-grid dd {
  margin: 0;
  color: var(--teacher-text-primary);
  font-size: 13px;
  font-weight: 700;
  overflow-wrap: anywhere;
}

.detail-section {
  display: grid;
  gap: 10px;
  padding: 14px;
}

.detail-section h4 {
  margin: 0;
  color: var(--teacher-text-primary);
  font-size: 14px;
}

.detail-section p {
  margin: 0;
  color: var(--teacher-text-secondary);
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.detail-file-list,
.history-list {
  display: grid;
  gap: 8px;
}

.detail-file-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  padding: 10px;
  border-radius: 10px;
  background: var(--teacher-surface);
}

.detail-file-row span {
  min-width: 0;
  overflow-wrap: anywhere;
  color: var(--teacher-text-primary);
  font-size: 13px;
  font-weight: 700;
}

.detail-file-row div {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-file-row button,
.detail-file-row a {
  border: 0;
  background: transparent;
  color: var(--teacher-accent, #2456ad);
  cursor: pointer;
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
}

.history-item {
  display: grid;
  gap: 8px;
  padding: 12px;
  border-radius: 12px;
  background: var(--teacher-surface);
}

.history-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.history-head strong {
  color: var(--teacher-text-primary);
  font-size: 13px;
}

.history-head span {
  color: var(--teacher-text-tertiary);
  font-size: 12px;
}

.loading-hint,
.empty-hint {
  text-align: center;
  padding: 40px 20px;
  color: var(--teacher-text-tertiary);
  font-size: 14px;
}

.back-btn {
  border-radius: 10px;
  height: 40px;
  padding: 0 14px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid var(--teacher-border);
  background: var(--teacher-surface);
  color: var(--teacher-text-primary);
}

@media (max-width: 1200px) {
  .progress-layout {
    grid-template-columns: 1fr 1fr;
  }
  .queue-card {
    grid-column: 1 / -1;
  }
}

@media (max-width: 768px) {
  .progress-layout {
    grid-template-columns: 1fr;
  }

  .subtask-header,
  .subtask-row,
  .detail-meta-grid {
    grid-template-columns: 1fr;
  }

  .subtask-detail-head,
  .detail-file-row,
  .history-head {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
