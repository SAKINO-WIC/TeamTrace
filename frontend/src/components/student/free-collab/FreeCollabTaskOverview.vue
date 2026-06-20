<script setup>
import { ref } from 'vue'
import AttachmentFileBadge from '../../common/AttachmentFileBadge.vue'
import {
  canPreviewMedia,
  downloadMediaFile,
  formatFileSize,
  resolveMediaUrl,
  resolvePreviewMode,
} from '../../../utils/mediaUrl'

defineProps({
  selectedTask: { type: Object, default: null },
  statusLabels: { type: Object, required: true },
  taskActionClass: { type: Function, required: true },
  nextTaskAction: { type: Function, required: true },
  taskActionDescription: { type: Function, required: true },
  daysUntil: { type: Function, required: true },
  formatDate: { type: Function, required: true },
  dependencyStatus: { type: Function, required: true },
  dependencyTasks: { type: Function, required: true },
  openTaskDetail: { type: Function, required: true },
  currentFlowNode: { type: Function, required: true },
  hasFlowNodes: { type: Function, required: true },
})

defineEmits(['close'])

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
</script>

<template>
  <template v-if="selectedTask">
    <div class="detail-title">
      <div>
        <span>{{ statusLabels[selectedTask.status] || selectedTask.status }}</span>
        <h2>{{ selectedTask.title }}</h2>
      </div>
      <button type="button" class="text-btn" @click="$emit('close')">关闭</button>
    </div>

    <section class="detail-action-card" :class="taskActionClass(selectedTask)">
      <div>
        <span>下一步动作</span>
        <h3>{{ nextTaskAction(selectedTask).label }}</h3>
        <p>{{ taskActionDescription(selectedTask) }}</p>
      </div>
      <strong>{{ daysUntil(selectedTask.dueAt) }}</strong>
    </section>

    <section>
      <h3>任务说明</h3>
      <p>{{ selectedTask.description || '暂无任务说明。' }}</p>
    </section>
    <section>
      <h3>交付要求</h3>
      <p>{{ selectedTask.deliverableRequirements || '暂无交付要求。' }}</p>
    </section>

    <section v-if="hasFlowNodes(selectedTask)" class="flow-chain-card">
      <h3>交接流程</h3>
      <div class="flow-chain-list">
        <article
          v-for="node in selectedTask.flowNodes"
          :key="node.id || node.stepOrder"
          :class="{ current: currentFlowNode(selectedTask)?.id === node.id, done: node.status === 'COMPLETED' }"
        >
          <strong>{{ node.stepOrder }}</strong>
          <span>
            <b>{{ node.title }}</b>
            <small>{{ node.assigneeName || (node.claimable ? '开放认领' : '未指定') }} · {{ statusLabels[node.status] || node.status }}</small>
          </span>
        </article>
      </div>
    </section>

    <section v-if="selectedTask.attachments?.length">
      <h3>任务附件</h3>
      <div class="material-list compact">
        <div
          v-for="material in selectedTask.attachments"
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
      <section v-if="previewFile" class="free-preview-panel">
        <div class="free-preview-bar">
          <span>{{ previewFile.name }}</span>
          <button type="button" @click="closePreview">收起</button>
        </div>
        <iframe
          v-if="resolvePreviewMode(previewFile.url) === 'pdf'"
          class="free-preview-frame"
          :src="previewFile.url"
          title="附件预览"
        ></iframe>
        <img
          v-else-if="resolvePreviewMode(previewFile.url) === 'image'"
          class="free-preview-image"
          :src="previewFile.url"
          :alt="previewFile.name"
        />
      </section>
    </section>
    <div class="detail-grid">
      <article><span>负责人</span><strong>{{ selectedTask.assigneeName || '待认领' }}</strong></article>
      <article><span>接收人</span><strong>{{ selectedTask.receiverName || '任一成员' }}</strong></article>
      <article><span>开始</span><strong>{{ formatDate(selectedTask.startAt) }}</strong></article>
      <article><span>截止</span><strong>{{ formatDate(selectedTask.dueAt) }}</strong></article>
    </div>

    <section v-if="dependencyStatus(selectedTask).total">
      <h3>前置关系</h3>
      <div class="dependency-list">
        <button
          v-for="dependency in dependencyTasks(selectedTask)"
          :key="dependency.id || dependency.missing"
          type="button"
          :disabled="dependency.missing"
          @click="!dependency.missing && openTaskDetail(dependency)"
        >
          <strong>{{ dependency.title || '前置任务不存在' }}</strong>
          <span>{{ statusLabels[dependency.status] || dependency.status }} · {{ dependency.assigneeName || '成员' }}</span>
        </button>
      </div>
    </section>
  </template>
  <div v-else class="empty-state detail-empty">
    点击左侧任务后，在这里处理认领、提交、接收和打回。
  </div>
</template>
