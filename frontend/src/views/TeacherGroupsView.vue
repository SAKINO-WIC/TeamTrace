<script setup>
import EmptyState from '../components/common/EmptyState.vue'
import WorkspaceDialogMask from '../components/common/WorkspaceDialogMask.vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTeacherLocale } from '../composables/useTeacherLocale'
import {
  addStudentToGroup,
  createTeacherClassGroup,
  fetchTeacherClassDetail,
  fetchTeacherClassGroups,
  fetchTeacherClassStudents,
  fetchTeacherClassTasks,
  fetchTeacherTaskGroupProgress,
  moveTeacherClassGroupMember,
  setTeacherClassGroupingLock,
} from '../services/teacher'
import { formatDateTime, formatTaskStatus, normalizePagedPayload } from '../utils/teacher'
import { readProgressCache, writeProgressCache } from '../utils/progressCache'
import { readSessionCache, writeSessionCache } from '../utils/sessionCache'

const route = useRoute()
const router = useRouter()
const { t, isEn } = useTeacherLocale()

const classId = computed(() => String(route.params.classId || ''))
const loading = ref(false)
const studentsLoading = ref(false)
const progressLoading = ref(false)
const creating = ref(false)
const moving = ref(false)
const message = ref('')
const messageType = ref('info')
const classDetail = ref(null)
const groups = ref([])
const students = ref([])
const tasks = ref([])
const progressRows = ref([])
const taskLoadError = ref('')
const progressLoadError = ref('')
const selectedTaskId = ref('')
const selectedGroupId = ref('')
const createDialogOpen = ref(false)
const moveDialogOpen = ref(false)
const movingStudent = ref(null)
const createSearchKeyword = ref('')

const createForm = reactive({
  name: '',
  memberStudentIds: [],
  leaderId: '',
})

const moveForm = reactive({
  targetGroupId: '',
})

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

function normalizeClassDetail(raw) {
  return {
    classId: String(raw?.classId ?? raw?.id ?? '-'),
    studentCount: Number(raw?.studentCount ?? raw?.memberCount ?? 0),
    groupingLocked: Number(raw?.groupingLocked) === 1 || raw?.groupingLocked === true,
  }
}

function buildGroupMap(rawGroups) {
  const map = new Map()
  rawGroups.forEach((group) => {
    const memberIds = Array.isArray(group?.memberStudentIds) ? group.memberStudentIds : []
    memberIds.forEach((studentId) => {
      map.set(String(studentId), group?.name || group?.groupName || '未分组')
    })
  })
  return map
}

function normalizeStudent(raw, groupMap) {
  const studentId = String(raw?.studentId ?? raw?.id ?? '-')
  return {
    id: studentId,
    name: raw?.name ?? raw?.studentName ?? '-',
    email: raw?.email ?? '-',
    groupName: groupMap?.get(studentId) || raw?.groupName || '未分组',
    status: raw?.statusLabel ?? '在班',
  }
}

function normalizeGroup(raw) {
  const memberIds = Array.isArray(raw?.memberStudentIds) ? raw.memberStudentIds.map((item) => String(item)) : []
  const leaderId = raw?.leaderId ? String(raw.leaderId) : memberIds[0] || ''
  const leaderName = raw?.leaderName || students.value.find((item) => item.id === leaderId)?.name || '待设置'

  return {
    id: String(raw?.groupId ?? raw?.id ?? '-'),
    name: raw?.name ?? raw?.groupName ?? '未命名小组',
    leaderId,
    leaderName,
    memberIds,
    memberCount: Number(raw?.memberCount ?? memberIds.length ?? 0),
    joinMode: raw?.joinMode ?? '教师创建',
  }
}

function normalizeTask(raw) {
  return {
    id: String(raw?.taskId ?? raw?.id ?? ''),
    name: raw?.name ?? raw?.taskName ?? '未命名任务',
    status: formatTaskStatus(raw?.taskStatus ?? raw?.status),
    deadlineText: formatDateTime(raw?.deadline),
  }
}

const selectedTask = computed(() => tasks.value.find((item) => item.id === selectedTaskId.value) || null)
const selectedGroup = computed(() => groups.value.find((item) => item.id === selectedGroupId.value) || null)

const selectedGroupMembers = computed(() => {
  if (!selectedGroup.value) {
    return []
  }

  return selectedGroup.value.memberIds
    .map((studentId) => students.value.find((item) => item.id === studentId))
    .filter(Boolean)
})

const selectedLeaderStudent = computed(() => {
  if (!selectedGroup.value?.leaderId) {
    return null
  }
  return students.value.find((item) => item.id === selectedGroup.value.leaderId) || null
})

const ungroupedStudents = computed(() => students.value.filter((item) => item.groupName === '未分组'))
const hasNoStudents = computed(() => !loading.value && !students.value.length)

