<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getActiveSession, getCurrentUserId } from '../utils/auth'
import { resolveMediaUrl } from '../utils/mediaUrl'
import { buildSubmissionContent, parseSubmissionContent } from '../utils/submissionAttachments'
import FreeCollabCommandBar from '../components/student/free-collab/FreeCollabCommandBar.vue'
import FreeCollabHomeSection from '../components/student/free-collab/FreeCollabHomeSection.vue'
import FreeCollabNotificationsSection from '../components/student/free-collab/FreeCollabNotificationsSection.vue'
import FreeCollabProgressSection from '../components/student/free-collab/FreeCollabProgressSection.vue'
import FreeCollabSpacesSection from '../components/student/free-collab/FreeCollabSpacesSection.vue'
import FreeCollabTaskBoard from '../components/student/free-collab/FreeCollabTaskBoard.vue'
import FreeCollabTraceSection from '../components/student/free-collab/FreeCollabTraceSection.vue'
import '../styles/free-collab-sections.css'
import {
  acceptStudentCollaborationTask,
  archiveStudentCollaborationProject,
  archiveStudentCollaborationTask,
  claimStudentCollaborationFlowNode,
  claimStudentCollaborationTask,
  createStudentCollaborationProject,
  createStudentCollaborationProjectWithTasks,
  createStudentCollaborationSpace,
  createStudentCollaborationTask,
  fetchStudentCollaborationActivityLogs,
  fetchStudentCollaborationDashboard,
  fetchStudentCollaborationProgress,
  fetchStudentCollaborationProject,
  fetchStudentCollaborationProjects,
  fetchStudentCollaborationSpace,
  fetchStudentCollaborationSpaces,
  generateStudentCollaborationInviteCode,
  joinStudentCollaborationSpace,
  leaveStudentCollaborationSpace,
  removeStudentCollaborationMember,
  returnStudentCollaborationTask,
  submitStudentCollaborationTask,
  transferStudentCollaborationOwner,
  updateStudentCollaborationProject,
  updateStudentCollaborationTask,
} from '../services/student'

const route = useRoute()
const router = useRouter()

const sectionConfigs = {
  home: {
    title: '自由首页',
    subtitle: '把你今天要处理、要接收、要关注的协作信息收拢到一个工作台。',
    stage: '总览',
    focus: '今天先看什么',
  },
  spaces: {
    title: '协作空间',
    subtitle: '先确定团队边界、空间目标和邀请方式，再进入项目协作。',
    stage: '组队',
    focus: '创建、加入、邀请成员',
  },
  tasks: {
    title: '任务面板',
    subtitle: '把项目拆成可认领、可提交、可接收的任务，让每一步都看得见。',
    stage: '推进',
    focus: '发布、认领、提交、接收',
  },
  progress: {
    title: '项目进度',
    subtitle: '系统根据时间、状态和前置关系自动生成进度、风险和交接视图。',
    stage: '可视化',
    focus: '甘特图、阻塞、成员负载',
  },
  contributions: {
    title: '协作痕迹',
    subtitle: '关键动作持续记录，复盘时不用再翻聊天记录。',
    stage: '复盘',
    focus: '谁在什么时间推进了什么',
  },
  notifications: {
    title: '协作通知',
    subtitle: '只处理自由协作场景里的提醒，保持在当前产品线，不切回课堂模式。',
    stage: '提醒',
    focus: '待办、风险、系统消息',
  },
}

const workflowSteps = [
  { key: 'spaces', title: '组队', text: '创建或加入空间' },
  { key: 'tasks', title: '拆解', text: '发布项目和任务' },
  { key: 'progress', title: '推进', text: '查看风险和交接' },
  { key: 'contributions', title: '复盘', text: '沉淀协作痕迹' },
]

const createEmptyFlowNode = () => ({
  title: '',
  description: '',
  assigneeId: '',
  claimable: false,
})

const statusLabels = {
  PENDING: '待流转',
  UNCLAIMED: '未认领',
  CLAIMED: '已认领',
  IN_PROGRESS: '进行中',
  WAITING_RECEIVE: '待接收',
  RETURNED: '被打回',
  COMPLETED: '已完成',
}

const boardColumns = [
  { key: 'UNCLAIMED', title: '未认领' },
  { key: 'CLAIMED', title: '进行中' },
  { key: 'WAITING_RECEIVE', title: '待接收' },
  { key: 'RETURNED', title: '被打回' },
  { key: 'COMPLETED', title: '已完成' },
]

const sessionUser = computed(() => getActiveSession()?.user || {})
const displayName = computed(() => sessionUser.value?.name || '协作者')
const currentUserId = computed(() => Number(getCurrentUserId() || sessionUser.value?.id || 0))
const activeSection = computed(() => {
  if (route.path.startsWith('/student/free/notifications')) return 'notifications'
  const pathSection = String(route.path.split('/').filter(Boolean).at(-1) || '')
  const raw = String(route.params.section || pathSection || 'home')
  return sectionConfigs[raw] ? raw : 'home'
})
const sectionMeta = computed(() => sectionConfigs[activeSection.value])

const spaces = ref([])
const selectedSpaceId = ref(null)
const selectedSpaceDetail = ref(null)
const projects = ref([])
const selectedProjectId = ref(null)
const selectedProjectDetail = ref(null)
const progress = ref(null)
const dashboard = ref(null)
const activityLogs = ref([])
const activityFilter = ref('ALL')
const taskBoardFilter = ref('ALL')
const activeCreatePanel = ref('')
const failedAvatarKeys = ref({})

const isLoading = ref(false)
const isSubmitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const copiedCode = ref('')

const createSpaceForm = ref({ name: '', description: '' })
const joinForm = ref({ inviteCode: '' })
const projectForm = ref({ title: '', description: '', startAt: '', dueAt: '', attachments: [] })
const projectWizardForm = ref({
  step: 'project',
  project: { title: '', description: '', startAt: '', dueAt: '', attachments: [] },
  tasks: [
    {
      title: '',
      description: '',
      deliverableRequirements: '',
      assigneeId: '',
      receiverId: '',
      startAt: '',
      dueAt: '',
      dependsOnLocalIds: [],
      attachments: [],
      flowNodes: [createEmptyFlowNode()],
    },
  ],
})
const taskForm = ref({
  title: '',
  description: '',
  deliverableRequirements: '',
  assigneeId: '',
  receiverId: '',
  startAt: '',
  dueAt: '',
  dependsOnTaskIds: [],
  attachments: [],
  flowNodes: [createEmptyFlowNode()],
})
const taskSubmitDrafts = ref({})
const reviewDrafts = ref({})
const editingProject = ref(false)
const projectEditForm = ref({ title: '', description: '', startAt: '', dueAt: '', status: 'ACTIVE', attachments: [] })
const editingTaskId = ref(null)
const highlightedTaskId = ref(null)
const expandedSubmissionTaskId = ref(null)
const selectedTaskId = ref(null)
const pendingRouteTarget = ref({ spaceId: null, taskId: null })
const taskEditForm = ref({
  title: '',
  description: '',
  deliverableRequirements: '',
  assigneeId: '',
  receiverId: '',
  startAt: '',
  dueAt: '',
  status: '',
  dependsOnTaskIds: [],
  attachments: [],
  flowNodes: [],
})

