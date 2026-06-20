<script setup>
import FileUploadZone from '../../common/FileUploadZone.vue'

defineProps({
  selectedTask: { type: Object, default: null },
  isSubmitting: { type: Boolean, default: false },
  selectedMembers: { type: Array, default: () => [] },
  statusLabels: { type: Object, required: true },
  taskEditForm: { type: Object, required: true },
  editableDependencyCandidates: { type: Array, default: () => [] },
  submitEditTask: { type: Function, required: true },
  cancelEditTask: { type: Function, required: true },
  addTaskFormFlowNode: { type: Function, required: true },
  removeTaskFormFlowNode: { type: Function, required: true },
})
</script>

<template>
  <form v-if="selectedTask" class="task-edit-form" @submit.prevent="submitEditTask(selectedTask)">
    <input v-model="taskEditForm.title" class="free-input" placeholder="任务标题" />
    <textarea v-model="taskEditForm.description" class="free-textarea" placeholder="任务说明" />
    <textarea v-model="taskEditForm.deliverableRequirements" class="free-textarea" placeholder="交付要求" />
    <select v-model="taskEditForm.assigneeId" class="free-input">
      <option value="">成员自己认领</option>
      <option v-for="member in selectedMembers" :key="member.studentId" :value="member.studentId">{{ member.name }}</option>
    </select>
    <select v-model="taskEditForm.receiverId" class="free-input">
      <option value="">任一成员接收</option>
      <option v-for="member in selectedMembers" :key="member.studentId" :value="member.studentId">{{ member.name }}</option>
    </select>
    <label class="free-field">
      <span>开始时间</span>
      <input v-model="taskEditForm.startAt" class="free-input" type="datetime-local" />
    </label>
    <label class="free-field">
      <span>截止时间</span>
      <input v-model="taskEditForm.dueAt" class="free-input" type="datetime-local" />
    </label>
    <select v-model="taskEditForm.status" class="free-input">
      <option v-for="(label, key) in statusLabels" :key="key" :value="key">{{ label }}</option>
    </select>
    <div class="dependency-picker">
      <label v-for="candidate in editableDependencyCandidates" :key="candidate.id">
        <input v-model="taskEditForm.dependsOnTaskIds" type="checkbox" :value="candidate.id" />
        <span>{{ candidate.title }}</span>
      </label>
    </div>
    <div class="wizard-attachment-field">
      <span>任务附件</span>
      <FileUploadZone v-model="taskEditForm.attachments" :disabled="isSubmitting" :max-files="10" />
    </div>
    <div class="flow-node-editor">
      <div class="flow-node-editor-head">
        <span>交接流程</span>
        <button type="button" class="text-btn" @click="addTaskFormFlowNode(taskEditForm)">添加环节</button>
      </div>
      <div v-for="(node, nodeIndex) in taskEditForm.flowNodes" :key="nodeIndex" class="flow-node-row">
        <strong>{{ nodeIndex + 1 }}</strong>
        <input v-model="node.title" class="free-input" placeholder="环节名称" />
        <select v-model="node.assigneeId" class="free-input" @change="node.claimable = !node.assigneeId">
          <option value="">开放认领</option>
          <option v-for="member in selectedMembers" :key="member.studentId" :value="member.studentId">{{ member.name }}</option>
        </select>
        <input v-model="node.description" class="free-input" placeholder="交接要求，可选" />
        <button type="button" class="text-btn" @click="removeTaskFormFlowNode(taskEditForm, nodeIndex)">删除</button>
      </div>
    </div>
    <footer class="free-modal-actions task-edit-actions">
      <button class="primary-btn" type="submit" :disabled="isSubmitting">保存任务</button>
      <button class="secondary-btn" type="button" @click="cancelEditTask">取消</button>
    </footer>
  </form>
</template>