const createCandidateStudents = computed(() => {
  return students.value.filter((item) => item.status === '在班' && item.groupName === '未分组')
})

const filteredCreateCandidates = computed(() => {
  const keyword = createSearchKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return createCandidateStudents.value
  }

  return createCandidateStudents.value.filter((item) => {
    return [item.name, item.id, item.email].some((field) =>
      String(field || '').toLowerCase().includes(keyword),
    )
  })
})

const selectedCreateStudents = computed(() => {
  const candidateMap = new Map(createCandidateStudents.value.map((item) => [String(item.id), item]))
  return createForm.memberStudentIds
    .map((studentId) => candidateMap.get(String(studentId)) || students.value.find((item) => item.id === String(studentId)))
    .filter(Boolean)
})

const selectedCreateMemberIdSet = computed(() => new Set(createForm.memberStudentIds.map(String)))

const moveTargetGroupOptions = computed(() => {
  if (!movingStudent.value) {
    return groups.value
  }

  if (movingStudent.value.fromGroupId === 'ungrouped') {
    return groups.value
  }

  return groups.value.filter((item) => item.id !== movingStudent.value.fromGroupId)
})

const selectedGroupProgress = computed(() => {
  return progressRows.value.find((item) => item.groupId === selectedGroupId.value) || null
})

const selectedTaskMetrics = computed(() => {
  const progress = selectedGroupProgress.value
  const completed = Number(progress?.groupCompletedSubtasks ?? 0)
  const total = Number(progress?.groupTotalSubtasks ?? 0)
  const pending = Number(progress?.groupPendingSubtasks ?? Math.max(total - completed, 0))
  const percent = Math.round(Number(progress?.groupProgressPercent ?? 0))

  return {
    completed,
    pending,
    total,
    percent,
  }
})

const groupProgressMap = computed(() =>
  Object.fromEntries(
    progressRows.value.map((item) => [item.groupId, Math.round(Number(item.groupProgressPercent ?? 0))]),
  ))

const createMemberHint = computed(() => '，至少 1 人，不限制固定人数')

function workspaceCacheKey() {
  return `teacher:groups-workspace:${classId.value}`
}

function applyWorkspaceSnapshot(snapshot) {
  classDetail.value = snapshot.classDetail
  students.value = snapshot.students
  groups.value = snapshot.groups
  tasks.value = snapshot.tasks
  selectedTaskId.value = snapshot.selectedTaskId
  selectedGroupId.value = snapshot.selectedGroupId
}

async function loadClassStudents(options = {}) {
  const { silent = false } = options
  if (!classId.value) {
    students.value = []
    return
  }
  if (!silent) {
    studentsLoading.value = true
  }
  try {
    const studentsResult = await fetchTeacherClassStudents(classId.value, { page: 1, size: 200 })
    const studentPayload = normalizePagedPayload(studentsResult?.data?.data || {})
    const groupMap = buildGroupMap(groups.value)
    students.value = studentPayload.list.map((item) => normalizeStudent(item, groupMap))
    if (classDetail.value) {
      classDetail.value.studentCount = studentPayload.total || students.value.length
    }
    writeSessionCache(workspaceCacheKey(), {
      classDetail: classDetail.value,
      students: students.value,
      groups: groups.value,
      tasks: tasks.value,
      selectedTaskId: selectedTaskId.value,
      selectedGroupId: selectedGroupId.value,
    })
  } catch (error) {
    if (!silent) {
      setMessage(error.message || '加载学生列表失败', 'error')
    }
  } finally {
    studentsLoading.value = false
  }
}