const selectedSpace = computed(() => {
  if (selectedSpaceDetail.value?.id === selectedSpaceId.value) return selectedSpaceDetail.value
  return spaces.value.find((item) => item.id === selectedSpaceId.value) || null
})
const selectedProject = computed(() => {
  if (selectedProjectDetail.value?.id === selectedProjectId.value) return selectedProjectDetail.value
  return projects.value.find((item) => item.id === selectedProjectId.value) || null
})
const selectedTasks = computed(() =>
  (selectedProjectDetail.value?.tasks || []).map((task) => contextualizeTask(task)),
)
const activeSelectedTasks = computed(() =>
  selectedTasks.value.filter((task) => task.status !== 'ARCHIVED'),
)
const selectedTask = computed(() =>
  activeSelectedTasks.value.find((item) => Number(item.id) === Number(selectedTaskId.value)) || null,
)
const selectedMembers = computed(() => selectedSpace.value?.members || [])
const canOrganize = computed(() => selectedSpace.value?.myRole === 'OWNER')
const ownedSpaceCount = computed(() => spaces.value.filter((item) => item.myRole === 'OWNER').length)
const totalMemberCount = computed(() => spaces.value.reduce((total, item) => total + Number(item.memberCount || 0), 0))
const selectedSpaceStats = computed(() => {
  const projectCount = projects.value.length
  const taskCount = projects.value.reduce((total, project) => total + Number(project.taskCount || 0), 0)
  const completedTaskCount = projects.value.reduce((total, project) => total + Number(project.completedTaskCount || 0), 0)
  const memberCount = selectedMembers.value.length || Number(selectedSpace.value?.memberCount || 0)
  return {
    memberCount,
    projectCount,
    taskCount,
    completedTaskCount,
    completionRate: taskCount ? Math.round((completedTaskCount / taskCount) * 100) : 0,
  }
})
const inviteStatus = computed(() => {
  const code = selectedSpace.value?.activeInviteCode
  const expiresAt = selectedSpace.value?.inviteCodeExpiresAt
  if (!code) {
    return { label: '未生成', hint: '发起人生成后，成员可用口令加入空间。', expired: false }
  }
  if (!expiresAt) {
    return { label: '可使用', hint: '当前邀请口令可复制给团队成员。', expired: false }
  }
  const expires = new Date(expiresAt).getTime()
  const expired = Number.isFinite(expires) && expires < Date.now()
  return {
    label: expired ? '已过期' : '可使用',
    hint: expired ? '请重新生成邀请口令。' : `有效期至 ${formatDate(expiresAt)}`,
    expired,
  }
})
const progressTasks = computed(() =>
  (progress.value?.tasks || []).map((task) => contextualizeTask(task)),
)
const progressView = computed(() => {
  if (!progress.value) return null
  return {
    ...progress.value,
    tasks: progressTasks.value,
  }
})
const progressDependencyLinks = computed(() => {
  const tasks = progressTasks.value
  const byId = new Map(tasks.map((task) => [Number(task.id), task]))
  return (progress.value?.dependencies || [])
    .map((dependency) => ({
      id: dependency.id,
      task: byId.get(Number(dependency.taskId)),
      dependsOn: byId.get(Number(dependency.dependsOnTaskId)),
    }))
    .filter((item) => item.task && item.dependsOn)
})
const progressRisks = computed(() => progressTasks.value.filter((task) => {
  if (task.overdue) return true
  if (task.status === 'RETURNED') return true
  if (task.status === 'WAITING_RECEIVE') return true
  return isDueSoonTask(task)
}))
const progressHandoffs = computed(() =>
  progressTasks.value.filter((task) => task.status === 'WAITING_RECEIVE' || task.receiverId),
)
const progressMemberLoads = computed(() => {
  const loads = new Map()
  selectedMembers.value.forEach((member) => {
    loads.set(Number(member.studentId), {
      id: Number(member.studentId),
      name: member.name || '成员',
      avatarUrl: member.avatarUrl,
      total: 0,
      active: 0,
      waiting: 0,
      completed: 0,
      risk: 0,
    })
  })
  progressTasks.value.forEach((task) => {
    const id = Number(task.assigneeId || 0)
    if (!id) return
    const current = loads.get(id) || {
      id,
      name: task.assigneeName || '成员',
      avatarUrl: '',
      total: 0,
      active: 0,
      waiting: 0,
      completed: 0,
      risk: 0,
    }
    loads.set(id, {
      ...current,
      total: current.total + 1,
      active: current.active + (['CLAIMED', 'IN_PROGRESS', 'RETURNED'].includes(task.status) ? 1 : 0),
      waiting: current.waiting + (task.status === 'WAITING_RECEIVE' ? 1 : 0),
      completed: current.completed + (task.status === 'COMPLETED' ? 1 : 0),
      risk: current.risk + (task.overdue || task.status === 'RETURNED' || isDueSoonTask(task) ? 1 : 0),
    })
  })
  return Array.from(loads.values())
    .filter((item) => item.total > 0)
    .sort((a, b) => b.active + b.waiting - (a.active + a.waiting) || b.risk - a.risk || b.total - a.total)
})
const progressBlockedTasks = computed(() =>
  progressTasks.value.filter((task) => isProgressDependencyBlocked(task)),
)
const progressRiskTasks = computed(() =>
  progressRisks.value.filter((task) =>
    !progressBlockedTasks.value.some((blockedTask) => Number(blockedTask.id) === Number(task.id)),
  ),
)
const projectHealthText = computed(() => {
  if (!selectedProject.value) return '先选择或发布项目'
  if (!activeSelectedTasks.value.length) return '项目已建立，下一步是拆解任务'
  if (taskFlowStats.value.risk > 0) return `有 ${taskFlowStats.value.risk} 个任务需要关注`
  if (taskFlowStats.value.waiting > 0) return `有 ${taskFlowStats.value.waiting} 个任务等待接收`
  if (taskFlowStats.value.completed === taskFlowStats.value.total) return '项目任务已全部完成'
  return '项目正在正常推进'
})
const nextHomeAction = computed(() => {
  if (!spaces.value.length) {
    return { title: '先建立协作空间', text: '创建自己的团队，或者用邀请码加入已有团队。', action: '去组队', section: 'spaces' }
  }
  if (!projects.value.length) {
    return { title: '发布第一个项目', text: '项目是任务拆解和进度可视化的起点。', action: '去发布', section: 'tasks' }
  }
  if (dashboard.value?.waitingForMeCount > 0) {
    return { title: '先处理交接', text: '有人提交了结果，等你接收或打回。', action: '查看交接', section: 'tasks' }
  }
  if (dashboard.value?.myActiveTaskCount > 0) {
    return { title: '推进我的任务', text: '把正在进行的任务继续往提交状态推进。', action: '查看任务', section: 'tasks' }
  }
  if (dashboard.value?.dueSoonCount > 0) {
    return { title: '检查临近截止', text: '先把时间风险排掉，避免最后集中返工。', action: '查看进度', section: 'progress' }
  }
  return { title: '看看整体进度', text: '当前没有急件，可以查看项目进度和协作痕迹。', action: '查看进度', section: 'progress' }
})
const ganttRangeLabel = computed(() => {
  const dates = progressTasks.value
    .flatMap((task) => [task.startAt, task.dueAt])
    .filter(Boolean)
    .map((date) => new Date(date).getTime())
    .filter(Number.isFinite)
  if (!dates.length) return '未设置时间范围'
  return `${formatDate(new Date(Math.min(...dates)).toISOString())} - ${formatDate(new Date(Math.max(...dates)).toISOString())}`
})
const progressCompletionStyle = computed(() => ({
  width: `${Math.max(4, Math.min(100, Number(progress.value?.completionRate || 0)))}%`,
}))
const taskFilterOptions = [
  { key: 'ALL', label: '全部任务' },
  { key: 'MINE', label: '我的任务' },
  { key: 'OPEN', label: '可认领' },
  { key: 'REVIEW', label: '等我接收' },
  { key: 'RISK', label: '风险任务' },
]
const visibleTasks = computed(() => {
  if (taskBoardFilter.value === 'MINE') {
    return activeSelectedTasks.value.filter((task) => Number(task.assigneeId) === currentUserId.value)
  }
  if (taskBoardFilter.value === 'OPEN') {
    return activeSelectedTasks.value.filter((task) => task.status === 'UNCLAIMED')
  }
  if (taskBoardFilter.value === 'REVIEW') {
    return activeSelectedTasks.value.filter((task) => canReviewTask(task))
  }
  if (taskBoardFilter.value === 'RISK') {
    return activeSelectedTasks.value.filter((task) => isRiskTask(task))
  }
  return activeSelectedTasks.value
})
const myActionTasks = computed(() =>
  activeSelectedTasks.value.filter((task) => canStartTask(task) || canSubmitTask(task)),
)
const taskFlowStats = computed(() => ({
  total: activeSelectedTasks.value.length,
  open: activeSelectedTasks.value.filter((task) => task.status === 'UNCLAIMED').length,
  active: activeSelectedTasks.value.filter((task) => ['CLAIMED', 'IN_PROGRESS'].includes(task.status)).length,
  waiting: activeSelectedTasks.value.filter((task) => task.status === 'WAITING_RECEIVE').length,
  completed: activeSelectedTasks.value.filter((task) => task.status === 'COMPLETED').length,
  risk: activeSelectedTasks.value.filter((task) => isRiskTask(task)).length,
}))
const activityFilterOptions = [
  { key: 'ALL', label: '全部' },
  { key: 'PROJECT', label: '项目' },
  { key: 'TASK', label: '任务' },
  { key: 'SPACE', label: '空间' },
  { key: 'INVITE', label: '邀请' },
  { key: 'CLAIM', label: '认领' },
  { key: 'SUBMIT', label: '提交' },
  { key: 'REVIEW', label: '处理' },
]
const filteredActivityLogs = computed(() => {
  if (activityFilter.value === 'ALL') return activityLogs.value
  return activityLogs.value.filter((log) => getActivityLogType(log) === activityFilter.value)
})
const activityTypeStats = computed(() => activityFilterOptions
  .filter((option) => option.key !== 'ALL')
  .map((option) => ({
    ...option,
    count: activityLogs.value.filter((log) => getActivityLogType(log) === option.key).length,
  }))
  .filter((item) => item.count > 0))
const groupedActivityLogs = computed(() => {
  const groups = new Map()
  filteredActivityLogs.value.forEach((log) => {
    const key = activityDayKey(log.createdAt)
    const current = groups.get(key) || []
    groups.set(key, [...current, log])
  })
  return Array.from(groups.entries()).map(([date, logs]) => ({ date, logs }))
})
const recentActivityLogs = computed(() => filteredActivityLogs.value.slice(0, 5))

function responseData(response) {
  return response?.data?.data
}

function contextualizeTask(task) {
  if (!task) return task
  return {
    ...task,
    spaceId: normalizeId(task.spaceId) || selectedSpaceId.value,
    projectId: normalizeId(task.projectId) || selectedProjectId.value,
  }
}

