<script setup>
import FileUploadZone from '../../common/FileUploadZone.vue'

defineProps({
  selectedTask: { type: Object, default: null },
  isSubmitting: { type: Boolean, default: false },
  submitDraft: { type: Object, required: true },
  reviewDraft: { type: Object, required: true },
  canSubmitTask: { type: Function, required: true },
  canReviewTask: { type: Function, required: true },
  submitTaskResult: { type: Function, required: true },
  reviewTask: { type: Function, required: true },
  reportUploadError: { type: Function, required: true },
})
</script>

<template>
  <template v-if="selectedTask">
    <section v-if="canSubmitTask(selectedTask)" class="submit-box work-form-card">
      <div class="section-head compact">
        <div>
          <span>提交给接收人</span>
          <h2>结果说明</h2>
        </div>
      </div>
      <textarea v-model="submitDraft.content" class="free-textarea" placeholder="写清楚已完成内容、待接收人需要检查什么" />
      <input v-model="submitDraft.links" class="free-input" placeholder="材料链接，可选。多个链接可换行或用空格分隔" />
      <FileUploadZone
        v-model="submitDraft.files"
        :disabled="isSubmitting"
        :max-files="6"
        :max-size-mb="20"
        @error="reportUploadError"
      />
      <div class="form-action-row">
        <p>可只提交说明，也可以附带文件或链接。提交后任务会进入待接收状态。</p>
        <button class="primary-btn" type="button" :disabled="isSubmitting" @click="submitTaskResult(selectedTask)">提交结果</button>
      </div>
    </section>

    <section v-if="canReviewTask(selectedTask)" class="submit-box work-form-card review-card">
      <div class="section-head compact">
        <div>
          <span>接收处理</span>
          <h2>确认能否交接</h2>
        </div>
      </div>
      <textarea v-model="reviewDraft.comment" class="free-textarea" placeholder="接收说明或打回原因，可选" />
      <div class="form-action-row">
        <p>接收表示这一步可以进入后续协作；打回会让负责人重新处理。</p>
        <div class="button-row">
          <button class="primary-btn" type="button" :disabled="isSubmitting" @click="reviewTask(selectedTask, true)">确认接收</button>
          <button class="danger-btn" type="button" :disabled="isSubmitting" @click="reviewTask(selectedTask, false)">打回重做</button>
        </div>
      </div>
    </section>
  </template>
</template>