async function loadWorkspace(options = {}) {
  const cached = options.force ? null : readSessionCache(workspaceCacheKey(), 120000)
  if (cached) {
    applyWorkspaceSnapshot(cached)
    loading.value = false
  } else {
    loading.value = true
  }
  taskLoadError.value = ''
  try {
    const [detailResult, groupsResult, tasksResult] = await Promise.allSettled([
      fetchTeacherClassDetail(classId.value),
      fetchTeacherClassGroups(classId.value),
      fetchTeacherClassTasks(classId.value),
    ])

    if (groupsResult.status === 'rejected') {
      classDetail.value = null
      students.value = []
      groups.value = []
      tasks.value = []
      progressRows.value = []
      selectedTaskId.value = ''
      selectedGroupId.value = ''
      setMessage(groupsResult.reason?.message || '加载分组管理失败', 'error')
      return
    }

    classDetail.value = detailResult.status === 'fulfilled'
      ? normalizeClassDetail(detailResult.value?.data?.data || {})
      : normalizeClassDetail({})

    const groupsPayload = groupsResult.value?.data?.data
    const rawGroups = Array.isArray(groupsPayload)
      ? groupsPayload
      : Array.isArray(groupsResult.value?.data)
        ? groupsResult.value.data
        : []
    groups.value = rawGroups.map(normalizeGroup)

    if (tasksResult.status === 'fulfilled') {
      const taskPayload = tasksResult.value?.data?.data
      tasks.value = Array.isArray(taskPayload)
        ? taskPayload.map(normalizeTask)
        : Array.isArray(tasksResult.value?.data)
          ? tasksResult.value.data.map(normalizeTask)
          : []
    } else {
      tasks.value = []
      taskLoadError.value = tasksResult.reason?.message || '任务列表暂不可用'
    }

    if (!tasks.value.some((item) => item.id === selectedTaskId.value)) {
      selectedTaskId.value = tasks.value[0]?.id || ''
    }
    if (!groups.value.some((item) => item.id === selectedGroupId.value)) {
      selectedGroupId.value = groups.value[0]?.id || ''
    }

    writeSessionCache(workspaceCacheKey(), {
      classDetail: classDetail.value,
      students: students.value,
      groups: groups.value,
      tasks: tasks.value,
      selectedTaskId: selectedTaskId.value,
      selectedGroupId: selectedGroupId.value,
    })
    setMessage('')
    void loadClassStudents({ silent: true })
  } catch (error) {
    classDetail.value = null
    students.value = []
    groups.value = []
    tasks.value = []
    progressRows.value = []
    selectedTaskId.value = ''
    selectedGroupId.value = ''
    setMessage(error.message || '加载分组管理失败', 'error')
  } finally {
    loading.value = false
  }
}

async function loadProgress(options = {}) {
  if (!selectedTaskId.value || !groups.value.length) {
    progressRows.value = []
    progressLoadError.value = ''
    return
  }

  const cacheKey = `${classId.value}:${selectedTaskId.value}`
  const cachedRows = readProgressCache(cacheKey)
  if (cachedRows && !options.force) {
    progressRows.value = cachedRows
    void loadProgress({ force: true })
    return
  }

  progressLoading.value = true
  progressLoadError.value = ''
  try {
    const priorityGroupId = selectedGroupId.value || groups.value[0]?.id
    const orderedGroups = [
      ...groups.value.filter((g) => g.id === priorityGroupId),
      ...groups.value.filter((g) => g.id !== priorityGroupId),
    ]

    // Batch: one HTTP call instead of N per-group calls
    const { data: batchData } = await fetchTeacherTaskGroupProgress(classId.value, selectedTaskId.value)
    const batchMap = batchData?.data || {}

    const rows = orderedGroups.map((group) => {
      const payload = batchMap[group.id] || {}
      return {
        groupId: group.id,
        groupTotalSubtasks: Number(payload?.groupTotalSubtasks ?? 0),
        groupCompletedSubtasks: Number(payload?.groupCompletedSubtasks ?? 0),
        groupPendingSubtasks: Number(payload?.groupPendingSubtasks ?? 0),
        groupClaimedSubtasks: Number(payload?.groupClaimedSubtasks ?? 0),
        groupProgressPercent: Number(payload?.groupProgressPercent ?? 0),
        members: Array.isArray(payload?.members)
          ? payload.members.map((item) => ({
              studentId: String(item?.studentId ?? ''),
              studentName: item?.studentName ?? '-',
              claimedSubtasks: Number(item?.claimedSubtasks ?? 0),
              completedSubtasks: Number(item?.completedSubtasks ?? 0),
              progressPercent: Number(item?.progressPercent ?? 0),
            }))
          : [],
      }
    })
    progressRows.value = rows
    writeProgressCache(cacheKey, rows)
  } catch (error) {
    if (!cachedRows) {
      progressRows.value = []
    }
    progressLoadError.value = error.message || '任务进度暂不可用'
  } finally {
    progressLoading.value = false
  }
}

async function toggleGroupingLock() {
  if (!classDetail.value) {
    return
  }

  const nextLocked = !classDetail.value.groupingLocked
  try {
    await setTeacherClassGroupingLock(classId.value, nextLocked)
    classDetail.value.groupingLocked = nextLocked
    setMessage(nextLocked ? '班级分组已锁定。' : '班级分组已解锁。', 'success')
  } catch (error) {
    setMessage(error.message || '更新分组锁定状态失败', 'error')
  }
}

async function openCreateDialog() {
  if (!students.value.length && !studentsLoading.value) {
    await loadClassStudents()
  }
  if (!students.value.length) {
    setMessage('需要先有学生加入班级，才能创建含成员的小组。', 'error')
    return
  }
  if (!createCandidateStudents.value.length) {
    setMessage('当前没有可用于建组的未分组学生。', 'error')
    return
  }
  createForm.name = `第${groups.value.length + 1}组`
  createForm.memberStudentIds = []
  createForm.leaderId = ''
  createSearchKeyword.value = ''
  createDialogOpen.value = true
}

