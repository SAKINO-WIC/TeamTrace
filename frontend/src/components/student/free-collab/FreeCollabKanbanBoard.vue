<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import AttachmentFileBadge from '../../common/AttachmentFileBadge.vue'
import FileUploadZone from '../../common/FileUploadZone.vue'
import {
  canPreviewMedia,
  downloadMediaFile,
  formatFileSize,
  resolveMediaUrl,
  resolvePreviewMode,
} from '../../../utils/mediaUrl'

const props = defineProps({
  selectedProject: { type: Object, default: null },
  selectedTaskId: { type: [Number, String, null], default: null },
  highlightedTaskId: { type: [Number, String, null], default: null },
  selectedTasks: { type: Array, default: () => [] },
  taskQueues: { type: Array, default: () => [] },
  taskFilterOptions: { type: Array, default: () => [] },
  boardColumns: { type: Array, default: () => [] },
  visibleTasks: { type: Array, default: () => [] },
  taskFlowStats: { type: Object, required: true },
  statusLabels: { type: Object, required: true },
  projectHealthText: { type: String, default: '' },
  taskBoardFilter: { type: String, default: 'ALL' },
  setTaskBoardFilter: { type: Function, required: true },
  tasksByColumn: { type: Function, required: true },
  isDependencyBlocked: { type: Function, required: true },
  openTaskDetail: { type: Function, required: true },
  taskActionClass: { type: Function, required: true },
  nextTaskAction: { type: Function, required: true },
  formatDate: { type: Function, required: true },
  taskActionDescription: { type: Function, required: true },
  dependencyStatus: { type: Function, required: true },
  dependencyTone: { type: Function, required: true },
  dependencyLabel: { type: Function, required: true },
  taskVersionCount: { type: Function, required: true },
  isDueSoonTask: { type: Function, required: true },
  setActiveCreatePanel: { type: Function, default: null },
  canOrganize: { type: Boolean, default: false },
  editingProject: { type: Boolean, default: false },
  projectEditForm: { type: Object, required: true },
  archiveCurrentProject: { type: Function, default: null },
  startEditProject: { type: Function, required: true },
  submitEditProject: { type: Function, required: true },
  cancelEditProject: { type: Function, required: true },
})

const completionPercent = computed(() => {
  const total = Number(props.taskFlowStats.total || 0)
  if (!total) return 0
  return Math.round((Number(props.taskFlowStats.completed || 0) / total) * 100)
})

const hasVisibleTasks = computed(() => props.visibleTasks.length > 0)

const flowSummary = computed(() => {
  if (!props.selectedProject) return '先选择一个项目'
  if (!props.taskFlowStats.total) return '项目已经创建，等待拆解第一个任务'
  if (props.taskFlowStats.risk) return `${props.taskFlowStats.risk} 个任务需要关注`
  if (props.taskFlowStats.waiting) return `${props.taskFlowStats.waiting} 个任务等待接收`
  if (props.taskFlowStats.active) return `${props.taskFlowStats.active} 个任务正在推进`
  if (props.taskFlowStats.completed === props.taskFlowStats.total) return '全部任务已完成'
  return props.projectHealthText || '协作流转正常'
})

function statusLabel(task) {
  return props.statusLabels[task?.status] || task?.status || '未知状态'
}

function handoffLabel(task) {
  const assignee = task?.assigneeName || '待认领'
  const receiver = task?.receiverName || '任一成员'
  return `${assignee} → ${receiver}`
}

function dueLabel(task) {
  if (!task?.dueAt) return '未设截止'
  return `截止 ${props.formatDate(task.dueAt)}`
}

function isSelectedTask(task) {
  return Number(props.selectedTaskId) === Number(task?.id)
}

function isHighlightedTask(task) {
  return Number(props.highlightedTaskId) === Number(task?.id)
}

function materialUrl(material) {
  return resolveMediaUrl(material?.url || material?.value || '')
}

const previewFile = ref(null)

function canPreviewMaterial(material) {
  return canPreviewMedia(materialUrl(material))
}

function openMaterialPreview(material) {
  const url = materialUrl(material)
  if (!url) return
  if (!canPreviewMedia(url)) {
    window.open(url, '_blank', 'noopener,noreferrer')
    return
  }
  previewFile.value = {
    name: material?.name || '附件预览',
    url,
  }
}

