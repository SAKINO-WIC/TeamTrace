<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  progress: { type: Object, default: null },
  progressCompletionStyle: { type: Object, required: true },
  progressRisks: { type: Array, default: () => [] },
  progressRiskTasks: { type: Array, default: () => [] },
  progressDependencyLinks: { type: Array, default: () => [] },
  progressMemberLoads: { type: Array, default: () => [] },
  progressBlockedTasks: { type: Array, default: () => [] },
  progressHandoffs: { type: Array, default: () => [] },
  ganttRangeLabel: { type: String, default: '未设置时间范围' },
  statusLabels: { type: Object, required: true },
  switchSection: { type: Function, required: true },
  setTaskBoardFilter: { type: Function, required: true },
  openTaskContext: { type: Function, required: true },
  riskLabel: { type: Function, required: true },
  progressDependencyLabel: { type: Function, required: true },
})

const DAY_MS = 24 * 60 * 60 * 1000
const selectedGanttTask = ref(null)

const ganttTasks = computed(() => props.progress?.tasks || [])
const activeGanttTask = computed(() => {
  if (!selectedGanttTask.value) return null
  const current = ganttTasks.value.find((task) => Number(task.id) === Number(selectedGanttTask.value.id))
  return {
    ...selectedGanttTask.value,
    ...(current || {}),
  }
})

const ganttBounds = computed(() => {
  const times = ganttTasks.value.flatMap((task) => [
    task.startAt ? new Date(task.startAt).getTime() : null,
    task.dueAt ? new Date(task.dueAt).getTime() : null,
  ]).filter(Boolean)
  const now = Date.now()
  const min = times.length ? Math.min(...times) : now
  const max = times.length ? Math.max(...times) : now + DAY_MS * 7
  return {
    start: new Date(min - DAY_MS),
    end: new Date(max + DAY_MS),
  }
})

const ganttDays = computed(() => {
  const days = []
  const start = new Date(ganttBounds.value.start)
  start.setHours(0, 0, 0, 0)
  const end = new Date(ganttBounds.value.end)
  end.setHours(0, 0, 0, 0)
  for (let time = start.getTime(); time <= end.getTime(); time += DAY_MS) {
    const date = new Date(time)
    days.push({
      key: date.toISOString().slice(0, 10),
      label: `${date.getMonth() + 1}/${date.getDate()}`,
      month: `${date.getFullYear()}-${date.getMonth() + 1}`,
      isToday: new Date().toDateString() === date.toDateString(),
    })
  }
  return days
})

const ganttMonths = computed(() => {
  const groups = []
  ganttDays.value.forEach((day) => {
    const current = groups[groups.length - 1]
    if (current?.key === day.month) {
      current.span += 1
      return
    }
    groups.push({ key: day.month, label: day.month, span: 1 })
  })
  return groups
})

function ganttTaskStyle(task) {
  const startTime = ganttBounds.value.start.getTime()
  const endTime = ganttBounds.value.end.getTime()
  const total = Math.max(DAY_MS, endTime - startTime)
  const taskStart = task.startAt ? new Date(task.startAt).getTime() : startTime
  const taskEnd = task.dueAt ? new Date(task.dueAt).getTime() : taskStart + DAY_MS
  const left = Math.max(0, Math.min(100, ((taskStart - startTime) / total) * 100))
  const width = Math.max(4, Math.min(100 - left, ((Math.max(taskEnd, taskStart + DAY_MS) - taskStart) / total) * 100))
  return { left: `${left}%`, width: `${width}%` }
}

function openProgressTask(task) {
  selectedGanttTask.value = task || null
}

function closeProgressTask() {
  selectedGanttTask.value = null
}