function closeCreateDialog() {
  if (creating.value) {
    return
  }
  createDialogOpen.value = false
}

function setCreateLeader(studentId) {
  if (!createForm.memberStudentIds.map(String).includes(String(studentId))) {
    return
  }
  createForm.leaderId = String(studentId)
}

function isCreateLeader(studentId) {
  return String(createForm.leaderId) === String(studentId)
}

function isCreateMemberSelected(studentId) {
  return selectedCreateMemberIdSet.value.has(String(studentId))
}

function removeCreateMember(studentId) {
  createForm.memberStudentIds = createForm.memberStudentIds.filter((item) => String(item) !== String(studentId))
}

function clearCreateMembers() {
  createForm.memberStudentIds = []
  createForm.leaderId = ''
}

function selectFilteredCreateMembers() {
  const selected = new Set(createForm.memberStudentIds.map(String))
  filteredCreateCandidates.value.forEach((item) => selected.add(String(item.id)))
  createForm.memberStudentIds = Array.from(selected)
}

async function submitCreateGroup() {
  if (!createForm.name.trim()) {
    setMessage('请输入小组名称。', 'error')
    return
  }
  if (!createForm.memberStudentIds.length) {
    setMessage('请至少选择一名学生。', 'error')
    return
  }
  if (!createForm.leaderId) {
    setMessage('请选择组长。', 'error')
    return
  }

  creating.value = true
  try {
    await createTeacherClassGroup(classId.value, {
      name: createForm.name.trim(),
      leaderId: Number(createForm.leaderId),
      memberStudentIds: createForm.memberStudentIds.map((item) => Number(item)),
    })
    createDialogOpen.value = false
    setMessage('教师分组已创建。', 'success')
    await loadWorkspace()
    await loadProgress()
  } catch (error) {
    setMessage(error.message || '创建小组失败', 'error')
  } finally {
    creating.value = false
  }
}

function openMoveDialog(studentId, fromGroupId = '') {
  const sourceGroupId = fromGroupId ? String(fromGroupId) : 'ungrouped'
  const availableGroups =
    sourceGroupId === 'ungrouped' ? groups.value : groups.value.filter((item) => item.id !== sourceGroupId)

  if (!availableGroups.length) {
    setMessage('当前没有可移动的目标小组。', 'error')
    return
  }

  movingStudent.value = {
    studentId: String(studentId),
    fromGroupId: sourceGroupId,
  }
  moveForm.targetGroupId = availableGroups[0]?.id || ''
  moveDialogOpen.value = true
}

function closeMoveDialog() {
  if (moving.value) {
    return
  }
  movingStudent.value = null
  moveForm.targetGroupId = ''
  moveDialogOpen.value = false
}

async function submitMoveMember() {
  if (!movingStudent.value || !moveForm.targetGroupId) {
    setMessage('请选择目标小组。', 'error')
    return
  }

  moving.value = true
  try {
    if (movingStudent.value.fromGroupId === 'ungrouped') {
      await addStudentToGroup(
        classId.value,
        moveForm.targetGroupId,
        movingStudent.value.studentId,
      )
    } else {
      await moveTeacherClassGroupMember(
        classId.value,
        movingStudent.value.fromGroupId,
        movingStudent.value.studentId,
        moveForm.targetGroupId,
      )
    }
    moveDialogOpen.value = false
    movingStudent.value = null
    moveForm.targetGroupId = ''
    setMessage('学生已移动到目标小组。', 'success')
    await loadWorkspace()
    await loadProgress()
  } catch (error) {
    setMessage(error.message || '移动小组成员失败', 'error')
  } finally {
    moving.value = false
  }
}

watch(classId, async (nextId, prevId) => {
  if (nextId && nextId !== prevId) {
    selectedTaskId.value = ''
    await loadWorkspace()
    void loadProgress()
  }
})

watch(
  () => selectedTaskId.value,
  () => {
    void loadProgress()
  },
)

watch(
  () => createForm.memberStudentIds.map(String),
  (memberIds) => {
    if (!memberIds.length) {
      createForm.leaderId = ''
      return
    }

    if (!memberIds.includes(String(createForm.leaderId))) {
      createForm.leaderId = memberIds[0]
    }
  },
)

onMounted(async () => {
  await loadWorkspace()
  void loadProgress()
})
</script>