function resolveTaskRoute(task) {
  const taskId = normalizeId(task?.id)
  const spaceId = normalizeId(task?.spaceId) || selectedSpaceId.value
  const projectId = normalizeId(task?.projectId) || selectedProjectId.value
  if (!spaceId || !projectId || !taskId) {
    return null
  }
  return { spaceId, projectId, taskId }
}

function setFeedback(message, isError = false) {
  errorMessage.value = isError ? message : ''
  successMessage.value = isError ? '' : message
}

async function loadAll({ keepSelection = true } = {}) {
  isLoading.value = true
  errorMessage.value = ''
  try {
    const [spaceResponse, dashboardResponse] = await Promise.all([
      fetchStudentCollaborationSpaces(),
      fetchStudentCollaborationDashboard().catch(() => null),
    ])
    spaces.value = responseData(spaceResponse) || []
    dashboard.value = responseData(dashboardResponse) || null
    const routeSpaceId = normalizeId(route.query.spaceId)
    const hasRouteSpace = spaces.value.some((item) => Number(item.id) === Number(routeSpaceId))
    if (hasRouteSpace) {
      selectedSpaceId.value = routeSpaceId
    } else if (!keepSelection || !spaces.value.some((item) => item.id === selectedSpaceId.value)) {
      selectedSpaceId.value = spaces.value[0]?.id || null
    }
    await loadSelectedSpaceBundle()
    await applyRouteTarget()
  } catch (error) {
    setFeedback(error.message || '自由协作数据加载失败', true)
  } finally {
    isLoading.value = false
  }
}

async function loadSelectedSpaceBundle() {
  if (!selectedSpaceId.value) {
    selectedSpaceDetail.value = null
    projects.value = []
    selectedProjectId.value = null
    selectedProjectDetail.value = null
    progress.value = null
    activityLogs.value = []
    return
  }
  const [spaceResponse, projectResponse, logResponse] = await Promise.all([
    fetchStudentCollaborationSpace(selectedSpaceId.value),
    fetchStudentCollaborationProjects(selectedSpaceId.value),
    fetchStudentCollaborationActivityLogs(selectedSpaceId.value).catch(() => null),
  ])
  selectedSpaceDetail.value = responseData(spaceResponse) || null
  projects.value = responseData(projectResponse) || []
  activityLogs.value = responseData(logResponse) || []
  if (!projects.value.some((item) => item.id === selectedProjectId.value)) {
    selectedProjectId.value = projects.value[0]?.id || null
  }
  await loadSelectedProjectBundle()
}

async function applyRouteTarget() {
  const routeSpaceId = normalizeId(route.query.spaceId)
  const routeTaskId = normalizeId(route.query.taskId)
  if (routeSpaceId && !routeTaskId) {
    pendingRouteTarget.value = { spaceId: null, taskId: null }
    if (spaces.value.some((item) => Number(item.id) === Number(routeSpaceId))
      && Number(selectedSpaceId.value) !== Number(routeSpaceId)) {
      selectedSpaceId.value = routeSpaceId
      selectedProjectId.value = null
      await loadSelectedSpaceBundle()
    }
    return
  }
  if (!routeTaskId || pendingRouteTarget.value.taskId === routeTaskId) {
    return
  }
  pendingRouteTarget.value = { spaceId: routeSpaceId, taskId: routeTaskId }
  const found = await locateTaskById(routeTaskId, routeSpaceId)
  if (!found) {
    setFeedback('没有找到这条通知关联的自由协作任务，可能已删除或不在当前账号空间内。', true)
    return
  }
  selectedSpaceId.value = found.spaceId
  selectedProjectId.value = found.projectId
  taskBoardFilter.value = 'ALL'
  await loadSelectedSpaceBundle()
  selectedTaskId.value = found.taskId
  highlightedTaskId.value = found.taskId
}

async function locateTaskById(taskId, preferredSpaceId = null) {
  const candidateSpaces = preferredSpaceId
    ? spaces.value.filter((space) => Number(space.id) === Number(preferredSpaceId))
    : spaces.value
  for (const space of candidateSpaces) {
    const projectResponse = await fetchStudentCollaborationProjects(space.id).catch(() => null)
    const candidateProjects = responseData(projectResponse) || []
    for (const project of candidateProjects) {
      const detailResponse = await fetchStudentCollaborationProject(space.id, project.id).catch(() => null)
      const detail = responseData(detailResponse)
      const task = (detail?.tasks || []).find((item) => Number(item.id) === Number(taskId))
      if (task) {
        return { spaceId: space.id, projectId: project.id, taskId: task.id }
      }
    }
  }
  return null
}

async function loadSelectedProjectBundle() {
  if (!selectedSpaceId.value || !selectedProjectId.value) {
    selectedProjectDetail.value = null
    progress.value = null
    return
  }
  const [projectResponse, progressResponse] = await Promise.all([
    fetchStudentCollaborationProject(selectedSpaceId.value, selectedProjectId.value),
    fetchStudentCollaborationProgress(selectedSpaceId.value, selectedProjectId.value).catch(() => null),
  ])
  selectedProjectDetail.value = responseData(projectResponse) || null
  progress.value = responseData(progressResponse) || null
}

function mergeTaskIntoList(tasks, updatedTask, routeIds) {
  if (!Array.isArray(tasks) || !updatedTask) return tasks || []
  const taskId = normalizeId(updatedTask.id) || routeIds.taskId
  const normalizedTask = {
    ...updatedTask,
    id: taskId,
    spaceId: normalizeId(updatedTask.spaceId) || routeIds.spaceId,
    projectId: normalizeId(updatedTask.projectId) || routeIds.projectId,
  }
  const hasTask = tasks.some((task) => Number(task?.id) === Number(taskId))
  if (!hasTask) return [...tasks, normalizedTask]
  return tasks.map((task) =>
    Number(task?.id) === Number(taskId)
      ? { ...task, ...normalizedTask }
      : task,
  )
}

function mergeUpdatedTask(updatedTask, routeIds) {
  if (!updatedTask || !routeIds) return
  if (selectedProjectDetail.value) {
    selectedProjectDetail.value = {
      ...selectedProjectDetail.value,
      tasks: mergeTaskIntoList(selectedProjectDetail.value.tasks, updatedTask, routeIds),
    }
  }
  if (progress.value?.tasks) {
    progress.value = {
      ...progress.value,
      tasks: mergeTaskIntoList(progress.value.tasks, updatedTask, routeIds),
    }
  }
}

function syncTaskAssigneeToFirstFlowNode(flowNodes, assigneeId) {
  if (!Array.isArray(flowNodes) || !flowNodes.length) {
    return flowNodes
  }
  const [firstNode, ...restNodes] = flowNodes
  return [
    {
      ...firstNode,
      assigneeId: assigneeId || null,
      claimable: !assigneeId,
    },
    ...restNodes,
  ]
}

function normalizeTaskStatusForAssignee(status, assigneeId) {
  if (assigneeId && status === 'UNCLAIMED') {
    return 'CLAIMED'
  }
  if (!assigneeId && status === 'CLAIMED') {
    return 'UNCLAIMED'
  }
  return status
}

async function selectSpace(spaceId) {
  if (selectedSpaceId.value === spaceId) return
  selectedSpaceId.value = spaceId
  selectedProjectId.value = null
  await loadSelectedSpaceBundle()
}

async function selectProject(projectId) {
  if (selectedProjectId.value === projectId) return
  selectedProjectId.value = projectId
  await loadSelectedProjectBundle()
}

async function handleSpaceChange(event) {
  const nextId = normalizeId(event?.target?.value)
  selectedSpaceId.value = nextId
  selectedProjectId.value = null
  await loadSelectedSpaceBundle()
}

async function handleProjectChange(event) {
  selectedProjectId.value = normalizeId(event?.target?.value)
  await loadSelectedProjectBundle()
}

async function submitCreateSpace() {
  const name = createSpaceForm.value.name.trim()
  if (!name) return setFeedback('请填写协作空间名称', true)
  isSubmitting.value = true
  try {
    const response = await createStudentCollaborationSpace({
      name,
      description: createSpaceForm.value.description.trim(),
    })
    selectedSpaceId.value = responseData(response)?.id || null
    createSpaceForm.value = { name: '', description: '' }
    setFeedback('协作空间已创建')
    await loadAll({ keepSelection: true })
  } catch (error) {
    setFeedback(error.message || '创建失败', true)
  } finally {
    isSubmitting.value = false
  }
}

async function submitJoinSpace() {
  const inviteCode = joinForm.value.inviteCode.trim()
  if (!inviteCode) return setFeedback('请输入邀请码', true)
  isSubmitting.value = true
  try {
    const response = await joinStudentCollaborationSpace(inviteCode)
    selectedSpaceId.value = responseData(response)?.id || null
    joinForm.value = { inviteCode: '' }
    setFeedback('已加入协作空间')
    await loadAll({ keepSelection: true })
  } catch (error) {
    setFeedback(error.message || '加入失败', true)
  } finally {
    isSubmitting.value = false
  }
}

