<script setup>
import { computed } from 'vue'
import FreeCollabTaskActions from './FreeCollabTaskActions.vue'
import FreeCollabTaskEditForm from './FreeCollabTaskEditForm.vue'
import FreeCollabTaskOverview from './FreeCollabTaskOverview.vue'
import FreeCollabTaskReviewPanels from './FreeCollabTaskReviewPanels.vue'
import FreeCollabTaskSubmissions from './FreeCollabTaskSubmissions.vue'

const props = defineProps({
  selectedTask: { type: Object, default: null },
  editingTaskId: { type: [Number, String, null], default: null },
  expandedSubmissionTaskId: { type: [Number, String, null], default: null },
  canOrganize: { type: Boolean, default: false },
  isSubmitting: { type: Boolean, default: false },
  selectedMembers: { type: Array, default: () => [] },
  selectedTasks: { type: Array, default: () => [] },
  statusLabels: { type: Object, required: true },
  taskEditForm: { type: Object, required: true },
  taskSubmitDrafts: { type: Object, required: true },
  reviewDrafts: { type: Object, required: true },
  closeTaskDetail: { type: Function, required: true },
  taskActionClass: { type: Function, required: true },
  nextTaskAction: { type: Function, required: true },
  taskActionDescription: { type: Function, required: true },
  daysUntil: { type: Function, required: true },
  formatDate: { type: Function, required: true },
  dependencyStatus: { type: Function, required: true },
  dependencyTasks: { type: Function, required: true },
  openTaskDetail: { type: Function, required: true },
  submitEditTask: { type: Function, required: true },
  cancelEditTask: { type: Function, required: true },
  startEditTask: { type: Function, required: true },
  archiveTask: { type: Function, required: true },
  claimTask: { type: Function, required: true },
  canStartTask: { type: Function, required: true },
  startTaskProgress: { type: Function, required: true },
  canSubmitTask: { type: Function, required: true },
  canReviewTask: { type: Function, required: true },
  submitTaskResult: { type: Function, required: true },
  reviewTask: { type: Function, required: true },
  reportUploadError: { type: Function, required: true },
  toggleSubmissionHistory: { type: Function, required: true },
  submissionStatusLabel: { type: Function, required: true },
  submissionLinks: { type: Function, required: true },
  submissionText: { type: Function, required: true },
  addTaskFormFlowNode: { type: Function, required: true },
  removeTaskFormFlowNode: { type: Function, required: true },
  currentFlowNode: { type: Function, required: true },
  hasFlowNodes: { type: Function, required: true },
})

const submitDraft = computed(() => {
  if (!props.selectedTask) return {}
  const taskId = props.selectedTask.id
  props.taskSubmitDrafts[taskId] ||= { content: '', links: '' }
  return props.taskSubmitDrafts[taskId]
})

const reviewDraft = computed(() => {
  if (!props.selectedTask) return {}
  const taskId = props.selectedTask.id
  props.reviewDrafts[taskId] ||= { comment: '' }
  return props.reviewDrafts[taskId]
})

const editableDependencyCandidates = computed(() => {
  if (!props.selectedTask) return []
  return props.selectedTasks.filter((item) => item.id !== props.selectedTask.id)
})
</script>

<template>
  <Teleport to="body">
    <div v-if="selectedTask" class="task-detail-layer" role="presentation">
      <button type="button" class="task-detail-backdrop" aria-label="关闭任务详情" @click="closeTaskDetail"></button>
      <aside class="task-detail" :class="{ muted: !selectedTask }">
        <div class="task-detail-drawer">
          <FreeCollabTaskOverview
            v-if="editingTaskId !== selectedTask.id"
            :selected-task="selectedTask"
            :status-labels="statusLabels"
            :task-action-class="taskActionClass"
            :next-task-action="nextTaskAction"
            :task-action-description="taskActionDescription"
            :days-until="daysUntil"
            :format-date="formatDate"
            :dependency-status="dependencyStatus"
            :dependency-tasks="dependencyTasks"
            :open-task-detail="openTaskDetail"
            :current-flow-node="currentFlowNode"
            :has-flow-nodes="hasFlowNodes"
            @close="closeTaskDetail"
          />

          <FreeCollabTaskEditForm
            v-else
            :selected-task="selectedTask"
            :is-submitting="isSubmitting"
            :selected-members="selectedMembers"
            :status-labels="statusLabels"
            :task-edit-form="taskEditForm"
            :editable-dependency-candidates="editableDependencyCandidates"
            :submit-edit-task="submitEditTask"
            :cancel-edit-task="cancelEditTask"
            :add-task-form-flow-node="addTaskFormFlowNode"
            :remove-task-form-flow-node="removeTaskFormFlowNode"
          />

          <FreeCollabTaskActions
            :selected-task="selectedTask"
            :editing-task-id="editingTaskId"
            :can-organize="canOrganize"
            :is-submitting="isSubmitting"
            :can-start-task="canStartTask"
            :start-edit-task="startEditTask"
            :claim-task="claimTask"
            :start-task-progress="startTaskProgress"
            :archive-task="archiveTask"
            :has-flow-nodes="hasFlowNodes"
          />

          <FreeCollabTaskReviewPanels
            :selected-task="selectedTask"
            :is-submitting="isSubmitting"
            :submit-draft="submitDraft"
            :review-draft="reviewDraft"
            :can-submit-task="canSubmitTask"
            :can-review-task="canReviewTask"
            :submit-task-result="submitTaskResult"
            :review-task="reviewTask"
            :report-upload-error="reportUploadError"
          />

          <FreeCollabTaskSubmissions
            :selected-task="selectedTask"
            :expanded-submission-task-id="expandedSubmissionTaskId"
            :toggle-submission-history="toggleSubmissionHistory"
            :submission-status-label="submissionStatusLabel"
            :submission-links="submissionLinks"
            :submission-text="submissionText"
            :format-date="formatDate"
          />
        </div>
      </aside>
    </div>
  </Teleport>
</template>