<template>
  <div class="teacher-page">
    <header class="card topbar">
      <div>
        <h2>{{ t('分组管理', 'Groups') }}</h2>
      </div>
      <div class="actions">
        <button
          class="secondary-btn"
          :class="{ 'state-btn locked': classDetail?.groupingLocked, 'state-btn': true }"
          type="button"
          @click="toggleGroupingLock"
        >
          {{ classDetail?.groupingLocked ? '解锁分组' : '锁定分组' }}
        </button>
        <button class="primary-btn" type="button" :disabled="loading || hasNoStudents" @click="openCreateDialog">
          {{ hasNoStudents ? '等待学生加入' : groups.length ? '创建分组' : '创建第一个小组' }}
        </button>
      </div>
    </header>

    <p v-if="message" class="message" :class="messageType">{{ message }}</p>

    <section v-if="hasNoStudents" class="card empty-guide">
      <EmptyState
        icon="👥"
        :title="t('需要先有学生加入', 'Students must join first')"
        description=""
        action-label="返回班级详情复制邀请码"
        @action="router.push(`/teacher/classes/${classId}`)"
      />
    </section>


    <section v-if="!hasNoStudents" class="content-grid">
      <article class="card panel groups-panel">
        <div class="panel-head list-panel-head">
          <h3>小组列表</h3>
          <span class="meta">{{ groups.length }} 个</span>
        </div>

        <div class="list-scroll-slot">
        <div class="group-list teacher-list-scroll-7">
          <button
            v-for="item in groups"
            :key="item.id"
            class="group-card"
            :class="{ active: item.id === selectedGroupId }"
            type="button"
            @click="selectedGroupId = item.id"
          >
            <h3 class="group-name">{{ item.name }}</h3>
          </button>

          <EmptyState
            v-if="!groups.length"
            icon="👥"
            :title="t('当前班级还没有小组', 'No groups in this class yet')"
            description=""
            action-label="创建第一个小组"
            @action="openCreateDialog"
          />
        </div>
        </div>
      </article>

      <article class="card panel workspace-panel">
        <template v-if="selectedGroup">
          <div class="member-panel">
            <div class="panel-head list-panel-head">
              <h3>{{ selectedGroup.name }}</h3>
              <span class="meta">{{ selectedGroupMembers.length }} 人</span>
            </div>
            <div class="list-scroll-slot">
            <div class="member-list teacher-list-scroll-7">
              <article v-for="item in selectedGroupMembers" :key="item.id" class="member-row">
                <div>
                  <p class="member-name">{{ item.name }}</p>
                </div>
                <button
                  class="mini-btn"
                  type="button"
                  :disabled="groups.length <= 1"
                  @click="openMoveDialog(item.id, selectedGroup?.id)"
                >
                  移动到其他组
                </button>
              </article>

              <p v-if="!selectedGroupMembers.length" class="empty">当前小组没有成员。</p>
            </div>
            </div>
          </div>
        </template>

        <p v-else class="empty">请选择一个小组查看详情。</p>
      </article>
    </section>

    <WorkspaceDialogMask :open="createDialogOpen" @close="closeCreateDialog">
      <section class="card dialog-panel">
        <div class="dialog-head">
          <div>
            <h3>创建分组</h3>
          </div>
        </div>

        <div class="dialog-grid">
          <label class="full-width">
            <span>小组名称</span>
            <input v-model.trim="createForm.name" type="text" placeholder="例如：第3组 / 数据分析组" />
          </label>

          <div class="full-width member-picker">
            <div class="picker-head">
              <div>
                <span class="field-label">选择成员</span>
                <p class="meta">
                  {{ createCandidateStudents.length }} 人可选，已筛出 {{ filteredCreateCandidates.length }} 人{{ createMemberHint }}
                </p>
              </div>
              <strong class="selected-count">已选 {{ selectedCreateStudents.length }} 人</strong>
            </div>

            <div class="picker-toolbar">
              <input
                v-model.trim="createSearchKeyword"
                type="search"
                placeholder="搜索姓名、学号或邮箱"
              />
              <div class="picker-actions">
                <button
                  class="mini-btn"
                  type="button"
                  :disabled="!filteredCreateCandidates.length"
                  @click="selectFilteredCreateMembers"
                >
                  全选筛选结果
                </button>
                <button
                  class="mini-btn"
                  type="button"
                  :disabled="!selectedCreateStudents.length"
                  @click="clearCreateMembers"
                >
                  清空已选
                </button>
              </div>
            </div>

            <div class="member-picker-grid">
              <div class="student-checkboxes">
                <label
                  v-for="item in filteredCreateCandidates"
                  :key="item.id"
                  class="checkbox-row"
                  :class="{ checked: isCreateMemberSelected(item.id) }"
                >
                  <input
                    type="checkbox"
                    :value="item.id"
                    v-model="createForm.memberStudentIds"
                  />
                  <span class="check-body">
                    <span class="check-name">{{ item.name }}</span>
                    <span class="check-meta">{{ item.id }} · {{ item.email }}</span>
                  </span>
                </label>
                <p v-if="!filteredCreateCandidates.length" class="empty-hint">
                  {{ createSearchKeyword ? '没有匹配的未分组学生' : '没有可选的未分组学生' }}
                </p>
              </div>

              <aside class="selected-members-panel">
                <div class="selected-head">
                  <span class="field-label">已选成员</span>
                  <button
                    class="text-btn"
                    type="button"
                    :disabled="!selectedCreateStudents.length"
                    @click="clearCreateMembers"
                  >
                    清空
                  </button>
                </div>

                <div class="selected-list">
                  <article
                    v-for="item in selectedCreateStudents"
                    :key="item.id"
                    class="selected-row"
                    :class="{ leader: isCreateLeader(item.id) }"
                  >
                    <div>
                      <p class="member-name">{{ item.name }}</p>
                      <p class="member-meta">{{ item.id }} · {{ item.email }}</p>
                    </div>
                    <div class="selected-row-actions">
                      <button
                        class="mini-btn"
                        type="button"
                        :disabled="isCreateLeader(item.id)"
                        @click="setCreateLeader(item.id)"
                      >
                        {{ isCreateLeader(item.id) ? '组长' : '设为组长' }}
                      </button>
                      <button class="text-btn danger" type="button" @click="removeCreateMember(item.id)">移除</button>
                    </div>
                  </article>

                  <p v-if="!selectedCreateStudents.length" class="empty-hint">从左侧选择至少一名学生。</p>
                </div>
              </aside>
            </div>
          </div>

          <label class="full-width">
            <span>指定组长</span>
            <select v-model="createForm.leaderId">
              <option value="" disabled>请选择组长</option>
              <option v-for="item in selectedCreateStudents" :key="item.id" :value="item.id">
                {{ item.name }}（{{ item.id }}）
              </option>
            </select>
          </label>
        </div>

        <div class="dialog-actions">
          <button class="secondary-btn" type="button" :disabled="creating" @click="closeCreateDialog">取消</button>
          <button class="primary-btn" type="button" :disabled="creating" @click="submitCreateGroup">
            {{ creating ? '创建中...' : '确认创建' }}
          </button>
        </div>
      </section>
    </WorkspaceDialogMask>

    <WorkspaceDialogMask :open="moveDialogOpen" @close="closeMoveDialog">
      <section class="card dialog-panel compact-dialog">
        <div class="dialog-head">
          <div>
            <h3>移动小组成员</h3>
          </div>
        </div>

        <div class="dialog-grid">
          <label class="full-width">
            <span>目标小组</span>
            <select v-model="moveForm.targetGroupId">
              <option value="" disabled>请选择目标小组</option>
              <option v-for="item in moveTargetGroupOptions" :key="item.id" :value="item.id">
                {{ item.name }}
              </option>
            </select>
          </label>
        </div>

        <div class="dialog-actions">
          <button class="secondary-btn" type="button" :disabled="moving" @click="closeMoveDialog">取消</button>
          <button class="primary-btn" type="button" :disabled="moving" @click="submitMoveMember">
            {{ moving ? '移动中...' : '确认移动' }}
          </button>
        </div>
      </section>
    </WorkspaceDialogMask>
  </div>
