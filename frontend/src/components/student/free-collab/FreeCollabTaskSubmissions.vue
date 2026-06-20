<script setup>
defineProps({
  selectedTask: { type: Object, default: null },
  expandedSubmissionTaskId: { type: [Number, String, null], default: null },
  toggleSubmissionHistory: { type: Function, required: true },
  submissionStatusLabel: { type: Function, required: true },
  submissionLinks: { type: Function, required: true },
  submissionText: { type: Function, required: true },
  formatDate: { type: Function, required: true },
})
</script>

<template>
  <template v-if="selectedTask">
    <section v-if="selectedTask.latestSubmission" class="submission-box">
      <div class="section-head compact">
        <div>
          <span>最新提交</span>
          <h2>{{ selectedTask.latestSubmission.submittedByName || '成员' }} 的结果</h2>
        </div>
        <button
          v-if="selectedTask.submissions?.length"
          type="button"
          class="text-btn"
          @click="toggleSubmissionHistory(selectedTask.id)"
        >
          {{ expandedSubmissionTaskId === selectedTask.id ? '收起版本' : '查看版本' }}
        </button>
      </div>
      <p>{{ submissionText(selectedTask.latestSubmission) || '提交人未填写说明。' }}</p>
      <div class="submission-meta">
        <span>{{ submissionStatusLabel(selectedTask.latestSubmission.status) }}</span>
        <span>{{ formatDate(selectedTask.latestSubmission.createdAt || selectedTask.latestSubmission.submittedAt) }}</span>
      </div>
      <div v-if="submissionLinks(selectedTask).length" class="link-list">
        <a v-for="link in submissionLinks(selectedTask)" :key="link" :href="link" target="_blank" rel="noreferrer">查看材料</a>
      </div>
    </section>

    <section v-if="expandedSubmissionTaskId === selectedTask.id" class="history-list">
      <div class="section-head compact">
        <div>
          <span>版本记录</span>
          <h2>每次提交都保留痕迹</h2>
        </div>
      </div>
      <article v-for="submission in selectedTask.submissions || []" :key="submission.id">
        <div>
          <strong>{{ submission.submittedByName || '成员' }}</strong>
          <span>{{ formatDate(submission.createdAt) }} · {{ submissionStatusLabel(submission.status) }}</span>
        </div>
        <p>{{ submissionText(submission) || '未填写说明' }}</p>
      </article>
    </section>
  </template>
</template>