async function leaveSelectedSpace() {
  if (!selectedSpace.value) return
  const message = canOrganize.value
    ? '如果空间只剩你一人，退出后空间会被归档。确认退出吗？'
    : `确认退出「${selectedSpace.value.name}」吗？`
  if (!window.confirm(message)) return
  isSubmitting.value = true
  try {
    await leaveStudentCollaborationSpace(selectedSpace.value.id)
    selectedSpaceId.value = null
    selectedProjectId.value = null
    selectedTaskId.value = null
    setFeedback('已退出协作空间')
    await loadAll({ keepSelection: false })
  } catch (error) {
    setFeedback(error.message || '退出空间失败', true)
  } finally {
    isSubmitting.value = false
  }
}

async function removeSpaceMember(member) {
  if (!selectedSpace.value || !member?.studentId) return
  if (!window.confirm(`确认将「${member.name || '该成员'}」移出协作空间吗？`)) return
  isSubmitting.value = true
  try {
    await removeStudentCollaborationMember(selectedSpace.value.id, member.studentId)
    setFeedback('成员已移出')
    await loadSelectedSpaceBundle()
    await loadActivityLogs().catch(() => {})
  } catch (error) {
    setFeedback(error.message || '移除成员失败', true)
  } finally {
    isSubmitting.value = false
  }
}

async function transferSpaceOwner(member) {
  if (!selectedSpace.value || !member?.studentId) return
  if (!window.confirm(`确认把「${selectedSpace.value.name}」的发起人转让给「${member.name || '该成员'}」吗？`)) return
  isSubmitting.value = true
  try {
    await transferStudentCollaborationOwner(selectedSpace.value.id, member.studentId)
    setFeedback('发起人已转让')
    await loadSelectedSpaceBundle()
    await loadActivityLogs().catch(() => {})
  } catch (error) {
    setFeedback(error.message || '转让发起人失败', true)
  } finally {
    isSubmitting.value = false
  }
}

async function generateInviteCode() {
  if (!selectedSpace.value || !canOrganize.value) return
  isSubmitting.value = true
  try {
    await generateStudentCollaborationInviteCode(selectedSpace.value.id)
    setFeedback('邀请码已生成')
    await loadSelectedSpaceBundle()
  } catch (error) {
    setFeedback(error.message || '邀请码生成失败', true)
  } finally {
    isSubmitting.value = false
  }
}

async function copyInviteCode(code) {
  if (!code) return
  try {
    await navigator.clipboard.writeText(code)
    copiedCode.value = code
    window.setTimeout(() => {
      if (copiedCode.value === code) copiedCode.value = ''
    }, 1600)
  } catch {
    setFeedback('复制失败，请手动复制', true)
  }
}

async function submitCreateProject() {
  if (!selectedSpaceId.value) return setFeedback('请先选择协作空间', true)
  const title = projectForm.value.title.trim()
  if (!title) return setFeedback('请填写项目名称', true)
  if (!isValidDateRange(projectForm.value.startAt, projectForm.value.dueAt)) {
    return setFeedback('截止时间不能早于开始时间', true)
  }
  isSubmitting.value = true
  try {
    const response = await createStudentCollaborationProject(selectedSpaceId.value, {
      title,
      description: projectForm.value.description.trim(),
      startAt: toDateTime(projectForm.value.startAt),
      dueAt: toDateTime(projectForm.value.dueAt),
      attachments: normalizeAttachments(projectForm.value.attachments),
    })
    selectedProjectId.value = responseData(response)?.id || null
    projectForm.value = { title: '', description: '', startAt: '', dueAt: '', attachments: [] }
    activeCreatePanel.value = ''
    setFeedback('项目已创建')
    await loadSelectedSpaceBundle()
  } catch (error) {
    setFeedback(error.message || '项目创建失败', true)
  } finally {
    isSubmitting.value = false
  }
}

function resetProjectWizard() {
  projectWizardForm.value = {
    step: 'project',
    project: { title: '', description: '', startAt: '', dueAt: '', attachments: [] },
    tasks: [
      {
        title: '',
        description: '',
        deliverableRequirements: '',
        assigneeId: '',
        receiverId: '',
        startAt: '',
        dueAt: '',
        dependsOnLocalIds: [],
        attachments: [],
        flowNodes: [createEmptyFlowNode()],
      },
    ],
  }
}

function addWizardTask() {
  projectWizardForm.value = {
    ...projectWizardForm.value,
    tasks: [
      ...projectWizardForm.value.tasks,
      {
        title: '',
        description: '',
        deliverableRequirements: '',
        assigneeId: '',
        receiverId: '',
        startAt: '',
        dueAt: '',
        dependsOnLocalIds: [],
        attachments: [],
        flowNodes: [createEmptyFlowNode()],
      },
    ],
  }
}

function removeWizardTask(index) {
  if (projectWizardForm.value.tasks.length <= 1) return
  const nextTasks = projectWizardForm.value.tasks
    .filter((_, taskIndex) => taskIndex !== index)
    .map((task) => ({
      ...task,
      dependsOnLocalIds: task.dependsOnLocalIds
        .map(Number)
        .filter((localId) => localId !== index + 1)
        .map((localId) => localId > index + 1 ? localId - 1 : localId),
    }))
  projectWizardForm.value = {
    ...projectWizardForm.value,
    tasks: nextTasks,
  }
}

function setWizardStep(step) {
  projectWizardForm.value = {
    ...projectWizardForm.value,
    step,
  }
}

async function submitProjectWizard() {
  if (!selectedSpaceId.value) return setFeedback('请先选择协作空间', true)
  const projectTitle = projectWizardForm.value.project.title.trim()
  if (!projectTitle) return setFeedback('请填写项目名称', true)
  if (!isValidDateRange(projectWizardForm.value.project.startAt, projectWizardForm.value.project.dueAt)) {
    return setFeedback('项目截止时间不能早于开始时间', true)
  }
  const validTasks = projectWizardForm.value.tasks
    .map((task, index) => ({ ...task, localId: `task-${index + 1}`, title: task.title.trim() }))
    .filter((task) => task.title)
  if (!validTasks.length) return setFeedback('至少需要拆解一个任务', true)
  const invalidTimeTask = validTasks.find((task) => !isValidDateRange(task.startAt, task.dueAt))
  if (invalidTimeTask) return setFeedback(`任务「${invalidTimeTask.title}」截止时间不能早于开始时间`, true)

  isSubmitting.value = true
  try {
    const localIdByOldIndex = new Map(validTasks.map((task, index) => [index + 1, task.localId]))
    const projectResponse = await createStudentCollaborationProjectWithTasks(selectedSpaceId.value, {
      project: {
        title: projectTitle,
        description: projectWizardForm.value.project.description.trim(),
        startAt: toDateTime(projectWizardForm.value.project.startAt),
        dueAt: toDateTime(projectWizardForm.value.project.dueAt),
        attachments: normalizeAttachments(projectWizardForm.value.project.attachments),
      },
      tasks: validTasks.map((task) => ({
        localId: task.localId,
        title: task.title,
        description: String(task.description || '').trim(),
        deliverableRequirements: String(task.deliverableRequirements || '').trim(),
        assigneeId: normalizeId(task.assigneeId),
        receiverId: normalizeId(task.receiverId),
        startAt: toDateTime(task.startAt),
        dueAt: toDateTime(task.dueAt),
        dependsOnLocalIds: (task.dependsOnLocalIds || [])
          .map((localId) => localIdByOldIndex.get(Number(localId)))
          .filter(Boolean),
        attachments: normalizeAttachments(task.attachments),
        flowNodes: normalizeFlowNodes(task.flowNodes),
      })),
    })
    const projectId = responseData(projectResponse)?.id
    if (!projectId) throw new Error('项目创建成功，但未返回项目 ID')
    selectedProjectId.value = projectId

    resetProjectWizard()
    activeCreatePanel.value = ''
    setFeedback(`项目已发布，并创建 ${validTasks.length} 个任务`)
    await loadSelectedSpaceBundle()
    await loadActivityLogs()
  } catch (error) {
    setFeedback(error.message || '发布协作项目失败', true)
  } finally {
    isSubmitting.value = false
  }
}

async function submitCreateTask() {
  if (!selectedSpaceId.value || !selectedProjectId.value) return setFeedback('请先选择项目', true)
  const title = taskForm.value.title.trim()
  if (!title) return setFeedback('请填写任务名称', true)
  if (!isValidDateRange(taskForm.value.startAt, taskForm.value.dueAt)) {
    return setFeedback('截止时间不能早于开始时间', true)
  }
  isSubmitting.value = true
  try {
    await createStudentCollaborationTask(selectedSpaceId.value, selectedProjectId.value, {
      title,
      description: taskForm.value.description.trim(),
      deliverableRequirements: taskForm.value.deliverableRequirements.trim(),
      assigneeId: normalizeId(taskForm.value.assigneeId),
      receiverId: normalizeId(taskForm.value.receiverId),
      startAt: toDateTime(taskForm.value.startAt),
      dueAt: toDateTime(taskForm.value.dueAt),
      dependsOnTaskIds: taskForm.value.dependsOnTaskIds.map(Number).filter(Boolean),
      attachments: normalizeAttachments(taskForm.value.attachments),
      flowNodes: normalizeFlowNodes(taskForm.value.flowNodes),
    })
    taskForm.value = {
      title: '',
      description: '',
      deliverableRequirements: '',
      assigneeId: '',
      receiverId: '',
      startAt: '',
      dueAt: '',
      dependsOnTaskIds: [],
      attachments: [],
      flowNodes: [createEmptyFlowNode()],
    }
    activeCreatePanel.value = ''
    setFeedback('任务已创建')
    await loadSelectedProjectBundle()
    await loadActivityLogs()
  } catch (error) {
    setFeedback(error.message || '任务创建失败', true)
  } finally {
    isSubmitting.value = false
  }
}