async function downloadMaterial(material) {
  try {
    await downloadMediaFile(materialUrl(material), material?.name || '附件')
  } catch {
    window.alert('文件下载失败，请稍后重试。')
  }
}

function closePreview() {
  previewFile.value = null
}

watch(
  () => props.highlightedTaskId,
  async (taskId) => {
    if (!taskId) return
    await nextTick()
    const element = document.querySelector(`[data-free-task-id="${taskId}"]`)
    element?.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'center' })
  },
)
</script>

<template>
  <section v-if="selectedProject" class="project-brief-panel">
    <div class="project-brief-main">
      <span>当前项目</span>
      <h2>{{ selectedProject.title }}</h2>
      <p>{{ selectedProject.description || '项目说明暂未填写。' }}</p>
      <div v-if="selectedProject.attachments?.length" class="material-list compact project-material-list">
        <div
          v-for="material in selectedProject.attachments"
          :key="material.attachmentId || material.url"
          class="material-item"
        >
          <AttachmentFileBadge :name="material.name" :url="material.url" :kind="material.type" size="sm" />
          <span>
            <strong>{{ material.name || '查看附件' }}</strong>
            <small>{{ material.type === 'link' ? '外部链接' : formatFileSize(material.size) || '上传文件' }}</small>
          </span>
          <div class="material-actions">
            <button v-if="canPreviewMaterial(material)" type="button" @click="openMaterialPreview(material)">预览</button>
            <button type="button" @click="downloadMaterial(material)">
              {{ material.type === 'link' ? '打开' : '下载' }}
            </button>
          </div>
        </div>
      </div>
      <section v-if="previewFile" class="free-preview-panel project-preview-panel">
        <div class="free-preview-bar">
          <span>{{ previewFile.name }}</span>
          <button type="button" @click="closePreview">收起</button>
        </div>
        <iframe
          v-if="resolvePreviewMode(previewFile.url) === 'pdf'"
          class="free-preview-frame"
          :src="previewFile.url"
          title="项目附件预览"
        ></iframe>
        <img
          v-else-if="resolvePreviewMode(previewFile.url) === 'image'"
          class="free-preview-image"
          :src="previewFile.url"
          :alt="previewFile.name"
        />
      </section>
    </div>

    <div class="project-brief-progress">
      <strong>{{ completionPercent }}%</strong>
      <span>{{ projectHealthText || '协作状态待更新' }}</span>
      <i><b :style="{ width: `${completionPercent}%` }"></b></i>
    </div>

    <div class="project-brief-stats">
      <article><strong>{{ taskFlowStats.total }}</strong><span>总任务</span></article>
      <article><strong>{{ taskFlowStats.active }}</strong><span>推进中</span></article>
      <article><strong>{{ taskFlowStats.waiting }}</strong><span>待接收</span></article>
      <article><strong>{{ taskFlowStats.completed }}</strong><span>已完成</span></article>
      <article><strong>{{ taskFlowStats.risk }}</strong><span>风险</span></article>
    </div>
  </section>

  <section v-if="selectedProject" class="board-shell">
    <div class="board-toolbar">
      <div>
        <span>任务流转面板</span>
        <h2>{{ flowSummary }}</h2>
        <p>点击任务卡片后，在右侧详情完成认领、提交、接收、打回和编辑。</p>
      </div>
      <div class="board-toolbar-actions">
        <button
          v-if="canOrganize"
          type="button"
          class="secondary-btn"
          @click="startEditProject"
        >
          编辑项目
        </button>
        <button
          v-if="setActiveCreatePanel"
          type="button"
          class="secondary-btn"
          @click="setActiveCreatePanel('task')"
        >
          追加任务
        </button>
        <button
          v-if="canOrganize && archiveCurrentProject"
          type="button"
          class="danger-text-btn"
          @click="archiveCurrentProject"
        >
          归档项目
        </button>
      </div>
    </div>

    <div class="task-scope-tabs" aria-label="任务范围">
      <button
        v-for="option in taskFilterOptions"
        :key="option.key"
        type="button"
        :class="{ active: taskBoardFilter === option.key }"
        @click="setTaskBoardFilter(option.key)"
      >
        {{ option.label }}
      </button>
    </div>

    <div v-if="!selectedTasks.length" class="empty-state large">
      <p>当前项目还没有任务。发起人拆解任务后，成员就可以认领、提交和接收。</p>
      <button
        v-if="setActiveCreatePanel"
        type="button"
        class="primary-btn"
        @click="setActiveCreatePanel('task')"
      >
        追加第一个任务
      </button>
    </div>
    <div v-else-if="!hasVisibleTasks" class="empty-state large">
      <p>当前筛选下没有任务。切回全部任务，或从上方队列查看需要处理的事项。</p>
      <button type="button" class="secondary-btn" @click="setTaskBoardFilter('ALL')">查看全部任务</button>
    </div>
    <div v-else class="kanban-board">
      <section v-for="column in boardColumns" :key="column.key" class="kanban-column">
        <header>
          <strong>{{ column.title }}</strong>
          <span>{{ tasksByColumn(column.key).length }}</span>
        </header>
        <button
          v-for="task in tasksByColumn(column.key)"
          :key="task.id"
          type="button"
          class="kanban-task"
          :data-free-task-id="task.id"
          :class="{
            overdue: task.overdue,
            blocked: isDependencyBlocked(task),
            selected: isSelectedTask(task),
            highlighted: isHighlightedTask(task),
          }"
          @click="openTaskDetail(task)"
        >
          <div class="task-card-head">
            <strong>{{ task.title }}</strong>
            <em :class="taskActionClass(task)">{{ nextTaskAction(task).label }}</em>
          </div>
          <div class="task-card-route">
            <span>{{ statusLabel(task) }}</span>
            <strong>{{ handoffLabel(task) }}</strong>
          </div>
          <small>{{ taskActionDescription(task) }}</small>
          <div class="task-card-time">
            <span>{{ dueLabel(task) }}</span>
            <span v-if="task.startAt">开始 {{ formatDate(task.startAt) }}</span>
          </div>
          <div class="task-card-meta">
            <b v-if="dependencyStatus(task).total" :class="dependencyTone(task)">{{ dependencyLabel(task) }}</b>
            <b v-if="taskVersionCount(task)">{{ taskVersionCount(task) }} 个提交版本</b>
            <b v-if="isDueSoonTask(task)">临近截止</b>
          </div>
        </button>
        <p v-if="!tasksByColumn(column.key).length">暂无任务</p>
      </section>
    </div>
  </section>

  <section v-else class="empty-state large">
    <p>先在顶部选择空间和项目。没有项目时，发起人可以在这里发布第一个项目。</p>
    <button
      v-if="setActiveCreatePanel"
      type="button"
      class="primary-btn"
      @click="setActiveCreatePanel('wizard')"
    >
      发布协作项目
    </button>
  </section>

  <Teleport to="body">
    <div v-if="selectedProject && editingProject" class="free-modal-layer project-edit-modal-layer" role="presentation">
      <button
        type="button"
        class="free-modal-backdrop"
        aria-label="关闭编辑项目"
        @click="cancelEditProject"
      ></button>
      <form class="free-modal-panel project-edit-modal" role="dialog" aria-modal="true" aria-label="编辑项目" @submit.prevent="submitEditProject">
        <header class="free-modal-head">
          <div>
            <span>编辑项目</span>
            <h2>{{ selectedProject.title || '未命名项目' }}</h2>
          </div>
          <button type="button" class="text-btn" @click="cancelEditProject">关闭</button>
        </header>
        <div class="project-edit-form">
          <div class="project-edit-grid">
            <input v-model="projectEditForm.title" class="free-input" placeholder="项目名称" />
            <select v-model="projectEditForm.status" class="free-input">
              <option value="ACTIVE">进行中</option>
              <option value="COMPLETED">已完成</option>
              <option value="ARCHIVED">已归档</option>
            </select>
            <label class="free-field">
              <span>开始时间</span>
              <input v-model="projectEditForm.startAt" class="free-input" type="datetime-local" />
            </label>
            <label class="free-field">
              <span>截止时间</span>
              <input v-model="projectEditForm.dueAt" class="free-input" type="datetime-local" />
            </label>
            <textarea v-model="projectEditForm.description" class="free-textarea" placeholder="项目目标、团队规则、最终交付物说明" />
            <div class="wizard-attachment-field">
              <span>项目附件</span>
              <FileUploadZone v-model="projectEditForm.attachments" :max-files="10" />
            </div>
          </div>
          <footer class="free-modal-actions project-edit-actions">
            <button type="button" class="secondary-btn" @click="cancelEditProject">取消</button>
            <button type="submit" class="primary-btn">保存项目</button>
          </footer>
        </div>
      </form>
    </div>
  </Teleport>
</template>