</template>

<style scoped>
.teacher-page,
.content-grid,
.group-list,
.detail-grid,
.member-list,
.dialog-grid,
.ungrouped-list {
  display: grid;
  gap: 14px;
}

.teacher-page {
  gap: 16px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.card {
  background: var(--teacher-surface);
  border-radius: var(--teacher-radius-card);
  box-shadow: var(--teacher-shadow);
}

.topbar,
.panel,
.dialog-panel {
  padding: 18px;
}

.topbar,
.actions,
.panel-actions,
.panel-head,
.workspace-head,
.dialog-head,
.dialog-actions,
.member-row,
.group-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.actions,
.panel-actions,
.dialog-actions,
.workspace-head {
  flex-wrap: wrap;
}

.content-grid {
  grid-template-columns: minmax(300px, 0.72fr) minmax(0, 1.28fr);
  align-items: start;
  flex: 1;
  min-height: 0;
  height: clamp(480px, calc(100vh - 248px), 720px);
}

.filter-panel {
  display: grid;
  gap: 14px;
  align-content: start;
}

.groups-panel,
.workspace-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 0;
  align-content: stretch;
  padding: 16px;
}

.filters {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.filter-item,
label {
  display: grid;
  gap: 8px;
}

.label,
.filter-item span,
label span,
.field-label,
.metric-label {
  margin: 0;
  color: var(--teacher-text-tertiary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

h2,
h3,
.detail-value,
.task-name,
.member-name {
  margin: 0;
}

.meta,
.message,
.group-meta,
.workspace-meta,
.member-meta,
.detail-meta,
.empty,
.task-deadline {
  margin: 0;
  color: var(--teacher-text-tertiary);
  font-size: 13px;
  line-height: 1.65;
}

.message.success {
  color: var(--teacher-success);
}

.message.error {
  color: var(--teacher-danger);
}

.empty-guide {
  padding: 10px;
}

.inline-warning {
  margin: 0;
  padding: 12px 14px;
  border: 1px solid rgba(230, 138, 45, 0.2);
  border-radius: 14px;
  background: rgba(255, 247, 237, 0.86);
  color: #9a5a13;
  font-size: 13px;
  line-height: 1.6;
}

input,
select {
  height: 42px;
  border: 1px solid var(--teacher-border);
  border-radius: var(--teacher-radius-control);
  background: var(--teacher-surface);
  color: var(--teacher-text-primary);
  padding: 0 12px;
}

input[type='search'] {
  width: 100%;
}

.multi-select {
  display: none;
}

.member-picker {
  display: grid;
  gap: 12px;
}

.picker-head,
.picker-toolbar,
.selected-head,
.selected-row,
.selected-row-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.picker-head,
.picker-toolbar {
  flex-wrap: wrap;
}

.picker-toolbar {
  align-items: stretch;
}

.picker-toolbar input {
  flex: 1 1 280px;
}

.picker-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.selected-count {
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  border: 1px solid rgba(36, 86, 173, 0.14);
  border-radius: 999px;
  padding: 0 12px;
  background: rgba(232, 241, 255, 0.64);
  color: var(--teacher-text-primary);
  font-size: 13px;
}

.member-picker-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.06fr) minmax(260px, 0.94fr);
  gap: 12px;
  align-items: stretch;
}

.student-checkboxes {
  max-height: 320px;
  overflow-y: auto;
  border: 1px solid var(--teacher-border);
  border-radius: 10px;
  padding: 8px;
}
.checkbox-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 54px;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
}
.checkbox-row:hover,
.checkbox-row.checked {
  background: rgba(36, 86, 173, 0.04);
}
.checkbox-row.checked {
  border-color: rgba(36, 86, 173, 0.18);
}
.checkbox-row input {
  width: 16px;
  height: 16px;
  flex: 0 0 auto;
}
.check-body {
  min-width: 0;
  display: grid;
  gap: 2px;
}
.check-name {
  color: var(--teacher-text-primary);
  font-size: 14px;
  line-height: 1.35;
}
.check-meta {
  color: var(--teacher-text-tertiary);
  font-size: 12px;
  line-height: 1.35;
}
.empty-hint { font-size: 13px; color: var(--teacher-text-tertiary); text-align: center; padding: 12px; }