function startEditProject() {
  if (!selectedProject.value || !canOrganize.value) return
  editingProject.value = true
  projectEditForm.value = {
    title: selectedProject.value.title || '',
    description: selectedProject.value.description || '',
    startAt: toInputDateTime(selectedProject.value.startAt),
    dueAt: toInputDateTime(selectedProject.value.dueAt),
    status: selectedProject.value.status || 'ACTIVE',
    attachments: Array.isArray(selectedProject.value.attachments) ? [...selectedProject.value.attachments] : [],
  }
}

function cancelEditProject() {
  editingProject.value = false
}

async function submitEditProject() {
  if (!selectedSpaceId.value || !selectedProjectId.value) return setFeedback('请先选择项目', true)
  const title = projectEditForm.value.title.trim()
  if (!title) return setFeedback('请填写项目名称', true)
  if (!isValidDateRange(projectEditForm.value.startAt, projectEditForm.value.dueAt)) {
    return setFeedback('截止时间不能早于开始时间', true)
  }
  isSubmitting.value = true
  try {
    await updateStudentCollaborationProject(selectedSpaceId.value, selectedProjectId.value, {
      title,
      description: projectEditForm.value.description.trim(),
      startAt: toDateTime(projectEditForm.value.startAt),
      dueAt: toDateTime(projectEditForm.value.dueAt),
      status: projectEditForm.value.status,
      attachments: normalizeAttachments(projectEditForm.value.attachments),
    })
    editingProject.value = false
    setFeedback('项目已更新')
    await loadSelectedSpaceBundle()
  } catch (error) {
    setFeedback(error.message || '项目更新失败', true)
  } finally {
    isSubmitting.value = false
  }
}

function startEditTask(task) {
  if (!task || !canOrganize.value) return
  editingTaskId.value = task.id
  taskEditForm.value = {
    title: task.title || '',
    description: task.description || '',
    deliverableRequirements: task.deliverableRequirements || '',
    assigneeId: task.assigneeId || '',
    receiverId: task.receiverId || '',
    startAt: toInputDateTime(task.startAt),
    dueAt: toInputDateTime(task.dueAt),
    status: task.status || '',
    dependsOnTaskIds: Array.isArray(task.dependsOnTaskIds) ? [...task.dependsOnTaskIds] : [],
    attachments: Array.isArray(task.attachments) ? [...task.attachments] : [],
    flowNodes: cloneFlowNodes(task.flowNodes, task.title || ''),
  }
}

function cancelEditTask() {
  editingTaskId.value = null
}

async function submitEditTask(task) {
  const routeIds = resolveTaskRoute(task)
  if (!routeIds) return setFeedback('任务参数缺失，请刷新后重试', true)
  const title = taskEditForm.value.title.trim()
  if (!title) return setFeedback('请填写任务名称', true)
  if (!isValidDateRange(taskEditForm.value.startAt, taskEditForm.value.dueAt)) {
    return setFeedback('截止时间不能早于开始时间', true)
  }
  isSubmitting.value = true
  try {
    const assigneeId = normalizeId(taskEditForm.value.assigneeId)
    const receiverId = normalizeId(taskEditForm.value.receiverId)
    const flowNodes = syncTaskAssigneeToFirstFlowNode(
      normalizeFlowNodes(taskEditForm.value.flowNodes),
      assigneeId,
    )
    const status = normalizeTaskStatusForAssignee(taskEditForm.value.status, assigneeId)
    const response = await updateStudentCollaborationTask(routeIds.spaceId, routeIds.projectId, routeIds.taskId, {
      title,
      description: taskEditForm.value.description.trim(),
      deliverableRequirements: taskEditForm.value.deliverableRequirements.trim(),
      assigneeId,
      receiverId,
      startAt: toDateTime(taskEditForm.value.startAt),
      dueAt: toDateTime(taskEditForm.value.dueAt),
      status,
      dependsOnTaskIds: taskEditForm.value.dependsOnTaskIds.map(Number).filter(Boolean),
      attachments: normalizeAttachments(taskEditForm.value.attachments),
      flowNodes,
    })
    const updatedTask = responseData(response)
    mergeUpdatedTask(updatedTask, routeIds)
    editingTaskId.value = null
    setFeedback('任务已更新')
    await loadSelectedProjectBundle()
    mergeUpdatedTask(updatedTask, routeIds)
    await loadActivityLogs()
  } catch (error) {
    setFeedback(error.message || '任务更新失败', true)
  } finally {
    isSubmitting.value = false
  }
}

async function archiveCurrentProject() {
  if (!selectedSpaceId.value || !selectedProject.value) return
  if (!window.confirm(`确认归档项目「${selectedProject.value.title}」吗？归档后项目会从活跃列表隐藏，未完成任务也会停止流转。`)) return
  isSubmitting.value = true
  try {
    await archiveStudentCollaborationProject(selectedSpaceId.value, selectedProject.value.id)
    setFeedback('项目已归档')
    selectedProjectId.value = null
    selectedTaskId.value = null
    await loadSelectedSpaceBundle()
    await loadActivityLogs().catch(() => {})
  } catch (error) {
    setFeedback(error.message || '项目归档失败', true)
  } finally {
    isSubmitting.value = false
  }
}

async function archiveTask(task) {
  const routeIds = resolveTaskRoute(task)
  if (!routeIds) return setFeedback('任务参数缺失，请刷新后重试', true)
  if (!window.confirm(`确认归档任务「${task.title}」吗？归档后该任务会从看板隐藏。`)) return
  isSubmitting.value = true
  try {
    await archiveStudentCollaborationTask(routeIds.spaceId, routeIds.projectId, routeIds.taskId)
    setFeedback('任务已归档')
    selectedTaskId.value = null
    highlightedTaskId.value = null
    await loadSelectedProjectBundle()
    await loadActivityLogs().catch(() => {})
  } catch (error) {
    setFeedback(error.message || '任务归档失败', true)
  } finally {
    isSubmitting.value = false
  }
}

async function startTaskProgress(task) {
  const routeIds = resolveTaskRoute(task)
  if (!routeIds) return setFeedback('任务参数缺失，请刷新后重试', true)
  if (!canStartTask(task)) return setFeedback('只有负责人可以开始处理该任务', true)
  isSubmitting.value = true
  try {
    await updateStudentCollaborationTask(routeIds.spaceId, routeIds.projectId, routeIds.taskId, { status: 'IN_PROGRESS' })
    setFeedback('任务已开始处理')
    await loadSelectedProjectBundle()
    await loadActivityLogs()
  } catch (error) {
    setFeedback(error.message || '开始处理失败', true)
  } finally {
    isSubmitting.value = false
  }
}
async function claimTask(task) {
  const routeIds = resolveTaskRoute(task)
  if (!routeIds) return setFeedback('任务参数缺失，请刷新后重试', true)
  isSubmitting.value = true
  try {
    const node = currentFlowNode(task)
    if (node?.id && task.status === 'UNCLAIMED') {
      await claimStudentCollaborationFlowNode(routeIds.spaceId, routeIds.projectId, routeIds.taskId, node.id)
    } else {
      await claimStudentCollaborationTask(routeIds.spaceId, routeIds.projectId, routeIds.taskId)
    }
    setFeedback(node?.id ? '协作环节已认领' : '任务已认领')
    await loadSelectedProjectBundle()
    await loadActivityLogs()
  } catch (error) {
    setFeedback(error.message || '认领失败', true)
  } finally {
    isSubmitting.value = false
  }
}

async function submitTaskResult(task) {
  const routeIds = resolveTaskRoute(task)
  if (!routeIds) return setFeedback('任务参数缺失，请刷新后重试', true)
  const draft = taskSubmitDrafts.value[task.id] || {}
  const content = String(draft.content || '').trim()
  const links = String(draft.links || '').trim()
  const files = Array.isArray(draft.files) ? draft.files : []
  if (!content && !links && !files.length) return setFeedback('请填写提交说明或上传材料', true)
  isSubmitting.value = true
  try {
    await submitStudentCollaborationTask(routeIds.spaceId, routeIds.projectId, routeIds.taskId, {
      flowNodeId: currentFlowNode(task)?.id || null,
      content: buildSubmissionContent({ text: content, link: links, files }),
      linksJson: links
        ? JSON.stringify([{ title: '任务链接', url: links }])
        : '',
      attachmentsJson: files.length
        ? JSON.stringify(files.map((file) => ({
          type: 'file',
          name: String(file?.name || '附件').trim(),
          value: String(file?.url || file?.value || '').trim(),
          size: file?.size ?? null,
        })).filter((file) => file.value))
        : '',
    })
    taskSubmitDrafts.value = { ...taskSubmitDrafts.value, [task.id]: { content: '', links: '', files: [] } }
    setFeedback('任务结果已提交，等待接收')
    await loadSelectedProjectBundle()
    await loadActivityLogs()
  } catch (error) {
    setFeedback(error.message || '提交失败', true)
  } finally {
    isSubmitting.value = false
  }
}

