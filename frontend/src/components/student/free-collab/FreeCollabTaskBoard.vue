<script setup>
import { computed, ref, watch } from 'vue'
import FreeCollabTaskDetail from './FreeCollabTaskDetail.vue'
import FreeCollabKanbanBoard from './FreeCollabKanbanBoard.vue'
import FreeCollabTaskComposer from './FreeCollabTaskComposer.vue'

const props = defineProps({
  selectedSpace: { type: Object, default: null },
  selectedProject: { type: Object, default: null },
  selectedProjectId: { type: [Number, String, null], default: null },
  selectedTask: { type: Object, default: null },
  selectedTaskId: { type: [Number, String, null], default: null },
  highlightedTaskId: { type: [Number, String, null], default: null },
  editingTaskId: { type: [Number, String, null], default: null },
  expandedSubmissionTaskId: { type: [Number, String, null], default: null },
  canOrganize: { type: Boolean, default: false },
  activeCreatePanel: { type: String, default: '' },
  isSubmitting: { type: Boolean, default: false },
  selectedMembers: { type: Array, default: () => [] },
  selectedTasks: { type: Array, default: () => [] },
  taskQueues: { type: Array, default: () => [] },
  taskFilterOptions: { type: Array, default: () => [] },
  boardColumns: { type: Array, default: () => [] },
  visibleTasks: { type: Array, default: () => [] },
  taskFlowStats: { type: Object, required: true },
  statusLabels: { type: Object, required: true },
  projectForm: { type: Object, required: true },
  projectWizardForm: { type: Object, required: true },
  projectEditForm: { type: Object, required: true },
  taskForm: { type: Object, required: true },
  taskEditForm: { type: Object, required: true },
  taskSubmitDrafts: { type: Object, required: true },
  reviewDrafts: { type: Object, required: true },
  projectHealthText: { type: String, default: '' },
  taskBoardFilter: { type: String, default: 'ALL' },
  editingProject: { type: Boolean, default: false },
  setActiveCreatePanel: { type: Function, required: true },
  setTaskBoardFilter: { type: Function, required: true },
  archiveCurrentProject: { type: Function, default: null },
  startEditProject: { type: Function, required: true },
  submitEditProject: { type: Function, required: true },
  cancelEditProject: { type: Function, required: true },
  submitCreateProject: { type: Function, required: true },
  submitProjectWizard: { type: Function, required: true },
  resetProjectWizard: { type: Function, required: true },
  addWizardTask: { type: Function, required: true },
  removeWizardTask: { type: Function, required: true },
  setWizardStep: { type: Function, required: true },
  submitCreateTask: { type: Function, required: true },
  addTaskFormFlowNode: { type: Function, required: true },
  removeTaskFormFlowNode: { type: Function, required: true },
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
  closeTaskDetail: { type: Function, required: true },
  daysUntil: { type: Function, required: true },
  dependencyTasks: { type: Function, required: true },
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
  currentFlowNode: { type: Function, required: true },
  hasFlowNodes: { type: Function, required: true },
})

const localSelectedTask = ref(null)

const effectiveSelectedTask = computed(() => {
  if (props.selectedTask) return props.selectedTask
  if (localSelectedTask.value) return localSelectedTask.value
  if (!props.selectedTaskId) return null
  const taskId = Number(props.selectedTaskId)
  return (
    props.selectedTasks.find((task) => Number(task?.id) === taskId) ||
    props.visibleTasks.find((task) => Number(task?.id) === taskId) ||
    null
  )
})

function openTaskDrawer(task) {
  localSelectedTask.value = task || null
  props.openTaskDetail(task)
}

function closeTaskDrawer() {
  localSelectedTask.value = null
  props.closeTaskDetail()
}

watch(
  () => props.selectedTaskId,
  (taskId) => {
    if (!taskId) {
      localSelectedTask.value = null
      return
    }
    if (Number(localSelectedTask.value?.id) === Number(taskId)) return
    localSelectedTask.value =
      props.selectedTasks.find((task) => Number(task?.id) === Number(taskId)) ||
      props.visibleTasks.find((task) => Number(task?.id) === Number(taskId)) ||
      null
  },
)
</script>