.selected-members-panel {
  min-height: 320px;
  border: 1px solid var(--teacher-border);
  border-radius: 10px;
  padding: 10px;
  background: var(--teacher-surface-muted);
  display: grid;
  grid-template-rows: auto 1fr;
  gap: 10px;
}

.selected-list {
  display: grid;
  gap: 8px;
  align-content: start;
  max-height: 276px;
  overflow-y: auto;
  padding-right: 2px;
}

.selected-row {
  min-height: 66px;
  padding: 10px;
  border: 1px solid var(--teacher-border);
  border-radius: 10px;
  background: var(--teacher-surface);
}

.selected-row.leader {
  border-color: color-mix(in srgb, var(--teacher-accent) 24%, var(--teacher-border));
  background: var(--teacher-accent-soft);
}

.selected-row-actions {
  justify-content: flex-end;
  flex-wrap: wrap;
}

.text-btn {
  min-height: 32px;
  border: 0;
  background: transparent;
  color: var(--teacher-text-secondary);
  font-family: inherit;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.text-btn.danger {
  color: var(--teacher-danger);
}

.text-btn:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.detail-card,
.member-row,
.task-spotlight {
  border: 1px solid var(--teacher-divider);
  border-radius: 16px;
}

.list-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 44px;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--teacher-divider);
  flex-shrink: 0;
}

.list-panel-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 800;
  line-height: 1.2;
}

.list-panel-head .meta {
  flex-shrink: 0;
  font-size: 12px;
}

.list-scroll-slot {
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  justify-content: flex-start;
}

.group-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  padding-right: 4px;
  box-sizing: border-box;
}

.group-card {
  width: 100%;
  flex: 0 0 auto;
  box-sizing: border-box;
  border: 1px solid var(--teacher-border);
  border-radius: 12px;
  background: var(--teacher-surface);
  min-height: 52px;
  height: 52px;
  padding: 0 14px;
  display: flex;
  align-items: center;
  text-align: left;
  cursor: pointer;
  transition: box-shadow 0.18s ease, border-color 0.18s ease, background 0.18s ease;
}

.group-card:hover {
  border-color: color-mix(in srgb, var(--teacher-accent) 28%, var(--teacher-border));
  background: var(--teacher-surface-muted);
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.08);
}