async function reviewTask(task, accepted) {
  const routeIds = resolveTaskRoute(task)
  if (!routeIds) return setFeedback('任务参数缺失，请刷新后重试', true)
  const draft = reviewDrafts.value[task.id] || {}
  isSubmitting.value = true
  try {
    const action = accepted ? acceptStudentCollaborationTask : returnStudentCollaborationTask
    await action(routeIds.spaceId, routeIds.projectId, routeIds.taskId, { comment: String(draft.comment || '').trim() })
    reviewDrafts.value = { ...reviewDrafts.value, [task.id]: { comment: '' } }
    setFeedback(accepted ? '任务已接收' : '任务已打回')
    await loadSelectedProjectBundle()
    await loadActivityLogs()
  } catch (error) {
    setFeedback(error.message || '处理失败', true)
  } finally {
    isSubmitting.value = false
  }
}

async function loadActivityLogs() {
  if (!selectedSpaceId.value) return
  const response = await fetchStudentCollaborationActivityLogs(selectedSpaceId.value, selectedProjectId.value)
  activityLogs.value = responseData(response) || []
}

function switchSection(section) {
  router.push({ path: section === 'home' ? '/student/free' : `/student/free/${section}` })
}

async function openTaskContext(task, options = {}) {
  const routeIds = resolveTaskRoute(task)
  if (!routeIds) return setFeedback('任务参数缺失，请刷新后重试', true)
  selectedSpaceId.value = routeIds.spaceId
  selectedProjectId.value = routeIds.projectId
  highlightedTaskId.value = routeIds.taskId
  selectedTaskId.value = routeIds.taskId
  await loadSelectedSpaceBundle()
  if (!options.stayInSection) switchSection('tasks')
}

function openTaskDetail(task) {
  selectedTaskId.value = task?.id || null
  highlightedTaskId.value = task?.id || null
}

function closeTaskDetail() {
  selectedTaskId.value = null
  highlightedTaskId.value = null
}

function tasksByColumn(columnKey) {
  return visibleTasks.value.filter((task) => {
    if (columnKey === 'CLAIMED') return ['CLAIMED', 'IN_PROGRESS'].includes(task.status)
    return task.status === columnKey
  })
}

function memberName(userId) {
  const member = selectedMembers.value.find((item) => Number(item.studentId) === Number(userId))
  return member?.name || '成员'
}

function getActivityLogType(log) {
  const rawType = String(log?.type || log?.actionType || log?.eventType || '').trim().toUpperCase()
  if (rawType.includes('INVITE')) return 'INVITE'
  if (rawType.includes('CLAIM')) return 'CLAIM'
  if (rawType.includes('SUBMIT')) return 'SUBMIT'
  if (rawType.includes('ACCEPT') || rawType.includes('RETURN') || rawType.includes('REVIEW')) return 'REVIEW'
  if (rawType.includes('PROJECT')) return 'PROJECT'
  if (rawType.includes('SPACE')) return 'SPACE'
  if (rawType.includes('TASK')) return 'TASK'
  const summary = String(log?.summary || '').toLowerCase()
  if (summary.includes('邀请')) return 'INVITE'
  if (summary.includes('接收')) return 'REVIEW'
  if (summary.includes('认领')) return 'CLAIM'
  if (summary.includes('提交')) return 'SUBMIT'
  if (summary.includes('打回') || summary.includes('处理') || summary.includes('审核')) return 'REVIEW'
  if (summary.includes('项目')) return 'PROJECT'
  if (summary.includes('任务')) return 'TASK'
  if (summary.includes('空间')) return 'SPACE'
  return 'TASK'
}

function activityTypeLabel(log) {
  const type = getActivityLogType(log)
  return {
    ALL: '全部',
    PROJECT: '项目',
    TASK: '任务',
    SPACE: '空间',
    INVITE: '邀请',
    CLAIM: '认领',
    SUBMIT: '提交',
    REVIEW: '处理',
  }[type] || '任务'
}

function activityMeta(log) {
  const summary = String(log?.summary || '')
  if (summary.includes('邀请')) return '空间协作'
  if (summary.includes('创建项目') || summary.includes('更新项目')) return '项目流转'
  if (summary.includes('创建任务') || summary.includes('更新任务')) return '任务发布'
  if (summary.includes('认领')) return '任务认领'
  if (summary.includes('提交')) return '任务提交'
  if (summary.includes('接收') || summary.includes('打回')) return '处理结果'
  return '协作动态'
}

function activityActionLabel(log) {
  const summary = String(log?.summary || '')
  if (summary.includes('打回')) return '打回重做'
  if (summary.includes('接收')) return '完成接收'
  if (summary.includes('提交')) return '提交结果'
  if (summary.includes('认领')) return '认领任务'
  if (summary.includes('创建任务')) return '拆解任务'
  if (summary.includes('更新任务')) return '调整任务'
  if (summary.includes('创建项目')) return '发布项目'
  if (summary.includes('更新项目')) return '调整项目'
  if (summary.includes('邀请')) return '邀请成员'
  return activityTypeLabel(log)
}

function resolveActivityTask(log) {
  const explicitId = Number(log?.taskId || log?.targetTaskId || log?.entityId || 0)
  if (explicitId) {
    const task = selectedTasks.value.find((item) => Number(item.id) === explicitId)
    if (task) return task
  }
  const summary = String(log?.summary || '')
  if (!summary) return null
  return selectedTasks.value.find((task) => task?.title && summary.includes(task.title)) || null
}

function activityDayKey(value) {
  if (!value) return '未记录日期'
  const date = String(value).slice(0, 10)
  if (!date || date.length < 10) return '未记录日期'
  const today = new Date().toISOString().slice(0, 10)
  const yesterday = new Date(Date.now() - 86400000).toISOString().slice(0, 10)
  if (date === today) return '今天'
  if (date === yesterday) return '昨天'
  return date
}

function canSubmitTask(task) {
  const node = currentFlowNode(task)
  const ownerId = node?.assigneeId || task.assigneeId
  return Number(ownerId) === currentUserId.value
    && !isDependencyBlocked(task)
    && ['IN_PROGRESS', 'RETURNED'].includes(task.status)
    && !['WAITING_RECEIVE', 'COMPLETED'].includes(task.status)
}

function canStartTask(task) {
  const node = currentFlowNode(task)
  const ownerId = node?.assigneeId || task?.assigneeId
  return Number(ownerId) === currentUserId.value
    && ['CLAIMED', 'RETURNED'].includes(task?.status)
    && !isDependencyBlocked(task)
}

function canReviewTask(task) {
  if (task.status !== 'WAITING_RECEIVE') return false
  return !task.receiverId || Number(task.receiverId) === currentUserId.value
}

function formatDate(value) {
  if (!value) return '未设置'
  const text = String(value).replace('T', ' ').slice(0, 16)
  const [datePart, timePart] = text.split(' ')
  if (!datePart || !timePart) return text
  return `${datePart.slice(5)} ${timePart}`
}

function daysUntil(value) {
  if (!value) return '未设置'
  const diff = new Date(value).getTime() - Date.now()
  if (!Number.isFinite(diff)) return '未设置'
  const days = Math.ceil(diff / 86400000)
  if (days < 0) return `逾期 ${Math.abs(days)} 天`
  if (days === 0) return '今天截止'
  return `${days} 天后`
}

function isDueSoonTask(task) {
  if (!task?.dueAt || ['COMPLETED'].includes(task.status)) return false
  const diff = new Date(task.dueAt).getTime() - Date.now()
  return diff >= 0 && diff <= 72 * 60 * 60 * 1000
}

function isRiskTask(task) {
  return Boolean(task?.overdue || task?.status === 'RETURNED' || isDependencyBlocked(task) || isDueSoonTask(task))
}

function nextTaskAction(task) {
  if (!task) return { label: '未选中', tone: 'neutral', hint: '' }
  if (isDependencyBlocked(task)) {
    return { label: '等待前置完成', tone: 'blocked', hint: dependencyNames(task) }
  }
  if (task.status === 'UNCLAIMED') {
    return { label: hasFlowNodes(task) ? '可认领环节' : '可认领', tone: 'open', hint: hasFlowNodes(task) ? '认领当前开放环节' : '先把任务拿到手里' }
  }
  const node = currentFlowNode(task)
  const ownerId = node?.assigneeId || task.assigneeId
  if (Number(ownerId) === currentUserId.value && ['CLAIMED', 'RETURNED'].includes(task.status)) {
    return { label: '待开始处理', tone: 'active', hint: '开始后再提交结果' }
  }
  if (canSubmitTask(task)) {
    return { label: '可提交结果', tone: 'active', hint: '提交后进入接收队列' }
  }
  if (canReviewTask(task)) {
    return { label: '待接收处理', tone: 'review', hint: '完成接收或打回' }
  }
  if (task.status === 'RETURNED') {
    return { label: '需要重新处理', tone: 'warn', hint: '打回后重新提交' }
  }
  if (task.status === 'COMPLETED') {
    return { label: '已完成', tone: 'done', hint: '当前流程已结束' }
  }
  if (task.status === 'WAITING_RECEIVE') {
    return { label: '等待接收', tone: 'review', hint: '等待接收人处理' }
  }
  return { label: '推进中', tone: 'active', hint: '继续协作即可' }
}