<template>
  <section class="tasks-workspace">
    <div class="task-main">
      <FreeCollabTaskComposer
        :selected-space="selectedSpace"
        :selected-project-id="selectedProjectId"
        :can-organize="canOrganize"
        :active-create-panel="activeCreatePanel"
        :is-submitting="isSubmitting"
        :selected-members="selectedMembers"
        :selected-tasks="selectedTasks"
        :project-form="projectForm"
        :project-wizard-form="projectWizardForm"
        :task-form="taskForm"
        :set-active-create-panel="setActiveCreatePanel"
        :submit-create-project="submitCreateProject"
        :submit-project-wizard="submitProjectWizard"
        :reset-project-wizard="resetProjectWizard"
        :add-wizard-task="addWizardTask"
        :remove-wizard-task="removeWizardTask"
        :set-wizard-step="setWizardStep"
        :submit-create-task="submitCreateTask"
        :add-task-form-flow-node="addTaskFormFlowNode"
        :remove-task-form-flow-node="removeTaskFormFlowNode"
      />

      <FreeCollabKanbanBoard
        :selected-project="selectedProject"
        :selected-task-id="selectedTaskId"
        :highlighted-task-id="highlightedTaskId"
        :selected-tasks="selectedTasks"
        :task-queues="taskQueues"
        :task-filter-options="taskFilterOptions"
        :board-columns="boardColumns"
        :visible-tasks="visibleTasks"
        :task-flow-stats="taskFlowStats"
        :status-labels="statusLabels"
        :editing-project="editingProject"
        :project-edit-form="projectEditForm"
        :project-health-text="projectHealthText"
        :task-board-filter="taskBoardFilter"
        :set-active-create-panel="setActiveCreatePanel"
        :set-task-board-filter="setTaskBoardFilter"
        :archive-current-project="archiveCurrentProject"
        :start-edit-project="startEditProject"
        :submit-edit-project="submitEditProject"
        :cancel-edit-project="cancelEditProject"
        :tasks-by-column="tasksByColumn"
        :is-dependency-blocked="isDependencyBlocked"
        :open-task-detail="openTaskDrawer"
        :task-action-class="taskActionClass"
        :next-task-action="nextTaskAction"
        :format-date="formatDate"
        :task-action-description="taskActionDescription"
        :dependency-status="dependencyStatus"
        :dependency-tone="dependencyTone"
        :dependency-label="dependencyLabel"
        :task-version-count="taskVersionCount"
        :is-due-soon-task="isDueSoonTask"
      />
    </div>

    <FreeCollabTaskDetail
      :selected-task="effectiveSelectedTask"
      :editing-task-id="editingTaskId"
      :expanded-submission-task-id="expandedSubmissionTaskId"
      :can-organize="canOrganize"
      :is-submitting="isSubmitting"
      :selected-members="selectedMembers"
      :selected-tasks="selectedTasks"
      :status-labels="statusLabels"
      :task-edit-form="taskEditForm"
      :task-submit-drafts="taskSubmitDrafts"
      :review-drafts="reviewDrafts"
      :close-task-detail="closeTaskDrawer"
      :task-action-class="taskActionClass"
      :next-task-action="nextTaskAction"
      :task-action-description="taskActionDescription"
      :days-until="daysUntil"
      :format-date="formatDate"
      :dependency-status="dependencyStatus"
      :dependency-tasks="dependencyTasks"
      :open-task-detail="openTaskDrawer"
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
      :report-upload-error="reportUploadError"
      :toggle-submission-history="toggleSubmissionHistory"
      :submission-status-label="submissionStatusLabel"
      :submission-links="submissionLinks"
      :submission-text="submissionText"
      :add-task-form-flow-node="addTaskFormFlowNode"
      :remove-task-form-flow-node="removeTaskFormFlowNode"
      :current-flow-node="currentFlowNode"
      :has-flow-nodes="hasFlowNodes"
    />
  </section>
</template>

<style scoped>
@media (max-width: 1180px) {
  .tasks-workspace {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .project-header,
  .board-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .project-stats {
    width: auto;
  }
}
</style>