function formatTaskDate(value) {
  if (!value) return '未设置'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '未设置'
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function currentFlowNode(task) {
  if (!Array.isArray(task?.flowNodes)) return null
  return task.flowNodes.find((node) => node.current) || task.flowNodes.find((node) => node.status !== 'COMPLETED') || null
}

function displayText(value, emptyText = '暂无') {
  const text = String(value || '').trim()
  return text || emptyText
}

function assigneeLabel(task) {
  const node = currentFlowNode(task)
  return task?.assigneeName || node?.assigneeName || (node?.claimable ? '待成员认领当前环节' : '待指定负责人')
}

function receiverLabel(task) {
  return task?.receiverName || '任一成员'
}

function currentStepLabel(task) {
  const node = currentFlowNode(task)
  if (!node) return '无交接流程'
  const owner = node.assigneeName || (node.claimable ? '开放认领' : '待指定负责人')
  return `${node.stepOrder || 1}. ${node.title || '未命名环节'} · ${owner}`
}

function taskDescription(task) {
  return displayText(task?.description || currentFlowNode(task)?.description, '暂无任务说明')
}

function deliverableText(task) {
  return displayText(task?.deliverableRequirements, '暂无交付要求')
}

function latestSubmissionText(task) {
  const submission = task?.latestSubmission
  if (!submission) return '暂无提交记录'
  const parsedContent = parseSubmissionContent(submission.content)
  return displayText(parsedContent.text || submission.content, '提交人未填写说明')
}

function taskAttachmentCount(task) {
  const taskAttachments = Array.isArray(task?.attachments) ? task.attachments.length : 0
  const latestSubmission = task?.latestSubmission
  const contentAttachments = parseSubmissionContent(latestSubmission?.content).attachments.length
  const submissionAttachments = latestSubmission?.attachmentsJson
    ? safeJsonArray(latestSubmission.attachmentsJson).length
    : contentAttachments
  return taskAttachments + submissionAttachments
}

function parseSubmissionContent(value) {
  if (!value) return { text: '', attachments: [] }
  if (typeof value !== 'string') {
    return {
      text: String(value?.text || '').trim(),
      attachments: Array.isArray(value?.attachments) ? value.attachments : [],
    }
  }
  try {
    const parsed = JSON.parse(value)
    return {
      text: String(parsed?.text || '').trim(),
      attachments: Array.isArray(parsed?.attachments) ? parsed.attachments : [],
    }
  } catch {
    return { text: value, attachments: [] }
  }
}

function safeJsonArray(value) {
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function riskText(task) {
  if (!task) return '暂无风险'
  if (task.overdue) return '已逾期，需要尽快处理'
  const dependencyText = props.progressDependencyLabel(task)
  if (dependencyText) return dependencyText
  return props.riskLabel(task) || '暂无明显风险'
}
</script>

<template>
  <section class="progress-layout">
    <template v-if="progress?.project">
      <section class="progress-cockpit">
        <div class="progress-title">
          <span>项目进度</span>
          <h2>{{ progress.project.title }}</h2>
          <div class="progress-bar">
            <i :style="progressCompletionStyle"></i>
          </div>
        </div>
        <div class="progress-score">
          <strong>{{ progress.completionRate || 0 }}%</strong>
          <span>{{ progress.completedTaskCount }}/{{ progress.taskCount }} 已完成</span>
        </div>
        <div class="progress-pulse">
          <article>
            <strong>{{ progress.waitingReceiveCount || 0 }}</strong>
            <span>等待接收</span>
          </article>
          <article>
            <strong>{{ progressRisks.length }}</strong>
            <span>风险任务</span>
          </article>
          <article>
            <strong>{{ progressDependencyLinks.length }}</strong>
            <span>前置关系</span>
          </article>
        </div>
      </section>

      <Teleport to="body">
        <div v-if="activeGanttTask" class="free-modal-layer progress-task-modal-layer" role="presentation">
          <button
            type="button"
            class="free-modal-backdrop"
            aria-label="关闭任务详情"
            @click="closeProgressTask"
          ></button>
          <section class="free-modal-panel progress-task-modal" role="dialog" aria-modal="true" aria-label="任务详情">
            <header class="free-modal-head">
              <div>
                <span>任务详情</span>
                <h2>{{ activeGanttTask.title || '未命名任务' }}</h2>
              </div>
              <button type="button" class="text-btn" @click="closeProgressTask">关闭</button>
            </header>

            <div class="progress-task-detail-grid">
              <article>
                <span>状态</span>
                <strong>{{ statusLabels[activeGanttTask.status] || activeGanttTask.status || '未设置' }}</strong>
              </article>
              <article>
                <span>负责人</span>
                <strong>{{ assigneeLabel(activeGanttTask) }}</strong>
              </article>
              <article>
                <span>接收人</span>
                <strong>{{ receiverLabel(activeGanttTask) }}</strong>
              </article>
              <article>
                <span>时间范围</span>
                <strong>{{ formatTaskDate(activeGanttTask.startAt) }} 至 {{ formatTaskDate(activeGanttTask.dueAt) }}</strong>
              </article>
              <article>
                <span>当前环节</span>
                <strong>{{ currentStepLabel(activeGanttTask) }}</strong>
              </article>
              <article>
                <span>附件与提交</span>
                <strong>{{ taskAttachmentCount(activeGanttTask) }} 个附件，{{ activeGanttTask.submissions?.length || 0 }} 次提交</strong>
              </article>
            </div>

            <div class="progress-task-detail-body">
              <section>
                <h3>任务说明</h3>
                <p>{{ taskDescription(activeGanttTask) }}</p>
              </section>
              <section>
                <h3>交付要求</h3>
                <p>{{ deliverableText(activeGanttTask) }}</p>
              </section>
              <section>
                <h3>最新提交</h3>
                <p>{{ latestSubmissionText(activeGanttTask) }}</p>
              </section>
              <section>
                <h3>风险提示</h3>
                <p>{{ riskText(activeGanttTask) }}</p>
              </section>
            </div>
          </section>
        </div>
      </Teleport>

      <section class="progress-stack">
        <article class="progress-panel gantt-panel">
          <div class="section-head compact">
            <div>
              <span>自动甘特图</span>
              <h2>{{ ganttRangeLabel }}</h2>
            </div>
            <button type="button" class="text-btn" @click="switchSection('tasks')">看任务面板</button>
          </div>

          <div class="gantt-scroll">
          <div class="gantt-board" :style="{ '--gantt-days': ganttDays.length }">
            <div class="gantt-left-head">任务</div>
            <div class="gantt-scale">
              <div class="gantt-months">
                <span v-for="month in ganttMonths" :key="month.key" :style="{ gridColumn: `span ${month.span}` }">{{ month.label }}</span>
              </div>
              <div class="gantt-days">
                <span v-for="day in ganttDays" :key="day.key" :class="{ today: day.isToday }">{{ day.label }}</span>
              </div>
            </div>

            <template v-for="task in ganttTasks" :key="task.id">
              <button type="button" class="gantt-task-label" @click="openProgressTask(task)">
                <strong>{{ task.title }}</strong>
                <span>{{ task.assigneeName || '待认领' }}</span>
              </button>
              <button type="button" class="gantt-lane" @click="openProgressTask(task)">
                <i
                  :class="{ done: task.status === 'COMPLETED', waiting: task.status === 'WAITING_RECEIVE', overdue: task.overdue }"
                  :style="ganttTaskStyle(task)"
                >
                  {{ statusLabels[task.status] || task.status }}
                </i>
              </button>
            </template>

            <div v-if="!ganttTasks.length" class="gantt-empty">
              还没有任务
            </div>
          </div>
          </div>
        </article>

        <section class="progress-data-strip">
          <article class="progress-panel risk-panel">
            <div class="section-head compact">
              <div>
                <span>风险与阻塞</span>
                <h2>{{ progressRiskTasks.length + progressBlockedTasks.length }} 个</h2>
              </div>
              <button type="button" class="text-btn" @click="setTaskBoardFilter('RISK'); switchSection('tasks')">处理</button>
            </div>
            <div v-if="!progressRiskTasks.length && !progressBlockedTasks.length" class="empty-state">暂无风险</div>
            <div v-else class="task-list compact-scroll">
              <button v-for="task in progressBlockedTasks" :key="`blocked-${task.id}`" type="button" class="task-row" @click="openProgressTask(task)">
                <strong>{{ task.title }}</strong>
                <span>{{ progressDependencyLabel(task) }}</span>
              </button>
              <button v-for="task in progressRiskTasks" :key="`risk-${task.id}`" type="button" class="task-row warning" @click="openProgressTask(task)">
                <strong>{{ task.title }}</strong>
                <span>{{ riskLabel(task) }} · {{ task.assigneeName || '待认领' }}</span>
              </button>
            </div>
          </article>

          <article class="progress-panel member-panel">
            <div class="section-head compact">
              <div>
                <span>成员负载</span>
                <h2>{{ progressMemberLoads.length }} 人</h2>
              </div>
            </div>
            <div v-if="!progressMemberLoads.length" class="empty-state">暂无分配</div>
            <div v-else class="load-list compact-scroll">
              <div v-for="member in progressMemberLoads" :key="member.id" class="load-row expanded">
                <div>
                  <strong>{{ member.name }}</strong>
                  <span>{{ member.active }} 进行中 · {{ member.waiting }} 待接收 · {{ member.risk }} 风险</span>
                </div>
                <em>{{ member.completed }}/{{ member.total }}</em>
              </div>
            </div>
          </article>

          <article class="progress-panel handoff-panel">
            <div class="section-head compact">
              <div>
                <span>交接链路</span>
                <h2>{{ progressHandoffs.length }} 个</h2>
              </div>
            </div>
            <div v-if="!progressHandoffs.length" class="empty-state">暂无交接</div>
            <div v-else class="task-list compact-scroll">
              <button v-for="task in progressHandoffs" :key="task.id" type="button" class="task-row" @click="openProgressTask(task)">
                <strong>{{ task.title }}</strong>
                <span>{{ task.assigneeName || '待认领' }} → {{ task.receiverName || '任一成员' }}</span>
              </button>
            </div>
          </article>
        </section>
      </section>
    </template>
    <section v-else class="empty-state large">
      请选择项目
    </section>
  </section>
</template>