function nextTaskActor(task) {
  if (!task) return '未选择任务'
  const node = currentFlowNode(task)
  if (node) return node.assigneeName || (node.claimable ? '待成员认领当前环节' : '待指定负责人')
  if (isDependencyBlocked(task)) return '前置任务负责人'
  if (task.status === 'UNCLAIMED') return '可由任一成员认领'
  if (Number(task.assigneeId) === currentUserId.value && ['CLAIMED', 'RETURNED'].includes(task.status)) {
    return task.assigneeName || '负责人'
  }
  if (canSubmitTask(task) || ['CLAIMED', 'IN_PROGRESS', 'RETURNED'].includes(task.status)) {
    return task.assigneeName || '负责人'
  }
  if (task.status === 'WAITING_RECEIVE') return task.receiverName || '任一接收成员'
  if (task.status === 'COMPLETED') return '流程已完成'
  return task.assigneeName || '协作者'
}

function taskActionClass(task) {
  return `tone-${nextTaskAction(task).tone || 'neutral'}`
}

function taskActionDescription(task) {
  if (!task) return ''
  const action = nextTaskAction(task)
  const actor = nextTaskActor(task)
  if (action.hint) return `${actor} · ${action.hint}`
  return actor
}

function taskVersionCount(task) {
  if (Array.isArray(task?.submissions)) return task.submissions.length
  return task?.latestSubmission ? 1 : 0
}

function submissionStatusLabel(status) {
  const value = String(status || '').toUpperCase()
  return {
    PENDING: '待接收',
    ACCEPTED: '已接收',
    RETURNED: '已打回',
    SUBMITTED: '已提交',
  }[value] || status || '已提交'
}

function dependencyTone(task) {
  const status = dependencyStatus(task)
  if (!status.total) return 'none'
  if (status.completed === status.total) return 'done'
  return 'blocked'
}

function riskLabel(task) {
  if (task?.overdue) return '已逾期'
  if (task?.status === 'RETURNED') return '被打回'
  if (isDependencyBlocked(task)) return '前置阻塞'
  if (task?.status === 'WAITING_RECEIVE') return '等待接收'
  if (isDueSoonTask(task)) return '临近截止'
  return '需关注'
}

function toDateTime(value) {
  return value ? `${value}:00` : null
}

function isValidDateRange(startValue, dueValue) {
  if (!startValue || !dueValue) return true
  const startTime = new Date(startValue).getTime()
  const dueTime = new Date(dueValue).getTime()
  if (!Number.isFinite(startTime) || !Number.isFinite(dueTime)) return true
  return dueTime >= startTime
}

function toInputDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16).replace(' ', 'T') : ''
}

function normalizeId(value) {
  const id = Number(value)
  return Number.isFinite(id) && id > 0 ? id : null
}

function normalizeAttachments(files) {
  if (!Array.isArray(files)) return []
  return files
    .map((file) => ({
      type: file?.type || file?.kind || 'file',
      name: String(file?.name || '').trim(),
      url: String(file?.url || file?.value || '').trim(),
      size: Number.isFinite(Number(file?.size)) ? Number(file.size) : null,
    }))
    .filter((file) => file.url)
}

function normalizeFlowNodes(nodes) {
  if (!Array.isArray(nodes)) return []
  return nodes
    .map((node) => {
      const title = String(node?.title || '').trim()
      const assigneeId = normalizeId(node?.assigneeId)
      const claimable = Boolean(node?.claimable) || !assigneeId
      return {
        title,
        description: String(node?.description || '').trim(),
        assigneeId,
        claimable,
      }
    })
    .filter((node) => node.title)
}

function cloneFlowNodes(nodes, fallbackTitle = '') {
  const source = Array.isArray(nodes) && nodes.length ? nodes : []
  const cloned = source.map((node) => ({
    title: node?.title || '',
    description: node?.description || '',
    assigneeId: node?.assigneeId || '',
    claimable: Boolean(node?.claimable) || !node?.assigneeId,
  }))
  return cloned.length ? cloned : [{ ...createEmptyFlowNode(), title: fallbackTitle }]
}

function addTaskFormFlowNode(form) {
  form.flowNodes = [...(form.flowNodes || []), createEmptyFlowNode()]
}

function removeTaskFormFlowNode(form, index) {
  const nodes = Array.isArray(form.flowNodes) ? form.flowNodes : []
  form.flowNodes = nodes.length <= 1 ? [createEmptyFlowNode()] : nodes.filter((_, nodeIndex) => nodeIndex !== index)
}

function currentFlowNode(task) {
  return task?.currentFlowNode || (Array.isArray(task?.flowNodes)
    ? task.flowNodes.find((node) => node?.current)
    : null)
}

function hasFlowNodes(task) {
  return Array.isArray(task?.flowNodes) && task.flowNodes.length > 0
}

function avatarSrc(member) {
  const key = member?.studentId || member?.id || member?.name || ''
  if (key && failedAvatarKeys.value[key]) return ''
  return resolveMediaUrl(member?.avatarUrl || '')
}

function markAvatarFailed(member) {
  const key = member?.studentId || member?.id || member?.name || ''
  if (!key) return
  failedAvatarKeys.value = { ...failedAvatarKeys.value, [key]: true }
}

function dependencyNames(task) {
  return dependencyTasks(task)
    .map((item) => item.title)
    .filter(Boolean)
    .join('、')
}

function dependencyTasks(task) {
  const ids = Array.isArray(task?.dependsOnTaskIds) ? task.dependsOnTaskIds : []
  if (!ids.length) return []
  return ids.map((id) => {
    const dependency = selectedTasks.value.find((item) => Number(item.id) === Number(id))
    if (dependency) return dependency
    return {
      id,
      title: '已删除或不可见的前置任务',
      status: 'UNKNOWN',
      assigneeName: '未知成员',
      missing: true,
    }
  })
}

function dependencyStatus(task) {
  const dependencies = dependencyTasks(task)
  const completed = dependencies.filter((item) => item.status === 'COMPLETED').length
  return {
    total: dependencies.length,
    completed,
    blocked: dependencies.length > 0 && completed < dependencies.length && task?.status !== 'COMPLETED',
  }
}

function isDependencyBlocked(task) {
  return dependencyStatus(task).blocked
}

function dependencyLabel(task) {
  const status = dependencyStatus(task)
  if (!status.total) return ''
  if (status.completed === status.total) return '前置已完成'
  return `前置 ${status.completed}/${status.total} 完成`
}

function progressDependencyTasks(task) {
  const links = progressDependencyLinks.value.filter((link) => Number(link.task.id) === Number(task?.id))
  return links.map((link) => link.dependsOn)
}

function progressDependencyStatus(task) {
  const dependencies = progressDependencyTasks(task)
  const completed = dependencies.filter((item) => item.status === 'COMPLETED').length
  return {
    total: dependencies.length,
    completed,
    blocked: dependencies.length > 0 && completed < dependencies.length && task?.status !== 'COMPLETED',
  }
}

function isProgressDependencyBlocked(task) {
  return progressDependencyStatus(task).blocked
}

function progressDependencyLabel(task) {
  const status = progressDependencyStatus(task)
  if (!status.total) return ''
  if (status.completed === status.total) return '前置已完成'
  return `前置 ${status.completed}/${status.total} 完成`
}

function submissionLinks(task) {
  return submissionMaterials(task?.latestSubmission)
}

function submissionMaterials(submission) {
  const contentParsed = parseSubmissionContent(submission?.content)
  const attachmentParsed = parseSubmissionContent(JSON.stringify({
    attachments: parseAttachmentJson(submission?.attachmentsJson),
  }))
  const linkItems = parseAttachmentJson(submission?.linksJson)
    .map((item, index) => ({
      type: 'link',
      name: String(item?.title || item?.name || `材料链接 ${index + 1}`).trim(),
      value: String(item?.url || item?.value || '').trim(),
      size: null,
    }))
    .filter((item) => item.value)
  const seen = new Set()
  return [
    ...contentParsed.attachments,
    ...attachmentParsed.attachments,
    ...linkItems,
  ].filter((item) => {
    const value = String(item?.value || '').trim()
    if (!value || seen.has(value)) return false
    seen.add(value)
    return true
  })
}

function submissionText(submission) {
  const parsed = parseSubmissionContent(submission?.content)
  return parsed.text || String(submission?.content || '').trim()
}

function parseAttachmentJson(raw) {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed
  } catch {
    return []
  }
}

function toggleSubmissionHistory(taskId) {
  expandedSubmissionTaskId.value = expandedSubmissionTaskId.value === taskId ? null : taskId
}