.group-card.active {
  border-color: var(--teacher-accent);
  background: var(--teacher-accent-soft);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--teacher-accent) 24%, transparent);
}

.group-metrics,
.detail-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.group-name {
  font-size: 14px;
  line-height: 1.35;
}

.group-progress {
  font-size: 20px;
  font-weight: 800;
  color: var(--teacher-text-primary);
}

.detail-card {
  padding: 14px;
  display: grid;
  gap: 8px;
  align-content: start;
}

.metric-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(248, 250, 252, 0.96);
  border: 1px solid rgba(148, 163, 184, 0.14);
  color: var(--teacher-text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.group-badge {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--teacher-surface-muted);
  color: var(--teacher-text-primary);
  font-size: 12px;
  font-weight: 700;
}

.workspace-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.groups-panel,
.workspace-panel {
  min-height: 0;
  max-height: none;
  height: 100%;
  overflow: hidden;
}

.groups-panel {
  grid-template-rows: auto minmax(0, 1fr);
}

.workspace-panel {
  display: grid;
  grid-template-rows: 1fr;
}

.member-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 0;
  min-height: 0;
  height: 100%;
}

.detail-value {
  color: var(--teacher-text-primary);
  font-size: 22px;
  font-weight: 800;
}

.detail-value.percent {
  font-size: 32px;
  line-height: 1;
}

.task-spotlight {
  margin-top: 14px;
  padding: 16px 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.82), rgba(255, 255, 255, 0.96));
}

.task-name {
  margin-top: 8px;
  font-size: 18px;
  font-weight: 800;
  color: var(--teacher-text-primary);
}

.ungrouped-list {
  max-height: 240px;
  overflow-y: auto;
  padding-right: 4px;
  overscroll-behavior: auto;
}

.ungrouped-section {
  display: grid;
  gap: 12px;
  padding-top: 14px;
  border-top: 1px solid rgba(148, 163, 184, 0.14);
}

.compact-head h3 {
  font-size: 15px;
}

.member-row {
  flex: 0 0 auto;
  min-height: 52px;
  height: 52px;
  padding: 0 14px;
  box-sizing: border-box;
  border: 1px solid var(--teacher-border);
  border-radius: 16px;
  background: var(--teacher-surface);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--teacher-border) 40%, transparent);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.member-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  padding-right: 4px;
  box-sizing: border-box;
}

.member-list.teacher-list-scroll-7 {
  --teacher-list-row-gap: 8px;
}

.member-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--teacher-text-primary);
}

.mini-btn,
.secondary-btn,
.primary-btn,
.icon-close-btn {
  min-height: 40px;
  border: 0;
  border-radius: 10px;
  padding: 0 14px;
  font-family: inherit;
  font-weight: 700;
  cursor: pointer;
}

.mini-btn:disabled,
.secondary-btn:disabled,
.primary-btn:disabled,
.icon-close-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.mini-btn {
  min-height: 34px;
  border: 1px solid color-mix(in srgb, var(--teacher-accent) 32%, var(--teacher-border));
  border-radius: 999px;
  background: color-mix(in srgb, var(--teacher-accent) 10%, var(--teacher-surface));
  color: var(--teacher-accent);
  white-space: nowrap;
}

.mini-btn:hover:not(:disabled) {
  background: var(--teacher-accent-soft);
  border-color: var(--teacher-accent);
  color: var(--teacher-text-primary);
}

.secondary-btn {
  background: var(--teacher-surface-muted);
  color: var(--teacher-text-primary);
}

.state-btn.locked {
  background: rgba(230, 138, 45, 0.14);
  color: #9a5a13;
}

.primary-btn {
  background: var(--teacher-accent);
  color: #fff;
}

.icon-close-btn {
  background: rgba(15, 23, 42, 0.08);
  color: var(--teacher-text-primary);
}

.dialog-mask {
  /* positioning handled by teacher-dialog.css */
}

.dialog-panel {
  width: min(920px, 100%);
}

.compact-dialog {
  width: min(520px, 100%);
}

.full-width {
  grid-column: 1 / -1;
}

.dialog-actions {
  margin-top: 18px;
}

@media (max-width: 1200px) {
  .content-grid,
  .filters {
    grid-template-columns: 1fr;
  }

  .task-spotlight {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 760px) {
  .topbar,
  .actions,
  .panel-head,
  .workspace-head,
  .dialog-head,
  .dialog-actions,
  .member-row,
  .group-card-head {
    flex-direction: column;
    align-items: stretch;
  }

  .group-metrics,
  .detail-grid,
  .member-picker-grid {
    grid-template-columns: 1fr;
  }

  .picker-toolbar,
  .selected-row,
  .selected-row-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .picker-toolbar input {
    flex-basis: auto;
  }
}
</style>
