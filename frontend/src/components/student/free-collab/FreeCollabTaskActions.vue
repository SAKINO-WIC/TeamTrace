<script setup>
defineProps({
  selectedTask: { type: Object, default: null },
  editingTaskId: { type: [Number, String, null], default: null },
  canOrganize: { type: Boolean, default: false },
  isSubmitting: { type: Boolean, default: false },
  canStartTask: { type: Function, required: true },
  startEditTask: { type: Function, required: true },
  claimTask: { type: Function, required: true },
  hasFlowNodes: { type: Function, required: true },
  startTaskProgress: { type: Function, required: true },
  archiveTask: { type: Function, required: true },
})
</script>

<template>
  <section v-if="selectedTask" class="task-action-center">
    <div class="section-head compact">
      <div>
        <span>任务操作</span>
        <h2>把下一步推进掉</h2>
      </div>
    </div>
    <div class="action-stack">
      <button v-if="selectedTask.status === 'UNCLAIMED'" class="primary-btn" type="button" :disabled="isSubmitting" @click="claimTask(selectedTask)">
        {{ hasFlowNodes(selectedTask) ? '认领当前环节' : '认领任务' }}
      </button>
      <button v-if="canStartTask(selectedTask)" class="primary-btn" type="button" :disabled="isSubmitting" @click="startTaskProgress(selectedTask)">
        开始处理
      </button>
      <button v-if="canOrganize && editingTaskId !== selectedTask.id" class="secondary-btn" type="button" @click="startEditTask(selectedTask)">
        编辑任务
      </button>
      <button v-if="canOrganize" class="danger-text-btn" type="button" :disabled="isSubmitting" @click="archiveTask(selectedTask)">
        归档任务
      </button>
    </div>
    <p v-if="selectedTask.status === 'UNCLAIMED'">{{ hasFlowNodes(selectedTask) ? '认领后，这个环节会进入你的推进队列。' : '认领后，这个任务会进入你的推进队列。' }}</p>
    <p v-else-if="canStartTask(selectedTask)">开始处理后，就可以填写结果并提交给接收人。</p>
    <p v-else-if="selectedTask.status === 'CLAIMED' || selectedTask.status === 'RETURNED'">这个任务已分配给负责人，等待负责人开始处理。</p>
    <p v-else-if="selectedTask.status === 'WAITING_RECEIVE'">任务已提交，等待接收人确认或打回。</p>
    <p v-else-if="selectedTask.status === 'COMPLETED'">任务已完成，可以查看提交版本和协作痕迹。</p>
    <p v-else>当前任务正在推进中，按下面的提交或接收区域继续处理。</p>
  </section>
</template>