watch(activeSection, async (section) => {
  if (section === 'contributions') await loadActivityLogs().catch(() => {})
})

watch(
  () => route.query,
  async () => {
    await applyRouteTarget()
  },
)

watch(selectedProjectId, async () => {
  if (activeSection.value === 'contributions') await loadActivityLogs().catch(() => {})
  if (selectedTaskId.value && !selectedTasks.value.some((task) => task.id === selectedTaskId.value)) {
    selectedTaskId.value = null
  }
})

onMounted(() => {
  loadAll({ keepSelection: false })
})
</script>

<template>
  <main class="free-view">
    <FreeCollabCommandBar
      :section-meta="sectionMeta"
      :selected-space-id="selectedSpaceId"
      :selected-project-id="selectedProjectId"
      :spaces="spaces"
      :projects="projects"
      :handle-space-change="handleSpaceChange"
      :handle-project-change="handleProjectChange"
      :refresh="() => loadAll({ keepSelection: true })"
    />

    <div v-if="errorMessage || successMessage" class="feedback-line" :class="{ error: errorMessage }">
      {{ errorMessage || successMessage }}
    </div>

    <section v-if="isLoading" class="loading-panel">
      <i></i>
      <span>正在整理自由协作数据</span>
    </section>

    <template v-else>
      <FreeCollabHomeSection
        v-if="activeSection === 'home'"
        :display-name="displayName"
        :dashboard="dashboard"
        :workflow-steps="workflowSteps"
        :next-home-action="nextHomeAction"
        :status-labels="statusLabels"
        :switch-section="switchSection"
        :open-task-context="openTaskContext"
        :format-date="formatDate"
        :activity-type-label="activityTypeLabel"
      />

      <FreeCollabSpacesSection
        v-else-if="activeSection === 'spaces'"
        :spaces="spaces"
        :selected-space="selectedSpace"
        :selected-space-id="selectedSpaceId"
        :selected-members="selectedMembers"
        :projects="projects"
        :selected-project-id="selectedProjectId"
        :can-organize="canOrganize"
        :is-submitting="isSubmitting"
        :selected-space-stats="selectedSpaceStats"
        :invite-status="inviteStatus"
        :copied-code="copiedCode"
        :create-space-form="createSpaceForm"
        :join-form="joinForm"
        :select-space="selectSpace"
        :select-project="selectProject"
        :submit-create-space="submitCreateSpace"
        :submit-join-space="submitJoinSpace"
        :leave-selected-space="leaveSelectedSpace"
        :remove-space-member="removeSpaceMember"
        :transfer-space-owner="transferSpaceOwner"
        :generate-invite-code="generateInviteCode"
        :copy-invite-code="copyInviteCode"
        :avatar-src="avatarSrc"
        :mark-avatar-failed="markAvatarFailed"
        :format-date="formatDate"
        :switch-section="switchSection"
      />

      <FreeCollabTaskBoard
        v-else-if="activeSection === 'tasks'"
        :selected-space="selectedSpace"
        :selected-project="selectedProject"
        :selected-project-id="selectedProjectId"
        :selected-task="selectedTask"
        :selected-task-id="selectedTaskId"
        :highlighted-task-id="highlightedTaskId"
        :editing-project="editingProject"
        :editing-task-id="editingTaskId"
        :expanded-submission-task-id="expandedSubmissionTaskId"
        :can-organize="canOrganize"
        :active-create-panel="activeCreatePanel"
        :is-submitting="isSubmitting"
        :selected-members="selectedMembers"
        :selected-tasks="selectedTasks"
        :task-filter-options="taskFilterOptions"
        :board-columns="boardColumns"
        :visible-tasks="visibleTasks"
        :task-flow-stats="taskFlowStats"
        :status-labels="statusLabels"
        :project-form="projectForm"
        :project-wizard-form="projectWizardForm"
        :project-edit-form="projectEditForm"
        :task-form="taskForm"
        :task-edit-form="taskEditForm"
        :task-submit-drafts="taskSubmitDrafts"
        :review-drafts="reviewDrafts"
        :project-health-text="projectHealthText"
        :task-board-filter="taskBoardFilter"
        :set-active-create-panel="(value) => { activeCreatePanel = value }"
        :set-task-board-filter="(value) => { taskBoardFilter = value }"
        :archive-current-project="archiveCurrentProject"
        :start-edit-project="startEditProject"
        :submit-edit-project="submitEditProject"
        :cancel-edit-project="cancelEditProject"
        :submit-create-project="submitCreateProject"
        :submit-project-wizard="submitProjectWizard"
        :reset-project-wizard="resetProjectWizard"
        :add-wizard-task="addWizardTask"
        :remove-wizard-task="removeWizardTask"
        :set-wizard-step="setWizardStep"
        :submit-create-task="submitCreateTask"
        :add-task-form-flow-node="addTaskFormFlowNode"
        :remove-task-form-flow-node="removeTaskFormFlowNode"
        :tasks-by-column="tasksByColumn"
        :is-dependency-blocked="isDependencyBlocked"
        :open-task-detail="openTaskDetail"
        :task-action-class="taskActionClass"
        :next-task-action="nextTaskAction"
        :format-date="formatDate"
        :task-action-description="taskActionDescription"
        :dependency-status="dependencyStatus"
        :dependency-tone="dependencyTone"
        :dependency-label="dependencyLabel"
        :task-version-count="taskVersionCount"
        :is-due-soon-task="isDueSoonTask"
        :close-task-detail="closeTaskDetail"
        :days-until="daysUntil"
        :dependency-tasks="dependencyTasks"
        :submit-edit-task="submitEditTask"
        :cancel-edit-task="cancelEditTask"
        :start-edit-task="startEditTask"
        :archive-task="archiveTask"
        :claim-task="claimTask"
        :can-start-task="canStartTask"
        :start-task-progress="startTaskProgress"
        :can-submit-task="canSubmitTask"
        :can-review-task="canReviewTask"
        :submit-task-result="submitTaskResult"
        :review-task="reviewTask"
        :report-upload-error="(message) => setFeedback(message || '文件上传失败', true)"
        :toggle-submission-history="toggleSubmissionHistory"
        :submission-status-label="submissionStatusLabel"
        :submission-links="submissionLinks"
        :submission-text="submissionText"
        :current-flow-node="currentFlowNode"
        :has-flow-nodes="hasFlowNodes"
      />
      <FreeCollabProgressSection
        v-else-if="activeSection === 'progress'"
        :progress="progressView"
        :progress-completion-style="progressCompletionStyle"
        :progress-risks="progressRisks"
        :progress-risk-tasks="progressRiskTasks"
        :progress-dependency-links="progressDependencyLinks"
        :progress-member-loads="progressMemberLoads"
        :progress-blocked-tasks="progressBlockedTasks"
        :progress-handoffs="progressHandoffs"
        :gantt-range-label="ganttRangeLabel"
        :status-labels="statusLabels"
        :switch-section="switchSection"
        :set-task-board-filter="(value) => { taskBoardFilter = value }"
        :open-task-context="openTaskContext"
        :risk-label="riskLabel"
        :progress-dependency-label="progressDependencyLabel"
      />

      <FreeCollabTraceSection
        v-else-if="activeSection === 'contributions'"
        :activity-logs="activityLogs"
        :selected-space="selectedSpace"
        :selected-project="selectedProject"
        :activity-filter="activityFilter"
        :activity-filter-options="activityFilterOptions"
        :activity-type-stats="activityTypeStats"
        :recent-activity-logs="recentActivityLogs"
        :grouped-activity-logs="groupedActivityLogs"
        :filtered-activity-logs="filteredActivityLogs"
        :set-activity-filter="(value) => { activityFilter = value }"
        :load-activity-logs="loadActivityLogs"
        :activity-type-label="activityTypeLabel"
        :activity-action-label="activityActionLabel"
        :activity-meta="activityMeta"
        :resolve-activity-task="resolveActivityTask"
        :open-task-context="openTaskContext"
        :switch-section="switchSection"
        :format-date="formatDate"
      />

      <FreeCollabNotificationsSection v-else />
    </template>
  </main>
</template>

<style scoped>
.free-view {
  --surface: oklch(0.985 0.006 235);
  --panel: oklch(0.965 0.012 235);
  --panel-strong: oklch(0.94 0.018 230);
  --text: oklch(0.23 0.025 245);
  --muted: oklch(0.49 0.028 245);
  --line: oklch(0.88 0.018 230);
  --accent: oklch(0.55 0.16 245);
  --accent-soft: oklch(0.93 0.04 245);
  --success: oklch(0.58 0.14 150);
  --warning: oklch(0.68 0.14 72);
  --danger: oklch(0.58 0.18 28);
  min-height: calc(100vh - 80px);
  padding: 16px;
  color: var(--text);
  background:
    radial-gradient(circle at 12% 0%, oklch(0.94 0.045 245 / 0.65), transparent 30%),
    linear-gradient(135deg, oklch(0.99 0.008 220), oklch(0.965 0.018 250));
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif;
}

button,
input,
select,
textarea {
  font: inherit;
}

button {
  cursor: pointer;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
</style>






